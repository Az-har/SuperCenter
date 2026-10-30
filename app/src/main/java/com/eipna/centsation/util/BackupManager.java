package com.eipna.centsation.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eipna.centsation.data.Database;
import com.eipna.centsation.data.saving.Saving;
import com.eipna.centsation.data.saving.SavingRepository;
import com.eipna.centsation.data.transaction.Transaction;
import com.eipna.centsation.data.transaction.TransactionRepository;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BackupManager {

    private static final String TAG = "BackupManager";
    public static final String BACKUP_DIR_NAME = "Centsation";
    public static final String AUTO_BACKUP_FILE_NAME = "centsation_auto_backup.json";
    public static final int CURRENT_BACKUP_VERSION = 1;

    private static final ExecutorService BACKUP_EXECUTOR = Executors.newSingleThreadExecutor();

    public static class BackupInfo {
        public long timestamp;
        public String dateFormatted;
        public int savingsCount;
        public int transactionsCount;
        public long fileSizeBytes;
        public File sourceFile;
        public Uri sourceUri;
    }

    public static void triggerAutoBackup(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        BACKUP_EXECUTOR.execute(() -> {
            try {
                PreferenceUtil prefs = new PreferenceUtil(appContext);
                if (!prefs.isAutoBackupEnabled()) return;
                performAutoBackup(appContext);
            } catch (Exception e) {
                Log.e(TAG, "Error performing auto backup", e);
            }
        });
    }

    public static boolean performAutoBackup(@NonNull Context context) {
        try {
            String jsonPayload = generateBackupJSON(context);
            if (jsonPayload == null) return false;

            boolean wroteAtLeastOne = false;

            // 1. Write to Public Documents/Centsation (Survives app uninstallation)
            File docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            File centsationDocs = new File(docsDir, BACKUP_DIR_NAME);
            if (centsationDocs.exists() || centsationDocs.mkdirs()) {
                File target = new File(centsationDocs, AUTO_BACKUP_FILE_NAME);
                if (writeStringToFile(target, jsonPayload)) {
                    wroteAtLeastOne = true;
                }
            }

            // 2. Write to Public Downloads/Centsation (Secondary fallback)
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File centsationDownloads = new File(downloadsDir, BACKUP_DIR_NAME);
            if (centsationDownloads.exists() || centsationDownloads.mkdirs()) {
                File target = new File(centsationDownloads, AUTO_BACKUP_FILE_NAME);
                if (writeStringToFile(target, jsonPayload)) {
                    wroteAtLeastOne = true;
                }
            }

            // 3. Write via MediaStore on Android 10+ if supported
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeViaMediaStore(context, jsonPayload);
            }

            if (wroteAtLeastOne) {
                new PreferenceUtil(context).setLastBackupTimestamp(System.currentTimeMillis());
            }
            return wroteAtLeastOne;
        } catch (Exception e) {
            Log.e(TAG, "Auto-backup creation failed", e);
            return false;
        }
    }

    public static String generateBackupJSON(@NonNull Context context) throws JSONException {
        try (SavingRepository savingRepo = new SavingRepository(context);
             TransactionRepository txRepo = new TransactionRepository(context)) {

            List<Saving> savings = savingRepo.getAllSavings();
            List<Transaction> transactions = txRepo.getAll();

            JSONArray savingJsonArray = new JSONArray();
            for (Saving saving : savings) {
                JSONObject obj = new JSONObject();
                obj.put(Database.COLUMN_SAVING_ID, saving.getID());
                obj.put(Database.COLUMN_SAVING_NAME, saving.getName());
                obj.put(Database.COLUMN_SAVING_CURRENT_SAVING, saving.getCurrentSaving());
                obj.put(Database.COLUMN_SAVING_GOAL, saving.getGoal());
                obj.put(Database.COLUMN_SAVING_NOTES, saving.getNotes() == null ? "" : saving.getNotes());
                obj.put(Database.COLUMN_SAVING_IS_ARCHIVED, saving.getIsArchived());
                obj.put(Database.COLUMN_SAVING_DEADLINE, saving.getDeadline());
                savingJsonArray.put(obj);
            }

            JSONArray transactionJsonArray = new JSONArray();
            for (Transaction transaction : transactions) {
                JSONObject obj = new JSONObject();
                obj.put(Database.COLUMN_TRANSACTION_ID, transaction.getID());
                obj.put(Database.COLUMN_TRANSACTION_SAVING_ID, transaction.getSavingID());
                obj.put(Database.COLUMN_TRANSACTION_AMOUNT, transaction.getAmount());
                obj.put(Database.COLUMN_TRANSACTION_TYPE, transaction.getType());
                obj.put(Database.COLUMN_TRANSACTION_DATE, transaction.getDate());
                transactionJsonArray.put(obj);
            }

            JSONObject root = new JSONObject();
            root.put("app", "Centsation");
            root.put("version", CURRENT_BACKUP_VERSION);
            root.put("timestamp", System.currentTimeMillis());
            root.put("savings_count", savings.size());
            root.put("transactions_count", transactions.size());

            // Support both direct table names and generic keys for compatibility
            root.put(Database.TABLE_SAVING, savingJsonArray);
            root.put(Database.TABLE_TRANSACTION, transactionJsonArray);

            return root.toString(2);
        }
    }

    @Nullable
    public static BackupInfo detectExistingBackup(@NonNull Context context) {
        // Candidate file paths that persist after uninstallation
        List<File> candidates = new ArrayList<>();

        File docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        candidates.add(new File(docsDir, BACKUP_DIR_NAME + "/" + AUTO_BACKUP_FILE_NAME));
        candidates.add(new File(docsDir, BACKUP_DIR_NAME + "/exported_savings.json"));

        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        candidates.add(new File(downloadsDir, BACKUP_DIR_NAME + "/" + AUTO_BACKUP_FILE_NAME));
        candidates.add(new File(downloadsDir, AUTO_BACKUP_FILE_NAME));
        candidates.add(new File(downloadsDir, "exported_savings.json"));

        for (File candidate : candidates) {
            if (candidate.exists() && candidate.isFile() && candidate.length() > 0) {
                BackupInfo info = inspectFile(context, candidate);
                if (info != null) return info;
            }
        }

        // Check MediaStore query on Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            BackupInfo mediaStoreInfo = queryMediaStoreForBackup(context);
            if (mediaStoreInfo != null) return mediaStoreInfo;
        }

        return null;
    }

    @Nullable
    public static BackupInfo inspectFile(Context context, File file) {
        try (InputStream in = new FileInputStream(file)) {
            return inspectInputStream(context, in, file.length(), file, null);
        } catch (Exception e) {
            Log.d(TAG, "Cannot read candidate file: " + file.getAbsolutePath());
            return null;
        }
    }

    @Nullable
    public static BackupInfo inspectUri(Context context, Uri uri) {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            return inspectInputStream(context, in, -1, null, uri);
        } catch (Exception e) {
            Log.e(TAG, "Cannot inspect uri: " + uri, e);
            return null;
        }
    }

    private static BackupInfo inspectInputStream(Context context, InputStream in, long size, File file, Uri uri) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JSONObject root = new JSONObject(sb.toString());

            JSONArray savingsArray = root.optJSONArray(Database.TABLE_SAVING);
            JSONArray txArray = root.optJSONArray(Database.TABLE_TRANSACTION);

            BackupInfo info = new BackupInfo();
            info.timestamp = root.optLong("timestamp", file != null ? file.lastModified() : System.currentTimeMillis());
            info.savingsCount = savingsArray != null ? savingsArray.length() : root.optInt("savings_count", 0);
            info.transactionsCount = txArray != null ? txArray.length() : root.optInt("transactions_count", 0);
            info.fileSizeBytes = size > 0 ? size : sb.length();
            info.sourceFile = file;
            info.sourceUri = uri;

            PreferenceUtil prefs = new PreferenceUtil(context);
            info.dateFormatted = DateParser.getStringDate(info.timestamp, prefs.getDeadlineFormat());

            return info;
        } catch (Exception e) {
            Log.d(TAG, "Failed parsing JSON backup header", e);
            return null;
        }
    }

    public static boolean restoreFromPayload(@NonNull Context context, @NonNull String jsonContent) {
        try (SavingRepository savingRepo = new SavingRepository(context)) {
            JSONObject jsonImport = new JSONObject(jsonContent);

            JSONArray savingsArray = jsonImport.optJSONArray(Database.TABLE_SAVING);
            if (savingsArray == null) savingsArray = jsonImport.optJSONArray("savings");

            JSONArray transactionsArray = jsonImport.optJSONArray(Database.TABLE_TRANSACTION);
            if (transactionsArray == null) transactionsArray = jsonImport.optJSONArray("transactions");

            if (savingsArray == null) return false;

            List<Saving> savings = parseSavings(savingsArray);
            List<Transaction> transactions = transactionsArray != null ? parseTransactions(transactionsArray) : new ArrayList<>();

            savingRepo.bulkImport(savings, transactions);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Restore from payload failed", e);
            return false;
        }
    }

    public static boolean restoreFromFile(@NonNull Context context, @NonNull File file) {
        try (InputStream in = new FileInputStream(file)) {
            String content = readStreamToString(in);
            return restoreFromPayload(context, content);
        } catch (Exception e) {
            Log.e(TAG, "Restore from file failed: " + file.getAbsolutePath(), e);
            return false;
        }
    }

    public static boolean restoreFromUri(@NonNull Context context, @NonNull Uri uri) {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return false;
            String content = readStreamToString(in);
            return restoreFromPayload(context, content);
        } catch (Exception e) {
            Log.e(TAG, "Restore from uri failed: " + uri, e);
            return false;
        }
    }

    private static String readStreamToString(InputStream in) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }

    private static List<Saving> parseSavings(JSONArray array) throws JSONException {
        List<Saving> result = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            Saving saving = new Saving();
            saving.setID(obj.getString(Database.COLUMN_SAVING_ID));
            saving.setName(obj.getString(Database.COLUMN_SAVING_NAME));
            saving.setCurrentSaving(obj.getDouble(Database.COLUMN_SAVING_CURRENT_SAVING));
            saving.setGoal(obj.getDouble(Database.COLUMN_SAVING_GOAL));
            saving.setNotes(obj.optString(Database.COLUMN_SAVING_NOTES, ""));
            saving.setIsArchived(obj.getInt(Database.COLUMN_SAVING_IS_ARCHIVED));
            saving.setDeadline(obj.optLong(Database.COLUMN_SAVING_DEADLINE, AlarmSetter.NO_ALARM));
            result.add(saving);
        }
        return result;
    }

    private static List<Transaction> parseTransactions(JSONArray array) throws JSONException {
        List<Transaction> result = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            Transaction transaction = new Transaction();
            transaction.setSavingID(obj.getString(Database.COLUMN_TRANSACTION_SAVING_ID));
            transaction.setAmount(obj.getDouble(Database.COLUMN_TRANSACTION_AMOUNT));
            transaction.setType(obj.getString(Database.COLUMN_TRANSACTION_TYPE));
            transaction.setDate(obj.optLong(Database.COLUMN_TRANSACTION_DATE, System.currentTimeMillis()));
            result.add(transaction);
        }
        return result;
    }

    private static boolean writeStringToFile(File target, String content) {
        try (FileOutputStream out = new FileOutputStream(target)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.flush();
            return true;
        } catch (Exception e) {
            Log.d(TAG, "Could not write to file " + target.getAbsolutePath(), e);
            return false;
        }
    }

    private static void writeViaMediaStore(Context context, String content) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        try {
            ContentResolver resolver = context.getContentResolver();
            Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);

            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, AUTO_BACKUP_FILE_NAME);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/json");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/" + BACKUP_DIR_NAME);
            values.put(MediaStore.Downloads.IS_PENDING, 1);

            Uri uri = resolver.insert(collection, values);
            if (uri != null) {
                try (OutputStream os = resolver.openOutputStream(uri)) {
                    if (os != null) {
                        os.write(content.getBytes(StandardCharsets.UTF_8));
                        os.flush();
                    }
                }
                values.clear();
                values.put(MediaStore.Downloads.IS_PENDING, 0);
                resolver.update(uri, values, null, null);
            }
        } catch (Exception e) {
            Log.d(TAG, "MediaStore backup write ignored", e);
        }
    }

    @Nullable
    private static BackupInfo queryMediaStoreForBackup(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null;
        try {
            ContentResolver resolver = context.getContentResolver();
            Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            String selection = MediaStore.Downloads.DISPLAY_NAME + " = ?";
            String[] args = new String[]{AUTO_BACKUP_FILE_NAME};

            try (Cursor cursor = resolver.query(collection, new String[]{MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME}, selection, args, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID));
                    Uri uri = Uri.withAppendedPath(collection, String.valueOf(id));
                    return inspectUri(context, uri);
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "MediaStore query for backup failed", e);
        }
        return null;
    }
}
