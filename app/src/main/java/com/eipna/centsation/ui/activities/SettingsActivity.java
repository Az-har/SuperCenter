package com.eipna.centsation.ui.activities;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.eipna.centsation.R;
import com.eipna.centsation.data.Currency;
import com.eipna.centsation.data.Theme;
import com.eipna.centsation.data.saving.SavingRepository;
import com.eipna.centsation.data.transaction.Transaction;
import com.eipna.centsation.databinding.ActivitySettingsBinding;
import com.eipna.centsation.util.BackupManager;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.eipna.centsation.util.AppExecutors;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;

public class SettingsActivity extends CentsationActivity {

    private ActivitySettingsBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_container, new SettingsFragment())
                    .commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {

        private static final String TAG_EXPORT = "SettingsExport";
        private static final String TAG_IMPORT = "SettingsImport";

        private final ExecutorService executor = AppExecutors.getInstance().diskIO();
        private AlertDialog progressDialog;

        private ListPreference listContrast;
        private ListPreference listTheme;
        private ListPreference listCurrency;

        private SwitchPreferenceCompat switchDynamicColors;
        private SwitchPreferenceCompat switchScreenPrivacy;

        private Preference exportSavings;
        private Preference importSavings;

        private final ActivityResultLauncher<Intent> exportDataLauncher =
                registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null) {
                            exportJSON(data.getData());
                        }
                    }
                });

        private final ActivityResultLauncher<Intent> importDataLauncher =
                registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null) {
                            importJSON(data.getData());
                        }
                    }
                });


        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences_settings, rootKey);
            findPreferences();

            exportSavings.setOnPreferenceClickListener(preference -> {
                exportData();
                return true;
            });

            importSavings.setOnPreferenceClickListener(preference -> {
                importData();
                return true;
            });

            Preference backupNow = findPreference("backup_now");
            if (backupNow != null) {
                backupNow.setOnPreferenceClickListener(preference -> {
                    performManualBackup();
                    return true;
                });
            }

            Preference restoreAuto = findPreference("restore_auto");
            if (restoreAuto != null) {
                restoreAuto.setOnPreferenceClickListener(preference -> {
                    checkAndPromptAutoRestore();
                    return true;
                });
            }


            listCurrency.setEntries(Currency.getNames());
            listCurrency.setEntryValues(Currency.getCodes());

            listTheme.setOnPreferenceChangeListener((preference, newValue) -> {
                String selectedTheme = (String) newValue;
                if (selectedTheme.equals(Theme.SYSTEM.VALUE)) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                if (selectedTheme.equals(Theme.BATTERY.VALUE)) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY);
                if (selectedTheme.equals(Theme.LIGHT.VALUE)) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                if (selectedTheme.equals(Theme.DARK.VALUE)) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                return true;
            });

            listContrast.setOnPreferenceChangeListener((preference, newValue) -> {
                requireActivity().recreate();
                return true;
            });

            switchDynamicColors.setVisible(DynamicColors.isDynamicColorAvailable());
            switchDynamicColors.setOnPreferenceChangeListener((preference, newValue) -> {
                requireActivity().recreate();
                return true;
            });

            switchScreenPrivacy.setOnPreferenceChangeListener((preference, newValue) -> {
                requireActivity().recreate();
                return true;
            });
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();
            dismissProgressDialog();
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            executor.shutdown();
        }

        private void findPreferences() {
            listCurrency = findPreference("currency");
            listTheme = findPreference("theme");
            listContrast = findPreference("contrast");

            switchDynamicColors = findPreference("dynamic_colors");
            switchScreenPrivacy = findPreference("screen_privacy");

            exportSavings = findPreference("export");
            importSavings = findPreference("import");
        }

        private void performManualBackup() {
            Context context = requireContext().getApplicationContext();
            showProgressDialog(R.string.progress_exporting);
            executor.execute(() -> {
                boolean success = BackupManager.performAutoBackup(context);
                postToUi(() -> {
                    dismissProgressDialog();
                    Toast.makeText(requireContext(),
                            success ? R.string.toast_backup_successful : R.string.toast_backup_failed,
                            Toast.LENGTH_LONG).show();
                });
            });
        }

        private void checkAndPromptAutoRestore() {
            Context context = requireContext().getApplicationContext();
            showProgressDialog(R.string.progress_importing);
            executor.execute(() -> {
                BackupManager.BackupInfo info = BackupManager.detectExistingBackup(context);
                postToUi(() -> {
                    dismissProgressDialog();
                    if (info == null) {
                        Toast.makeText(requireContext(), R.string.toast_no_auto_backup, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String message;
                    if (info.debtsCount > 0) {
                        message = String.format("Found backup from %s containing %d goal(s), %d debt(s), and transactions.\n\nRestore now?",
                                info.dateFormatted, info.savingsCount, info.debtsCount);
                    } else {
                        message = String.format("Found backup from %s containing %d goal(s) and %d transaction(s).\n\nRestore now?",
                                info.dateFormatted, info.savingsCount, info.transactionsCount);
                    }
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.preference_restore_auto_title)
                            .setIcon(R.drawable.ic_unarchive)
                            .setMessage(message)
                            .setPositiveButton("Restore", (d, w) -> executeAutoRestore(info))
                            .setNegativeButton(R.string.dialog_button_cancel, null)
                            .show();
                });
            });
        }

        private void executeAutoRestore(BackupManager.BackupInfo info) {
            Context context = requireContext().getApplicationContext();
            showProgressDialog(R.string.progress_importing);
            executor.execute(() -> {
                boolean ok = false;
                if (info.sourceFile != null) {
                    ok = BackupManager.restoreFromFile(context, info.sourceFile);
                } else if (info.sourceUri != null) {
                    ok = BackupManager.restoreFromUri(context, info.sourceUri);
                }
                final boolean success = ok;
                postToUi(() -> {
                    dismissProgressDialog();
                    Toast.makeText(requireContext(),
                            success ? R.string.toast_import_successful : R.string.toast_import_failed,
                            Toast.LENGTH_SHORT).show();
                });
            });
        }

        private void exportData() {
            Context appContext = requireContext().getApplicationContext();
            executor.execute(() -> {
                boolean empty = isSavingsTableEmpty(appContext);
                postToUi(() -> {
                    if (empty) {
                        Toast.makeText(requireContext(), R.string.toast_export_no_savings_found, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Intent exportIntent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    exportIntent.addCategory(Intent.CATEGORY_OPENABLE);
                    exportIntent.setType("application/json");
                    exportIntent.putExtra(Intent.EXTRA_TITLE, "exported_savings.json");
                    exportDataLauncher.launch(exportIntent);
                });
            });
        }

        private void exportJSON(Uri uri) {
            showProgressDialog(R.string.progress_exporting);
            Context appContext = requireContext().getApplicationContext();
            executor.execute(() -> {
                boolean success = performExport(appContext, uri);
                postToUi(() -> {
                    dismissProgressDialog();
                    Toast.makeText(requireContext(),
                            success ? R.string.toast_export_successful : R.string.toast_export_failed,
                            Toast.LENGTH_SHORT).show();
                });
            });
        }

        private boolean isSavingsTableEmpty(Context context) {
            try (SavingRepository repo = new SavingRepository(context)) {
                return repo.getAllSavings().isEmpty();
            } catch (Exception e) {
                Log.e(TAG_EXPORT, "Could not check savings table", e);
                return false;
            }
        }

        private boolean performExport(Context context, Uri uri) {
            try {
                String jsonPayload = BackupManager.generateBackupJSON(context);
                try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                    if (out == null) {
                        Log.e(TAG_EXPORT, "openOutputStream returned null for " + uri);
                        return false;
                    }
                    out.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
                return true;
            } catch (Exception e) {
                Log.e(TAG_EXPORT, "Export failed", e);
                return false;
            }
        }

        private void importData() {
            Intent importIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            importIntent.addCategory(Intent.CATEGORY_OPENABLE);
            importIntent.setType("application/json");
            importDataLauncher.launch(importIntent);
        }

        private void importJSON(Uri uri) {
            showProgressDialog(R.string.progress_importing);
            Context appContext = requireContext().getApplicationContext();
            executor.execute(() -> {
                boolean success = performImport(appContext, uri);
                postToUi(() -> {
                    dismissProgressDialog();
                    if (success) {
                        Toast.makeText(requireContext(), R.string.toast_import_successful, Toast.LENGTH_SHORT).show();
                        requireActivity().recreate();
                    } else {
                        Toast.makeText(requireContext(), R.string.toast_import_failed, Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }

        private boolean performImport(Context context, Uri uri) {
            return BackupManager.restoreFromUri(context, uri);
        }

        private void showProgressDialog(@StringRes int messageRes) {
            if (!isAdded()) return;
            dismissProgressDialog();

            View view = LayoutInflater.from(requireContext())
                    .inflate(R.layout.dialog_progress, null, false);
            ((TextView) view.findViewById(R.id.progress_message)).setText(messageRes);

            progressDialog = new MaterialAlertDialogBuilder(requireContext())
                    .setView(view)
                    .setCancelable(false)
                    .create();
            progressDialog.show();
        }

        private void dismissProgressDialog() {
            if (progressDialog != null) {
                if (progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                progressDialog = null;
            }
        }

        private void postToUi(Runnable runnable) {
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (isAdded()) {
                    runnable.run();
                }
            });
        }
    }
}
