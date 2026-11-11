package com.eipna.centsation.ui.adapters;

import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StrikethroughSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.eipna.centsation.R;
import com.eipna.centsation.data.Currency;
import com.eipna.centsation.data.debt.Debt;
import com.eipna.centsation.data.debt.DebtCategory;
import com.eipna.centsation.databinding.RecyclerDebtBinding;
import com.eipna.centsation.util.DateParser;
import com.eipna.centsation.util.FormatUtil;
import com.eipna.centsation.util.PreferenceUtil;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DebtAdapter extends ListAdapter<Debt, DebtAdapter.DebtViewHolder> {

    public interface Listener {
        void onDebtClick(Debt debt);
        void onPaymentClick(Debt debt);
        void onHistoryClick(Debt debt);
        void onDeleteClick(Debt debt);
        default void onArchiveClick(Debt debt) {}
        default void onUnarchiveClick(Debt debt) {}
    }

    @Nullable
    public Debt getDebtAt(int position) {
        if (position < 0 || position >= getItemCount()) {
            return null;
        }
        return getItem(position);
    }

    private final Listener listener;
    private String currencySymbol = "$";
    private String deadlineFormat = "MM/dd/yyyy";

    private static final DiffUtil.ItemCallback<Debt> DIFF_CALLBACK = new DiffUtil.ItemCallback<Debt>() {
        @Override
        public boolean areItemsTheSame(@NonNull Debt oldItem, @NonNull Debt newItem) {
            return Objects.equals(oldItem.getId(), newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Debt oldItem, @NonNull Debt newItem) {
            return Objects.equals(oldItem.getName(), newItem.getName())
                    && Double.compare(oldItem.getTotalAmount(), newItem.getTotalAmount()) == 0
                    && Double.compare(oldItem.getAmountPaid(), newItem.getAmountPaid()) == 0
                    && oldItem.getDebtType() == newItem.getDebtType()
                    && Objects.equals(oldItem.getCategory(), newItem.getCategory())
                    && oldItem.getIsArchived() == newItem.getIsArchived()
                    && oldItem.getDeadline() == newItem.getDeadline()
                    && Objects.equals(oldItem.getNotes(), newItem.getNotes());
        }
    };

    public DebtAdapter(Listener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    public void setDebts(List<Debt> newDebts, Context context) {
        boolean prefsChanged = false;
        if (context != null) {
            PreferenceUtil prefs = new PreferenceUtil(context);
            String newCurrency = Currency.getSymbol(prefs.getCurrency());
            String newDateFormat = prefs.getDeadlineFormat();
            prefsChanged = !Objects.equals(this.currencySymbol, newCurrency)
                    || !Objects.equals(this.deadlineFormat, newDateFormat);
            this.currencySymbol = newCurrency;
            this.deadlineFormat = newDateFormat;
        }
        final boolean formatChanged = prefsChanged;
        List<Debt> snapshot = newDebts != null ? new ArrayList<>(newDebts) : new ArrayList<>();
        submitList(snapshot, () -> {
            if (formatChanged && getItemCount() > 0) {
                notifyItemRangeChanged(0, getItemCount());
            }
        });
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
        holder.bind(getItem(position));
    }

    class DebtViewHolder extends RecyclerView.ViewHolder {
        private final RecyclerDebtBinding binding;

        public DebtViewHolder(@NonNull RecyclerDebtBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Debt debt) {
            Context context = itemView.getContext();
            boolean isArchived = debt.getIsArchived() == Debt.IS_ARCHIVE;
            if (isArchived) {
                SpannableString spannable = new SpannableString(debt.getName());
                spannable.setSpan(new StrikethroughSpan(), 0, debt.getName().length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                binding.debtName.setText(spannable);
                binding.debtName.setAlpha(0.6f);
                binding.btnDebtPay.setVisibility(View.GONE);
                binding.btnDebtArchive.setVisibility(View.GONE);
                binding.btnDebtUnarchive.setVisibility(View.VISIBLE);
            } else {
                binding.debtName.setText(debt.getName());
                binding.debtName.setAlpha(1.0f);
                binding.btnDebtPay.setVisibility(View.VISIBLE);
                binding.btnDebtArchive.setVisibility(View.VISIBLE);
                binding.btnDebtUnarchive.setVisibility(View.GONE);
            }

            boolean isOwed = debt.isOwedToMe();
            int primaryColor = MaterialColors.getColor(itemView, androidx.appcompat.R.attr.colorPrimary);
            int errorColor = MaterialColors.getColor(itemView, androidx.appcompat.R.attr.colorError);
            int onPrimaryContainer = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnPrimaryContainer);
            int onErrorContainer = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnErrorContainer);
            int onSurfaceVariant = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnSurfaceVariant);

            if (isOwed) {
                binding.debtTypeBadge.setText("📥 Owed to Me");
                binding.debtTypeBadge.setBackgroundResource(R.drawable.bg_pill_accent);
                binding.debtTypeBadge.setTextColor(onPrimaryContainer);
                binding.debtRemainingAmount.setTextColor(primaryColor);
                binding.debtRemainingLabel.setText(" pending");
                binding.debtPercent.setBackgroundResource(R.drawable.bg_pill_accent);
                binding.debtPercent.setTextColor(onPrimaryContainer);
                binding.debtPercent.setText(debt.isPaidOff() ? "Settled 🎉" : debt.getProgressPercent() + "% Collected");
                binding.btnDebtPay.setText(R.string.action_record_received);
            } else {
                binding.debtTypeBadge.setText("💸 I Owe");
                binding.debtTypeBadge.setBackgroundResource(R.drawable.bg_pill_error);
                binding.debtTypeBadge.setTextColor(onErrorContainer);
                binding.debtRemainingAmount.setTextColor(errorColor);
                binding.debtRemainingLabel.setText(" remaining");
                if (debt.isPaidOff()) {
                    binding.debtPercent.setBackgroundResource(R.drawable.bg_pill_accent);
                    binding.debtPercent.setTextColor(onPrimaryContainer);
                    binding.debtPercent.setText("Paid Off 🎉");
                } else {
                    binding.debtPercent.setBackgroundResource(R.drawable.bg_pill_error);
                    binding.debtPercent.setTextColor(onErrorContainer);
                    binding.debtPercent.setText(debt.getProgressPercent() + "% Paid");
                }
                binding.btnDebtPay.setText(R.string.action_record_payment);
            }

            DebtCategory category = DebtCategory.fromId(debt.getCategory());
            binding.debtCategoryBadge.setText(category.getFormattedLabel());
            binding.debtCategoryBadge.setBackgroundResource(R.drawable.bg_pill_neutral);
            binding.debtCategoryBadge.setTextColor(onSurfaceVariant);

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

            binding.btnDebtArchive.setOnClickListener(v -> {
                if (listener != null) listener.onArchiveClick(debt);
            });

            binding.btnDebtUnarchive.setOnClickListener(v -> {
                if (listener != null) listener.onUnarchiveClick(debt);
            });

            binding.btnDebtDelete.setOnClickListener(v -> {
                if (listener != null) listener.onDeleteClick(debt);
            });
        }
    }
}
