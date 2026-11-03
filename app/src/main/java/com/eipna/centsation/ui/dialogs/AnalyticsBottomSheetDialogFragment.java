package com.eipna.centsation.ui.dialogs;

import android.content.Context;
import android.graphics.Color;
import android.icu.text.NumberFormat;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
import com.eipna.centsation.data.transaction.TransactionType;
import com.eipna.centsation.databinding.DialogBottomSheetAnalyticsBinding;
import com.eipna.centsation.util.AlarmSetter;
import com.eipna.centsation.util.DateParser;
import com.eipna.centsation.util.PreferenceUtil;
import com.eipna.centsation.util.SavingsCalculator;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AnalyticsBottomSheetDialogFragment extends BottomSheetDialogFragment {

    private DialogBottomSheetAnalyticsBinding binding;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private List<Saving> cachedSavings = new ArrayList<>();
    private List<Transaction> cachedTransactions = new ArrayList<>();
    private List<Debt> cachedDebts = new ArrayList<>();
    private List<DebtTransaction> cachedDebtTransactions = new ArrayList<>();
    private String selectedDebtId = null; // null means Total Debt view

    public static AnalyticsBottomSheetDialogFragment newInstance() {
        return new AnalyticsBottomSheetDialogFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogBottomSheetAnalyticsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.btnAnalyticsClose.setOnClickListener(v -> dismiss());

        // Toggle between Savings and Debt modes
        binding.analyticsModeToggleGroup.check(R.id.btn_analytics_tab_savings);
        binding.analyticsModeToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btn_analytics_tab_savings) {
                binding.analyticsSavingsContainer.setVisibility(View.VISIBLE);
                binding.analyticsDebtContainer.setVisibility(View.GONE);
            } else if (checkedId == R.id.btn_analytics_tab_debt) {
                binding.analyticsSavingsContainer.setVisibility(View.GONE);
                binding.analyticsDebtContainer.setVisibility(View.VISIBLE);
            }
        });

        loadAllData();
    }

    private void loadAllData() {
        final Context context = requireContext().getApplicationContext();
        executor.execute(() -> {
            List<Saving> savings;
            List<Transaction> transactions;
            List<Debt> debts;
            List<DebtTransaction> debtTxs;

            try (SavingRepository savingRepo = new SavingRepository(context);
                 TransactionRepository txRepo = new TransactionRepository(context);
                 DebtRepository debtRepo = new DebtRepository(context)) {
                savings = savingRepo.getSavings(Saving.NOT_ARCHIVE);
                transactions = txRepo.getAll();
                debts = debtRepo.getDebts(Debt.NOT_ARCHIVE);
                debtTxs = debtRepo.getAllTransactions();
            }

            if (isAdded() && getActivity() != null) {
                requireActivity().runOnUiThread(() -> {
                    if (binding != null && isAdded()) {
                        cachedSavings = savings != null ? savings : new ArrayList<>();
                        cachedTransactions = transactions != null ? transactions : new ArrayList<>();
                        cachedDebts = debts != null ? debts : new ArrayList<>();
                        cachedDebtTransactions = debtTxs != null ? debtTxs : new ArrayList<>();

                        bindSavingsAnalyticsData();
                        setupDebtOptionsChips();
                        bindTotalDebtData();
                    }
                });
            }
        });
    }

    private void bindSavingsAnalyticsData() {
        Context context = getContext();
        if (context == null || binding == null) return;

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
            if (TransactionType.DEPOSIT.VALUE.equalsIgnoreCase(tx.getType())
                    || TransactionType.CREATED.VALUE.equalsIgnoreCase(tx.getType())) {
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

        binding.analytics7dayTotal.setText(String.format("%s%s", currencySymbol, NumberFormat.getInstance().format(Math.round(total7Days))));
        binding.analytics30dayTotal.setText(String.format("%s%s", currencySymbol, NumberFormat.getInstance().format(Math.round(total30Days))));
        binding.analyticsTxCount.setText(String.format("%d Total Deposits", depositCount));
        binding.analyticsAvgDeposit.setText(String.format("Avg: %s%s", currencySymbol, NumberFormat.getInstance().format(Math.round(avgDeposit))));

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
                amountTv.setText(String.format("%s%s", currencySymbol, NumberFormat.getInstance().format(Math.round(amount))));
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
                    currencySymbol, NumberFormat.getInstance().format(Math.round(totalDailyPace))));
            binding.analyticsPaceSubtext.setText(String.format("Recommended pace across %d active goal(s) to hit all deadlines on time.", paceGoalsCount));
        } else {
            binding.analyticsTotalDailyPace.setText("All active deadlines achieved!");
            binding.analyticsPaceSubtext.setText("Set deadlines on your savings goals to unlock daily pace recommendations.");
        }
    }

    private void setupDebtOptionsChips() {
        Context context = getContext();
        if (context == null || binding == null) return;

        binding.debtAnalyticsChipGroup.removeAllViews();

        // 1. Total Debt Option Chip
        Chip totalChip = new Chip(context);
        totalChip.setText("📊 Total Debt (All Combined)");
        totalChip.setCheckable(true);
        totalChip.setChecked(selectedDebtId == null);
        totalChip.setOnClickListener(v -> {
            selectedDebtId = null;
            binding.layoutTotalDebtView.setVisibility(View.VISIBLE);
            binding.layoutIndividualDebtView.setVisibility(View.GONE);
            bindTotalDebtData();
        });
        binding.debtAnalyticsChipGroup.addView(totalChip);

        // 2. Individual Debt Option Chips
        for (Debt debt : cachedDebts) {
            Chip debtChip = new Chip(context);
            DebtCategory cat = DebtCategory.fromId(debt.getCategory());
            debtChip.setText(cat.getEmoji() + " " + debt.getName());
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
        Context context = getContext();
        if (context == null || binding == null) return;

        PreferenceUtil prefUtil = new PreferenceUtil(context);
        String currencySymbol = Currency.getSymbol(prefUtil.getCurrency());

        double totalOriginal = 0.0;
        double totalPaid = 0.0;
        double totalRemaining = 0.0;

        Map<DebtCategory, Double> categoryDebtTotals = new HashMap<>();

        for (Debt d : cachedDebts) {
            totalOriginal += d.getTotalAmount();
            totalPaid += d.getAmountPaid();
            double rem = d.getRemainingDebt();
            totalRemaining += rem;

            DebtCategory cat = DebtCategory.fromId(d.getCategory());
            categoryDebtTotals.put(cat, categoryDebtTotals.getOrDefault(cat, 0.0) + rem);
        }

        int overallProgress = totalOriginal > 0 ? (int) Math.round((totalPaid / totalOriginal) * 100.0) : 100;

        binding.analyticsTotalDebtRemaining.setText(String.format("%s%s", currencySymbol,
                NumberFormat.getInstance().format(totalRemaining)));
        binding.analyticsTotalDebtSubtext.setText(String.format("Total Borrowed: %s%s | Paid Off: %s%s (%d%%)",
                currencySymbol, NumberFormat.getInstance().format(totalOriginal),
                currencySymbol, NumberFormat.getInstance().format(totalPaid),
                overallProgress));
        binding.analyticsTotalDebtProgress.setProgress(overallProgress);

        // Net Financial Position
        double totalSavings = 0.0;
        for (Saving s : cachedSavings) {
            totalSavings += s.getCurrentSaving();
        }
        double netWorth = totalSavings - totalRemaining;

        binding.analyticsNetSavings.setText(String.format("+%s%s", currencySymbol, NumberFormat.getInstance().format(totalSavings)));
        binding.analyticsNetDebt.setText(String.format("-%s%s", currencySymbol, NumberFormat.getInstance().format(totalRemaining)));

        if (netWorth >= 0) {
            binding.analyticsNetWorth.setText(String.format("+%s%s", currencySymbol, NumberFormat.getInstance().format(netWorth)));
            binding.analyticsNetWorth.setTextColor(Color.parseColor("#1B6B50"));
        } else {
            binding.analyticsNetWorth.setText(String.format("-%s%s", currencySymbol, NumberFormat.getInstance().format(Math.abs(netWorth))));
            binding.analyticsNetWorth.setTextColor(Color.parseColor("#BA1A1A"));
        }

        // Debt Repayment Velocity
        long now = System.currentTimeMillis();
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

        binding.analyticsDebt7dayTotal.setText(String.format("%s%s", currencySymbol, NumberFormat.getInstance().format(Math.round(debt7Days))));
        binding.analyticsDebt30dayTotal.setText(String.format("%s%s", currencySymbol, NumberFormat.getInstance().format(Math.round(debt30Days))));
        binding.analyticsDebtTxCount.setText(String.format("%d Total Repayments", debtPaymentCount));
        binding.analyticsDebtAvgPayment.setText(String.format("Avg: %s%s", currencySymbol, NumberFormat.getInstance().format(Math.round(avgDebtPayment))));

        // Debt by Category
        binding.analyticsDebtCategoryContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);

        if (categoryDebtTotals.isEmpty() || totalRemaining <= 0) {
            TextView emptyTv = new TextView(context);
            emptyTv.setText(cachedDebts.isEmpty() ? "No debts added yet." : "All debts are 100% paid off!");
            emptyTv.setTextColor(Color.GRAY);
            binding.analyticsDebtCategoryContainer.addView(emptyTv);
        } else {
            for (Map.Entry<DebtCategory, Double> entry : categoryDebtTotals.entrySet()) {
                DebtCategory cat = entry.getKey();
                double amount = entry.getValue();
                int pct = (int) Math.round((amount / totalRemaining) * 100.0);

                View row = inflater.inflate(R.layout.item_analytics_category, binding.analyticsDebtCategoryContainer, false);
                TextView label = row.findViewById(R.id.category_name);
                TextView amountTv = row.findViewById(R.id.category_amount);
                ProgressBar bar = row.findViewById(R.id.category_progress);

                label.setText(String.format("%s (%d%%)", cat.getFormattedLabel(), pct));
                amountTv.setText(String.format("%s%s", currencySymbol, NumberFormat.getInstance().format(Math.round(amount))));
                bar.setProgress(pct);

                try {
                    bar.getProgressDrawable().setTint(Color.parseColor(cat.getColorHex()));
                } catch (Exception ignored) {}

                binding.analyticsDebtCategoryContainer.addView(row);
            }
        }
    }

    private void bindIndividualDebtData(String debtId) {
        Context context = getContext();
        if (context == null || binding == null || debtId == null) return;

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

        binding.analyticsIndivDebtName.setText(targetDebt.getName());
        DebtCategory cat = DebtCategory.fromId(targetDebt.getCategory());
        binding.analyticsIndivDebtBadge.setText(cat.getFormattedLabel());
        try {
            binding.analyticsIndivDebtBadge.setTextColor(Color.parseColor(cat.getColorHex()));
        } catch (Exception ignored) {}

        double remaining = targetDebt.getRemainingDebt();
        binding.analyticsIndivDebtRemaining.setText(String.format("%s%s", currencySymbol,
                NumberFormat.getInstance().format(remaining)));

        int pct = targetDebt.getProgressPercent();
        binding.analyticsIndivDebtSubtext.setText(String.format("Total: %s%s | Paid: %s%s (%d%%)",
                currencySymbol, NumberFormat.getInstance().format(targetDebt.getTotalAmount()),
                currencySymbol, NumberFormat.getInstance().format(targetDebt.getAmountPaid()),
                pct));
        binding.analyticsIndivDebtProgress.setProgress(pct);

        // Repayment velocity for this specific debt
        long now = System.currentTimeMillis();
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

        binding.analyticsIndiv7day.setText(String.format("%s%s", currencySymbol, NumberFormat.getInstance().format(Math.round(debt7Days))));
        binding.analyticsIndiv30day.setText(String.format("%s%s", currencySymbol, NumberFormat.getInstance().format(Math.round(debt30Days))));
        binding.analyticsIndivTxCount.setText(String.format("%d Repayments", paymentCount));
        binding.analyticsIndivAvgPayment.setText(String.format("Avg: %s%s", currencySymbol, NumberFormat.getInstance().format(Math.round(avgPayment))));

        // Payoff Pace Advisor for this debt
        if (targetDebt.getDeadline() > 0 && remaining > 0) {
            long daysRemaining = (targetDebt.getDeadline() - now) / (24L * 60 * 60 * 1000);
            if (daysRemaining > 0) {
                double dailyRate = remaining / daysRemaining;
                double monthlyRate = dailyRate * 30.0;
                binding.analyticsIndivPaceTitle.setText(String.format("Pay %s%s / month (%s%s / day)",
                        currencySymbol, NumberFormat.getInstance().format(Math.round(monthlyRate)),
                        currencySymbol, NumberFormat.getInstance().format(Math.round(dailyRate))));
                binding.analyticsIndivPaceSubtext.setText(String.format("Payoff deadline in %d days (%s).",
                        daysRemaining, DateParser.format(targetDebt.getDeadline(), deadlineFormat)));
            } else {
                binding.analyticsIndivPaceTitle.setText("Payoff target deadline reached!");
                binding.analyticsIndivPaceSubtext.setText("Recommended to clear the remaining balance as soon as possible.");
            }
        } else if (remaining == 0) {
            binding.analyticsIndivPaceTitle.setText("🎉 Debt 100% Paid Off!");
            binding.analyticsIndivPaceSubtext.setText("Congratulations! You have completely cleared this liability.");
        } else {
            binding.analyticsIndivPaceTitle.setText("No target payoff date configured");
            binding.analyticsIndivPaceSubtext.setText("Edit this debt to set a target payoff date and calculate daily/monthly required payment rates.");
        }

        // Recent Payments for this debt
        binding.analyticsIndivPaymentsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);

        if (indivTxs.isEmpty()) {
            TextView emptyTv = new TextView(context);
            emptyTv.setText("No payments recorded yet for this debt.");
            emptyTv.setTextColor(Color.GRAY);
            binding.analyticsIndivPaymentsContainer.addView(emptyTv);
        } else {
            for (DebtTransaction dt : indivTxs) {
                View row = inflater.inflate(R.layout.recycler_transaction, binding.analyticsIndivPaymentsContainer, false);
                TextView typeTv = row.findViewById(R.id.transaction_type);
                TextView amountTv = row.findViewById(R.id.transaction_amount);
                TextView dateTv = row.findViewById(R.id.transaction_date);

                if (typeTv != null) {
                    typeTv.setText(dt.getNotes() != null && !dt.getNotes().isEmpty() ? dt.getNotes() : "Payment");
                    typeTv.setVisibility(View.VISIBLE);
                }
                if (amountTv != null) {
                    amountTv.setText(String.format("-%s%s", currencySymbol, NumberFormat.getInstance().format(dt.getAmount())));
                    amountTv.setTextColor(Color.parseColor("#1B6B50"));
                }
                if (dateTv != null) {
                    dateTv.setText(DateParser.format(dt.getDate(), deadlineFormat));
                }

                binding.analyticsIndivPaymentsContainer.addView(row);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
