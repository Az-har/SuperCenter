package com.eipna.centsation.ui.dialogs;

import android.content.Context;
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
import com.eipna.centsation.util.AppExecutors;
import com.eipna.centsation.util.BackupManager;
import com.eipna.centsation.util.DateParser;
import com.eipna.centsation.util.FormatUtil;
import com.eipna.centsation.util.PreferenceUtil;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.concurrent.ExecutorService;

public class DebtDetailsBottomSheetDialogFragment extends BottomSheetDialogFragment {

    public interface OnDebtUpdatedListener {
        void onDebtUpdated();
    }

    private static final String ARG_DEBT_ID = "arg_debt_id";
    private String debtId;
    private Debt currentDebt;
    private DialogBottomSheetDebtDetailsBinding binding;
    private OnDebtUpdatedListener listener;
    private final ExecutorService executor = AppExecutors.getInstance().diskIO();

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

        if (getArguments() != null) {
            debtId = getArguments().getString(ARG_DEBT_ID);
        }

        if (debtId == null) {
            dismiss();
            return;
        }

        binding.btnDebtDetailClose.setOnClickListener(v -> dismiss());
        binding.btnSubmitPayment.setOnClickListener(v -> submitPayment());
        binding.btnEditDebt.setOnClickListener(v -> openEditDialog());
        binding.btnArchiveDebt.setOnClickListener(v -> toggleArchive());
        binding.btnDeleteDebt.setOnClickListener(v -> confirmDelete());

