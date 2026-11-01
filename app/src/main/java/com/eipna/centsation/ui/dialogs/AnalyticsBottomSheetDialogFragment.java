package com.eipna.centsation.ui.dialogs;

import android.content.Context;
import android.graphics.Color;
import android.icu.text.NumberFormat;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eipna.centsation.R;
import com.eipna.centsation.data.Currency;
import com.eipna.centsation.data.saving.Saving;
import com.eipna.centsation.data.saving.SavingCategory;
import com.eipna.centsation.data.saving.SavingRepository;
import com.eipna.centsation.data.transaction.Transaction;
import com.eipna.centsation.data.transaction.TransactionRepository;
import com.eipna.centsation.data.transaction.TransactionType;
import com.eipna.centsation.databinding.DialogBottomSheetAnalyticsBinding;
import com.eipna.centsation.util.AlarmSetter;
import com.eipna.centsation.util.PreferenceUtil;
import com.eipna.centsation.util.SavingsCalculator;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AnalyticsBottomSheetDialogFragment extends BottomSheetDialogFragment {

    private DialogBottomSheetAnalyticsBinding binding;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

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

        final Context context = requireContext().getApplicationContext();
        executor.execute(() -> {
            List<Saving> savings;
            List<Transaction> transactions;

            try (SavingRepository savingRepo = new SavingRepository(context);
                 TransactionRepository txRepo = new TransactionRepository(context)) {
                savings = savingRepo.getSavings(Saving.NOT_ARCHIVE);
                transactions = txRepo.getAll();
            }

            if (isAdded() && getActivity() != null) {
                requireActivity().runOnUiThread(() -> {
                    if (binding != null && isAdded()) {
                        bindAnalyticsData(savings, transactions);
                    }
                });
            }
        });
    }

    private void bindAnalyticsData(List<Saving> savings, List<Transaction> transactions) {
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

        if (transactions != null) {
            for (Transaction tx : transactions) {
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

        if (savings != null) {
            for (Saving s : savings) {
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
        }

        // Render Category Progress Bars
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        executor.shutdown();
        binding = null;
    }
}
