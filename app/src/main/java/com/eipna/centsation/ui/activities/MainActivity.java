package com.eipna.centsation.ui.activities;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import com.eipna.centsation.util.FormatUtil;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.MenuCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.eipna.centsation.R;
import com.eipna.centsation.data.Currency;
import com.eipna.centsation.data.Database;
import com.eipna.centsation.data.saving.Saving;
import com.eipna.centsation.data.saving.SavingOperation;
import com.eipna.centsation.data.saving.SavingRepository;
import com.eipna.centsation.data.saving.SavingSort;
import com.eipna.centsation.databinding.ActivityMainBinding;
import com.eipna.centsation.habit.ui.HabitTrackerActivity;
import com.eipna.centsation.data.debt.Debt;
import com.eipna.centsation.data.debt.DebtRepository;
import com.eipna.centsation.ui.adapters.DebtAdapter;
import com.eipna.centsation.ui.dialogs.CreateDebtBottomSheetDialogFragment;
import com.eipna.centsation.ui.dialogs.DebtDetailsBottomSheetDialogFragment;
import com.eipna.centsation.ui.adapters.SavingAdapter;
import com.eipna.centsation.ui.dialogs.AnalyticsBottomSheetDialogFragment;
import com.eipna.centsation.ui.dialogs.SavingDetailsBottomSheetDialogFragment;
import com.eipna.centsation.ui.dialogs.TransactionBottomSheetDialogFragment;
import com.eipna.centsation.ui.viewmodel.MainViewModel;
import com.eipna.centsation.util.AlarmSetter;
import com.eipna.centsation.util.AnimationUtil;
import com.eipna.centsation.util.BackupManager;
import com.eipna.centsation.util.CrashReporter;
import com.eipna.centsation.util.DateParser;
import com.eipna.centsation.util.HapticUtil;
import com.eipna.centsation.util.SavingsCalculator;
import com.eipna.centsation.util.SwipeActionCallback;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends CentsationActivity implements SavingAdapter.Listener, DebtAdapter.Listener {

    private ActivityMainBinding binding;
    private MainViewModel viewModel;
    private SavingAdapter savingAdapter;
    private DebtAdapter debtAdapter;
    private boolean isDebtMode = false;
    private final List<Debt> cachedDebts = new java.util.ArrayList<>();
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
        binding.savingList.setHasFixedSize(true);
        binding.savingList.setItemViewCacheSize(20);
        binding.savingList.setAdapter(savingAdapter);

        debtAdapter = new DebtAdapter(this);
        binding.debtList.setLayoutManager(new LinearLayoutManager(this));
        binding.debtList.setHasFixedSize(true);
        binding.debtList.setItemViewCacheSize(20);
        binding.debtList.setAdapter(debtAdapter);

        binding.mainModeToggleGroup.check(R.id.btn_main_tab_savings);
        binding.mainModeToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            HapticUtil.performTap(group);
            if (checkedId == R.id.btn_main_tab_savings) {
                setAppMode(false);
            } else if (checkedId == R.id.btn_main_tab_debts) {
                setAppMode(true);
            }
        });

        // Setup Side Navigation Drawer
        binding.toolbar.setNavigationOnClickListener(v -> {
            if (binding != null) {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    binding.drawerLayout.openDrawer(GravityCompat.START);
                }
            }
        });

        binding.navigationView.setCheckedItem(R.id.nav_savings);
        binding.navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_savings) {
                setAppMode(false);
            } else if (id == R.id.nav_debts) {
                setAppMode(true);
            } else if (id == R.id.nav_habits) {
                startActivity(new Intent(MainActivity.this, HabitTrackerActivity.class));
            } else if (id == R.id.nav_analytics) {
                showAnalytics();
            } else if (id == R.id.nav_archive) {
                startActivity(new Intent(MainActivity.this, ArchiveActivity.class));
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            } else if (id == R.id.nav_about) {
                startActivity(new Intent(MainActivity.this, AboutActivity.class));
            }
            if (binding != null) {
                binding.drawerLayout.closeDrawer(GravityCompat.START);
            }
            return true;
        });

        binding.bottomNavigation.setSelectedItemId(R.id.nav_bottom_savings);
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_bottom_savings) {
                if (isDebtMode) setAppMode(false);
                return true;
            } else if (id == R.id.nav_bottom_debts) {
                if (!isDebtMode) setAppMode(true);
                return true;
            } else if (id == R.id.nav_bottom_habits) {
                startActivity(new Intent(MainActivity.this, HabitTrackerActivity.class));
                return false;
            } else if (id == R.id.nav_bottom_analytics) {
                showAnalytics();
                return false;
            } else if (id == R.id.nav_bottom_settings) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                return false;
            }
            return false;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding != null && binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START);
                } else if (isDebtMode) {
                    setAppMode(false);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

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
        AnimationUtil.applySpringPressEffect(binding.btnDashboardAnalytics);

        // Status Filter Chips
        binding.filterChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            HapticUtil.performTap(group);
            if (checkedIds.isEmpty() || checkedIds.contains(R.id.chip_filter_all)) {
                viewModel.setStatusFilter(MainViewModel.StatusFilter.ALL);
            } else if (checkedIds.contains(R.id.chip_filter_in_progress)) {
                viewModel.setStatusFilter(MainViewModel.StatusFilter.IN_PROGRESS);
            } else if (checkedIds.contains(R.id.chip_filter_due_soon)) {
                viewModel.setStatusFilter(MainViewModel.StatusFilter.DUE_SOON);
            } else if (checkedIds.contains(R.id.chip_filter_completed)) {
                viewModel.setStatusFilter(MainViewModel.StatusFilter.COMPLETED);
            }
        });

        binding.debtFilterChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            HapticUtil.performTap(group);
            filterAndDisplayDebts();
        });

        // Insights / Analytics Triggers
        binding.dashboardCard.setOnClickListener(v -> {
            HapticUtil.performTap(v);
            showAnalytics();
        });
        binding.btnDashboardAnalytics.setOnClickListener(v -> {
            HapticUtil.performTap(v);
            showAnalytics();
        });

        viewModel.getSavings().observe(this, this::onSavingsChanged);

        binding.createSaving.setOnClickListener(v -> {
            if (isDebtMode) {
                showCreateDebtDialog();
            } else {
                startActivity(new Intent(this, CreateActivity.class));
            }
        });
        binding.btnEmptyCreateDebt.setOnClickListener(v -> showCreateDebtDialog());
        AnimationUtil.applySpringPressEffect(binding.btnEmptyCreateDebt);
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
            try {
                try (SavingRepository repo = new SavingRepository(appContext)) {
                    if (!repo.getAllSavings().isEmpty()) {
                        return;
                    }
                }
                if (preferenceUtil.hasDismissedReinstallRestore()) {
                    return;
                }

                BackupManager.BackupInfo backupInfo = BackupManager.detectExistingBackup(appContext);
                if (backupInfo != null && (backupInfo.savingsCount > 0 || backupInfo.debtsCount > 0)) {
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            showReinstallRestoreDialog(backupInfo);
                        }
                    });
                }
            } catch (Throwable t) {
                android.util.Log.e("MainActivity", "Failed checking for reinstall backup", t);
            }
        });
    }

    private void showReinstallRestoreDialog(BackupManager.BackupInfo backupInfo) {
        if (isFinishing() || isDestroyed()) return;

        String currencyNotice = (backupInfo.currency != null && !backupInfo.currency.isEmpty())
                ? " (Currency: " + backupInfo.currency + ")"
                : "";
        String message;
        if (backupInfo.debtsCount > 0) {
            message = String.format(
                    "We found a previous backup from %s containing %d savings goal(s), %d debt(s), and %d transaction(s)%s.\n\nWould you like to restore your data and settings?",
                    backupInfo.dateFormatted,
                    backupInfo.savingsCount,
                    backupInfo.debtsCount,
                    backupInfo.transactionsCount + backupInfo.debtTransactionsCount,
                    currencyNotice
            );
        } else {
            message = String.format(
                    "We found a previous backup from %s containing %d savings goal(s) and %d transaction(s)%s.\n\nWould you like to restore your savings and settings?",
                    backupInfo.dateFormatted,
                    backupInfo.savingsCount,
                    backupInfo.transactionsCount,
                    currencyNotice
            );
        }

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
                        if (isDebtMode) {
            loadDebts();
        } else {
            viewModel.loadSavings();
        }
                        recreate();
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
                        if (isDebtMode) {
            loadDebts();
        } else {
            viewModel.loadSavings();
        }
                        recreate();
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
        if (binding != null) {
            binding.navigationView.setCheckedItem(isDebtMode ? R.id.nav_debts : R.id.nav_savings);
            int targetId = isDebtMode ? R.id.nav_bottom_debts : R.id.nav_bottom_savings;
            if (binding.bottomNavigation.getSelectedItemId() != targetId) {
                binding.bottomNavigation.setSelectedItemId(targetId);
            }
        }
        if (isDebtMode) {
            loadDebts();
        } else {
            viewModel.loadSavings();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        binding = null;
    }

    private void onSavingsChanged(List<Saving> updated) {
        if (isDebtMode) return;
        savingAdapter.submitList(updated);

        boolean empty = updated.isEmpty();
        if (empty) {
            AnimationUtil.crossfade(binding.savingList, binding.emptyIndicator, 250);
            binding.dashboardCard.setVisibility(View.GONE);
            binding.savingsFilterScroll.setVisibility(View.GONE);
            previousTotalSaved = 0.0;
        } else {
            AnimationUtil.crossfade(binding.emptyIndicator, binding.savingList, 250);
            binding.dashboardCard.setVisibility(View.VISIBLE);
            binding.savingsFilterScroll.setVisibility(View.VISIBLE);
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

        binding.dashboardTotalGoal.setText(String.format("Target: %s%s", currencySymbol, FormatUtil.formatAmount(totalGoal, this)));
        binding.dashboardOverallPercent.setText(String.format("%d%%", overallPercent));
        AnimationUtil.animateProgress(binding.dashboardProgress, Math.min(overallPercent, 100), 500);

        binding.dashboardChipActive.setText(String.format("%d Active", activeCount));
        binding.dashboardChipCompleted.setText(String.format("%d Completed", completedCount));
    }

    private void setAppMode(boolean debtMode) {
        this.isDebtMode = debtMode;
        int targetNavId = debtMode ? R.id.nav_bottom_debts : R.id.nav_bottom_savings;
        if (binding.bottomNavigation.getSelectedItemId() != targetNavId) {
            binding.bottomNavigation.setSelectedItemId(targetNavId);
        }
        if (!debtMode) {
            binding.toolbar.setTitle(R.string.toolbar_main);
            binding.navigationView.setCheckedItem(R.id.nav_savings);
            binding.mainModeToggleGroup.check(R.id.btn_main_tab_savings);
            binding.savingList.setVisibility(View.VISIBLE);
            binding.debtList.setVisibility(View.GONE);
            binding.debtEmptyIndicator.setVisibility(View.GONE);
            binding.debtFilterScroll.setVisibility(View.GONE);
            binding.savingsFilterScroll.setVisibility(View.VISIBLE);
            binding.dashboardCardHeader.setText("TOTAL SAVINGS");
            try {
                binding.dashboardTotalSaved.setTextColor(com.google.android.material.color.MaterialColors.getColor(binding.dashboardTotalSaved, android.R.attr.colorPrimary));
            } catch (Exception ignored) {}
            viewModel.loadSavings();
        } else {
            binding.toolbar.setTitle("Debts & Owed");
            binding.navigationView.setCheckedItem(R.id.nav_debts);
            binding.mainModeToggleGroup.check(R.id.btn_main_tab_debts);
            binding.savingList.setVisibility(View.GONE);
            binding.emptyIndicator.setVisibility(View.GONE);
            binding.debtList.setVisibility(View.VISIBLE);
            binding.savingsFilterScroll.setVisibility(View.GONE);
            binding.debtFilterScroll.setVisibility(View.VISIBLE);
            loadDebts();
        }
    }

    private void loadDebts() {
        final Context context = getApplicationContext();
        executor.execute(() -> {
            try (DebtRepository repo = new DebtRepository(context)) {
                List<Debt> debts = repo.getDebts(Debt.NOT_ARCHIVE);
                runOnUiThread(() -> {
                    if (binding != null && isDebtMode) {
                        cachedDebts.clear();
                        cachedDebts.addAll(debts);
                        filterAndDisplayDebts();
                    }
                });
            }
        });
    }

    private void filterAndDisplayDebts() {
        if (binding == null) return;
        List<Integer> checkedIds = binding.debtFilterChipGroup.getCheckedChipIds();
        int filterMode = 0; // 0 = all, 1 = i owe, 2 = owed to me
        if (checkedIds.contains(R.id.chip_debt_filter_i_owe)) {
            filterMode = 1;
        } else if (checkedIds.contains(R.id.chip_debt_filter_owed_to_me)) {
            filterMode = 2;
        }

        List<Debt> filtered = new java.util.ArrayList<>();
        for (Debt d : cachedDebts) {
            if (filterMode == 1 && !d.isIOwe()) continue;
            if (filterMode == 2 && !d.isOwedToMe()) continue;
            filtered.add(d);
        }

        debtAdapter.setDebts(filtered, this);

        if (filtered.isEmpty()) {
            binding.debtEmptyIndicator.setVisibility(View.VISIBLE);
            binding.debtList.setVisibility(View.GONE);
        } else {
            binding.debtEmptyIndicator.setVisibility(View.GONE);
            binding.debtList.setVisibility(View.VISIBLE);
        }

        updateDebtDashboard(cachedDebts, filterMode);
    }

    private void updateDebtDashboard(List<Debt> debts, int filterMode) {
        if (binding == null) return;

        if (debts.isEmpty()) {
            binding.debtEmptyIndicator.setVisibility(View.VISIBLE);
            binding.debtList.setVisibility(View.GONE);
            binding.dashboardCard.setVisibility(View.GONE);
            binding.debtFilterScroll.setVisibility(View.GONE);
            return;
        }

        binding.dashboardCard.setVisibility(View.VISIBLE);
        binding.debtFilterScroll.setVisibility(View.VISIBLE);

        String currencySymbol = Currency.getSymbol(preferenceUtil.getCurrency());
        double totalIOweRemaining = 0.0;
        double totalIOweOriginal = 0.0;
        double totalIOwePaid = 0.0;
        int iOweActive = 0;
        int iOweCompleted = 0;

        double totalOwedRemaining = 0.0;
        double totalOwedOriginal = 0.0;
        double totalOwedPaid = 0.0;
        int owedActive = 0;
        int owedCompleted = 0;

        for (Debt d : debts) {
            if (d.isOwedToMe()) {
                totalOwedRemaining += d.getRemainingDebt();
                totalOwedOriginal += d.getTotalAmount();
                totalOwedPaid += d.getAmountPaid();
                if (d.isPaidOff()) owedCompleted++; else owedActive++;
            } else {
                totalIOweRemaining += d.getRemainingDebt();
                totalIOweOriginal += d.getTotalAmount();
                totalIOwePaid += d.getAmountPaid();
                if (d.isPaidOff()) iOweCompleted++; else iOweActive++;
            }
        }

        int errorColor = com.google.android.material.color.MaterialColors.getColor(binding.dashboardTotalSaved, androidx.appcompat.R.attr.colorError);
        int primaryColor = com.google.android.material.color.MaterialColors.getColor(binding.dashboardTotalSaved, androidx.appcompat.R.attr.colorPrimary);

        if (filterMode == 1) { // Only I Owe
            binding.dashboardCardHeader.setText("TOTAL DEBT (I OWE)");
            binding.dashboardTotalSaved.setText(String.format("%s%s", currencySymbol,
                    FormatUtil.formatAmount(totalIOweRemaining, this)));
            binding.dashboardTotalSaved.setTextColor(errorColor);
            binding.dashboardTotalGoal.setText(String.format("Borrowed: %s%s | Paid: %s%s",
                    currencySymbol, FormatUtil.formatAmount(totalIOweOriginal, this),
                    currencySymbol, FormatUtil.formatAmount(totalIOwePaid, this)));
            int pct = totalIOweOriginal > 0 ? (int) Math.round((totalIOwePaid / totalIOweOriginal) * 100.0) : 100;
            binding.dashboardOverallPercent.setText(String.format("%d%% Paid", pct));
            AnimationUtil.animateProgress(binding.dashboardProgress, Math.min(pct, 100), 500);
            binding.dashboardChipActive.setText(String.format("%d Active Debts", iOweActive));
            binding.dashboardChipCompleted.setText(String.format("%d Paid Off", iOweCompleted));
        } else if (filterMode == 2) { // Only Owed to Me
            binding.dashboardCardHeader.setText("TOTAL OWED TO YOU");
            binding.dashboardTotalSaved.setText(String.format("%s%s", currencySymbol,
                    FormatUtil.formatAmount(totalOwedRemaining, this)));
            binding.dashboardTotalSaved.setTextColor(primaryColor);
            binding.dashboardTotalGoal.setText(String.format("Lent: %s%s | Collected: %s%s",
                    currencySymbol, FormatUtil.formatAmount(totalOwedOriginal, this),
                    currencySymbol, FormatUtil.formatAmount(totalOwedPaid, this)));
            int pct = totalOwedOriginal > 0 ? (int) Math.round((totalOwedPaid / totalOwedOriginal) * 100.0) : 100;
            binding.dashboardOverallPercent.setText(String.format("%d%% Collected", pct));
            AnimationUtil.animateProgress(binding.dashboardProgress, Math.min(pct, 100), 500);
            binding.dashboardChipActive.setText(String.format("%d Pending Owed", owedActive));
            binding.dashboardChipCompleted.setText(String.format("%d Settled", owedCompleted));
        } else { // All / Net Balance
            double netDebt = totalIOweRemaining - totalOwedRemaining;
            binding.dashboardCardHeader.setText("NET BALANCE (I OWE vs OWED TO ME)");
            if (netDebt > 0) {
                binding.dashboardTotalSaved.setText(String.format("-%s%s", currencySymbol,
                        FormatUtil.formatAmount(netDebt, this)));
                binding.dashboardTotalSaved.setTextColor(errorColor);
            } else if (netDebt < 0) {
                binding.dashboardTotalSaved.setText(String.format("+%s%s", currencySymbol,
                        FormatUtil.formatAmount(Math.abs(netDebt), this)));
                binding.dashboardTotalSaved.setTextColor(primaryColor);
            } else {
                binding.dashboardTotalSaved.setText(String.format("%s%s", currencySymbol,
                        FormatUtil.formatAmount(0, this)));
                binding.dashboardTotalSaved.setTextColor(primaryColor);
            }

            binding.dashboardTotalGoal.setText(String.format("You Owe: %s%s | Owed to You: %s%s",
                    currencySymbol, FormatUtil.formatAmount(totalIOweRemaining, this),
                    currencySymbol, FormatUtil.formatAmount(totalOwedRemaining, this)));

            double totalAll = totalIOweOriginal + totalOwedOriginal;
            double totalSettled = totalIOwePaid + totalOwedPaid;
            int pct = totalAll > 0 ? (int) Math.round((totalSettled / totalAll) * 100.0) : 100;
            binding.dashboardOverallPercent.setText(String.format("%d%% Settled", pct));
            AnimationUtil.animateProgress(binding.dashboardProgress, Math.min(pct, 100), 500);

            binding.dashboardChipActive.setText(String.format("%d I Owe (%d Active)", iOweActive + iOweCompleted, iOweActive));
            binding.dashboardChipCompleted.setText(String.format("%d Owed to Me (%d Active)", owedActive + owedCompleted, owedActive));
        }
    }

    private void showCreateDebtDialog() {
        CreateDebtBottomSheetDialogFragment dialog = CreateDebtBottomSheetDialogFragment.newInstance(null);
        dialog.setOnDebtSavedListener(this::loadDebts);
        dialog.show(getSupportFragmentManager(), "CREATE_DEBT_DIALOG");
    }

    private void showDebtDetails(String debtId) {
        DebtDetailsBottomSheetDialogFragment dialog = DebtDetailsBottomSheetDialogFragment.newInstance(debtId);
        dialog.setOnDebtUpdatedListener(this::loadDebts);
        dialog.show(getSupportFragmentManager(), "DEBT_DETAILS_DIALOG");
    }

    @Override
    public void onDebtClick(Debt debt) {
        if (debt != null) showDebtDetails(debt.getId());
    }

    @Override
    public void onPaymentClick(Debt debt) {
        if (debt != null) showDebtDetails(debt.getId());
    }

    @Override
    public void onHistoryClick(Debt debt) {
        if (debt != null) showDebtDetails(debt.getId());
    }

    @Override
    public void onDeleteClick(Debt debt) {
        if (debt == null) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Debt")
                .setMessage("Delete \"" + debt.getName() + "\" and its payment records?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    final Context appContext = getApplicationContext();
                    executor.execute(() -> {
                        try (DebtRepository repo = new DebtRepository(appContext)) {
                            repo.delete(debt.getId());
                        }
                        runOnUiThread(this::loadDebts);
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
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

        if (id == R.id.action_analytics) {
            showAnalytics();
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

            @Override
            public void onSavingUpdated(Saving s) {
                if (isDebtMode) {
            loadDebts();
        } else {
            viewModel.loadSavings();
        }
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
                .append(FormatUtil.formatAmount(saving.getCurrentSaving(), this))
                .append(" of ")
                .append(currencySymbol)
                .append(FormatUtil.formatAmount(saving.getGoal(), this))
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
                double prev = saving.getCurrentSaving();
                double next = prev + amount;
                if (prev < saving.getGoal() && next >= saving.getGoal()) {
                    HapticUtil.performCelebration(MainActivity.this);
                    binding.confettiView.startConfetti();
                    Toast.makeText(MainActivity.this, "🎉 Goal Achieved! Outstanding work!", Toast.LENGTH_LONG).show();
                } else {
                    HapticUtil.performSuccess(binding.getRoot());
                }
                viewModel.deposit(saving, amount);
            }

            @Override
            public boolean onWithdraw(Saving saving, double amount) {
                HapticUtil.performTap(binding.getRoot());
                return viewModel.withdraw(saving, amount);
            }
        });
        sheet.show(getSupportFragmentManager(), "transaction_bottom_sheet");
    }

    private void showAnalytics() {
        AnalyticsBottomSheetDialogFragment sheet = AnalyticsBottomSheetDialogFragment.newInstance();
        sheet.show(getSupportFragmentManager(), "analytics_sheet");
    }

    private void showHistoryActivity(Saving saving) {
        Intent historyIntent = new Intent(this, HistoryActivity.class);
        historyIntent.putExtra(Database.COLUMN_SAVING_ID, saving.getID());
        startActivity(historyIntent);
    }
}
