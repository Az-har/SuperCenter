package com.eipna.centsation.ui.adapters;

import android.content.Context;
import android.icu.text.NumberFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textview.MaterialTextView;
import com.eipna.centsation.R;
import com.eipna.centsation.data.Currency;
import com.eipna.centsation.data.transaction.Transaction;
import com.eipna.centsation.data.transaction.TransactionType;
import com.eipna.centsation.util.AnimationUtil;
import com.eipna.centsation.util.DateParser;
import com.eipna.centsation.util.FormatUtil;
import com.eipna.centsation.util.PreferenceUtil;

import java.util.Objects;

public class TransactionAdapter extends ListAdapter<Transaction, TransactionAdapter.ViewHolder> {

    private static final DiffUtil.ItemCallback<Transaction> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Transaction>() {
                @Override
                public boolean areItemsTheSame(@NonNull Transaction oldItem, @NonNull Transaction newItem) {
                    return oldItem.getID() == newItem.getID();
                }

                @Override
                public boolean areContentsTheSame(@NonNull Transaction oldItem, @NonNull Transaction newItem) {
                    return oldItem.getAmount() == newItem.getAmount()
                            && oldItem.getDate() == newItem.getDate()
                            && Objects.equals(oldItem.getType(), newItem.getType());
                }
            };

    private final Context context;
    private final PreferenceUtil preferenceUtil;

    public TransactionAdapter(Context context) {
        super(DIFF_CALLBACK);
        this.context = context;
        this.preferenceUtil = new PreferenceUtil(context);
    }

    @NonNull
    @Override
    public TransactionAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View transactionView = LayoutInflater.from(context)
                .inflate(R.layout.recycler_transaction, parent, false);
        return new ViewHolder(transactionView);
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionAdapter.ViewHolder holder, int position) {
        holder.bind(getItem(position), preferenceUtil);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        MaterialCardView parent;
        MaterialTextView amount, date;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            parent = itemView.findViewById(R.id.transaction_parent);
            amount = itemView.findViewById(R.id.transaction_amount);
            date = itemView.findViewById(R.id.transaction_date);
            AnimationUtil.applySpringPressEffect(parent);
        }

        public void bind(Transaction currentTransaction, PreferenceUtil preferenceUtil) {
            String selectedCurrencySymbol = Currency.getSymbol(preferenceUtil.getCurrency());
            String deadlineFormat = preferenceUtil.getDeadlineFormat();

            int depositCardColor = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorPrimaryContainer);
            int depositTextColor = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnPrimaryContainer);

            int withdrawCardColor = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorTertiaryContainer);
            int withdrawTextColor = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnTertiaryContainer);

            date.setText(DateParser.getStringDate(currentTransaction.getDate(), deadlineFormat));

            if (currentTransaction.getType().equals(TransactionType.DEPOSIT.VALUE) || currentTransaction.getType().equals(TransactionType.CREATED.VALUE)) {
                parent.setCardBackgroundColor(depositCardColor);
                amount.setTextColor(depositTextColor);
                date.setTextColor(depositTextColor);
                amount.setText(String.format("%c%s%s", '+', selectedCurrencySymbol, FormatUtil.formatAmount(currentTransaction.getAmount(), parent.getContext())));
            } else if (currentTransaction.getType().equals(TransactionType.WITHDRAW.VALUE)) {
                parent.setCardBackgroundColor(withdrawCardColor);
                amount.setTextColor(withdrawTextColor);
                date.setTextColor(withdrawTextColor);
                amount.setText(String.format("%c%s%s", '-', selectedCurrencySymbol, FormatUtil.formatAmount(currentTransaction.getAmount(), parent.getContext())));
            }
        }
    }
}
