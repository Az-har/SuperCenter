package com.eipna.centsation.ui.analytics;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
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
import com.eipna.centsation.util.AlarmSetter;
import com.eipna.centsation.util.AppExecutors;
import com.eipna.centsation.util.DateParser;
import com.eipna.centsation.util.FormatUtil;
import com.eipna.centsation.util.PreferenceUtil;
import com.eipna.centsation.util.SavingsCalculator;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class AnalyticsViewController {

    private final PageAnalyticsBinding binding;
    private final Context context;
    private final ExecutorService executor = AppExecutors.getInstance().diskIO();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final List<Saving> cachedSavings = new ArrayList<>();
    private final List<Transaction> cachedTransactions = new ArrayList<>();
    private final List<Debt> cachedDebts = new ArrayList<>();
    private final List<DebtTransaction> cachedDebtTransactions = new ArrayList<>();

    private String selectedDebtId = null;

    public AnalyticsViewController(@NonNull PageAnalyticsBinding binding, @NonNull Context context) {
        this.binding = binding;
        this.context = context;
        initListeners();
    }

    private void initListeners() {
        if (binding.btnAnalyticsClose != null) {
            binding.btnAnalyticsClose.setVisibility(View.GONE);
        }

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
                return;
            }

            mainHandler.post(() -> {
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
                bindTotalDebtData();
            });
        });
    }

    private void bindSavingsData() {
        PreferenceUtil prefUtil = new PreferenceUtil(context);
        String currencySymbol = Currency.getSymbol(prefUtil.getCurrency());

        long now = System.currentTimeMillis();
        long sevenDaysAgo = now - (7L * 24 * 60 * 60 * 1000);
        long thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000);

        double total7Days = 0.0;
        double total30Days = 0.0;
        double totalAllDeposits = 0.0;
        int depositCount = 0;

        for (Transaction tx : cachedTransactions) {
            if ("deposit".equalsIgnoreCase(tx.getType())) {
                double amt = tx.getAmount();
                if (tx.getDate() >= sevenDaysAgo) {
                    total7Days += amt;
                }
                if (tx.getDate() >= thirtyDaysAgo) {
                    total30Days += amt;
                }
                totalAllDeposits += amt;
                depositCount++;
            }
        }

        double avgDeposit = depositCount > 0 ? (totalAllDeposits / depositCount) : 0.0;

        binding.analytics7dayTotal.setText(String.format("%s%s", currencySymbol, FormatUtil.formatAmount(Math.round(total7Days), context)));
        binding.analytics30dayTotal.setText(String.format("%s%s", currencySymbol, FormatUtil.formatAmount(Math.round(total30Days), context)));
        binding.analyticsTxCount.setText(String.format("%d Total Deposits", depositCount));
        binding.analyticsAvgDeposit.setText(String.format("Avg: %s%s", currencySymbol, FormatUtil.formatAmount(Math.round(avgDeposit), context)));

        // Category breakdown
        Map<SavingCategory, Double> categoryTotals = new HashMap<>();
        double grandTotal = 0.0;
        double totalDailyPace = 0.0;
        int paceGoalsCount = 0;

        for (Saving s : cachedSavings) {
            SavingCategory cat = SavingCategory.fromId(s.getCategory());
            double current = s.getCurrentSaving();
            grandTotal += current;
            categoryTotals.put(cat, categoryTotals.getOrDefault(cat, 0.0) + current);

            if (!SavingsCalculator.isCompleted(s) && s.getDeadline() != AlarmSetter.NO_ALARM) {
                long days = SavingsCalculator.getDaysRemaining(s.getDeadline());
                if (days > 0) {
                    totalDailyPace += SavingsCalculator.getDailySavingsRate(s);
                    paceGoalsCount++;
                }
            }
        }

        binding.analyticsCategoryContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);

        if (categoryTotals.isEmpty() || grandTotal <= 0) {
            TextView emptyTv = new TextView(context);
            emptyTv.setText("No category savings data available yet.");
            emptyTv.setTextColor(Color.GRAY);
            binding.analyticsCategoryContainer.addView(emptyTv);
        } else {
            for (Map.Entry<SavingCategory, Double> entry : categoryTotals.entrySet()) {
                SavingCategory cat = entry.getKey();
                double amount = entry.getValue();
                int pct = (int) Math.round((amount / grandTotal) * 100.0);

                View row = inflater.inflate(R.layout.item_analytics_category, binding.analyticsCategoryContainer, false);
                TextView label = row.findViewById(R.id.category_name);
                TextView amountTv = row.findViewById(R.id.category_amount);
                ProgressBar bar = row.findViewById(R.id.category_progress);

                label.setText(String.format("%s (%d%%)", cat.getFormattedLabel(), pct));
                amountTv.setText(String.format("%s%s", currencySymbol, FormatUtil.formatAmount(Math.round(amount), context)));
                bar.setProgress(pct);

                try {
                    bar.getProgressDrawable().setTint(Color.parseColor(cat.getColorHex()));
                } catch (Exception ignored) {}

                binding.analyticsCategoryContainer.addView(row);
            }
        }

        // Daily Pace Advisor
        if (paceGoalsCount > 0 && totalDailyPace > 0) {
            binding.analyticsTotalDailyPace.setText(String.format("Save %s%s / day",
                    currencySymbol, FormatUtil.formatAmount(Math.round(totalDailyPace), context)));
            binding.analyticsPaceSubtext.setText(String.format("Recommended pace across %d active goal(s) to hit all deadlines on time.", paceGoalsCount));
        } else {
            binding.analyticsTotalDailyPace.setText("All active deadlines achieved!");
            binding.analyticsPaceSubtext.setText("Set deadlines on your savings goals to unlock daily pace recommendations.");
        }
    }

    private void setupDebtOptionsChips() {
        binding.debtAnalyticsChipGroup.removeAllViews();

        // 1. Total Combined Option Chip
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

        // 2. Liabilities (I Owe) Only Chip
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

        // 3. Receivables (Owed to Me) Only Chip
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

        // 4. Individual Debt Option Chips
        for (Debt debt : cachedDebts) {
            Chip debtChip = new Chip(context);
            DebtCategory cat = DebtCategory.fromId(debt.getCategory());
            String prefix = debt.isOwedToMe() ? "📥 " : "💸 ";
            debtChip.setText(prefix + cat.getEmoji() + " " + debt.getName());
            debtChip.setCheckable(true);
            debtChip.setTag(debt.getId());
            if (debt.getId().equals(selectedDebtId)) {
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
        long now = System.currentTimeMillis();

        double totalIOweOriginal = 0.0;
        double totalIOwePaid = 0.0;
        double totalIOweRemaining = 0.0;
        int activeIOweCount = 0;

        double totalOwedOriginal = 0.0;
        double totalOwedPaid = 0.0;
        double totalOwedRemaining = 0.0;
        int activeOwedCount = 0;

        Map<DebtCategory, Double> categoryDebtTotals = new HashMap<>();

        Debt snowballCandidate = null;
        Debt nextDueCandidate = null;
        long nextDueDiffMin = Long.MAX_VALUE;

        double totalDailyPace = 0.0;
        int paceGoalsCount = 0;

        boolean filterIOweOnly = "filter_i_owe".equals(selectedDebtId);
        boolean filterOwedOnly = "filter_owed_to_me".equals(selectedDebtId);

        for (Debt d : cachedDebts) {
            double orig = d.getTotalAmount();
            double paid = d.getAmountPaid();
            double rem = d.getRemainingDebt();

            if (d.isOwedToMe()) {
                totalOwedOriginal += orig;
                totalOwedPaid += paid;
                totalOwedRemaining += rem;
                if (!d.isPaidOff()) activeOwedCount++;

                if (!filterIOweOnly) {
                    DebtCategory cat = DebtCategory.fromId(d.getCategory());
                    categoryDebtTotals.put(cat, categoryDebtTotals.getOrDefault(cat, 0.0) + rem);
                }
            } else {
                totalIOweOriginal += orig;
                totalIOwePaid += paid;
                totalIOweRemaining += rem;
                if (!d.isPaidOff()) activeIOweCount++;

                if (!filterOwedOnly) {
                    DebtCategory cat = DebtCategory.fromId(d.getCategory());
                    categoryDebtTotals.put(cat, categoryDebtTotals.getOrDefault(cat, 0.0) + rem);
                }

                // Snowball Candidate
                if (rem > 0) {
                    if (snowballCandidate == null || rem < snowballCandidate.getRemainingDebt()) {
                        snowballCandidate = d;
                    }
                }

                // Pacing & Due Date
                if (d.getDeadline() > 0 && rem > 0) {
                    long days = (d.getDeadline() - now) / (24L * 60 * 60 * 1000);
                    if (days > 0) {
                        totalDailyPace += (rem / days);
                        paceGoalsCount++;
                    }
                    if (d.getDeadline() < nextDueDiffMin) {
                        nextDueDiffMin = d.getDeadline();
                        nextDueCandidate = d;
                    }
                }
            }
        }

        int primaryColor = com.google.android.material.color.MaterialColors.getColor(binding.getRoot(), androidx.appcompat.R.attr.colorPrimary);
        int errorColor = com.google.android.material.color.MaterialColors.getColor(binding.getRoot(), androidx.appcompat.R.attr.colorError);
        int tertiaryColor = com.google.android.material.color.MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorTertiary);

        // 1. Overview Card
        if (filterIOweOnly) {
            int pct = totalIOweOriginal > 0 ? (int) Math.round((totalIOwePaid / totalIOweOriginal) * 100.0) : 100;
            binding.analyticsTotalDebtRemaining.setText(String.format("%s%s", currencySymbol, FormatUtil.formatAmount(totalIOweRemaining, context)));
            binding.analyticsTotalDebtRemaining.setTextColor(errorColor);
            binding.analyticsTotalDebtSubtext.setText(String.format("Borrowed: %s%s | Repaid: %s%s (%d%%) • %d Active",
                    currencySymbol, FormatUtil.formatAmount(totalIOweOriginal, context),
                    currencySymbol, FormatUtil.formatAmount(totalIOwePaid, context),
                    pct, activeIOweCount));
            binding.analyticsTotalDebtProgress.setProgress(pct);
            binding.analyticsTotalDebtProgress.setIndicatorColor(errorColor);
        } else if (filterOwedOnly) {
            int pct = totalOwedOriginal > 0 ? (int) Math.round((totalOwedPaid / totalOwedOriginal) * 100.0) : 100;
            binding.analyticsTotalDebtRemaining.setText(String.format("%s%s", currencySymbol, FormatUtil.formatAmount(totalOwedRemaining, context)));
            binding.analyticsTotalDebtRemaining.setTextColor(tertiaryColor);
            binding.analyticsTotalDebtSubtext.setText(String.format("Lent Out: %s%s | Recovered: %s%s (%d%%) • %d Active",
                    currencySymbol, FormatUtil.formatAmount(totalOwedOriginal, context),
                    currencySymbol, FormatUtil.formatAmount(totalOwedPaid, context),
                    pct, activeOwedCount));
            binding.analyticsTotalDebtProgress.setProgress(pct);
            binding.analyticsTotalDebtProgress.setIndicatorColor(tertiaryColor);
        } else {
            double netDebt = totalIOweRemaining - totalOwedRemaining;
            int overallPct = (totalIOweOriginal + totalOwedOriginal) > 0
                    ? (int) Math.round(((totalIOwePaid + totalOwedPaid) / (totalIOweOriginal + totalOwedOriginal)) * 100.0)
                    : 100;

            binding.analyticsTotalDebtRemaining.setText(String.format("%s%s", currencySymbol, FormatUtil.formatAmount(Math.abs(netDebt), context)));
            if (netDebt > 0) {
                binding.analyticsTotalDebtRemaining.setTextColor(errorColor);
            } else if (netDebt < 0) {
                binding.analyticsTotalDebtRemaining.setTextColor(primaryColor);
            } else {
                binding.analyticsTotalDebtRemaining.setTextColor(tertiaryColor);
            }

            binding.analyticsTotalDebtSubtext.setText(String.format("You Owe: %s%s (%d active) | Owed to You: %s%s (%d active)",
                    currencySymbol, FormatUtil.formatAmount(totalIOweRemaining, context), activeIOweCount,
                    currencySymbol, FormatUtil.formatAmount(totalOwedRemaining, context), activeOwedCount));
            binding.analyticsTotalDebtProgress.setProgress(overallPct);
            binding.analyticsTotalDebtProgress.setIndicatorColor(primaryColor);
        }

        // 2. Net Financial Position (Net Worth)
        double totalSavings = 0.0;
        for (Saving s : cachedSavings) {
            totalSavings += s.getCurrentSaving();
        }
        double netWorth = totalSavings + totalOwedRemaining - totalIOweRemaining;

        binding.analyticsNetSavings.setText(String.format("+%s%s", currencySymbol, FormatUtil.formatAmount(totalSavings, context)));
        binding.analyticsNetOwed.setText(String.format("+%s%s", currencySymbol, FormatUtil.formatAmount(totalOwedRemaining, context)));
        binding.analyticsNetDebt.setText(String.format("-%s%s", currencySymbol, FormatUtil.formatAmount(totalIOweRemaining, context)));

        if (netWorth >= 0) {
            binding.analyticsNetWorth.setText(String.format("+%s%s", currencySymbol, FormatUtil.formatAmount(netWorth, context)));
            binding.analyticsNetWorth.setTextColor(primaryColor);
        } else {
            binding.analyticsNetWorth.setText(String.format("-%s%s", currencySymbol, FormatUtil.formatAmount(Math.abs(netWorth), context)));
            binding.analyticsNetWorth.setTextColor(errorColor);
        }

        // Debt-to-Savings Ratio
        if (totalIOweRemaining == 0) {
            binding.analyticsDebtSavingsRatio.setText("Debt-to-Savings: 0.00 (Debt-Free! 🎉)");
            binding.analyticsDebtHealthBadge.setText("Excellent");
            binding.analyticsDebtHealthBadge.setTextColor(primaryColor);
            binding.analyticsDebtHealthAdvice.setText("100% of your liquid savings are unencumbered by liabilities.");
        } else {
            double ratio = totalSavings > 0 ? (totalIOweRemaining / totalSavings) : 999.0;
            binding.analyticsDebtSavingsRatio.setText(String.format("Debt-to-Savings: %.2f", ratio));
            if (ratio < 0.5) {
                binding.analyticsDebtHealthBadge.setText("Very Healthy");
                binding.analyticsDebtHealthBadge.setTextColor(primaryColor);
                int cov = (int) Math.round((1.0 / ratio) * 100.0);
                binding.analyticsDebtHealthAdvice.setText(String.format("Strong liquidity: Savings cover %d%% of all liabilities.", cov));
            } else if (ratio <= 1.0) {
                binding.analyticsDebtHealthBadge.setText("Moderate");
                binding.analyticsDebtHealthBadge.setTextColor(tertiaryColor);
                binding.analyticsDebtHealthAdvice.setText("Liquid savings exceed total liabilities, but maintain an emergency buffer.");
            } else {
                binding.analyticsDebtHealthBadge.setText("High Leverage");
                binding.analyticsDebtHealthBadge.setTextColor(errorColor);
                binding.analyticsDebtHealthAdvice.setText(String.format("Liabilities exceed liquid savings by %s%s. Prioritize debt payoff.",
                        currencySymbol, FormatUtil.formatAmount(totalIOweRemaining - totalSavings, context)));
            }
        }

        // 3. Debt Repayment Velocity
        long sevenDaysAgo = now - (7L * 24 * 60 * 60 * 1000);
        long thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000);

        double debt7Days = 0.0;
        double debt30Days = 0.0;
        double debtAllRepayments = 0.0;
        int debtPaymentCount = 0;

        for (DebtTransaction dt : cachedDebtTransactions) {
            double amt = dt.getAmount();
            if (dt.getDate() >= sevenDaysAgo) {
                debt7Days += amt;
            }
            if (dt.getDate() >= thirtyDaysAgo) {
                debt30Days += amt;
            }
            debtAllRepayments += amt;
            debtPaymentCount++;
        }

        double avgDebtPayment = debtPaymentCount > 0 ? (debtAllRepayments / debtPaymentCount) : 0.0;

        binding.analyticsDebt7dayTotal.setText(String.format("%s%s", currencySymbol, FormatUtil.formatAmount(Math.round(debt7Days), context)));
        binding.analyticsDebt30dayTotal.setText(String.format("%s%s", currencySymbol, FormatUtil.formatAmount(Math.round(debt30Days), context)));
        binding.analyticsDebtTxCount.setText(String.format("%d Total Records", debtPaymentCount));
        binding.analyticsDebtAvgPayment.setText(String.format("Avg: %s%s", currencySymbol, FormatUtil.formatAmount(Math.round(avgDebtPayment), context)));

        // 4. Debt Freedom Forecast
        if (totalIOweRemaining == 0) {
            binding.analyticsDebtFreeForecast.setText("🎉 100% Debt-Free!");
            binding.analyticsDebtFreeSubtext.setText("Zero outstanding liabilities. Maintain your financial freedom!");
        } else if (debt30Days > 0) {
            double months = totalIOweRemaining / debt30Days;
            long projectedTimestamp = now + (long) (months * 30L * 24 * 60 * 60 * 1000);
            binding.analyticsDebtFreeForecast.setText(String.format("Debt-Free in ~%.1f months (%s)",
                    months, DateParser.format(projectedTimestamp, "MMMM yyyy")));
            binding.analyticsDebtFreeSubtext.setText(String.format("Based on your 30-day velocity of %s%s / month.",
                    currencySymbol, FormatUtil.formatAmount(Math.round(debt30Days), context)));
        } else {
            binding.analyticsDebtFreeForecast.setText("Repayment pace needed");
            binding.analyticsDebtFreeSubtext.setText("Log payments towards your debts to generate a debt-free completion milestone.");
        }

        if (paceGoalsCount > 0 && totalDailyPace > 0) {
            double monthlyPace = totalDailyPace * 30.0;
            binding.analyticsTotalPayoffPace.setText(String.format("Pay %s%s / month (%s%s / day)",
                    currencySymbol, FormatUtil.formatAmount(Math.round(monthlyPace), context),
                    currencySymbol, FormatUtil.formatAmount(Math.round(totalDailyPace), context)));
            binding.analyticsTotalPayoffPaceSubtext.setText(String.format("Recommended pace across %d debt(s) with deadlines to clear on time.", paceGoalsCount));
        } else {
            binding.analyticsTotalPayoffPace.setText("No deadline schedules");
            binding.analyticsTotalPayoffPaceSubtext.setText("Assign target payoff deadlines to your debts to unlock deadline pacing.");
        }

        // 5. Smart Payoff Strategy & Priorities
        if (snowballCandidate != null) {
            binding.analyticsSnowballRecommendation.setText(String.format("🎯 Snowball Win: Clear '%s' (%s%s left) first",
                    snowballCandidate.getName(), currencySymbol, FormatUtil.formatAmount(snowballCandidate.getRemainingDebt(), context)));
            binding.analyticsSnowballSubtext.setText("Knocking out your smallest balance first generates psychological momentum.");
        } else {
            binding.analyticsSnowballRecommendation.setText("🎯 No active liabilities to clear!");
            binding.analyticsSnowballSubtext.setText("All loans and debts are fully paid off.");
        }

        if (nextDueCandidate != null) {
            long diffDays = (nextDueCandidate.getDeadline() - now) / (24L * 60 * 60 * 1000);
            if (diffDays < 0) {
                binding.analyticsNextDueRecommendation.setText(String.format("⚠️ Overdue: '%s' was due %d day(s) ago!",
                        nextDueCandidate.getName(), Math.abs(diffDays)));
                binding.analyticsNextDueRecommendation.setTextColor(errorColor);
            } else {
                binding.analyticsNextDueRecommendation.setText(String.format("⏰ Next Due: '%s' due in %d day(s) (%s)",
                        nextDueCandidate.getName(), diffDays, DateParser.format(nextDueCandidate.getDeadline(), deadlineFormat)));
                binding.analyticsNextDueRecommendation.setTextColor(tertiaryColor);
            }
            binding.analyticsNextDueSubtext.setText(String.format("Remaining balance: %s%s",
                    currencySymbol, FormatUtil.formatAmount(nextDueCandidate.getRemainingDebt(), context)));
        } else {
            binding.analyticsNextDueRecommendation.setText("⏰ No upcoming deadlines");
            binding.analyticsNextDueRecommendation.setTextColor(primaryColor);
            binding.analyticsNextDueSubtext.setText("All debts are on schedule or have no deadline set.");
        }

        // 6. Category Breakdown
        binding.analyticsDebtCategoryContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);

        double totalCategorySum = 0.0;
        for (double v : categoryDebtTotals.values()) totalCategorySum += v;

        if (categoryDebtTotals.isEmpty() || totalCategorySum <= 0) {
            TextView emptyTv = new TextView(context);
            emptyTv.setText(cachedDebts.isEmpty() ? "No debts added yet." : "All tracked debts are 100% paid off!");
            emptyTv.setTextColor(Color.GRAY);
            binding.analyticsDebtCategoryContainer.addView(emptyTv);
        } else {
            for (Map.Entry<DebtCategory, Double> entry : categoryDebtTotals.entrySet()) {
                DebtCategory cat = entry.getKey();
                double amount = entry.getValue();
                int pct = (int) Math.round((amount / totalCategorySum) * 100.0);

                View row = inflater.inflate(R.layout.item_analytics_category, binding.analyticsDebtCategoryContainer, false);
                TextView label = row.findViewById(R.id.category_name);
                TextView amountTv = row.findViewById(R.id.category_amount);
                ProgressBar bar = row.findViewById(R.id.category_progress);

                label.setText(String.format("%s (%d%%)", cat.getFormattedLabel(), pct));
                amountTv.setText(String.format("%s%s", currencySymbol, FormatUtil.formatAmount(Math.round(amount), context)));
                bar.setProgress(pct);

                try {
                    bar.getProgressDrawable().setTint(Color.parseColor(cat.getColorHex()));
                } catch (Exception ignored) {}

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
        long now = System.currentTimeMillis();
        int primaryColor = com.google.android.material.color.MaterialColors.getColor(binding.getRoot(), androidx.appcompat.R.attr.colorPrimary);
        int tertiaryColor = com.google.android.material.color.MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorTertiary);

        binding.analyticsIndivDebtName.setText(targetDebt.getName());
        DebtCategory cat = DebtCategory.fromId(targetDebt.getCategory());
        binding.analyticsIndivDebtBadge.setText(cat.getFormattedLabel());
        try {
            binding.analyticsIndivDebtBadge.setTextColor(Color.parseColor(cat.getColorHex()));
        } catch (Exception ignored) {}

        double remaining = targetDebt.getRemainingDebt();
        binding.analyticsIndivDebtRemaining.setText(String.format("%s%s", currencySymbol,
                FormatUtil.formatAmount(remaining, context)));

        int pct = targetDebt.getProgressPercent();
        if (targetDebt.isOwedToMe()) {
            binding.analyticsIndivDebtSubtext.setText(String.format("Lent: %s%s | Collected: %s%s (%d%%)",
                    currencySymbol, FormatUtil.formatAmount(targetDebt.getTotalAmount(), context),
                    currencySymbol, FormatUtil.formatAmount(targetDebt.getAmountPaid(), context),
                    pct));
        } else {
            binding.analyticsIndivDebtSubtext.setText(String.format("Total: %s%s | Paid: %s%s (%d%%)",
                    currencySymbol, FormatUtil.formatAmount(targetDebt.getTotalAmount(), context),
                    currencySymbol, FormatUtil.formatAmount(targetDebt.getAmountPaid(), context),
                    pct));
        }
        binding.analyticsIndivDebtProgress.setProgress(pct);

        // Timeline & Days Elapsed
        long daysSinceCreated = Math.max(0, (now - targetDebt.getCreatedAt()) / (24L * 60 * 60 * 1000));
        String timelineStr = "Created " + daysSinceCreated + " day(s) ago";
        if (targetDebt.getDeadline() > 0) {
            long daysLeft = (targetDebt.getDeadline() - now) / (24L * 60 * 60 * 1000);
            if (daysLeft < 0) {
                timelineStr += " • Overdue by " + Math.abs(daysLeft) + " day(s)";
            } else {
                timelineStr += " • " + daysLeft + " day(s) until deadline";
            }
        }
        binding.analyticsIndivTimeline.setText(timelineStr);

        // Repayment velocity for this specific debt
        long sevenDaysAgo = now - (7L * 24 * 60 * 60 * 1000);
        long thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000);

        double debt7Days = 0.0;
        double debt30Days = 0.0;
        double totalDebtPaid = 0.0;
        int paymentCount = 0;

        List<DebtTransaction> indivTxs = new ArrayList<>();
        for (DebtTransaction dt : cachedDebtTransactions) {
            if (debtId.equals(dt.getDebtId())) {
                indivTxs.add(dt);
                double amt = dt.getAmount();
                if (dt.getDate() >= sevenDaysAgo) {
                    debt7Days += amt;
                }
                if (dt.getDate() >= thirtyDaysAgo) {
                    debt30Days += amt;
                }
                totalDebtPaid += amt;
                paymentCount++;
            }
        }

        double avgPayment = paymentCount > 0 ? (totalDebtPaid / paymentCount) : 0.0;

        binding.analyticsIndiv7day.setText(String.format("%s%s", currencySymbol, FormatUtil.formatAmount(Math.round(debt7Days), context)));
        binding.analyticsIndiv30day.setText(String.format("%s%s", currencySymbol, FormatUtil.formatAmount(Math.round(debt30Days), context)));
        binding.analyticsIndivTxCount.setText(String.format("%d Repayments", paymentCount));
        binding.analyticsIndivAvgPayment.setText(String.format("Avg: %s%s", currencySymbol, FormatUtil.formatAmount(Math.round(avgPayment), context)));

        // Payoff Pace Advisor for this debt
        if (targetDebt.getDeadline() > 0 && remaining > 0) {
            long daysRemaining = (targetDebt.getDeadline() - now) / (24L * 60 * 60 * 1000);
            if (daysRemaining > 0) {
                double dailyRate = remaining / daysRemaining;
                double monthlyRate = dailyRate * 30.0;
                binding.analyticsIndivPaceTitle.setText(String.format((targetDebt.isOwedToMe() ? "Collect %s%s / month (%s%s / day)" : "Pay %s%s / month (%s%s / day)"),
                        currencySymbol, FormatUtil.formatAmount(Math.round(monthlyRate), context),
                        currencySymbol, FormatUtil.formatAmount(Math.round(dailyRate), context)));
                binding.analyticsIndivPaceSubtext.setText(String.format((targetDebt.isOwedToMe() ? "Expected return in %d days (%s)." : "Payoff deadline in %d days (%s)."),
                        daysRemaining, DateParser.format(targetDebt.getDeadline(), deadlineFormat)));
            } else {
                binding.analyticsIndivPaceTitle.setText(targetDebt.isOwedToMe() ? "Target return deadline reached!" : "Payoff target deadline reached!");
                binding.analyticsIndivPaceSubtext.setText("Recommended to clear the remaining balance as soon as possible.");
            }
        } else {
            binding.analyticsIndivPaceTitle.setText(targetDebt.isOwedToMe() ? "No return deadline configured" : "No payoff deadline configured");
            binding.analyticsIndivPaceSubtext.setText("Set a target deadline on this record to see pacing velocity.");
        }

        // Projected Completion Date for this debt based on actual payments
        if (remaining <= 0) {
            binding.analyticsIndivProjectedDate.setText("🎉 Fully Cleared! 100% completed.");
            binding.analyticsIndivProjectedDate.setTextColor(primaryColor);
        } else if (debt30Days > 0) {
            double monthsLeft = remaining / debt30Days;
            long projDate = now + (long) (monthsLeft * 30L * 24 * 60 * 60 * 1000);
            binding.analyticsIndivProjectedDate.setText(String.format(
                    "Projected completion: %s (in ~%.1f months at current pace)",
                    DateParser.format(projDate, "MMMM yyyy"), monthsLeft));
            binding.analyticsIndivProjectedDate.setTextColor(tertiaryColor);
        } else {
            binding.analyticsIndivProjectedDate.setText("Projected completion: Log repayments to forecast date");
            binding.analyticsIndivProjectedDate.setTextColor(Color.GRAY);
        }

        // Recent Repayments History for this debt
        binding.analyticsIndivPaymentsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);

        if (indivTxs.isEmpty()) {
            TextView emptyTv = new TextView(context);
            emptyTv.setText(targetDebt.isOwedToMe() ? "No received payments logged yet." : "No repayments logged yet.");
            emptyTv.setTextColor(Color.GRAY);
            binding.analyticsIndivPaymentsContainer.addView(emptyTv);
        } else {
            for (int i = 0; i < Math.min(indivTxs.size(), 5); i++) {
                DebtTransaction dt = indivTxs.get(i);
                View row = inflater.inflate(R.layout.recycler_transaction, binding.analyticsIndivPaymentsContainer, false);
                TextView typeTv = row.findViewById(R.id.transaction_type);
                TextView amountTv = row.findViewById(R.id.transaction_amount);
                TextView dateTv = row.findViewById(R.id.transaction_date);

                if (typeTv != null) {
                    typeTv.setText(dt.getNotes() != null && !dt.getNotes().isEmpty() ? dt.getNotes() : (targetDebt.isOwedToMe() ? "Received" : "Repayment"));
                    typeTv.setVisibility(View.VISIBLE);
                }
                if (amountTv != null) {
                    amountTv.setText(String.format((targetDebt.isOwedToMe() ? "+%s%s" : "-%s%s"), currencySymbol, FormatUtil.formatAmount(dt.getAmount(), context)));
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
