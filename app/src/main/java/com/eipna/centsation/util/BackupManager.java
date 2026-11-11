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
import com.eipna.centsation.data.saving.SavingCategory;
import com.eipna.centsation.data.saving.SavingRepository;
import com.eipna.centsation.data.debt.Debt;
import com.eipna.centsation.data.debt.DebtRepository;
import com.eipna.centsation.data.debt.DebtTransaction;
import com.eipna.centsation.data.transaction.Transaction;
import com.eipna.centsation.data.transaction.TransactionRepository;
import com.eipna.centsation.habit.data.entity.TrackerEntity;
import com.eipna.centsation.habit.data.entity.TrackerLogEntity;
import com.eipna.centsation.habit.data.repository.TrackerRepository;
import com.eipna.centsation.habit.notification.TrackerAlarmScheduler;
import com.eipna.centsation.habit.widget.HabitWidgetSyncManager;

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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class BackupManager {

    private static final String TAG = "BackupManager";
    public static final String BACKUP_DIR_NAME = "SuperCenter";
    public static final String LEGACY_BACKUP_DIR_NAME = "Centsation";
    public static final String AUTO_BACKUP_FILE_NAME = "supercenter_auto_backup.json";
    public static final String LEGACY_AUTO_BACKUP_FILE_NAME = "centsation_auto_backup.json";
    public static final int CURRENT_BACKUP_VERSION = 2;
    public static final String TABLE_TRACKERS = "trackers";
    public static final String TABLE_TRACKER_LOGS = "tracker_logs";

    private static final long DEBOUNCE_DELAY_MS = 2500L;
    private static final ScheduledExecutorService BACKUP_SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "backup-debounce");
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private static ScheduledFuture<?> sPendingBackupTask;
    private static final Object BACKUP_LOCK = new Object();

    public static class BackupInfo {
        public long timestamp;
        public String dateFormatted;
        public int savingsCount;
        public int transactionsCount;
        public int debtsCount;
        public int debtTransactionsCount;
        public int trackersCount;
        public int trackerLogsCount;
        public long fileSizeBytes;
        public File sourceFile;
        public Uri sourceUri;
        public String currency;
    }

    public static void triggerAutoBackup(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        synchronized (BACKUP_LOCK) {
            if (sPendingBackupTask != null && !sPendingBackupTask.isDone()) {
                sPendingBackupTask.cancel(false);
            }
            sPendingBackupTask = BACKUP_SCHEDULER.schedule(() -> {
                try {
                    PreferenceUtil prefs = new PreferenceUtil(appContext);
                    if (!prefs.isAutoBackupEnabled()) return;
                    performAutoBackup(appContext);
                } catch (Exception e) {
                    Log.e(TAG, "Error performing auto backup", e);
                }
            }, DEBOUNCE_DELAY_MS, TimeUnit.MILLISECONDS);
        }
    }

    public static boolean performAutoBackup(@NonNull Context context) {
        try {
            String jsonPayload = generateBackupJSON(context);
            if (jsonPayload == null) return false;

            boolean wroteAtLeastOne = false;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Scoped Storage (Android 10+): use MediaStore without raw external paths
                if (writeViaMediaStore(context, jsonPayload)) {
                    wroteAtLeastOne = true;
                }
            } else {
                // Legacy external public directories (Android 7-9)
                File docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                if (docsDir != null) {
                    File centsationDocs = new File(docsDir, BACKUP_DIR_NAME);
                    if (centsationDocs.exists() || centsationDocs.mkdirs()) {
                        File target = new File(centsationDocs, AUTO_BACKUP_FILE_NAME);
                        if (writeStringToFile(target, jsonPayload)) {
                            wroteAtLeastOne = true;
                        }
                    }
                }

                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (downloadsDir != null) {
                    File centsationDownloads = new File(downloadsDir, BACKUP_DIR_NAME);
                    if (centsationDownloads.exists() || centsationDownloads.mkdirs()) {
                        File target = new File(centsationDownloads, AUTO_BACKUP_FILE_NAME);
                        if (writeStringToFile(target, jsonPayload)) {
                            wroteAtLeastOne = true;
                        }
                    }
                }
            }

            // Always keep an app-scoped fallback copy for local diagnostics / recovery
            File localBackupDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (localBackupDir != null && (localBackupDir.exists() || localBackupDir.mkdirs())) {
                writeStringToFile(new File(localBackupDir, AUTO_BACKUP_FILE_NAME), jsonPayload);
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
             TransactionRepository txRepo = new TransactionRepository(context);
             DebtRepository debtRepo = new DebtRepository(context)) {

            List<Saving> savings = savingRepo.getAllSavings();
            List<Transaction> transactions = txRepo.getAll();
            List<Debt> debts = debtRepo.getAllDebts();
            List<DebtTransaction> debtTransactions = debtRepo.getAllTransactions();
            TrackerRepository trackerRepo = TrackerRepository.getInstance(context);
            List<TrackerEntity> trackers = trackerRepo.exportTrackersSync();
            List<TrackerLogEntity> trackerLogs = trackerRepo.exportTrackerLogsSync();

            JSONArray savingJsonArray = new JSONArray();
            for (Saving saving : savings) {
                JSONObject obj = new JSONObject();
                obj.put(Database.COLUMN_SAVING_ID, saving.getID());
                obj.put(Database.COLUMN_SAVING_NAME, saving.getName());
                obj.put(Database.COLUMN_SAVING_CURRENT_SAVING, saving.getCurrentSaving());
                obj.put(Database.COLUMN_SAVING_GOAL, saving.getGoal());
                obj.put(Database.COLUMN_SAVING_NOTES, saving.getNotes() == null ? "" : saving.getNotes());
                obj.put(Database.COLUMN_SAVING_CATEGORY, saving.getCategory());
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

            JSONArray debtJsonArray = new JSONArray();
            for (Debt debt : debts) {
                JSONObject obj = new JSONObject();
                obj.put(Database.COLUMN_DEBT_ID, debt.getId());
                obj.put(Database.COLUMN_DEBT_NAME, debt.getName());
                obj.put(Database.COLUMN_DEBT_TOTAL_AMOUNT, debt.getTotalAmount());
                obj.put(Database.COLUMN_DEBT_AMOUNT_PAID, debt.getAmountPaid());
                obj.put(Database.COLUMN_DEBT_NOTES, debt.getNotes() == null ? "" : debt.getNotes());
                obj.put(Database.COLUMN_DEBT_CATEGORY, debt.getCategory());
                obj.put(Database.COLUMN_DEBT_IS_ARCHIVED, debt.getIsArchived());
                obj.put(Database.COLUMN_DEBT_DEADLINE, debt.getDeadline());
                obj.put(Database.COLUMN_DEBT_CREATED_AT, debt.getCreatedAt());
                obj.put(Database.COLUMN_DEBT_TYPE, debt.getDebtType());
                debtJsonArray.put(obj);
            }

            JSONArray debtTransactionJsonArray = new JSONArray();
            for (DebtTransaction debtTx : debtTransactions) {
                JSONObject obj = new JSONObject();
                obj.put(Database.COLUMN_DEBT_TRANSACTION_ID, debtTx.getId());
                obj.put(Database.COLUMN_DEBT_TRANSACTION_DEBT_ID, debtTx.getDebtId());
                obj.put(Database.COLUMN_DEBT_TRANSACTION_AMOUNT, debtTx.getAmount());
                obj.put(Database.COLUMN_DEBT_TRANSACTION_DATE, debtTx.getDate());
                obj.put(Database.COLUMN_DEBT_TRANSACTION_NOTES, debtTx.getNotes() == null ? "" : debtTx.getNotes());
                debtTransactionJsonArray.put(obj);
            }

            PreferenceUtil prefs = new PreferenceUtil(context);
            JSONObject prefsObj = new JSONObject();
            prefsObj.put("currency", prefs.getCurrency());
            prefsObj.put("theme", prefs.getTheme());
            prefsObj.put("contrast", prefs.getContrast());
            prefsObj.put("deadline_format", prefs.getDeadlineFormat());
            prefsObj.put("dynamic_colors", prefs.isDynamicColors());
            prefsObj.put("screen_privacy", prefs.isScreenPrivacyEnabled());
            prefsObj.put("auto_backup", prefs.isAutoBackupEnabled());
            prefsObj.put("sort_criteria", prefs.getSortCriteria());
            prefsObj.put("sort_order", prefs.getSortOrder());
            prefsObj.put("regional_number_format", prefs.getRegionalNumberFormat());

            return buildBackupJsonPayload(
                    savingJsonArray,
                    transactionJsonArray,
                    debtJsonArray,
                    debtTransactionJsonArray,
                    serializeTrackers(trackers),
                    serializeTrackerLogs(trackerLogs),
                    prefsObj,
                    prefs.getCurrency(),
                    System.currentTimeMillis()
            );
        }
    }

    @NonNull
    public static String buildBackupJsonPayload(
            @NonNull JSONArray savingJsonArray,
            @NonNull JSONArray transactionJsonArray,
            @NonNull JSONArray debtJsonArray,
            @NonNull JSONArray debtTransactionJsonArray,
            @NonNull JSONArray trackerJsonArray,
            @NonNull JSONArray trackerLogJsonArray,
            @Nullable JSONObject prefsObj,
            @Nullable String currency,
            long timestamp) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("app", "Centsation");
        root.put("version", CURRENT_BACKUP_VERSION);
        root.put("timestamp", timestamp);
        root.put("savings_count", savingJsonArray.length());
        root.put("transactions_count", transactionJsonArray.length());
        root.put("debts_count", debtJsonArray.length());
        root.put("debt_transactions_count", debtTransactionJsonArray.length());
        root.put("trackers_count", trackerJsonArray.length());
        root.put("tracker_logs_count", trackerLogJsonArray.length());

        if (prefsObj != null) {
            root.put("preferences", prefsObj);
        }
        if (currency != null) {
            root.put("currency", currency);
        }

        // Support both direct table names and generic keys for compatibility
        root.put(Database.TABLE_SAVING, savingJsonArray);
        root.put(Database.TABLE_TRANSACTION, transactionJsonArray);
        root.put(Database.TABLE_DEBT, debtJsonArray);
        root.put(Database.TABLE_DEBT_TRANSACTION, debtTransactionJsonArray);
        root.put(TABLE_TRACKERS, trackerJsonArray);
        root.put(TABLE_TRACKER_LOGS, trackerLogJsonArray);

        return root.toString(2);
    }

    @NonNull
    public static JSONArray serializeTrackers(@NonNull List<TrackerEntity> trackers) throws JSONException {
        JSONArray array = new JSONArray();
        for (TrackerEntity tracker : trackers) {
            if (tracker == null) continue;
            JSONObject obj = new JSONObject();
            obj.put("id", tracker.getId());
            obj.put("title", tracker.getTitle());
            obj.put("baseDailyTarget", tracker.getBaseDailyTarget());
            obj.put("unit", tracker.getUnit());
            obj.put("scheduledHour", tracker.getScheduledHour());
            obj.put("scheduledMinute", tracker.getScheduledMinute());
            obj.put("syncCalendar", tracker.getSyncCalendar());
            if (tracker.getCalendarId() != null) {
                obj.put("calendarId", tracker.getCalendarId());
            }
            if (tracker.getCalendarEventId() != null) {
                obj.put("calendarEventId", tracker.getCalendarEventId());
            }
            obj.put("isActive", tracker.isActive());
            obj.put("createdAt", tracker.getCreatedAt());
            array.put(obj);
        }
        return array;
    }

    @NonNull
    public static JSONArray serializeTrackerLogs(@NonNull List<TrackerLogEntity> logs) throws JSONException {
        JSONArray array = new JSONArray();
        for (TrackerLogEntity log : logs) {
            if (log == null) continue;
            JSONObject obj = new JSONObject();
            obj.put("id", log.getId());
            obj.put("trackerId", log.getTrackerId());
            obj.put("date", log.getDate());
            obj.put("completedCount", log.getCompletedCount());
            obj.put("timestamp", log.getTimestamp());
            array.put(obj);
        }
        return array;
    }

    @Nullable
    public static BackupInfo detectExistingBackup(@NonNull Context context) {
        try {
            // Check MediaStore query first on Android 10+ (Scoped Storage)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                BackupInfo mediaStoreInfo = queryMediaStoreForBackup(context);
                if (mediaStoreInfo != null) return mediaStoreInfo;
            }

            // Candidate file paths (Android 7-9 public paths + app-scoped backup mirror)
            List<File> candidates = new ArrayList<>();

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                try {
                    File docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                    if (docsDir != null) {
                        candidates.add(new File(docsDir, BACKUP_DIR_NAME + "/" + AUTO_BACKUP_FILE_NAME));
                        candidates.add(new File(docsDir, BACKUP_DIR_NAME + "/exported_savings.json"));
                        candidates.add(new File(docsDir, LEGACY_BACKUP_DIR_NAME + "/" + LEGACY_AUTO_BACKUP_FILE_NAME));
                        candidates.add(new File(docsDir, LEGACY_BACKUP_DIR_NAME + "/exported_savings.json"));
                    }
                } catch (Throwable ignored) {
                }

                try {
                    File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (downloadsDir != null) {
                        candidates.add(new File(downloadsDir, BACKUP_DIR_NAME + "/" + AUTO_BACKUP_FILE_NAME));
                        candidates.add(new File(downloadsDir, LEGACY_BACKUP_DIR_NAME + "/" + LEGACY_AUTO_BACKUP_FILE_NAME));
                        candidates.add(new File(downloadsDir, AUTO_BACKUP_FILE_NAME));
                        candidates.add(new File(downloadsDir, "exported_savings.json"));
                    }
                } catch (Throwable ignored) {
                }
            }

            for (File candidate : candidates) {
                try {
                    if (candidate.exists() && candidate.isFile() && candidate.length() > 0) {
                        BackupInfo info = inspectFile(context, candidate);
                        if (info != null) return info;
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error in detectExistingBackup", t);
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
            JSONArray debtsArray = root.optJSONArray(Database.TABLE_DEBT);
            JSONArray debtTxArray = root.optJSONArray(Database.TABLE_DEBT_TRANSACTION);
            JSONArray trackersArray = root.optJSONArray(TABLE_TRACKERS);
            if (trackersArray == null) trackersArray = root.optJSONArray("habits");
            JSONArray trackerLogsArray = root.optJSONArray(TABLE_TRACKER_LOGS);
            if (trackerLogsArray == null) trackerLogsArray = root.optJSONArray("habit_logs");

            BackupInfo info = new BackupInfo();
            info.timestamp = root.optLong("timestamp", file != null ? file.lastModified() : System.currentTimeMillis());
            info.savingsCount = savingsArray != null ? savingsArray.length() : root.optInt("savings_count", 0);
            info.transactionsCount = txArray != null ? txArray.length() : root.optInt("transactions_count", 0);
            info.debtsCount = debtsArray != null ? debtsArray.length() : root.optInt("debts_count", 0);
            info.debtTransactionsCount = debtTxArray != null ? debtTxArray.length() : root.optInt("debt_transactions_count", 0);
            info.trackersCount = trackersArray != null ? trackersArray.length() : root.optInt("trackers_count", 0);
            info.trackerLogsCount = trackerLogsArray != null ? trackerLogsArray.length() : root.optInt("tracker_logs_count", 0);
            info.fileSizeBytes = size > 0 ? size : sb.length();
            info.sourceFile = file;
            info.sourceUri = uri;

            PreferenceUtil prefs = new PreferenceUtil(context);
            info.dateFormatted = DateParser.getStringDate(info.timestamp, prefs.getDeadlineFormat());

            JSONObject prefsObj = root.optJSONObject("preferences");
            if (prefsObj == null) prefsObj = root.optJSONObject("settings");
            if (prefsObj != null && prefsObj.has("currency")) {
                info.currency = prefsObj.optString("currency");
            } else if (root.has("currency")) {
                info.currency = root.optString("currency");
            }

            return info;
        } catch (Exception e) {
            Log.d(TAG, "Failed parsing JSON backup header", e);
            return null;
        }
    }

    public static boolean restoreFromPayload(@NonNull Context context, @NonNull String jsonContent) {
        try (SavingRepository savingRepo = new SavingRepository(context);
             DebtRepository debtRepo = new DebtRepository(context)) {
            JSONObject jsonImport = new JSONObject(jsonContent);

            JSONArray savingsArray = jsonImport.optJSONArray(Database.TABLE_SAVING);
            if (savingsArray == null) savingsArray = jsonImport.optJSONArray("savings");

            JSONArray transactionsArray = jsonImport.optJSONArray(Database.TABLE_TRANSACTION);
            if (transactionsArray == null) transactionsArray = jsonImport.optJSONArray("transactions");

            if (savingsArray != null) {
                List<Saving> savings = parseSavings(savingsArray);
                List<Transaction> transactions = transactionsArray != null ? parseTransactions(transactionsArray) : new ArrayList<>();
                savingRepo.bulkImport(savings, transactions);
            }

            JSONArray debtsArray = jsonImport.optJSONArray(Database.TABLE_DEBT);
            if (debtsArray == null) debtsArray = jsonImport.optJSONArray("debts");

            JSONArray debtTransactionsArray = jsonImport.optJSONArray(Database.TABLE_DEBT_TRANSACTION);
            if (debtTransactionsArray == null) debtTransactionsArray = jsonImport.optJSONArray("debt_transactions");

            if (debtsArray != null) {
                List<Debt> debts = parseDebts(debtsArray);
                List<DebtTransaction> debtTransactions = debtTransactionsArray != null ? parseDebtTransactions(debtTransactionsArray) : new ArrayList<>();
                debtRepo.bulkImport(debts, debtTransactions);
            }

            JSONArray trackersArray = jsonImport.optJSONArray(TABLE_TRACKERS);
            if (trackersArray == null) trackersArray = jsonImport.optJSONArray("habits");

            JSONArray trackerLogsArray = jsonImport.optJSONArray(TABLE_TRACKER_LOGS);
            if (trackerLogsArray == null) trackerLogsArray = jsonImport.optJSONArray("habit_logs");

            if (trackersArray != null) {
                List<TrackerEntity> trackers = parseTrackers(trackersArray);
                List<TrackerLogEntity> trackerLogs = trackerLogsArray != null ? parseTrackerLogs(trackerLogsArray) : new ArrayList<>();
                TrackerRepository.getInstance(context).bulkImportSync(trackers, trackerLogs);
                notifyTrackersRestored(context);
            }

            if (savingsArray == null && debtsArray == null && trackersArray == null) return false;

            // Restore user preferences/settings if present
            PreferenceUtil prefs = new PreferenceUtil(context);
            JSONObject prefsObj = jsonImport.optJSONObject("preferences");
            if (prefsObj == null) {
                prefsObj = jsonImport.optJSONObject("settings");
            }

            if (prefsObj != null) {
                if (prefsObj.has("currency")) {
                    prefs.setCurrency(prefsObj.getString("currency"));
                }
                if (prefsObj.has("theme")) {
                    prefs.setTheme(prefsObj.getString("theme"));
                    com.eipna.centsation.CentsationApp.applyTheme(prefsObj.getString("theme"));
                }
                if (prefsObj.has("contrast")) {
                    prefs.setContrast(prefsObj.getString("contrast"));
                }
                if (prefsObj.has("deadline_format")) {
                    prefs.setDeadlineFormat(prefsObj.getString("deadline_format"));
                }
                if (prefsObj.has("dynamic_colors")) {
                    prefs.setDynamicColors(prefsObj.getBoolean("dynamic_colors"));
                }
                if (prefsObj.has("screen_privacy")) {
                    prefs.setScreenPrivacy(prefsObj.getBoolean("screen_privacy"));
                }
                if (prefsObj.has("auto_backup")) {
                    prefs.setAutoBackupEnabled(prefsObj.getBoolean("auto_backup"));
                }
                if (prefsObj.has("sort_criteria")) {
                    prefs.setSortCriteria(prefsObj.getString("sort_criteria"));
                }
                if (prefsObj.has("sort_order")) {
                    prefs.setSortOrder(prefsObj.getBoolean("sort_order"));
                }
                if (prefsObj.has("regional_number_format")) {
                    prefs.setRegionalNumberFormat(prefsObj.getString("regional_number_format"));
                }
            } else if (jsonImport.has("currency")) {
                prefs.setCurrency(jsonImport.getString("currency"));
            } else {
                // If restoring an older backup without preferences and current currency is PHP (old legacy default),
                // migrate to INR as user's primary currency
                if ("PHP".equalsIgnoreCase(prefs.getCurrency())) {
                    prefs.setCurrency(com.eipna.centsation.data.Currency.INDIAN_RUPEE.CODE);
                }
            }

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
            saving.setCategory(obj.optString(Database.COLUMN_SAVING_CATEGORY, SavingCategory.GENERAL.getId()));
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

    private static List<Debt> parseDebts(JSONArray array) throws JSONException {
        List<Debt> result = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            Debt debt = new Debt();
            debt.setId(obj.getString(Database.COLUMN_DEBT_ID));
            debt.setName(obj.getString(Database.COLUMN_DEBT_NAME));
            debt.setTotalAmount(obj.getDouble(Database.COLUMN_DEBT_TOTAL_AMOUNT));
            debt.setAmountPaid(obj.optDouble(Database.COLUMN_DEBT_AMOUNT_PAID, 0.0));
            debt.setNotes(obj.optString(Database.COLUMN_DEBT_NOTES, ""));
            debt.setCategory(obj.optString(Database.COLUMN_DEBT_CATEGORY, "general"));
            debt.setIsArchived(obj.optInt(Database.COLUMN_DEBT_IS_ARCHIVED, 0));
            debt.setDeadline(obj.optLong(Database.COLUMN_DEBT_DEADLINE, AlarmSetter.NO_ALARM));
            debt.setCreatedAt(obj.optLong(Database.COLUMN_DEBT_CREATED_AT, System.currentTimeMillis()));
            debt.setDebtType(obj.optInt(Database.COLUMN_DEBT_TYPE, Debt.TYPE_I_OWE));
            result.add(debt);
        }
        return result;
    }

    private static List<DebtTransaction> parseDebtTransactions(JSONArray array) throws JSONException {
        List<DebtTransaction> result = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            DebtTransaction tx = new DebtTransaction();
            if (obj.has(Database.COLUMN_DEBT_TRANSACTION_ID)) {
                tx.setId(obj.getLong(Database.COLUMN_DEBT_TRANSACTION_ID));
            }
            tx.setDebtId(obj.getString(Database.COLUMN_DEBT_TRANSACTION_DEBT_ID));
            tx.setAmount(obj.getDouble(Database.COLUMN_DEBT_TRANSACTION_AMOUNT));
            tx.setDate(obj.optLong(Database.COLUMN_DEBT_TRANSACTION_DATE, System.currentTimeMillis()));
            tx.setNotes(obj.optString(Database.COLUMN_DEBT_TRANSACTION_NOTES, ""));
            result.add(tx);
        }
        return result;
    }

    @NonNull
    public static List<TrackerEntity> parseTrackers(@NonNull JSONArray array) throws JSONException {
        List<TrackerEntity> result = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            long id = obj.optLong("id", 0L);
            String title = obj.getString("title");
            int baseDailyTarget = Math.max(1, obj.optInt("baseDailyTarget", 1));
            String unit = obj.optString("unit", "reps");
            int scheduledHour = obj.optInt("scheduledHour", 21);
            int scheduledMinute = obj.optInt("scheduledMinute", 0);
            boolean syncCalendar = obj.optBoolean("syncCalendar", false);
            Long calendarId = obj.has("calendarId") && !obj.isNull("calendarId") ? obj.getLong("calendarId") : null;
            Long calendarEventId = obj.has("calendarEventId") && !obj.isNull("calendarEventId") ? obj.getLong("calendarEventId") : null;
            boolean isActive = obj.optBoolean("isActive", true);
            long createdAt = obj.optLong("createdAt", System.currentTimeMillis());

            result.add(new TrackerEntity(
                    id, title, baseDailyTarget, unit,
                    scheduledHour, scheduledMinute, syncCalendar,
                    calendarId, calendarEventId, isActive, createdAt
            ));
        }
        return result;
    }

    @NonNull
    public static List<TrackerLogEntity> parseTrackerLogs(@NonNull JSONArray array) throws JSONException {
        List<TrackerLogEntity> result = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            long id = obj.optLong("id", 0L);
            long trackerId = obj.getLong("trackerId");
            String date = obj.getString("date");
            int completedCount = Math.max(0, obj.optInt("completedCount", 0));
            long timestamp = obj.optLong("timestamp", System.currentTimeMillis());

            result.add(new TrackerLogEntity(id, trackerId, date, completedCount, timestamp));
        }
        return result;
    }

    private static void notifyTrackersRestored(@NonNull Context context) {
        try {
            HabitWidgetSyncManager.INSTANCE.enqueueUpdate(context);
        } catch (Throwable t) {
            Log.w(TAG, "Post-restore widget sync failed", t);
        }
        try {
            TrackerAlarmScheduler.INSTANCE.rescheduleAllAsync(context);
        } catch (Throwable t) {
            Log.w(TAG, "Post-restore alarm reschedule failed", t);
        }
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

    private static boolean writeViaMediaStore(Context context, String content) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false;
        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

        // Try overwriting our own existing MediaStore backup entry first to avoid duplicate files
        try (Cursor cursor = resolver.query(
                collection,
                new String[]{MediaStore.Downloads._ID},
                MediaStore.Downloads.DISPLAY_NAME + " = ?",
                new String[]{AUTO_BACKUP_FILE_NAME},
                MediaStore.Downloads.DATE_MODIFIED + " DESC"
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(0);
                Uri existingUri = Uri.withAppendedPath(collection, String.valueOf(id));
                try (OutputStream os = resolver.openOutputStream(existingUri, "wt")) {
                    if (os != null) {
                        os.write(bytes);
                        os.flush();
                        return true;
                    }
                } catch (SecurityException se) {
                    Log.d(TAG, "Existing MediaStore backup not owned by this install; creating a fresh entry");
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "MediaStore lookup failed before insert", e);
        }

        Uri createdUri = null;
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, AUTO_BACKUP_FILE_NAME);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/json");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + BACKUP_DIR_NAME);
            values.put(MediaStore.Downloads.IS_PENDING, 1);

            createdUri = resolver.insert(collection, values);
            if (createdUri == null) return false;

            boolean wrote = false;
            try (OutputStream os = resolver.openOutputStream(createdUri, "w")) {
                if (os != null) {
                    os.write(bytes);
                    os.flush();
                    wrote = true;
                }
            }
            if (!wrote) {
                resolver.delete(createdUri, null, null);
                return false;
            }

            values.clear();
            values.put(MediaStore.Downloads.IS_PENDING, 0);
            resolver.update(createdUri, values, null, null);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "MediaStore backup write failed", e);
            if (createdUri != null) {
                try {
                    resolver.delete(createdUri, null, null);
                } catch (Exception ignored) {
                }
            }
            return false;
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

            try (Cursor cursor = resolver.query(
                    collection,
                    new String[]{MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME},
                    selection,
                    args,
                    MediaStore.Downloads.DATE_MODIFIED + " DESC"
            )) {
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
