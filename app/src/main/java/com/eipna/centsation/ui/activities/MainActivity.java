package com.eipna.centsation.ui.activities;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
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
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.MenuCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewpager2.widget.ViewPager2;

import com.eipna.centsation.R;
import com.eipna.centsation.data.Currency;
import com.eipna.centsation.data.Database;
import com.eipna.centsation.data.debt.Debt;
import com.eipna.centsation.data.debt.DebtRepository;
import com.eipna.centsation.data.saving.Saving;
import com.eipna.centsation.data.saving.SavingOperation;
import com.eipna.centsation.data.saving.SavingRepository;
import com.eipna.centsation.data.saving.SavingSort;
import com.eipna.centsation.databinding.ActivityMainBinding;
import com.eipna.centsation.databinding.PageAnalyticsBinding;
import com.eipna.centsation.databinding.PageDebtsBinding;
import com.eipna.centsation.databinding.PageHabitsBinding;
import com.eipna.centsation.databinding.PageSavingsBinding;
import com.eipna.centsation.habit.ui.HabitTrackerActivity;
import com.eipna.centsation.habit.ui.HabitViewBinder;
import com.eipna.centsation.habit.ui.HabitViewModel;
import com.eipna.centsation.ui.adapters.DebtAdapter;
import com.eipna.centsation.ui.adapters.MainPagerAdapter;
import com.eipna.centsation.ui.adapters.SavingAdapter;
import com.eipna.centsation.ui.analytics.AnalyticsViewController;
import com.eipna.centsation.ui.dialogs.CreateDebtBottomSheetDialogFragment;
import com.eipna.centsation.ui.dialogs.DebtDetailsBottomSheetDialogFragment;
import com.eipna.centsation.ui.dialogs.SavingDetailsBottomSheetDialogFragment;
import com.eipna.centsation.ui.dialogs.TransactionBottomSheetDialogFragment;
import com.eipna.centsation.ui.viewmodel.MainViewModel;
import com.eipna.centsation.habit.widget.HabitWidgetSyncManager;
import com.eipna.centsation.util.AlarmSetter;
import com.eipna.centsation.util.AnimationUtil;
import com.eipna.centsation.util.AppExecutors;
import com.eipna.centsation.util.BackupManager;
import com.eipna.centsation.util.CrashReporter;
import com.eipna.centsation.util.DateParser;
import com.eipna.centsation.util.FormatUtil;
import com.eipna.centsation.util.HapticUtil;
import com.eipna.centsation.util.SavingsCalculator;
import com.eipna.centsation.util.SwipeActionCallback;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.concurrent.ExecutorService;

public class MainActivity extends CentsationActivity implements SavingAdapter.Listener, DebtAdapter.Listener {

    private ActivityMainBinding binding;
    private PageSavingsBinding savingsBinding;
    private PageDebtsBinding debtsBinding;
    private PageHabitsBinding habitsBinding;
    private PageAnalyticsBinding analyticsBinding;

    private AnalyticsViewController analyticsController;
    private MainViewModel viewModel;
    private SavingAdapter savingAdapter;
    private DebtAdapter debtAdapter;

