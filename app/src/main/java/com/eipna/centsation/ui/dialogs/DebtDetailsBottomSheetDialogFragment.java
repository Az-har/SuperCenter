package com.eipna.centsation.ui.dialogs;

import android.content.Context;
import android.graphics.Color;
import android.icu.text.NumberFormat;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eipna.centsation.R;
import com.eipna.centsation.data.Currency;
import com.eipna.centsation.data.debt.Debt;
import com.eipna.centsation.data.debt.DebtCategory;
import com.eipna.centsation.data.debt.DebtRepository;
import com.eipna.centsation.data.debt.DebtTransaction;
import com.eipna.centsation.databinding.DialogBottomSheetDebtDetailsBinding;
import com.eipna.centsation.util.DateParser;
import com.eipna.centsation.util.PreferenceUtil;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DebtDetailsBottomSheetDialogFragment extends BottomSheetDialogFragment {

    private static final String ARG_DEBT_ID = "arg_debt_id";

    public interface OnDebtUpdatedListener {
        void onDebtUpdated();
    }

    private DialogBottomSheetDebtDetailsBinding binding;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private String debtId;
    private Debt currentDebt;
    private OnDebtUpdatedListener listener;

    public static DebtDetailsBottomSheetDialogFragment newInstance(String debtId) {
        DebtDetailsBottomSheetDialogFragment fragment = new DebtDetailsBottomSheetDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_DEBT_ID, debtId);
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnDebtUpdatedListener(OnDebtUpdatedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogBottomSheetDebtDetailsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.btnDebtDetailClose.setOnClickListener(v -> dismiss());

        if (getArguments() != null) {
            debtId = getArguments().getString(ARG_DEBT_ID);
        }

        if (debtId == null) {
            dismiss();
            return;
        }

        binding.btnSubmitPayment.setOnClickListener(v -> submitPayment());
        binding.btnEditDebt.setOnClickListener(v -> editDebt());
        binding.btnDeleteDebt.setOnClickListener(v -> confirmDeleteDebt());

        loadDebtData();
    }

    private void loadDebtData() {
        final Context context = requireContext().getApplicationContext();
        executor.execute(() -> {
            try (DebtRepository repo = new DebtRepository(context)) {
                currentDebt = repo.getDebt(debtId);
                List<DebtTransaction> txs = repo.getTransactions(debtId);

                if (currentDebt != null && isAdded() && getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        if (binding == null) return;
                        bindDebtView(currentDebt, txs);
                    });
                }
            }
        });
    }

    private void bindDebtView(Debt debt, List<DebtTransaction> txs) {
        Context context = getContext();
        if (context == null || binding == null) return;

        PreferenceUtil prefs = new PreferenceUtil(context);
        String currencySymbol = Currency.getSymbol(prefs.getCurrency());
        String deadlineFormat = prefs.getDeadlineFormat();

        binding.detailDebtName.setText(debt.getName());

        DebtCategory category = DebtCategory.fromId(debt.getCategory());
        binding.detailDebtCategoryBadge.setText(category.getFormattedLabel());
        try {
            binding.detailDebtCategoryBadge.setTextColor(Color.parseColor(category.getColorHex()));
        } catch (Exception ignored) {}

        double remaining = debt.getRemainingDebt();
        binding.detailDebtRemaining.setText(String.format("%s%s", currencySymbol,
                NumberFormat.getInstance().format(remaining)));

        int pct = debt.getProgressPercent();
        binding.detailDebtSubtext.setText(String.format("Total Borrowed: %s%s | Paid: %s%s (%d%%)",
                currencySymbol, NumberFormat.getInstance().format(debt.getTotalAmount()),
                currencySymbol, NumberFormat.getInstance().format(debt.getAmountPaid()),
                pct));

        binding.detailDebtProgress.setProgress(pct);

        if (debt.getDeadline() > 0) {
            binding.detailDebtDeadline.setVisibility(View.VISIBLE);
            binding.detailDebtDeadline.setText("Target Payoff Date: " +
                    DateParser.format(debt.getDeadline(), deadlineFormat));
        } else {
            binding.detailDebtDeadline.setVisibility(View.GONE);
        }

        // Render Payment History
        binding.detailPaymentsContainer.removeAllViews();
        if (txs == null || txs.isEmpty()) {
            TextView emptyTv = new TextView(context);
            emptyTv.setText("No payments logged yet.");
            emptyTv.setTextColor(Color.GRAY);
            binding.detailPaymentsContainer.addView(emptyTv);
        } else {
            for (DebtTransaction tx : txs) {
                View row = LayoutInflater.from(context).inflate(R.layout.recycler_transaction, binding.detailPaymentsContainer, false);
                TextView typeTv = row.findViewById(R.id.transaction_type);
                TextView amountTv = row.findViewById(R.id.transaction_amount);
                TextView dateTv = row.findViewById(R.id.transaction_date);

                if (typeTv != null) {
                    typeTv.setText(tx.getNotes() != null && !tx.getNotes().isEmpty() ? tx.getNotes() : "Payment");
                    typeTv.setVisibility(View.VISIBLE);
                }
                if (amountTv != null) {
                    amountTv.setText(String.format("-%s%s", currencySymbol, NumberFormat.getInstance().format(tx.getAmount())));
                    amountTv.setTextColor(Color.parseColor("#1B6B50")); // green paydown
                }
                if (dateTv != null) {
                    dateTv.setText(DateParser.format(tx.getDate(), deadlineFormat));
                }

                binding.detailPaymentsContainer.addView(row);
            }
        }
    }

    private void submitPayment() {
        Context context = getContext();
        if (context == null || binding == null || currentDebt == null) return;

        String amountStr = binding.paymentAmountEdit.getText() != null ?
                binding.paymentAmountEdit.getText().toString().trim() : "";
        double amount;
        try {
            amount = Double.parseDouble(amountStr);
            if (amount <= 0) {
                binding.paymentAmountLayout.setError("Enter amount > 0");
                return;
            }
            binding.paymentAmountLayout.setError(null);
        } catch (NumberFormatException e) {
            binding.paymentAmountLayout.setError("Invalid number");
            return;
        }

        final Context appContext = context.getApplicationContext();
        final double paymentAmt = amount;

        executor.execute(() -> {
            try (DebtRepository repo = new DebtRepository(appContext)) {
                repo.makePayment(currentDebt, paymentAmt, "Repayment");
            }

            if (isAdded() && getActivity() != null) {
                requireActivity().runOnUiThread(() -> {
                    if (binding != null) {
                        binding.paymentAmountEdit.setText("");
                    }
                    Toast.makeText(appContext, "Payment recorded!", Toast.LENGTH_SHORT).show();
                    if (listener != null) listener.onDebtUpdated();
                    loadDebtData();
                });
            }
        });
    }

    private void editDebt() {
        CreateDebtBottomSheetDialogFragment editDialog = CreateDebtBottomSheetDialogFragment.newInstance(debtId);
        editDialog.setOnDebtSavedListener(() -> {
            loadDebtData();
            if (listener != null) listener.onDebtUpdated();
        });
        editDialog.show(getParentFragmentManager(), "EDIT_DEBT_DIALOG");
    }

    private void confirmDeleteDebt() {
        Context context = getContext();
        if (context == null || currentDebt == null) return;

        new MaterialAlertDialogBuilder(context)
                .setTitle("Delete Debt")
                .setMessage("Are you sure you want to delete \"" + currentDebt.getName() + "\" and its payment history?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    final Context appContext = context.getApplicationContext();
                    executor.execute(() -> {
                        try (DebtRepository repo = new DebtRepository(appContext)) {
                            repo.delete(debtId);
                        }
                        if (isAdded() && getActivity() != null) {
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(appContext, "Debt deleted", Toast.LENGTH_SHORT).show();
                                if (listener != null) listener.onDebtUpdated();
                                dismiss();
                            });
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
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
