package com.eipna.centsation.ui.activities;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.icu.text.NumberFormat;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.MenuCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.eipna.centsation.R;
import com.eipna.centsation.data.Currency;
import com.eipna.centsation.data.Database;
import com.eipna.centsation.data.Theme;
import com.eipna.centsation.data.saving.Saving;
import com.eipna.centsation.data.saving.SavingOperation;
import com.eipna.centsation.data.saving.SavingRepository;
import com.eipna.centsation.data.saving.SavingSort;
import com.eipna.centsation.databinding.ActivityMainBinding;
import com.eipna.centsation.ui.adapters.SavingAdapter;
import com.eipna.centsation.ui.dialogs.SavingDetailsBottomSheetDialogFragment;
import com.eipna.centsation.ui.dialogs.TransactionBottomSheetDialogFragment;
import com.eipna.centsation.ui.viewmodel.MainViewModel;
import com.eipna.centsation.util.AlarmSetter;
import com.eipna.centsation.util.AnimationUtil;
import com.eipna.centsation.util.BackupManager;
import com.eipna.centsation.util.CrashReporter;
import com.eipna.centsation.util.DateParser;
import com.eipna.centsation.util.SavingsCalculator;
import com.eipna.centsation.util.SwipeActionCallback;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends CentsationActivity implements SavingAdapter.Listener {

    private ActivityMainBinding binding;
    private MainViewModel viewModel;
    private SavingAdapter savingAdapter;
    private double previousTotalSaved = 0.0;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<Intent> saveCrashLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                            return;
                        }

                        Uri uri = result.getData().getData();
                        if (uri == null) {
                            return;
                        }

                        if (CrashReporter.writeReportToUri(this, uri)) {
                            Toast.makeText(this, R.string.crash_save_toast_success, Toast.LENGTH_SHORT).show();
                            CrashReporter.deleteReport(this);
                        } else {
                            Toast.makeText(this, R.string.crash_save_toast_failure, Toast.LENGTH_SHORT).show();
                        }
                    });

    private final ActivityResultLauncher<Intent> restoreDocumentLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        importBackupUri(uri);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, 0);
            return insets;
        });

        Drawable appBarDrawable = MaterialShapeDrawable.createWithElevationOverlay(this);
        binding.appBar.setStatusBarForeground(appBarDrawable);

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        savingAdapter = new SavingAdapter(this, this);
        binding.savingList.setLayoutManager(new LinearLayoutManager(this));
        binding.savingList.setAdapter(savingAdapter);

        // Fluid swipe gestures (Right = Transact, Left = Archive)
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new SwipeActionCallback(
                this,
                R.drawable.ic_add,
                R.drawable.ic_archive,
                com.google.android.material.R.attr.colorPrimaryContainer,
                com.google.android.material.R.attr.colorSecondaryContainer,
                com.google.android.material.R.attr.colorOnPrimaryContainer,
                com.google.android.material.R.attr.colorOnSecondaryContainer,
                new SwipeActionCallback.SwipeListener() {
                    @Override
                    public void onSwipeRight(int position) {
                        Saving saving = savingAdapter.getSavingAt(position);
                        savingAdapter.notifyItemChanged(position);
                        showTransactionBottomSheet(saving);
                    }

                    @Override
                    public void onSwipeLeft(int position) {
                        Saving saving = savingAdapter.getSavingAt(position);
                        archiveWithUndo(saving);
                    }
                }
        ));
        itemTouchHelper.attachToRecyclerView(binding.savingList);

        // Fluid touch animations
        AnimationUtil.applySpringPressEffect(binding.createSaving);
        AnimationUtil.applySpringPressEffect(binding.dashboardCard);

        viewModel.getSavings().observe(this, this::onSavingsChanged);

        binding.createSaving.setOnClickListener(v -> startActivity(new Intent(this, CreateActivity.class)));
        binding.btnEmptyCreate.setOnClickListener(v -> startActivity(new Intent(this, CreateActivity.class)));
        AnimationUtil.applySpringPressEffect(binding.btnEmptyCreate);
        binding.btnEmptyRestore.setOnClickListener(v -> onRestoreBackupClicked());
        AnimationUtil.applySpringPressEffect(binding.btnEmptyRestore);

        if (savedInstanceState == null) {
            CrashReporter.showDialogIfPending(this, saveCrashLauncher);
            handleNotificationLaunch(getIntent());
            checkForReinstallBackup();
        }
    }

    private void checkForReinstallBackup() {
        final Context appContext = getApplicationContext();
        executor.execute(() -> {
            try (SavingRepository repo = new SavingRepository(appContext)) {
                if (!repo.getAllSavings().isEmpty()) {
                    return;
                }
            }
            if (preferenceUtil.hasDismissedReinstallRestore()) {
                return;
            }

            BackupManager.BackupInfo backupInfo = BackupManager.detectExistingBackup(appContext);
            if (backupInfo != null && backupInfo.savingsCount > 0) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        showReinstallRestoreDialog(backupInfo);
                    }
                });
            }
        });
    }

    private void showReinstallRestoreDialog(BackupManager.BackupInfo backupInfo) {
        if (isFinishing() || isDestroyed()) return;

        String message = String.format(
                "We found a previous Centsation backup from %s containing %d savings goal(s) and %d transaction(s).\n\nWould you like to restore your savings?",
                backupInfo.dateFormatted,
                backupInfo.savingsCount,
                backupInfo.transactionsCount
        );

        new MaterialAlertDialogBuilder(this)
                .setTitle("Previous Backup Found")
                .setIcon(R.drawable.ic_unarchive)
                .setMessage(message)
                .setCancelable(true)
                .setPositiveButton("Restore Everything", (dialog, which) -> {
                    restoreBackup(backupInfo);
                })
                .setNegativeButton("Start Fresh", (dialog, which) -> {
                    preferenceUtil.setDismissedReinstallRestore(true);
                })
                .show();
    }

    private void restoreBackup(BackupManager.BackupInfo backupInfo) {
        final Context appContext = getApplicationContext();
        executor.execute(() -> {
            boolean success = false;
            if (backupInfo.sourceFile != null) {
                success = BackupManager.restoreFromFile(appContext, backupInfo.sourceFile);
            } else if (backupInfo.sourceUri != null) {
                success = BackupManager.restoreFromUri(appContext, backupInfo.sourceUri);
            }
            final boolean ok = success;
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    if (ok) {
                        Toast.makeText(this, "Savings restored successfully!", Toast.LENGTH_SHORT).show();
                        viewModel.loadSavings();
                    } else {
                        Toast.makeText(this, "Could not restore backup", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        });
    }

    private void onRestoreBackupClicked() {
        final Context appContext = getApplicationContext();
        executor.execute(() -> {
            BackupManager.BackupInfo info = BackupManager.detectExistingBackup(appContext);
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    if (info != null && info.savingsCount > 0) {
                        showReinstallRestoreDialog(info);
                    } else {
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("application/json");
                        restoreDocumentLauncher.launch(intent);
                    }
                }
            });
        });
    }

    private void importBackupUri(Uri uri) {
        final Context appContext = getApplicationContext();
        executor.execute(() -> {
            boolean success = BackupManager.restoreFromUri(appContext, uri);
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    if (success) {
                        Toast.makeText(this, "Backup restored successfully!", Toast.LENGTH_SHORT).show();
                        viewModel.loadSavings();
                    } else {
                        Toast.makeText(this, "Failed to restore backup", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationLaunch(intent);
    }

    private void handleNotificationLaunch(Intent intent) {
        if (intent != null && intent.hasExtra(Database.COLUMN_SAVING_ID)) {
            String savingId = intent.getStringExtra(Database.COLUMN_SAVING_ID);
            if (savingId != null) {
                showSavingDetails(savingId);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.loadSavings();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        binding = null;
    }

    private void onSavingsChanged(List<Saving> updated) {
        savingAdapter.submitList(updated);

        boolean empty = updated.isEmpty();
        if (empty) {
            AnimationUtil.crossfade(binding.savingList, binding.emptyIndicator, 250);
            binding.dashboardCard.setVisibility(View.GONE);
            previousTotalSaved = 0.0;
        } else {
            AnimationUtil.crossfade(binding.emptyIndicator, binding.savingList, 250);
            binding.dashboardCard.setVisibility(View.VISIBLE);
            updateDashboard(updated);
        }
    }

    private void updateDashboard(List<Saving> savings) {
        String currencySymbol = Currency.getSymbol(preferenceUtil.getCurrency());
        double totalSaved = 0.0;
        double totalGoal = 0.0;
        int activeCount = 0;
        int completedCount = 0;

        for (Saving s : savings) {
            totalSaved += s.getCurrentSaving();
            totalGoal += s.getGoal();
            if (s.getCurrentSaving() >= s.getGoal()) {
                completedCount++;
            } else {
                activeCount++;
            }
        }

        int overallPercent = totalGoal > 0 ? (int) ((totalSaved / totalGoal) * 100) : 0;

        // Smooth number ticker & progress
        AnimationUtil.animateCurrencyValue(binding.dashboardTotalSaved, previousTotalSaved, totalSaved, currencySymbol, 550);
        previousTotalSaved = totalSaved;

        binding.dashboardTotalGoal.setText(String.format("Target: %s%s", currencySymbol, NumberFormat.getInstance().format(totalGoal)));
        binding.dashboardOverallPercent.setText(String.format("%d%%", overallPercent));
        AnimationUtil.animateProgress(binding.dashboardProgress, Math.min(overallPercent, 100), 500);

        binding.dashboardChipActive.setText(String.format("%d Active", activeCount));
        binding.dashboardChipCompleted.setText(String.format("%d Completed", completedCount));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        MenuCompat.setGroupDividerEnabled(menu, true);
        loadSortingMenu(menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            androidx.appcompat.widget.SearchView searchView =
                    (androidx.appcompat.widget.SearchView) searchItem.getActionView();
            if (searchView != null) {
                searchView.setQueryHint(getString(R.string.search_hint));
                searchView.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        viewModel.setSearchQuery(query);
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        viewModel.setSearchQuery(newText);
                        return true;
                    }
                });
                searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                    @Override
                    public boolean onMenuItemActionExpand(MenuItem item) {
                        return true;
                    }

                    @Override
                    public boolean onMenuItemActionCollapse(MenuItem item) {
                        viewModel.setSearchQuery("");
                        return true;
                    }
                });
            }
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem themeItem = menu.findItem(R.id.action_theme_toggle);
        if (themeItem != null) {
            boolean isDark = isDarkModeActive();
            themeItem.setIcon(isDark ? R.drawable.ic_light_mode : R.drawable.ic_dark_mode);
            themeItem.setTitle(isDark ? "Switch to Light Mode" : "Switch to Dark Mode");
        }
        return super.onPrepareOptionsMenu(menu);
    }

    private boolean isDarkModeActive() {
        int nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    private void loadSortingMenu(Menu menu) {
        String criteria = viewModel.getSortCriteria();
        if (SavingSort.NAME.SORT.equals(criteria)) {
            menu.findItem(R.id.sort_name).setChecked(true);
        } else if (SavingSort.VALUE.SORT.equals(criteria)) {
            menu.findItem(R.id.sort_value).setChecked(true);
        } else if (SavingSort.GOAL.SORT.equals(criteria)) {
            menu.findItem(R.id.sort_goal).setChecked(true);
        } else if (SavingSort.DEADLINE.SORT.equals(criteria)) {
            menu.findItem(R.id.sort_deadline).setChecked(true);
        }

        if (viewModel.isSortAscending()) {
            menu.findItem(R.id.sort_ascending).setChecked(true);
        } else {
            menu.findItem(R.id.sort_descending).setChecked(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_theme_toggle) {
            boolean isDark = isDarkModeActive();
            String newTheme = isDark ? Theme.LIGHT.VALUE : Theme.DARK.VALUE;
            preferenceUtil.setTheme(newTheme);
            AppCompatDelegate.setDefaultNightMode(isDark ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES);
            return true;
        }

        if (id == R.id.archive) {
            startActivity(new Intent(this, ArchiveActivity.class));
            return true;
        }
        if (id == R.id.settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        if (id == R.id.about) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        }

        if (id == R.id.sort_name) return applySortCriteria(item, SavingSort.NAME.SORT);
        if (id == R.id.sort_value) return applySortCriteria(item, SavingSort.VALUE.SORT);
        if (id == R.id.sort_goal) return applySortCriteria(item, SavingSort.GOAL.SORT);
        if (id == R.id.sort_deadline) return applySortCriteria(item, SavingSort.DEADLINE.SORT);

        if (id == R.id.sort_ascending) return applySortOrder(item, true);
        if (id == R.id.sort_descending) return applySortOrder(item, false);

        return super.onOptionsItemSelected(item);
    }

    private boolean applySortCriteria(MenuItem item, String criteria) {
        item.setChecked(true);
        viewModel.setSortCriteria(criteria);
        return true;
    }

    private boolean applySortOrder(MenuItem item, boolean ascending) {
        item.setChecked(true);
        viewModel.setSortAscending(ascending);
        return true;
    }

    @Override
    public void onClick(int position) {
        Saving selectedSaving = savingAdapter.getSavingAt(position);
        showSavingDetails(selectedSaving.getID());
    }

    private void showSavingDetails(String savingId) {
        SavingDetailsBottomSheetDialogFragment sheet = SavingDetailsBottomSheetDialogFragment.newInstance(savingId);
        sheet.setOnDetailsActionListener(new SavingDetailsBottomSheetDialogFragment.OnDetailsActionListener() {
            @Override
            public void onTransact(Saving s) {
                showTransactionBottomSheet(s);
            }

            @Override
            public void onHistory(Saving s) {
                showHistoryActivity(s);
            }

            @Override
            public void onEdit(Saving s) {
                Intent editIntent = new Intent(MainActivity.this, EditActivity.class);
                editIntent.putExtra(Database.COLUMN_SAVING_ID, s.getID());
                startActivity(editIntent);
            }

            @Override
            public void onShare(Saving s) {
                shareSaving(s);
            }

            @Override
            public void onArchive(Saving s) {
                archiveWithUndo(s);
            }

            @Override
            public void onDelete(Saving s) {
                showDeleteDialog(s);
            }
        });
        sheet.show(getSupportFragmentManager(), "saving_details_sheet");
    }

    @Override
    public void onOperationClick(SavingOperation operation, int position) {
        Saving selectedSaving = savingAdapter.getSavingAt(position);
        switch (operation) {
            case DELETE:      showDeleteDialog(selectedSaving); break;
            case SHARE:       shareSaving(selectedSaving); break;
            case TRANSACTION: showTransactionBottomSheet(selectedSaving); break;
            case ARCHIVE:     archiveWithUndo(selectedSaving); break;
            case HISTORY:     showHistoryActivity(selectedSaving); break;
        }
    }

    private void archiveWithUndo(Saving saving) {
        viewModel.archiveSaving(saving);
        Snackbar.make(binding.getRoot(), getString(R.string.snackbar_archived, saving.getName()), Snackbar.LENGTH_LONG)
                .setAction(R.string.snackbar_undo, v -> viewModel.unarchiveSaving(saving))
                .setAnchorView(binding.createSaving)
                .show();
    }

    private void showDeleteDialog(Saving saving) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_title_delete_saving)
                .setIcon(R.drawable.ic_delete_forever)
                .setMessage(R.string.dialog_message_delete_saving)
                .setNegativeButton(R.string.dialog_button_cancel, null)
                .setPositiveButton(R.string.dialog_button_delete, (dialogInterface, i) ->
                        viewModel.deleteSaving(saving));
        builder.create().show();
    }

    private void shareSaving(Saving saving) {
        if (saving == null) return;
        String currencySymbol = Currency.getSymbol(preferenceUtil.getCurrency());
        int percent = SavingsCalculator.getPercent(saving);

        StringBuilder shareText = new StringBuilder();
        shareText.append("🎯 Goal: ").append(saving.getName()).append("\n");
        shareText.append("💰 Saved: ").append(currencySymbol)
                .append(NumberFormat.getInstance().format(saving.getCurrentSaving()))
                .append(" of ")
                .append(currencySymbol)
                .append(NumberFormat.getInstance().format(saving.getGoal()))
                .append(" (").append(percent).append("%)\n");

        if (saving.getDeadline() != AlarmSetter.NO_ALARM) {
            shareText.append("📅 Target Date: ")
                    .append(DateParser.getStringDate(saving.getDeadline(), preferenceUtil.getDeadlineFormat()))
                    .append("\n");
        }
        if (saving.getNotes() != null && !saving.getNotes().trim().isEmpty()) {
            shareText.append("📝 Notes: ").append(saving.getNotes().trim()).append("\n");
        }
        shareText.append("\nTracked with Centsation ✨");

        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_SUBJECT, "Savings Goal: " + saving.getName());
        sendIntent.putExtra(Intent.EXTRA_TEXT, shareText.toString());
        startActivity(Intent.createChooser(sendIntent, "Share Savings Goal"));
    }

    private void showTransactionBottomSheet(Saving selectedSaving) {
        TransactionBottomSheetDialogFragment sheet = TransactionBottomSheetDialogFragment.newInstance(selectedSaving);
        sheet.setOnTransactionListener(new TransactionBottomSheetDialogFragment.OnTransactionListener() {
            @Override
            public void onDeposit(Saving saving, double amount) {
                viewModel.deposit(saving, amount);
            }

            @Override
            public boolean onWithdraw(Saving saving, double amount) {
                return viewModel.withdraw(saving, amount);
            }
        });
        sheet.show(getSupportFragmentManager(), "transaction_bottom_sheet");
    }

    private void showHistoryActivity(Saving saving) {
        Intent historyIntent = new Intent(this, HistoryActivity.class);
        historyIntent.putExtra(Database.COLUMN_SAVING_ID, saving.getID());
        startActivity(historyIntent);
    }
}
