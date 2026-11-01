package com.eipna.centsation.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.eipna.centsation.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CrashReporter {

    private static final String TAG = "CrashReporter";
    private static final String REPORT_FILENAME = "last_crash.txt";
    private static String inMemoryReport = null;

    public static File getReportFile(Context context) {
        return new File(context.getFilesDir(), REPORT_FILENAME);
    }

    public static boolean hasReport(Context context) {
        File file = getReportFile(context);
        return file.exists() && file.length() > 0;
    }

    @Nullable
    public static String readReport(Context context) {
        if (inMemoryReport != null) return inMemoryReport;
        File file = getReportFile(context);
        if (!file.exists()) return null;

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            inMemoryReport = sb.toString();
            return inMemoryReport;
        } catch (IOException e) {
            Log.e(TAG, "Error reading crash report", e);
            return null;
        }
    }

    public static void deleteReport(Context context) {
        try {
            File file = getReportFile(context);
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting crash report file", e);
        }
    }

    public static void showDialogIfPending(Activity activity, ActivityResultLauncher<Intent> saveLauncher) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || !hasReport(activity)) return;

        // Read report into memory first
        final String report = readReport(activity);

        // Delete report immediately from disk so it NEVER repeats on subsequent launches!
        deleteReport(activity);

        if (report == null || report.trim().isEmpty()) {
            inMemoryReport = null;
            return;
        }

        inMemoryReport = report;

        try {
            View crashDialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_crash, null, false);

            MaterialButton actionEmail = crashDialogView.findViewById(R.id.crash_action_email);
            MaterialButton actionSave = crashDialogView.findViewById(R.id.crash_action_save);
            MaterialButton actionDismiss = crashDialogView.findViewById(R.id.crash_action_dismiss);

            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.dialog_title_crash)
                    .setMessage(R.string.dialog_message_crash)
                    .setIcon(R.drawable.ic_crash_error)
                    .setCancelable(true)
                    .setView(crashDialogView);

            AlertDialog dialog = builder.create();
            dialog.setOnShowListener(dialogInterface -> {
                actionEmail.setOnClickListener(view -> {
                    sendEmail(activity, report);
                    dialog.dismiss();
                });

                actionSave.setOnClickListener(view -> {
                    launchSaveDialog(saveLauncher);
                    dialog.dismiss();
                });

                actionDismiss.setOnClickListener(view -> {
                    inMemoryReport = null;
                    dialog.dismiss();
                });
            });

            dialog.show();
        } catch (Exception e) {
            Log.e(TAG, "Error displaying crash dialog", e);
            inMemoryReport = null;
        }
    }

    public static boolean writeReportToUri(Context context, Uri uri) {
        String report = inMemoryReport;
        if (report == null) {
            report = readReport(context);
        }
        if (report == null) return false;

        try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
            if (out == null) return false;
            out.write(report.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error writing report to URI", e);
            return false;
        } finally {
            inMemoryReport = null;
        }
    }

    public static String suggestedFilename() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        return "centsation_crash_" + sdf.format(new Date()) + ".txt";
    }

    private static boolean sendEmail(Activity activity, String report) {
        String developerEmail = activity.getString(R.string.developer_email);

        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
        emailIntent.setData(Uri.parse("mailto:"));
        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{developerEmail});
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.crash_email_subject));
        emailIntent.putExtra(Intent.EXTRA_TEXT, report);

        try {
            activity.startActivity(Intent.createChooser(emailIntent, activity.getString(R.string.crash_email_chooser_title)));
            return true;
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, R.string.crash_no_email_app_toast, Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private static void launchSaveDialog(ActivityResultLauncher<Intent> launcher) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, suggestedFilename());
        launcher.launch(intent);
    }
}
