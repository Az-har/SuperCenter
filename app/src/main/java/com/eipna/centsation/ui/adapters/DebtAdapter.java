package com.eipna.centsation.ui.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.eipna.centsation.data.Currency;
import com.eipna.centsation.data.debt.Debt;
import com.eipna.centsation.data.debt.DebtCategory;
import com.eipna.centsation.databinding.RecyclerDebtBinding;
import com.eipna.centsation.util.DateParser;
import com.eipna.centsation.util.FormatUtil;
import com.eipna.centsation.util.PreferenceUtil;

import java.util.ArrayList;
import java.util.List;

public class DebtAdapter extends RecyclerView.Adapter<DebtAdapter.DebtViewHolder> {

    public interface Listener {
        void onDebtClick(Debt debt);
        void onPaymentClick(Debt debt);
        void onHistoryClick(Debt debt);
        void onDeleteClick(Debt debt);
    }

    private final List<Debt> debts = new ArrayList<>();
    private final Listener listener;
    private String currencySymbol = "$";
    private String deadlineFormat = "MM/dd/yyyy";

    public DebtAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setDebts(List<Debt> newDebts, Context context) {
        if (context != null) {
            PreferenceUtil prefs = new PreferenceUtil(context);
            this.currencySymbol = Currency.getSymbol(prefs.getCurrency());
            this.deadlineFormat = prefs.getDeadlineFormat();
        }
        this.debts.clear();
        if (newDebts != null) {
            this.debts.addAll(newDebts);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DebtViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        RecyclerDebtBinding binding = RecyclerDebtBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new DebtViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull DebtViewHolder holder, int position) {
        holder.bind(debts.get(position));
    }

    @Override
    public int getItemCount() {
        return debts.size();
    }

    class DebtViewHolder extends RecyclerView.ViewHolder {
        private final RecyclerDebtBinding binding;

        public DebtViewHolder(@NonNull RecyclerDebtBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Debt debt) {
            Context context = itemView.getContext();
            binding.debtName.setText(debt.getName());

            boolean isOwed = debt.isOwedToMe();
            if (isOwed) {
                binding.debtTypeBadge.setText("📥 Owed to Me");
                binding.debtTypeBadge.setTextColor(Color.parseColor("#1B6B50"));
                binding.debtRemainingAmount.setTextColor(Color.parseColor("#1B6B50"));
                binding.debtRemainingLabel.setText(" pending");
                binding.debtPercent.setText(debt.isPaidOff() ? "Settled 🎉" : debt.getProgressPercent() + "% Collected");
                binding.btnDebtPay.setText("Record Received");
            } else {
                binding.debtTypeBadge.setText("💸 I Owe");
                binding.debtTypeBadge.setTextColor(Color.parseColor("#BA1A1A"));
                binding.debtRemainingAmount.setTextColor(Color.parseColor("#BA1A1A"));
                binding.debtRemainingLabel.setText(" remaining");
                binding.debtPercent.setText(debt.isPaidOff() ? "Paid Off 🎉" : debt.getProgressPercent() + "% Paid");
                binding.btnDebtPay.setText("Record Payment");
            }

            DebtCategory category = DebtCategory.fromId(debt.getCategory());
            binding.debtCategoryBadge.setText(category.getFormattedLabel());
            try {
                binding.debtCategoryBadge.setTextColor(Color.parseColor(category.getColorHex()));
            } catch (Exception ignored) {}

            int progressPercent = debt.getProgressPercent();
            binding.debtProgress.setProgress(progressPercent);

            double remaining = debt.getRemainingDebt();
            binding.debtRemainingAmount.setText(String.format("%s%s", currencySymbol,
                    FormatUtil.formatAmount(remaining, context)));

            if (isOwed) {
                binding.debtSubtext.setText(String.format("Lent: %s%s | Collected: %s%s",
                        currencySymbol, FormatUtil.formatAmount(debt.getTotalAmount(), context),
                        currencySymbol, FormatUtil.formatAmount(debt.getAmountPaid(), context)));
            } else {
                binding.debtSubtext.setText(String.format("Total: %s%s | Paid: %s%s",
                        currencySymbol, FormatUtil.formatAmount(debt.getTotalAmount(), context),
                        currencySymbol, FormatUtil.formatAmount(debt.getAmountPaid(), context)));
            }

            if (debt.getDeadline() > 0) {
                binding.debtDeadline.setVisibility(View.VISIBLE);
                String formattedDate = DateParser.format(debt.getDeadline(), deadlineFormat);
                binding.debtDeadline.setText((isOwed ? "Target Return: " : "Target Payoff: ") + formattedDate);
            } else {
                binding.debtDeadline.setVisibility(View.GONE);
            }

            if (debt.getNotes() != null && !debt.getNotes().trim().isEmpty()) {
                binding.debtNotesContainer.setVisibility(View.VISIBLE);
                binding.debtNotesPreview.setText(debt.getNotes().trim());
            } else {
                binding.debtNotesContainer.setVisibility(View.GONE);
            }

            binding.debtParent.setOnClickListener(v -> {
                if (listener != null) listener.onDebtClick(debt);
            });

            binding.btnDebtPay.setOnClickListener(v -> {
                if (listener != null) listener.onPaymentClick(debt);
            });

            binding.btnDebtHistory.setOnClickListener(v -> {
                if (listener != null) listener.onHistoryClick(debt);
            });

            binding.btnDebtDelete.setOnClickListener(v -> {
                if (listener != null) listener.onDeleteClick(debt);
            });
        }
    }
}