    private boolean isDebtMode = false;
    private final List<Debt> cachedDebts = new java.util.ArrayList<>();
    private double previousTotalSaved = 0.0;
    private final ExecutorService executor = AppExecutors.getInstance().diskIO();

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                // Choice recorded via PreferenceUtil so declined users are not repeatedly prompted.
            });

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

        // Inflate the 4 modular sliding pages
        savingsBinding = PageSavingsBinding.inflate(getLayoutInflater());
        debtsBinding = PageDebtsBinding.inflate(getLayoutInflater());
        habitsBinding = PageHabitsBinding.inflate(getLayoutInflater());
        analyticsBinding = PageAnalyticsBinding.inflate(getLayoutInflater());

        // Bind Compose Habits screen & Analytics controller
        HabitViewBinder.bind(habitsBinding.composeHabitsDashboard, this);
        analyticsController = new AnalyticsViewController(analyticsBinding, this);

        View[] pages = new View[] {
                savingsBinding.getRoot(),
                debtsBinding.getRoot(),
                habitsBinding.getRoot(),
                analyticsBinding.getRoot()
        };

        MainPagerAdapter pagerAdapter = new MainPagerAdapter(pages);
        binding.mainViewPager.setAdapter(pagerAdapter);
        binding.mainViewPager.setOffscreenPageLimit(3);

        // Setup Adapters
        savingAdapter = new SavingAdapter(this, this);
        savingsBinding.savingList.setLayoutManager(new LinearLayoutManager(this));
        savingsBinding.savingList.setHasFixedSize(true);
        savingsBinding.savingList.setItemViewCacheSize(20);
        savingsBinding.savingList.setAdapter(savingAdapter);

        debtAdapter = new DebtAdapter(this);
        debtsBinding.debtList.setLayoutManager(new LinearLayoutManager(this));
        debtsBinding.debtList.setHasFixedSize(true);
        debtsBinding.debtList.setItemViewCacheSize(20);
        debtsBinding.debtList.setAdapter(debtAdapter);

        // Side Navigation Drawer
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
                binding.mainViewPager.setCurrentItem(0, true);
            } else if (id == R.id.nav_debts) {
                binding.mainViewPager.setCurrentItem(1, true);
            } else if (id == R.id.nav_habits) {
                binding.mainViewPager.setCurrentItem(2, true);
            } else if (id == R.id.nav_analytics) {
                binding.mainViewPager.setCurrentItem(3, true);
            } else if (id == R.id.nav_archive) {
                openArchiveScreen();
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

        // Bottom Navigation with slide transition
        binding.bottomNavigation.setSelectedItemId(R.id.nav_bottom_savings);
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_bottom_savings) {
                binding.mainViewPager.setCurrentItem(0, true);
                return true;
            } else if (id == R.id.nav_bottom_debts) {
                binding.mainViewPager.setCurrentItem(1, true);
                return true;
            } else if (id == R.id.nav_bottom_habits) {
                binding.mainViewPager.setCurrentItem(2, true);
                return true;
            } else if (id == R.id.nav_bottom_analytics) {
                binding.mainViewPager.setCurrentItem(3, true);
                return true;
            }
            return false;
        });

        // ViewPager2 page change callback to synchronize BottomNav, Toolbar, and FAB
        binding.mainViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                onMainPageSelected(position);
            }
        });

        // Back navigation handling
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding != null && binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START);
                } else if (binding != null && binding.mainViewPager.getCurrentItem() != 0) {
                    binding.mainViewPager.setCurrentItem(0, true);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        // Swipe Gestures for Savings (Right = Transact, Left = Archive)
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
                        if (saving == null) return;
                        savingAdapter.notifyItemChanged(position);
                        showTransactionBottomSheet(saving);
                    }

                    @Override
                    public void onSwipeLeft(int position) {
                        Saving saving = savingAdapter.getSavingAt(position);
                        if (saving == null) return;
                        archiveWithUndo(saving);
                    }
                }
        ));
        itemTouchHelper.attachToRecyclerView(savingsBinding.savingList);

        // Swipe Gestures for Debts (Right = Record Payment, Left = Archive)
        ItemTouchHelper debtTouchHelper = new ItemTouchHelper(new SwipeActionCallback(
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
                        Debt debt = debtAdapter.getDebtAt(position);
                        if (debt == null) return;
                        debtAdapter.notifyItemChanged(position);
                        showDebtDetails(debt.getId());
                    }

                    @Override
                    public void onSwipeLeft(int position) {
                        Debt debt = debtAdapter.getDebtAt(position);
                        if (debt == null) return;
                        archiveDebtWithUndo(debt);
                    }
                }
        ));
        debtTouchHelper.attachToRecyclerView(debtsBinding.debtList);

        // Fluid touch animations
        AnimationUtil.applySpringPressEffect(binding.createSaving);
        AnimationUtil.applySpringPressEffect(savingsBinding.dashboardCard);
        AnimationUtil.applySpringPressEffect(debtsBinding.debtDashboardCard);

        // Savings Filter Chips
        savingsBinding.filterChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
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

        // Debt Filter Chips
        debtsBinding.debtFilterChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            HapticUtil.performTap(group);
            filterAndDisplayDebts();
        });

        // Insights / Analytics Triggers from dashboard summary cards
        savingsBinding.dashboardCard.setOnClickListener(v -> {
            HapticUtil.performTap(v);
            binding.mainViewPager.setCurrentItem(3, true);
        });
        debtsBinding.debtDashboardCard.setOnClickListener(v -> {
            HapticUtil.performTap(v);
            binding.mainViewPager.setCurrentItem(3, true);
        });

        viewModel.getSavings().observe(this, this::onSavingsChanged);

        // FAB Click
        binding.createSaving.setOnClickListener(v -> {
            int currentItem = binding.mainViewPager.getCurrentItem();
            if (currentItem == 1) {
                showCreateDebtDialog();
            } else if (currentItem == 2) {
                maybeRequestNotificationPermission();
                new ViewModelProvider(this).get(HabitViewModel.class).requestAddHabit();
            } else {
                startActivity(new Intent(this, CreateActivity.class));
            }
        });

        // Empty state buttons
        debtsBinding.btnEmptyCreateDebt.setOnClickListener(v -> showCreateDebtDialog());
        AnimationUtil.applySpringPressEffect(debtsBinding.btnEmptyCreateDebt);
        savingsBinding.btnEmptyCreate.setOnClickListener(v -> startActivity(new Intent(this, CreateActivity.class)));
        AnimationUtil.applySpringPressEffect(savingsBinding.btnEmptyCreate);
        savingsBinding.btnEmptyRestore.setOnClickListener(v -> onRestoreBackupClicked());
        AnimationUtil.applySpringPressEffect(savingsBinding.btnEmptyRestore);

        if (savedInstanceState == null) {
            CrashReporter.showDialogIfPending(this, saveCrashLauncher);
            handleNotificationLaunch(getIntent());
            checkForReinstallBackup();
            maybeRequestNotificationPermission();
        }
    }

    private void openArchiveScreen() {
        Intent intent = new Intent(this, ArchiveActivity.class);
        if (isDebtMode) {
            intent.putExtra(ArchiveActivity.EXTRA_ARCHIVE_TAB, "debts");
        }
        startActivity(intent);
    }

    private void maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (!preferenceUtil.hasPromptedNotifications()) {
            preferenceUtil.setPromptedNotifications(true);
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void onMainPageSelected(int position) {
        isDebtMode = (position == 1);
        int navItemId;
        switch (position) {
            case 0: // Savings
                navItemId = R.id.nav_bottom_savings;
                binding.toolbar.setTitle(R.string.toolbar_main);
                binding.navigationView.setCheckedItem(R.id.nav_savings);
                binding.createSaving.setContentDescription(getString(R.string.fab_add_saving));
                binding.createSaving.show();
                viewModel.loadSavings();
                break;
            case 1: // Debts
                navItemId = R.id.nav_bottom_debts;
                binding.toolbar.setTitle(R.string.toolbar_debts);
                binding.navigationView.setCheckedItem(R.id.nav_debts);
                binding.createSaving.setContentDescription(getString(R.string.fab_add_debt));
                binding.createSaving.show();
                loadDebts();
                break;
            case 2: // Habits
                navItemId = R.id.nav_bottom_habits;
                binding.toolbar.setTitle(R.string.toolbar_habits);
                binding.navigationView.setCheckedItem(R.id.nav_habits);
                binding.createSaving.setContentDescription(getString(R.string.fab_add_habit));
                binding.createSaving.show();
                break;
            case 3: // Insights
            default:
                navItemId = R.id.nav_bottom_analytics;
                binding.toolbar.setTitle(R.string.toolbar_insights);
                binding.navigationView.setCheckedItem(R.id.nav_analytics);
                binding.createSaving.setContentDescription(null);
                binding.createSaving.hide();
                if (analyticsController != null) {
                    analyticsController.loadData();
                }
                break;
        }
        if (binding.bottomNavigation.getSelectedItemId() != navItemId) {
            binding.bottomNavigation.setSelectedItemId(navItemId);
        }
        invalidateOptionsMenu();
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
                try (DebtRepository dRepo = new DebtRepository(appContext)) {
                    if (!dRepo.getAllDebts().isEmpty()) {
                        return;
                    }
                }
                if (preferenceUtil.hasDismissedReinstallRestore()) {
                    return;
                }

                BackupManager.BackupInfo backupInfo = BackupManager.detectExistingBackup(appContext);
                if (backupInfo != null && (backupInfo.savingsCount > 0 || backupInfo.debtsCount > 0 || backupInfo.trackersCount > 0)) {
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
        int totalLogs = backupInfo.transactionsCount + backupInfo.debtTransactionsCount + backupInfo.trackerLogsCount;
        StringBuilder message = new StringBuilder();
        message.append("We found a previous backup from ")
                .append(backupInfo.dateFormatted)
                .append(currencyNotice)
                .append(" containing:\n");
        message.append("• ").append(backupInfo.savingsCount).append(" savings goal(s)\n");
        if (backupInfo.debtsCount > 0) {
            message.append("• ").append(backupInfo.debtsCount).append(" debt(s)\n");
        }
        if (backupInfo.trackersCount > 0) {
            message.append("• ").append(backupInfo.trackersCount).append(" habit tracker(s)\n");
        }
        message.append("• ").append(totalLogs).append(" transaction / activity entry(ies)\n\nWould you like to restore your data and settings?");

        new MaterialAlertDialogBuilder(this)
                .setTitle("Previous Backup Found")
                .setIcon(R.drawable.ic_unarchive)
                .setMessage(message.toString())
                .setCancelable(true)
                .setPositiveButton("Restore Everything", (dialog, which) -> restoreBackup(backupInfo))
                .setNegativeButton("Start Fresh", (dialog, which) -> preferenceUtil.setDismissedReinstallRestore(true))
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
            if (success) {
                HabitWidgetSyncManager.INSTANCE.enqueueUpdate(appContext);
            }
            final boolean ok = success;
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    if (ok) {
                        Toast.makeText(this, "Backup restored successfully!", Toast.LENGTH_SHORT).show();
                        viewModel.loadSavings();
                        loadDebts();
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
                    if (info != null && (info.savingsCount > 0 || info.debtsCount > 0 || info.trackersCount > 0)) {
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
            if (success) {
                HabitWidgetSyncManager.INSTANCE.enqueueUpdate(appContext);
            }
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    if (success) {
                        Toast.makeText(this, "Backup restored successfully!", Toast.LENGTH_SHORT).show();
                        viewModel.loadSavings();
                        loadDebts();
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
            int currentItem = binding.mainViewPager.getCurrentItem();
            if (currentItem == 0) {
                viewModel.loadSavings();
            } else if (currentItem == 1) {
                loadDebts();
            } else if (currentItem == 3 && analyticsController != null) {
                analyticsController.loadData();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (analyticsController != null) {
            analyticsController.release();
        }
        binding = null;
        savingsBinding = null;
        debtsBinding = null;
        habitsBinding = null;
        analyticsBinding = null;
    }

    private void onSavingsChanged(List<Saving> updated) {
        if (savingsBinding == null) return;
        savingAdapter.submitList(updated);

        boolean empty = updated.isEmpty();
        if (empty) {
            AnimationUtil.crossfade(savingsBinding.savingList, savingsBinding.emptyIndicator, 250);
            savingsBinding.dashboardCard.setVisibility(View.GONE);
            savingsBinding.savingsFilterScroll.setVisibility(View.GONE);
            previousTotalSaved = 0.0;
        } else {
            AnimationUtil.crossfade(savingsBinding.emptyIndicator, savingsBinding.savingList, 250);
            savingsBinding.dashboardCard.setVisibility(View.VISIBLE);
            savingsBinding.savingsFilterScroll.setVisibility(View.VISIBLE);
            updateDashboard(updated);
        }
    }

    private void updateDashboard(List<Saving> savings) {
        if (savingsBinding == null) return;
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
        AnimationUtil.animateCurrencyValue(savingsBinding.dashboardTotalSaved, previousTotalSaved, totalSaved, currencySymbol, 550);
        previousTotalSaved = totalSaved;

        savingsBinding.dashboardTotalGoal.setText(String.format("Target: %s%s", currencySymbol, FormatUtil.formatAmount(totalGoal, this)));
        savingsBinding.dashboardOverallPercent.setText(String.format("%d%%", overallPercent));
        AnimationUtil.animateProgress(savingsBinding.dashboardProgress, Math.min(overallPercent, 100), 500);

        savingsBinding.dashboardChipActive.setText(String.format("%d Active", activeCount));
        savingsBinding.dashboardChipCompleted.setText(String.format("%d Completed", completedCount));
    }

    private void loadDebts() {
        final Context context = getApplicationContext();
        executor.execute(() -> {
            try (DebtRepository repo = new DebtRepository(context)) {
                List<Debt> debts = repo.getDebts(Debt.NOT_ARCHIVE);
                runOnUiThread(() -> {
                    if (binding != null) {
                        cachedDebts.clear();
                        cachedDebts.addAll(debts);
                        filterAndDisplayDebts();
                    }
                });
            }
        });
    }

    private void filterAndDisplayDebts() {
        if (debtsBinding == null) return;
        List<Integer> checkedIds = debtsBinding.debtFilterChipGroup.getCheckedChipIds();
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
            debtsBinding.debtEmptyIndicator.setVisibility(View.VISIBLE);
            debtsBinding.debtList.setVisibility(View.GONE);
        } else {
            debtsBinding.debtEmptyIndicator.setVisibility(View.GONE);
            debtsBinding.debtList.setVisibility(View.VISIBLE);
        }

        updateDebtDashboard(cachedDebts, filterMode);
    }

    private void updateDebtDashboard(List<Debt> debts, int filterMode) {
        if (debtsBinding == null) return;

        if (debts.isEmpty()) {
            debtsBinding.debtEmptyIndicator.setVisibility(View.VISIBLE);
            debtsBinding.debtList.setVisibility(View.GONE);
            debtsBinding.debtDashboardCard.setVisibility(View.GONE);
            debtsBinding.debtFilterScroll.setVisibility(View.GONE);
            return;
        }

        debtsBinding.debtDashboardCard.setVisibility(View.VISIBLE);
        debtsBinding.debtFilterScroll.setVisibility(View.VISIBLE);

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

        int errorColor = MaterialColors.getColor(debtsBinding.debtTotalSaved, androidx.appcompat.R.attr.colorError);
        int primaryColor = MaterialColors.getColor(debtsBinding.debtTotalSaved, androidx.appcompat.R.attr.colorPrimary);
        int onErrorContainer = MaterialColors.getColor(debtsBinding.debtOverallPercent, com.google.android.material.R.attr.colorOnErrorContainer);
        int onPrimaryContainer = MaterialColors.getColor(debtsBinding.debtOverallPercent, com.google.android.material.R.attr.colorOnPrimaryContainer);

        if (filterMode == 1) { // Only I Owe
            debtsBinding.debtCardHeader.setText("TOTAL DEBT (I OWE)");
            debtsBinding.debtTotalSaved.setText(String.format("%s%s", currencySymbol,
                    FormatUtil.formatAmount(totalIOweRemaining, this)));
            debtsBinding.debtTotalSaved.setTextColor(errorColor);
            debtsBinding.debtTotalGoal.setText(String.format("Borrowed: %s%s | Paid: %s%s",
                    currencySymbol, FormatUtil.formatAmount(totalIOweOriginal, this),
                    currencySymbol, FormatUtil.formatAmount(totalIOwePaid, this)));
            int pct = totalIOweOriginal > 0 ? (int) Math.round((totalIOwePaid / totalIOweOriginal) * 100.0) : 100;
            debtsBinding.debtOverallPercent.setText(String.format("%d%% Paid", pct));
            boolean settled = totalIOweRemaining <= 0.001;
            debtsBinding.debtOverallPercent.setBackgroundResource(settled ? R.drawable.bg_pill_accent : R.drawable.bg_pill_error);
            debtsBinding.debtOverallPercent.setTextColor(settled ? onPrimaryContainer : onErrorContainer);
            AnimationUtil.animateProgress(debtsBinding.debtDashboardProgress, Math.min(pct, 100), 500);
            debtsBinding.debtChipActive.setText(String.format("%d Active Debts", iOweActive));
            debtsBinding.debtChipCompleted.setText(String.format("%d Paid Off", iOweCompleted));
        } else if (filterMode == 2) { // Only Owed to Me
            debtsBinding.debtCardHeader.setText("TOTAL OWED TO YOU");
            debtsBinding.debtTotalSaved.setText(String.format("%s%s", currencySymbol,
                    FormatUtil.formatAmount(totalOwedRemaining, this)));
            debtsBinding.debtTotalSaved.setTextColor(primaryColor);
            debtsBinding.debtTotalGoal.setText(String.format("Lent: %s%s | Collected: %s%s",
                    currencySymbol, FormatUtil.formatAmount(totalOwedOriginal, this),
                    currencySymbol, FormatUtil.formatAmount(totalOwedPaid, this)));
            int pct = totalOwedOriginal > 0 ? (int) Math.round((totalOwedPaid / totalOwedOriginal) * 100.0) : 100;
            debtsBinding.debtOverallPercent.setText(String.format("%d%% Collected", pct));
            debtsBinding.debtOverallPercent.setBackgroundResource(R.drawable.bg_pill_accent);
            debtsBinding.debtOverallPercent.setTextColor(onPrimaryContainer);
            AnimationUtil.animateProgress(debtsBinding.debtDashboardProgress, Math.min(pct, 100), 500);
            debtsBinding.debtChipActive.setText(String.format("%d Pending Owed", owedActive));
            debtsBinding.debtChipCompleted.setText(String.format("%d Settled", owedCompleted));
        } else { // All / Net Balance
            double netDebt = totalIOweRemaining - totalOwedRemaining;
            debtsBinding.debtCardHeader.setText("NET BALANCE (I OWE vs OWED TO ME)");
            if (netDebt > 0) {
                debtsBinding.debtTotalSaved.setText(String.format("-%s%s", currencySymbol,
                        FormatUtil.formatAmount(netDebt, this)));
                debtsBinding.debtTotalSaved.setTextColor(errorColor);
                debtsBinding.debtOverallPercent.setBackgroundResource(R.drawable.bg_pill_error);
                debtsBinding.debtOverallPercent.setTextColor(onErrorContainer);
            } else if (netDebt < 0) {
                debtsBinding.debtTotalSaved.setText(String.format("+%s%s", currencySymbol,
                        FormatUtil.formatAmount(Math.abs(netDebt), this)));
                debtsBinding.debtTotalSaved.setTextColor(primaryColor);
                debtsBinding.debtOverallPercent.setBackgroundResource(R.drawable.bg_pill_accent);
                debtsBinding.debtOverallPercent.setTextColor(onPrimaryContainer);
            } else {
                debtsBinding.debtTotalSaved.setText(String.format("%s0.00", currencySymbol));
                debtsBinding.debtTotalSaved.setTextColor(primaryColor);
                debtsBinding.debtOverallPercent.setBackgroundResource(R.drawable.bg_pill_accent);
                debtsBinding.debtOverallPercent.setTextColor(onPrimaryContainer);
            }
            debtsBinding.debtTotalGoal.setText(String.format("Liabilities: %s%s | Assets: %s%s",
                    currencySymbol, FormatUtil.formatAmount(totalIOweRemaining, this),
                    currencySymbol, FormatUtil.formatAmount(totalOwedRemaining, this)));

            double totalOriginal = totalIOweOriginal + totalOwedOriginal;
            double totalPaid = totalIOwePaid + totalOwedPaid;
            int pct = totalOriginal > 0 ? (int) Math.round((totalPaid / totalOriginal) * 100.0) : 100;
            debtsBinding.debtOverallPercent.setText(String.format("%d%% Settled", pct));
            AnimationUtil.animateProgress(debtsBinding.debtDashboardProgress, Math.min(pct, 100), 500);

            debtsBinding.debtChipActive.setText(String.format("%d Liabilities", iOweActive));
            debtsBinding.debtChipCompleted.setText(String.format("%d Receivables", owedActive));
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
    public void onArchiveClick(Debt debt) {
        archiveDebtWithUndo(debt);
    }

    private void archiveDebtWithUndo(Debt debt) {
        if (debt == null) return;
        setDebtArchived(debt.getId(), true);
        Snackbar.make(binding.getRoot(), getString(R.string.snackbar_archived, debt.getName()), Snackbar.LENGTH_LONG)
                .setAction(R.string.snackbar_undo, v -> setDebtArchived(debt.getId(), false))
                .setAnchorView(binding.bottomNavigation)
                .show();
    }

    private void setDebtArchived(String debtId, boolean archived) {
        final Context appContext = getApplicationContext();
        executor.execute(() -> {
            try (DebtRepository repo = new DebtRepository(appContext)) {
                repo.setArchived(debtId, archived);
            }
            BackupManager.triggerAutoBackup(appContext);
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed() && binding != null) {
                    loadDebts();
                }
            });
        });
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
                        BackupManager.triggerAutoBackup(appContext);
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

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        int currentItem = binding != null ? binding.mainViewPager.getCurrentItem() : 0;
        boolean onSavings = currentItem == 0;

        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            if (!onSavings && searchItem.isActionViewExpanded()) {
                searchItem.collapseActionView();
            }
            searchItem.setVisible(onSavings);
        }

        MenuItem sortItem = menu.findItem(R.id.sort);
        if (sortItem != null) {
            sortItem.setVisible(onSavings);
        }
        return super.onPrepareOptionsMenu(menu);
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

        if (id == R.id.archive) {
            openArchiveScreen();
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
        if (selectedSaving == null) return;
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
        if (selectedSaving == null) return;
        switch (operation) {
            case DELETE:      showDeleteDialog(selectedSaving); break;
            case SHARE:       shareSaving(selectedSaving); break;
            case TRANSACTION: showTransactionBottomSheet(selectedSaving); break;
            case ARCHIVE:     archiveWithUndo(selectedSaving); break;
            case HISTORY:     showHistoryActivity(selectedSaving); break;
        }
    }

    private void archiveWithUndo(Saving saving) {
        if (saving == null) return;
        viewModel.archiveSaving(saving);
        Snackbar.make(binding.getRoot(), getString(R.string.snackbar_archived, saving.getName()), Snackbar.LENGTH_LONG)
                .setAction(R.string.snackbar_undo, v -> viewModel.unarchiveSaving(saving))
                .setAnchorView(binding.bottomNavigation)
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

    private void showHistoryActivity(Saving saving) {
        Intent historyIntent = new Intent(this, HistoryActivity.class);
        historyIntent.putExtra(Database.COLUMN_SAVING_ID, saving.getID());
        startActivity(historyIntent);
    }
}