        loadDebtDetails();
    }

    private void loadDebtDetails() {
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

        boolean isOwed = debt.isOwedToMe();
        int primaryColor = MaterialColors.getColor(binding.getRoot(), androidx.appcompat.R.attr.colorPrimary);
        int errorColor = MaterialColors.getColor(binding.getRoot(), androidx.appcompat.R.attr.colorError);
        int onPrimaryContainer = MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorOnPrimaryContainer);
        int onErrorContainer = MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorOnErrorContainer);
        int onSurfaceVariant = MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorOnSurfaceVariant);

        boolean isArchived = debt.getIsArchived() == Debt.IS_ARCHIVE;
        binding.btnArchiveDebt.setIconResource(isArchived ? R.drawable.ic_unarchive : R.drawable.ic_archive);
        binding.btnArchiveDebt.setContentDescription(getString(isArchived ? R.string.action_unarchive_debt : R.string.action_archive_debt));

        if (isOwed) {
            binding.detailDebtTypeBadge.setText("📥 Owed to Me");
            binding.detailDebtTypeBadge.setBackgroundResource(R.drawable.bg_pill_accent);
            binding.detailDebtTypeBadge.setTextColor(onPrimaryContainer);
            binding.detailDebtRemainingLabel.setText("PENDING TO COLLECT");
            binding.detailDebtRemainingLabel.setTextColor(primaryColor);
            binding.btnSubmitPayment.setText(R.string.action_record_received);
            binding.paymentAmountLayout.setHint("Payment Received (" + currencySymbol + ")");
        } else {
            binding.detailDebtTypeBadge.setText("💸 I Owe");
            binding.detailDebtTypeBadge.setBackgroundResource(R.drawable.bg_pill_error);
            binding.detailDebtTypeBadge.setTextColor(onErrorContainer);
            binding.detailDebtRemainingLabel.setText("REMAINING DEBT");
            binding.detailDebtRemainingLabel.setTextColor(errorColor);
            binding.btnSubmitPayment.setText(R.string.action_record_payment);
            binding.paymentAmountLayout.setHint("Repayment Amount (" + currencySymbol + ")");
        }

        DebtCategory category = DebtCategory.fromId(debt.getCategory());
        binding.detailDebtCategoryBadge.setText(category.getFormattedLabel());
        binding.detailDebtCategoryBadge.setBackgroundResource(R.drawable.bg_pill_neutral);
        binding.detailDebtCategoryBadge.setTextColor(onSurfaceVariant);

        double remaining = debt.getRemainingDebt();
        binding.detailDebtRemaining.setText(String.format("%s%s", currencySymbol,
                FormatUtil.formatAmount(remaining, context)));

        int pct = debt.getProgressPercent();
        if (isOwed) {
            binding.detailDebtSubtext.setText(String.format("Total Lent: %s%s | Collected: %s%s (%d%%)",
                currencySymbol, FormatUtil.formatAmount(debt.getTotalAmount(), context),
                currencySymbol, FormatUtil.formatAmount(debt.getAmountPaid(), context),
                pct));
        } else {
            binding.detailDebtSubtext.setText(String.format("Total Borrowed: %s%s | Paid: %s%s (%d%%)",
                currencySymbol, FormatUtil.formatAmount(debt.getTotalAmount(), context),
                currencySymbol, FormatUtil.formatAmount(debt.getAmountPaid(), context),
                pct));
        }

        binding.detailDebtProgress.setProgress(pct);

        if (debt.getDeadline() > 0) {
            binding.detailDebtDeadline.setVisibility(View.VISIBLE);
            binding.detailDebtDeadline.setText((isOwed ? "Target Return Date: " : "Target Payoff Date: ") +
                    DateParser.format(debt.getDeadline(), deadlineFormat));
        } else {
            binding.detailDebtDeadline.setVisibility(View.GONE);
        }

        // Render Payment History
        binding.detailPaymentsContainer.removeAllViews();
        if (txs == null || txs.isEmpty()) {
            TextView emptyTv = new TextView(context);
            emptyTv.setText(isOwed ? R.string.analytics_no_received_payments : R.string.analytics_no_repayments_logged);
            emptyTv.setTextColor(onSurfaceVariant);
            binding.detailPaymentsContainer.addView(emptyTv);
        } else {
            for (DebtTransaction tx : txs) {
                View row = LayoutInflater.from(context).inflate(R.layout.recycler_transaction, binding.detailPaymentsContainer, false);
                TextView typeTv = row.findViewById(R.id.transaction_type);
                TextView amountTv = row.findViewById(R.id.transaction_amount);
                TextView dateTv = row.findViewById(R.id.transaction_date);

                if (typeTv != null) {
                    typeTv.setText(tx.getNotes() != null && !tx.getNotes().isEmpty() ? tx.getNotes() : (isOwed ? "Received" : "Repayment"));
                    typeTv.setVisibility(View.VISIBLE);
                }
                if (amountTv != null) {
                    amountTv.setText(String.format(isOwed ? "+%s%s" : "-%s%s", currencySymbol, FormatUtil.formatAmount(tx.getAmount(), context)));
                    amountTv.setTextColor(isOwed ? primaryColor : errorColor);
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
            amount = FormatUtil.parseDouble(amountStr);
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
        final boolean isOwed = currentDebt.isOwedToMe();

        executor.execute(() -> {
            try (DebtRepository repo = new DebtRepository(appContext)) {
                repo.makePayment(currentDebt, paymentAmt, isOwed ? "Payment received" : "Repayment");
            }
            BackupManager.triggerAutoBackup(appContext);

            if (isAdded() && getActivity() != null) {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(appContext, isOwed ? "Payment received recorded" : "Payment recorded", Toast.LENGTH_SHORT).show();
                    binding.paymentAmountEdit.setText("");
                    if (listener != null) listener.onDebtUpdated();
                    loadDebtDetails();
                });
            }
        });
    }

    private void openEditDialog() {
        if (currentDebt == null) return;
        CreateDebtBottomSheetDialogFragment dialog = CreateDebtBottomSheetDialogFragment.newInstance(currentDebt.getId());
        dialog.setOnDebtSavedListener(() -> {
            if (listener != null) listener.onDebtUpdated();
            loadDebtDetails();
        });
        dialog.show(getParentFragmentManager(), "EDIT_DEBT_DIALOG");
    }

    private void toggleArchive() {
        Context context = getContext();
        if (context == null || currentDebt == null) return;

        final boolean archiveTarget = currentDebt.getIsArchived() != Debt.IS_ARCHIVE;
        final Context appContext = context.getApplicationContext();
        executor.execute(() -> {
            try (DebtRepository repo = new DebtRepository(appContext)) {
                repo.setArchived(currentDebt.getId(), archiveTarget);
            }
            BackupManager.triggerAutoBackup(appContext);

            if (isAdded() && getActivity() != null) {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(
                            appContext,
                            archiveTarget
                                    ? getString(R.string.snackbar_archived, currentDebt.getName())
                                    : getString(R.string.snackbar_unarchived, currentDebt.getName()),
                            Toast.LENGTH_SHORT
                    ).show();
                    if (listener != null) listener.onDebtUpdated();
                    dismiss();
                });
            }
        });
    }

    private void confirmDelete() {
        Context context = getContext();
        if (context == null || currentDebt == null) return;

        boolean isOwed = currentDebt.isOwedToMe();
        new MaterialAlertDialogBuilder(context)
                .setTitle(isOwed ? "Delete Owed Record" : "Delete Debt")
                .setMessage(isOwed ? "Are you sure you want to delete this owed record and all associated logs?" : "Are you sure you want to delete this debt and its entire payment history?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    final Context appContext = context.getApplicationContext();
                    executor.execute(() -> {
                        try (DebtRepository repo = new DebtRepository(appContext)) {
                            repo.delete(currentDebt.getId());
                        }
                        BackupManager.triggerAutoBackup(appContext);
                        if (isAdded() && getActivity() != null) {
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(appContext, isOwed ? "Owed entry deleted" : "Debt deleted", Toast.LENGTH_SHORT).show();
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
}
