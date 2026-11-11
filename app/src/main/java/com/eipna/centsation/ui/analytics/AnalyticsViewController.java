package com.eipna.centsation.ui.analytics;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.eipna.centsation.R;
import com.eipna.centsation.data.Currency;
import com.eipna.centsation.data.debt.Debt;
import com.eipna.centsation.data.debt.DebtCategory;
import com.eipna.centsation.data.debt.DebtRepository;
import com.eipna.centsation.data.debt.DebtTransaction;
import com.eipna.centsation.data.saving.Saving;
import com.eipna.centsation.data.saving.SavingCategory;
import com.eipna.centsation.data.saving.SavingRepository;
import com.eipna.centsation.data.transaction.Transaction;
import com.eipna.centsation.data.transaction.TransactionRepository;
import com.eipna.centsation.databinding.PageAnalyticsBinding;
import com.eipna.centsation.util.AppExecutors;
import com.eipna.centsation.util.DateParser;
import com.eipna.centsation.util.FormatUtil;
import com.eipna.centsation.util.PreferenceUtil;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class AnalyticsViewController {

    private static final String TAG = "AnalyticsController";

    private final PageAnalyticsBinding binding;
    private final Context context;
    private final ExecutorService executor = AppExecutors.getInstance().diskIO();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final List<Saving> cachedSavings = new ArrayList<>();
    private final List<Transaction> cachedTransactions = new ArrayList<>();
    private final List<Debt> cachedDebts = new ArrayList<>();
    private final List<DebtTransaction> cachedDebtTransactions = new ArrayList<>();

    private String selectedDebtId = null;
    private volatile boolean isReleased = false;

    public AnalyticsViewController(@NonNull PageAnalyticsBinding binding, @NonNull Context context) {
        this.binding = binding;
        this.context = context;
        initListeners();
    }

    public void release() {
        isReleased = true;
        mainHandler.removeCallbacksAndMessages(null);
    }

    private boolean isUiDestroyed() {
        if (isReleased) return true;
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            return activity.isDestroyed() || activity.isFinishing();
        }
        return false;
    }

    private int getMutedTextColor() {
        return MaterialColors.getColor(
                binding.getRoot(),
                com.google.android.material.R.attr.colorOnSurfaceVariant
        );
    }

    private String formatCurrency(double amount, String currencySymbol) {
        if (amount < 0.0) {
            return String.format(Locale.getDefault(), "-%s%s",
                    currencySymbol, FormatUtil.formatAmount(Math.abs(amount), context));
        }
        return String.format(Locale.getDefault(), "%s%s",
                currencySymbol, FormatUtil.formatAmount(amount, context));
    }

    private void initListeners() {
        binding.analyticsModeToggleGroup.check(R.id.btn_analytics_tab_savings);
        binding.analyticsModeToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btn_analytics_tab_debt) {
                binding.analyticsSavingsContainer.setVisibility(View.GONE);
                binding.analyticsDebtContainer.setVisibility(View.VISIBLE);
            } else {
                binding.analyticsSavingsContainer.setVisibility(View.VISIBLE);
                binding.analyticsDebtContainer.setVisibility(View.GONE);
            }
        });
    }

    public void loadData() {
        if (isUiDestroyed()) return;
        final Context appContext = context.getApplicationContext();
        executor.execute(() -> {
            List<Saving> savings;
            List<Transaction> transactions;
            List<Debt> debts;
            List<DebtTransaction> debtTransactions;

            try (SavingRepository sRepo = new SavingRepository(appContext);
                 TransactionRepository tRepo = new TransactionRepository(appContext);
                 DebtRepository dRepo = new DebtRepository(appContext)) {

                savings = sRepo.getSavings(Saving.NOT_ARCHIVE);
                transactions = tRepo.getAll();
                debts = dRepo.getDebts(Debt.NOT_ARCHIVE);
                debtTransactions = dRepo.getAllTransactions();
            } catch (Exception e) {
                Log.e(TAG, "Failed to load analytics data", e);
                mainHandler.post(() -> {
                    if (isUiDestroyed()) return;
                    cachedSavings.clear();
                    cachedTransactions.clear();
                    cachedDebts.clear();
                    cachedDebtTransactions.clear();
                    bindSavingsData();
                    setupDebtOptionsChips();
                    bindTotalDebtData();
                });
                return;
            }

            mainHandler.post(() -> {
                if (isUiDestroyed()) return;
                cachedSavings.clear();
                cachedSavings.addAll(savings);

                cachedTransactions.clear();
                cachedTransactions.addAll(transactions);

                cachedDebts.clear();
                cachedDebts.addAll(debts);

                cachedDebtTransactions.clear();
                cachedDebtTransactions.addAll(debtTransactions);

                bindSavingsData();
                setupDebtOptionsChips();
                if (selectedDebtId != null
                        && !"filter_i_owe".equals(selectedDebtId)
                        && !"filter_owed_to_me".equals(selectedDebtId)) {
                    boolean exists = false;
                    for (Debt d : cachedDebts) {
                        if (selectedDebtId.equals(d.getId())) {
                            exists = true;
                            break;
                        }
                    }
                    if (exists) {
                        bindIndividualDebtData(selectedDebtId);
                        return;
                    }
                    selectedDebtId = null;
                    binding.layoutTotalDebtView.setVisibility(View.VISIBLE);
                    binding.layoutIndividualDebtView.setVisibility(View.GONE);
                }
                bindTotalDebtData();
            });
        });
    }

    private void bindSavingsData() {
        PreferenceUtil prefUtil = new PreferenceUtil(context);
        String currencySymbol = Currency.getSymbol(prefUtil.getCurrency());
        Resources res = context.getResources();
        long now = System.currentTimeMillis();

        AnalyticsCalculator.SavingsMetrics metrics = AnalyticsCalculator.computeSavingsMetrics(
                cachedSavings, cachedTransactions, now
        );

        binding.analytics7dayTotal.setText(formatCurrency(metrics.net7Days, currencySymbol));
        binding.analytics30dayTotal.setText(formatCurrency(metrics.net30Days, currencySymbol));
        binding.analyticsTxCount.setText(res.getQuantityString(R.plurals.deposits_count, metrics.inflowCount, metrics.inflowCount));
        binding.analyticsAvgDeposit.setText(String.format(Locale.getDefault(), "Avg: %s", formatCurrency(metrics.avgInflow, currencySymbol)));

        binding.analyticsCategoryContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);

        if (metrics.categoryTotals.isEmpty() || metrics.grandTotalCurrent <= 0.0) {
            TextView emptyTv = new TextView(context);
            emptyTv.setText(R.string.analytics_no_category_savings);
            emptyTv.setTextColor(getMutedTextColor());
            binding.analyticsCategoryContainer.addView(emptyTv);
        } else {
            for (Map.Entry<SavingCategory, Double> entry : metrics.categoryTotals.entrySet()) {
                SavingCategory cat = entry.getKey();
                double amount = entry.getValue();
                int pct = (int) Math.round((amount / metrics.grandTotalCurrent) * 100.0);

                View row = inflater.inflate(R.layout.item_analytics_category, binding.analyticsCategoryContainer, false);
                TextView label = row.findViewById(R.id.category_name);
                TextView amountTv = row.findViewById(R.id.category_amount);
                ProgressBar bar = row.findViewById(R.id.category_progress);

                label.setText(String.format(Locale.getDefault(), "%s (%d%%)", cat.getFormattedLabel(), pct));
                amountTv.setText(formatCurrency(amount, currencySymbol));
                bar.setProgress(pct);

                try {
                    bar.getProgressDrawable().setTint(Color.parseColor(cat.getColorHex()));
                } catch (IllegalArgumentException e) {
                    Log.w(TAG, "Invalid saving category color: " + cat.getColorHex(), e);
                }

                binding.analyticsCategoryContainer.addView(row);
            }
        }

        if (metrics.paceGoalsCount > 0 && metrics.totalDailyPace > 0.0) {
            binding.analyticsTotalDailyPace.setText(String.format(Locale.getDefault(), "Save %s / day",
                    formatCurrency(metrics.totalDailyPace, currencySymbol)));
            String goalsText = res.getQuantityString(R.plurals.goals_count, metrics.paceGoalsCount, metrics.paceGoalsCount);
            if (metrics.overdueGoalsCount > 0) {
                String overdueText = res.getQuantityString(R.plurals.goals_count, metrics.overdueGoalsCount, metrics.overdueGoalsCount);
                binding.analyticsPaceSubtext.setText(String.format(Locale.getDefault(),
                        "Recommended pace across %s on schedule (%s overdue).", goalsText, overdueText));
            } else {
                binding.analyticsPaceSubtext.setText(String.format(Locale.getDefault(),
                        "Recommended pace across %s to hit all deadlines on time.", goalsText));
            }
        } else if (metrics.overdueGoalsCount > 0) {
            String overdueText = res.getQuantityString(R.plurals.goals_count, metrics.overdueGoalsCount, metrics.overdueGoalsCount);
            binding.analyticsTotalDailyPace.setText(String.format(Locale.getDefault(), "%s overdue", overdueText));
            binding.analyticsPaceSubtext.setText(String.format(Locale.getDefault(),
                    "%s remaining on past-due deadlines. Update deadlines or add deposits.",
                    formatCurrency(metrics.totalOverdueRemaining, currencySymbol)));
        } else {
            binding.analyticsTotalDailyPace.setText(R.string.analytics_all_deadlines_achieved);
            binding.analyticsPaceSubtext.setText(R.string.analytics_set_deadlines_hint);
        }
    }

    private void setupDebtOptionsChips() {
        binding.debtAnalyticsChipGroup.removeAllViews();

        Chip totalChip = new Chip(context);
        totalChip.setText("📊 Combined & Net Position");
        totalChip.setCheckable(true);
        totalChip.setChecked(selectedDebtId == null);
        totalChip.setOnClickListener(v -> {
            selectedDebtId = null;
            binding.layoutTotalDebtView.setVisibility(View.VISIBLE);
            binding.layoutIndividualDebtView.setVisibility(View.GONE);
            bindTotalDebtData();
        });
        binding.debtAnalyticsChipGroup.addView(totalChip);

        Chip iOweChip = new Chip(context);
        iOweChip.setText("💸 Liabilities (I Owe)");
        iOweChip.setCheckable(true);
        iOweChip.setChecked("filter_i_owe".equals(selectedDebtId));
        iOweChip.setOnClickListener(v -> {
            selectedDebtId = "filter_i_owe";
            binding.layoutTotalDebtView.setVisibility(View.VISIBLE);
            binding.layoutIndividualDebtView.setVisibility(View.GONE);
            bindTotalDebtData();
        });
        binding.debtAnalyticsChipGroup.addView(iOweChip);

        Chip owedToMeChip = new Chip(context);
        owedToMeChip.setText("📥 Receivables (Owed to Me)");
        owedToMeChip.setCheckable(true);
        owedToMeChip.setChecked("filter_owed_to_me".equals(selectedDebtId));
        owedToMeChip.setOnClickListener(v -> {
            selectedDebtId = "filter_owed_to_me";
            binding.layoutTotalDebtView.setVisibility(View.VISIBLE);
            binding.layoutIndividualDebtView.setVisibility(View.GONE);
            bindTotalDebtData();
        });
        binding.debtAnalyticsChipGroup.addView(owedToMeChip);

        for (Debt debt : cachedDebts) {
            Chip debtChip = new Chip(context);
            DebtCategory cat = DebtCategory.fromId(debt.getCategory());
            String prefix = debt.isOwedToMe() ? "📥 " : "💸 ";
            debtChip.setText(prefix + cat.getEmoji() + " " + debt.getName());
            debtChip.setCheckable(true);
            debtChip.setTag(debt.getId());
            if (debt.getId() != null && debt.getId().equals(selectedDebtId)) {
                debtChip.setChecked(true);
            }
            debtChip.setOnClickListener(v -> {
                selectedDebtId = (String) v.getTag();
                binding.layoutTotalDebtView.setVisibility(View.GONE);
                binding.layoutIndividualDebtView.setVisibility(View.VISIBLE);
                bindIndividualDebtData(selectedDebtId);
            });
            binding.debtAnalyticsChipGroup.addView(debtChip);
        }
    }

    private void bindTotalDebtData() {
        PreferenceUtil prefUtil = new PreferenceUtil(context);
        String currencySymbol = Currency.getSymbol(prefUtil.getCurrency());
        String deadlineFormat = prefUtil.getDeadlineFormat();
        Resources res = context.getResources();
        long now = System.currentTimeMillis();

        boolean filterIOweOnly = "filter_i_owe".equals(selectedDebtId);
        boolean filterOwedOnly = "filter_owed_to_me".equals(selectedDebtId);

        AnalyticsCalculator.DebtSummaryMetrics metrics = AnalyticsCalculator.computeDebtSummary(
                cachedDebts, cachedDebtTransactions, filterIOweOnly, filterOwedOnly, now
        );

        int primaryColor = MaterialColors.getColor(binding.getRoot(), androidx.appcompat.R.attr.colorPrimary);
        int errorColor = MaterialColors.getColor(binding.getRoot(), androidx.appcompat.R.attr.colorError);
        int tertiaryColor = MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorTertiary);
        int mutedColor = getMutedTextColor();

        // 1. Overview Card
        if (filterIOweOnly) {
            int pct = metrics.totalIOweOriginal > 0.0
                    ? (int) Math.round((metrics.totalIOwePaid / metrics.totalIOweOriginal) * 100.0)
                    : 100;
            binding.analyticsTotalDebtRemaining.setText(formatCurrency(metrics.totalIOweRemaining, currencySymbol));
            binding.analyticsTotalDebtRemaining.setTextColor(errorColor);
            binding.analyticsTotalDebtSubtext.setText(String.format(Locale.getDefault(),
                    "Borrowed: %s | Repaid: %s (%d%%) • %d Active",
                    formatCurrency(metrics.totalIOweOriginal, currencySymbol),
                    formatCurrency(metrics.totalIOwePaid, currencySymbol),
                    pct, metrics.activeIOweCount));
            binding.analyticsTotalDebtProgress.setProgress(pct);
            binding.analyticsTotalDebtProgress.setIndicatorColor(errorColor);
        } else if (filterOwedOnly) {
            int pct = metrics.totalOwedOriginal > 0.0
                    ? (int) Math.round((metrics.totalOwedPaid / metrics.totalOwedOriginal) * 100.0)
                    : 100;
            binding.analyticsTotalDebtRemaining.setText(formatCurrency(metrics.totalOwedRemaining, currencySymbol));
            binding.analyticsTotalDebtRemaining.setTextColor(tertiaryColor);
            binding.analyticsTotalDebtSubtext.setText(String.format(Locale.getDefault(),
                    "Lent Out: %s | Recovered: %s (%d%%) • %d Active",
                    formatCurrency(metrics.totalOwedOriginal, currencySymbol),
                    formatCurrency(metrics.totalOwedPaid, currencySymbol),
                    pct, metrics.activeOwedCount));
            binding.analyticsTotalDebtProgress.setProgress(pct);
            binding.analyticsTotalDebtProgress.setIndicatorColor(tertiaryColor);
        } else {
            double netDebt = metrics.totalIOweRemaining - metrics.totalOwedRemaining;
            double totalBase = metrics.totalIOweOriginal + metrics.totalOwedOriginal;
            int overallPct = totalBase > 0.0
                    ? (int) Math.round(((metrics.totalIOwePaid + metrics.totalOwedPaid) / totalBase) * 100.0)
                    : 100;

            binding.analyticsTotalDebtRemaining.setText(formatCurrency(Math.abs(netDebt), currencySymbol));
            if (netDebt > 0.0) {
                binding.analyticsTotalDebtRemaining.setTextColor(errorColor);
            } else if (netDebt < 0.0) {
                binding.analyticsTotalDebtRemaining.setTextColor(primaryColor);
            } else {
                binding.analyticsTotalDebtRemaining.setTextColor(tertiaryColor);
            }

            binding.analyticsTotalDebtSubtext.setText(String.format(Locale.getDefault(),
                    "You Owe: %s (%d active) | Owed to You: %s (%d active)",
                    formatCurrency(metrics.totalIOweRemaining, currencySymbol), metrics.activeIOweCount,
                    formatCurrency(metrics.totalOwedRemaining, currencySymbol), metrics.activeOwedCount));
            binding.analyticsTotalDebtProgress.setProgress(overallPct);
            binding.analyticsTotalDebtProgress.setIndicatorColor(primaryColor);
        }

        // 2. Net Financial Position (Net Worth)
        double totalSavings = 0.0;
        for (Saving s : cachedSavings) {
            totalSavings += s.getCurrentSaving();
        }
        double netWorth = totalSavings + metrics.totalOwedRemaining - metrics.totalIOweRemaining;

        binding.analyticsNetSavings.setText("+" + formatCurrency(totalSavings, currencySymbol));
        binding.analyticsNetOwed.setText("+" + formatCurrency(metrics.totalOwedRemaining, currencySymbol));
        binding.analyticsNetDebt.setText("-" + formatCurrency(metrics.totalIOweRemaining, currencySymbol));

        if (netWorth >= 0.0) {
            binding.analyticsNetWorth.setText("+" + formatCurrency(netWorth, currencySymbol));
            binding.analyticsNetWorth.setTextColor(primaryColor);
        } else {
            binding.analyticsNetWorth.setText(formatCurrency(netWorth, currencySymbol));
            binding.analyticsNetWorth.setTextColor(errorColor);
        }

        // Debt-to-Savings Ratio
        if (metrics.totalIOweRemaining == 0.0) {
            binding.analyticsDebtSavingsRatio.setText("Debt-to-Savings: 0.00 (Debt-Free! 🎉)");
            binding.analyticsDebtHealthBadge.setText("Excellent");
            binding.analyticsDebtHealthBadge.setTextColor(primaryColor);
            binding.analyticsDebtHealthAdvice.setText("100% of your liquid savings are unencumbered by liabilities.");
        } else if (totalSavings <= 0.0) {
            binding.analyticsDebtSavingsRatio.setText("Debt-to-Savings: ∞");
            binding.analyticsDebtHealthBadge.setText("High Leverage");
            binding.analyticsDebtHealthBadge.setTextColor(errorColor);
            binding.analyticsDebtHealthAdvice.setText(String.format(Locale.getDefault(),
                    "Liabilities exceed liquid savings by %s. Prioritize debt payoff.",
                    formatCurrency(metrics.totalIOweRemaining, currencySymbol)));
        } else {
            double ratio = metrics.totalIOweRemaining / totalSavings;
            binding.analyticsDebtSavingsRatio.setText(String.format(Locale.getDefault(), "Debt-to-Savings: %.2f", ratio));
            if (ratio < 0.5) {
                binding.analyticsDebtHealthBadge.setText("Very Healthy");
                binding.analyticsDebtHealthBadge.setTextColor(primaryColor);
                int cov = (int) Math.round((1.0 / ratio) * 100.0);
                binding.analyticsDebtHealthAdvice.setText(String.format(Locale.getDefault(),
                        "Strong liquidity: Savings cover %d%% of all liabilities.", cov));
            } else if (ratio <= 1.0) {
                binding.analyticsDebtHealthBadge.setText("Moderate");
                binding.analyticsDebtHealthBadge.setTextColor(tertiaryColor);
                binding.analyticsDebtHealthAdvice.setText("Liquid savings cover total liabilities, but maintain an emergency buffer.");
            } else {
                binding.analyticsDebtHealthBadge.setText("High Leverage");
                binding.analyticsDebtHealthBadge.setTextColor(errorColor);
                binding.analyticsDebtHealthAdvice.setText(String.format(Locale.getDefault(),
                        "Liabilities exceed liquid savings by %s. Prioritize debt payoff.",
                        formatCurrency(metrics.totalIOweRemaining - totalSavings, currencySymbol)));
            }
        }

        // 3. Debt Velocity (strictly separated: Liabilities vs Receivables)
        double vel7 = filterOwedOnly ? metrics.receivable7Days : metrics.liability7Days;
        double vel30 = filterOwedOnly ? metrics.receivable30Days : metrics.liability30Days;
        int velCount = filterOwedOnly ? metrics.receivablePaymentCount : metrics.liabilityPaymentCount;
        double velAvg = filterOwedOnly ? metrics.avgReceivablePayment : metrics.avgLiabilityPayment;

        binding.analyticsDebt7dayTotal.setText(formatCurrency(vel7, currencySymbol));
        binding.analyticsDebt30dayTotal.setText(formatCurrency(vel30, currencySymbol));
        binding.analyticsDebtTxCount.setText(res.getQuantityString(R.plurals.repayments_count, velCount, velCount));
        binding.analyticsDebtAvgPayment.setText(String.format(Locale.getDefault(), "Avg: %s", formatCurrency(velAvg, currencySymbol)));

        // 4. Debt Freedom Forecast (ALWAYS strictly from active liabilities I Owe!)
        if (metrics.totalIOweRemaining == 0.0) {
            binding.analyticsDebtFreeForecast.setText("🎉 100% Debt-Free!");
            binding.analyticsDebtFreeSubtext.setText("Zero outstanding liabilities. Maintain your financial freedom!");
        } else if (metrics.liability30Days > 0.0) {
            double months = metrics.totalIOweRemaining / metrics.liability30Days;
            long projectedTimestamp = now + (long) (months * 30.0 * 24.0 * 60.0 * 60.0 * 1000.0);
            binding.analyticsDebtFreeForecast.setText(String.format(Locale.getDefault(),
                    "Debt-Free in ~%.1f months (%s)",
                    months, DateParser.format(projectedTimestamp, "MMMM yyyy")));
            binding.analyticsDebtFreeSubtext.setText(String.format(Locale.getDefault(),
                    "Based on your 30-day liability repayment velocity of %s / month.",
                    formatCurrency(metrics.liability30Days, currencySymbol)));
        } else {
            binding.analyticsDebtFreeForecast.setText("Repayment pace needed");
            binding.analyticsDebtFreeSubtext.setText("Log repayments towards your liabilities to generate a debt-free completion milestone.");
        }

        if (metrics.paceGoalsCount > 0 && metrics.totalDailyPace > 0.0) {
            double monthlyPace = metrics.totalDailyPace * 30.0;
            String debtsLabel = res.getQuantityString(R.plurals.debts_count, metrics.paceGoalsCount, metrics.paceGoalsCount);
            binding.analyticsTotalPayoffPace.setText(String.format(Locale.getDefault(),
                    "Pay %s / month (%s / day)",
                    formatCurrency(monthlyPace, currencySymbol),
                    formatCurrency(metrics.totalDailyPace, currencySymbol)));
            binding.analyticsTotalPayoffPaceSubtext.setText(String.format(Locale.getDefault(),
                    "Recommended pace across %s with deadlines to clear on time.", debtsLabel));
        } else if (metrics.overdueLiabilityCount > 0) {
            String debtsLabel = res.getQuantityString(R.plurals.debts_count, metrics.overdueLiabilityCount, metrics.overdueLiabilityCount);
            binding.analyticsTotalPayoffPace.setText("Past-due liabilities present");
            binding.analyticsTotalPayoffPaceSubtext.setText(String.format(Locale.getDefault(),
                    "%s already passed the target deadline. Prioritize clearing overdue balances.", debtsLabel));
        } else {
            binding.analyticsTotalPayoffPace.setText("No deadline schedules");
            binding.analyticsTotalPayoffPaceSubtext.setText("Assign target payoff deadlines to your debts to unlock deadline pacing.");
        }

        // 5. Smart Payoff Strategy & Priorities
        if (metrics.snowballCandidate != null) {
            binding.analyticsSnowballRecommendation.setText(String.format(Locale.getDefault(),
                    "🎯 Snowball Win: Clear '%s' (%s left) first",
                    metrics.snowballCandidate.getName(),
                    formatCurrency(metrics.snowballCandidate.getRemainingDebt(), currencySymbol)));
            binding.analyticsSnowballSubtext.setText("Knocking out your smallest balance first generates psychological momentum.");
        } else {
            binding.analyticsSnowballRecommendation.setText("🎯 No active liabilities to clear!");
            binding.analyticsSnowballSubtext.setText("All loans and debts are fully paid off.");
        }

        if (metrics.nextDueCandidate != null) {
            long diffDays = com.eipna.centsation.util.SavingsCalculator.getCalendarDaysBetween(
                    now, metrics.nextDueCandidate.getDeadline()
            );
            if (diffDays < 0) {
                int overdueAbs = (int) Math.min(Integer.MAX_VALUE, Math.abs(diffDays));
                String agoText = res.getQuantityString(R.plurals.days_overdue, overdueAbs, overdueAbs);
                binding.analyticsNextDueRecommendation.setText(String.format(Locale.getDefault(),
                        "⚠️ Overdue: '%s' was due %s!",
                        metrics.nextDueCandidate.getName(), agoText));
                binding.analyticsNextDueRecommendation.setTextColor(errorColor);
            } else if (diffDays == 0) {
                binding.analyticsNextDueRecommendation.setText(String.format(Locale.getDefault(),
                        "⏰ Due Today: '%s' (%s)",
                        metrics.nextDueCandidate.getName(),
                        DateParser.format(metrics.nextDueCandidate.getDeadline(), deadlineFormat)));
                binding.analyticsNextDueRecommendation.setTextColor(tertiaryColor);
            } else {
                int remDays = (int) Math.min(Integer.MAX_VALUE, diffDays);
                String dueInText = res.getQuantityString(R.plurals.days_remaining, remDays, remDays);
                binding.analyticsNextDueRecommendation.setText(String.format(Locale.getDefault(),
                        "⏰ Next Due: '%s' due %s (%s)",
                        metrics.nextDueCandidate.getName(), dueInText,
                        DateParser.format(metrics.nextDueCandidate.getDeadline(), deadlineFormat)));
                binding.analyticsNextDueRecommendation.setTextColor(tertiaryColor);
            }
            binding.analyticsNextDueSubtext.setText(String.format(Locale.getDefault(),
                    "Remaining balance: %s",
                    formatCurrency(metrics.nextDueCandidate.getRemainingDebt(), currencySymbol)));
        } else {
            binding.analyticsNextDueRecommendation.setText("⏰ No upcoming deadlines");
            binding.analyticsNextDueRecommendation.setTextColor(primaryColor);
            binding.analyticsNextDueSubtext.setText("All debts are on schedule or have no deadline set.");
        }

        // 6. Category Breakdown
        binding.analyticsDebtCategoryContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);

        double totalCategorySum = 0.0;
        for (double v : metrics.categoryDebtTotals.values()) totalCategorySum += v;

        if (metrics.categoryDebtTotals.isEmpty() || totalCategorySum <= 0.0) {
            TextView emptyTv = new TextView(context);
            emptyTv.setText(cachedDebts.isEmpty() ? R.string.analytics_no_debts_added : R.string.analytics_all_debts_paid);
            emptyTv.setTextColor(mutedColor);
            binding.analyticsDebtCategoryContainer.addView(emptyTv);
        } else {
            for (Map.Entry<DebtCategory, Double> entry : metrics.categoryDebtTotals.entrySet()) {
                DebtCategory cat = entry.getKey();
                double amount = entry.getValue();
                int pct = (int) Math.round((amount / totalCategorySum) * 100.0);

                View row = inflater.inflate(R.layout.item_analytics_category, binding.analyticsDebtCategoryContainer, false);
                TextView label = row.findViewById(R.id.category_name);
                TextView amountTv = row.findViewById(R.id.category_amount);
                ProgressBar bar = row.findViewById(R.id.category_progress);

                label.setText(String.format(Locale.getDefault(), "%s (%d%%)", cat.getFormattedLabel(), pct));
                amountTv.setText(formatCurrency(amount, currencySymbol));
                bar.setProgress(pct);

                try {
                    bar.getProgressDrawable().setTint(Color.parseColor(cat.getColorHex()));
                } catch (IllegalArgumentException e) {
                    Log.w(TAG, "Invalid debt category color: " + cat.getColorHex(), e);
                }

                binding.analyticsDebtCategoryContainer.addView(row);
            }
        }
    }

    private void bindIndividualDebtData(String debtId) {
        if (debtId == null) return;

        Debt targetDebt = null;
        for (Debt d : cachedDebts) {
            if (debtId.equals(d.getId())) {
                targetDebt = d;
                break;
            }
        }
        if (targetDebt == null) return;

        PreferenceUtil prefUtil = new PreferenceUtil(context);
        String currencySymbol = Currency.getSymbol(prefUtil.getCurrency());
        String deadlineFormat = prefUtil.getDeadlineFormat();
        Resources res = context.getResources();
        long now = System.currentTimeMillis();

        int primaryColor = MaterialColors.getColor(binding.getRoot(), androidx.appcompat.R.attr.colorPrimary);
        int tertiaryColor = MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorTertiary);
        int mutedColor = getMutedTextColor();

        AnalyticsCalculator.IndividualDebtMetrics metrics = AnalyticsCalculator.computeIndividualDebtMetrics(
                targetDebt, cachedDebtTransactions, now
        );

        binding.analyticsIndivDebtName.setText(targetDebt.getName());
        DebtCategory cat = DebtCategory.fromId(targetDebt.getCategory());
        binding.analyticsIndivDebtBadge.setText(cat.getFormattedLabel());
        try {
            binding.analyticsIndivDebtBadge.setTextColor(Color.parseColor(cat.getColorHex()));
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Invalid debt badge color: " + cat.getColorHex(), e);
        }

        double remaining = targetDebt.getRemainingDebt();
        binding.analyticsIndivDebtRemaining.setText(formatCurrency(remaining, currencySymbol));

        int pct = targetDebt.getProgressPercent();
        if (targetDebt.isOwedToMe()) {
            binding.analyticsIndivDebtSubtext.setText(String.format(Locale.getDefault(),
                    "Lent: %s | Collected: %s (%d%%)",
                    formatCurrency(targetDebt.getTotalAmount(), currencySymbol),
                    formatCurrency(targetDebt.getAmountPaid(), currencySymbol),
                    pct));
        } else {
            binding.analyticsIndivDebtSubtext.setText(String.format(Locale.getDefault(),
                    "Total: %s | Paid: %s (%d%%)",
                    formatCurrency(targetDebt.getTotalAmount(), currencySymbol),
                    formatCurrency(targetDebt.getAmountPaid(), currencySymbol),
                    pct));
        }
        binding.analyticsIndivDebtProgress.setProgress(pct);

        int elapsedInt = (int) Math.min(Integer.MAX_VALUE, metrics.daysSinceCreated);
        StringBuilder timeline = new StringBuilder(
                String.format(Locale.getDefault(), "Created %s ago",
                        res.getQuantityString(R.plurals.days_count, elapsedInt, elapsedInt))
        );
        if (targetDebt.getDeadline() > 0L) {
            if (metrics.daysUntilDeadline < 0) {
                int overdueInt = (int) Math.min(Integer.MAX_VALUE, Math.abs(metrics.daysUntilDeadline));
                timeline.append(" • Overdue by ").append(res.getQuantityString(R.plurals.days_count, overdueInt, overdueInt));
            } else if (metrics.daysUntilDeadline == 0) {
                timeline.append(" • Due today");
            } else {
                int leftInt = (int) Math.min(Integer.MAX_VALUE, metrics.daysUntilDeadline);
                timeline.append(" • ").append(res.getQuantityString(R.plurals.days_count, leftInt, leftInt)).append(" until deadline");
            }
        }
        binding.analyticsIndivTimeline.setText(timeline.toString());

        binding.analyticsIndiv7day.setText(formatCurrency(metrics.period7Days, currencySymbol));
        binding.analyticsIndiv30day.setText(formatCurrency(metrics.period30Days, currencySymbol));
        binding.analyticsIndivTxCount.setText(res.getQuantityString(
                R.plurals.individual_repayments_count, metrics.paymentCount, metrics.paymentCount));
        binding.analyticsIndivAvgPayment.setText(String.format(Locale.getDefault(),
                "Avg: %s", formatCurrency(metrics.avgPayment, currencySymbol)));

        if (targetDebt.getDeadline() > 0L && remaining > 0.0) {
            if (metrics.daysUntilDeadline > 0 && metrics.dailyPace > 0.0) {
                double monthlyRate = metrics.dailyPace * 30.0;
                int leftInt = (int) Math.min(Integer.MAX_VALUE, metrics.daysUntilDeadline);
                String daysText = res.getQuantityString(R.plurals.days_count, leftInt, leftInt);
                binding.analyticsIndivPaceTitle.setText(String.format(Locale.getDefault(),
                        targetDebt.isOwedToMe() ? "Collect %s / month (%s / day)" : "Pay %s / month (%s / day)",
                        formatCurrency(monthlyRate, currencySymbol),
                        formatCurrency(metrics.dailyPace, currencySymbol)));
                binding.analyticsIndivPaceSubtext.setText(String.format(Locale.getDefault(),
                        targetDebt.isOwedToMe() ? "Expected return in %s (%s)." : "Payoff deadline in %s (%s).",
                        daysText, DateParser.format(targetDebt.getDeadline(), deadlineFormat)));
            } else {
                binding.analyticsIndivPaceTitle.setText(targetDebt.isOwedToMe()
                        ? "Target return deadline reached!"
                        : "Payoff target deadline reached!");
                binding.analyticsIndivPaceSubtext.setText("Recommended to clear the remaining balance as soon as possible.");
            }
        } else {
            binding.analyticsIndivPaceTitle.setText(targetDebt.isOwedToMe()
                    ? "No return deadline configured"
                    : "No payoff deadline configured");
            binding.analyticsIndivPaceSubtext.setText("Set a target deadline on this record to see pacing velocity.");
        }

        if (remaining <= 0.0) {
            binding.analyticsIndivProjectedDate.setText("🎉 Fully Cleared! 100% completed.");
            binding.analyticsIndivProjectedDate.setTextColor(primaryColor);
        } else if (metrics.period30Days > 0.0) {
            long projDate = now + (long) (metrics.projectedMonthsRemaining * 30.0 * 24.0 * 60.0 * 60.0 * 1000.0);
            binding.analyticsIndivProjectedDate.setText(String.format(Locale.getDefault(),
                    "Projected completion: %s (in ~%.1f months at current pace)",
                    DateParser.format(projDate, "MMMM yyyy"), metrics.projectedMonthsRemaining));
            binding.analyticsIndivProjectedDate.setTextColor(tertiaryColor);
        } else {
            binding.analyticsIndivProjectedDate.setText("Projected completion: Log repayments to forecast date");
            binding.analyticsIndivProjectedDate.setTextColor(mutedColor);
        }

        binding.analyticsIndivPaymentsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);

        List<DebtTransaction> indivTxs = metrics.debtTransactions != null
                ? metrics.debtTransactions
                : Collections.emptyList();

        if (indivTxs.isEmpty()) {
            TextView emptyTv = new TextView(context);
            emptyTv.setText(targetDebt.isOwedToMe()
                    ? R.string.analytics_no_received_payments
                    : R.string.analytics_no_repayments_logged);
            emptyTv.setTextColor(mutedColor);
            binding.analyticsIndivPaymentsContainer.addView(emptyTv);
        } else {
            for (int i = 0; i < Math.min(indivTxs.size(), 5); i++) {
                DebtTransaction dt = indivTxs.get(i);
                View row = inflater.inflate(R.layout.recycler_transaction, binding.analyticsIndivPaymentsContainer, false);
                TextView typeTv = row.findViewById(R.id.transaction_type);
                TextView amountTv = row.findViewById(R.id.transaction_amount);
                TextView dateTv = row.findViewById(R.id.transaction_date);

                if (typeTv != null) {
                    typeTv.setText(dt.getNotes() != null && !dt.getNotes().isEmpty()
                            ? dt.getNotes()
                            : (targetDebt.isOwedToMe() ? "Received" : "Repayment"));
                    typeTv.setVisibility(View.VISIBLE);
                }
                if (amountTv != null) {
                    amountTv.setText(String.format(Locale.getDefault(),
                            targetDebt.isOwedToMe() ? "+%s" : "-%s",
                            formatCurrency(dt.getAmount(), currencySymbol)));
                    amountTv.setTextColor(targetDebt.isOwedToMe() ? tertiaryColor : primaryColor);
                }
                if (dateTv != null) {
                    dateTv.setText(DateParser.format(dt.getDate(), deadlineFormat));
                }

                binding.analyticsIndivPaymentsContainer.addView(row);
            }
        }
    }
}
