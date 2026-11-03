package com.eipna.centsation.ui.dialogs;

import android.app.DatePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eipna.centsation.R;
import com.eipna.centsation.data.debt.Debt;
import com.eipna.centsation.data.debt.DebtCategory;
import com.eipna.centsation.data.debt.DebtRepository;
import com.eipna.centsation.databinding.DialogBottomSheetCreateDebtBinding;
import com.eipna.centsation.util.AppExecutors;
import com.eipna.centsation.util.DateParser;
import com.eipna.centsation.util.FormatUtil;
import com.eipna.centsation.util.PreferenceUtil;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;

import java.util.Calendar;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

public class CreateDebtBottomSheetDialogFragment extends BottomSheetDialogFragment {

    public interface OnDebtSavedListener {
        void onDebtSaved();
    }

    private static final String ARG_DEBT_ID = "arg_debt_id";

    private DialogBottomSheetCreateDebtBinding binding;
    private final ExecutorService executor = AppExecutors.getInstance().diskIO();

    private String debtIdToEdit = null;
    private long selectedDeadline = 0;
    private String selectedCategory = DebtCategory.GENERAL.getId();
    private int selectedDebtType = Debt.TYPE_I_OWE;
    private OnDebtSavedListener listener;

    public static CreateDebtBottomSheetDialogFragment newInstance(@Nullable String debtId) {
        CreateDebtBottomSheetDialogFragment fragment = new CreateDebtBottomSheetDialogFragment();
        Bundle args = new Bundle();
        if (debtId != null) {
            args.putString(ARG_DEBT_ID, debtId);
        }
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnDebtSavedListener(OnDebtSavedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogBottomSheetCreateDebtBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.btnCreateDebtClose.setOnClickListener(v -> dismiss());

        setupCategoryChips();

        binding.debtTypeChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chip_type_owed_to_me)) {
                selectedDebtType = Debt.TYPE_OWED_TO_ME;
                updateTypeUI(Debt.TYPE_OWED_TO_ME);
            } else {
                selectedDebtType = Debt.TYPE_I_OWE;
                updateTypeUI(Debt.TYPE_I_OWE);
            }
        });

        if (getArguments() != null) {
            debtIdToEdit = getArguments().getString(ARG_DEBT_ID);
        }

        if (debtIdToEdit != null) {
            binding.createDebtTitle.setText("Edit Debt");
            binding.btnSaveDebt.setText("Update Debt");
            loadExistingDebt(debtIdToEdit);
        } else {
            updateTypeUI(Debt.TYPE_I_OWE);
        }

        binding.btnDebtDeadline.setOnClickListener(v -> showDeadlinePicker());
        binding.btnSaveDebt.setOnClickListener(v -> saveDebt());
    }

    private void updateTypeUI(int type) {
        if (binding == null) return;
        if (type == Debt.TYPE_OWED_TO_ME) {
            if (debtIdToEdit == null) {
                binding.createDebtTitle.setText("Track Owed Amount");
                binding.createDebtSubtitle.setText("Track money lent to others and repayments received");
                binding.btnSaveDebt.setText("Save Owed");
            }
            binding.debtNameLayout.setHint("Who owes you? (Borrower Name)");
            binding.debtTotalAmountLayout.setHint("Total Amount Lent");
            binding.debtPaidAmountLayout.setHint("Already Received");
        } else {
            if (debtIdToEdit == null) {
                binding.createDebtTitle.setText("Add New Debt");
                binding.createDebtSubtitle.setText("Track liabilities, loans, and payoff velocity");
                binding.btnSaveDebt.setText("Save Debt");
            }
            binding.debtNameLayout.setHint("Debt Name (e.g. Credit Card, Student Loan)");
            binding.debtTotalAmountLayout.setHint("Total Debt Amount");
            binding.debtPaidAmountLayout.setHint("Already Paid");
        }
    }

    private void setupCategoryChips() {
        Context context = getContext();
        if (context == null || binding == null) return;

        binding.debtCategoryChipGroup.removeAllViews();
        for (DebtCategory cat : DebtCategory.getAll()) {
            Chip chip = new Chip(context);
            chip.setText(cat.getFormattedLabel());
            chip.setCheckable(true);
            chip.setTag(cat.getId());
            if (cat.getId().equals(selectedCategory)) {
                chip.setChecked(true);
            }
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedCategory = (String) buttonView.getTag();
                }
            });
            binding.debtCategoryChipGroup.addView(chip);
        }
    }

    private void loadExistingDebt(String id) {
        final Context context = requireContext().getApplicationContext();
        executor.execute(() -> {
            try (DebtRepository repo = new DebtRepository(context)) {
                Debt debt = repo.getDebt(id);
                if (debt != null && isAdded() && getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        if (binding == null) return;
                        selectedDebtType = debt.getDebtType();
                        if (selectedDebtType == Debt.TYPE_OWED_TO_ME) {
                            binding.chipTypeOwedToMe.setChecked(true);
                            binding.createDebtTitle.setText("Edit Owed Amount");
                            binding.btnSaveDebt.setText("Update Owed");
                        } else {
                            binding.chipTypeIOwe.setChecked(true);
                            binding.createDebtTitle.setText("Edit Debt");
                            binding.btnSaveDebt.setText("Update Debt");
                        }
                        updateTypeUI(selectedDebtType);

                        binding.debtNameEdit.setText(debt.getName());
                        binding.debtTotalAmountEdit.setText(FormatUtil.formatAmount(debt.getTotalAmount(), context));
                        binding.debtPaidAmountEdit.setText(FormatUtil.formatAmount(debt.getAmountPaid(), context));
                        binding.debtNotesEdit.setText(debt.getNotes() != null ? debt.getNotes() : "");

                        selectedDeadline = debt.getDeadline();
                        selectedCategory = debt.getCategory();
                        updateDeadlineButtonText();

                        // Select category chip
                        for (int i = 0; i < binding.debtCategoryChipGroup.getChildCount(); i++) {
                            View child = binding.debtCategoryChipGroup.getChildAt(i);
                            if (child instanceof Chip) {
                                Chip chip = (Chip) child;
                                chip.setChecked(selectedCategory.equals(chip.getTag()));
                            }
                        }
                    });
                }
            }
        });
    }

    private void showDeadlinePicker() {
        Context context = getContext();
        if (context == null) return;

        Calendar c = Calendar.getInstance();
        if (selectedDeadline > 0) {
            c.setTimeInMillis(selectedDeadline);
        }

        DatePickerDialog dialog = new DatePickerDialog(
                context,
                (view, year, month, dayOfMonth) -> {
                    Calendar picked = Calendar.getInstance();
                    picked.set(year, month, dayOfMonth, 23, 59, 59);
                    selectedDeadline = picked.getTimeInMillis();
                    updateDeadlineButtonText();
                },
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH),
                c.get(Calendar.DAY_OF_MONTH)
        );
        dialog.show();
    }

    private void updateDeadlineButtonText() {
        if (binding == null) return;
        if (selectedDeadline <= 0) {
            binding.btnDebtDeadline.setText("Set Target Payoff Deadline");
        } else {
            Context context = getContext();
            String pattern = context != null ? new PreferenceUtil(context).getDeadlineFormat() : "MM/dd/yyyy";
            binding.btnDebtDeadline.setText("Target Deadline: " + DateParser.getStringDate(selectedDeadline, pattern));
        }
    }

    private void saveDebt() {
        Context context = getContext();
        if (context == null || binding == null) return;

        String name = binding.debtNameEdit.getText() != null ? binding.debtNameEdit.getText().toString().trim() : "";
        if (name.isEmpty()) {
            binding.debtNameLayout.setError(selectedDebtType == Debt.TYPE_OWED_TO_ME ? "Borrower name cannot be empty" : "Debt name cannot be empty");
            return;
        } else {
            binding.debtNameLayout.setError(null);
        }

        String totalStr = binding.debtTotalAmountEdit.getText() != null ? binding.debtTotalAmountEdit.getText().toString().trim() : "";
        double totalAmount = 0.0;
        try {
            totalAmount = FormatUtil.parseDouble(totalStr);
            if (totalAmount <= 0) {
                binding.debtTotalAmountLayout.setError("Enter an amount greater than 0");
                return;
            }
            binding.debtTotalAmountLayout.setError(null);
        } catch (NumberFormatException e) {
            binding.debtTotalAmountLayout.setError("Invalid number");
            return;
        }

        String paidStr = binding.debtPaidAmountEdit.getText() != null ? binding.debtPaidAmountEdit.getText().toString().trim() : "0";
        double paidAmount = 0.0;
        try {
            paidAmount = FormatUtil.parseDouble(paidStr);
            if (paidAmount < 0) paidAmount = 0.0;
        } catch (NumberFormatException ignored) {}

        String notes = binding.debtNotesEdit.getText() != null ? binding.debtNotesEdit.getText().toString().trim() : "";

        final Context appContext = context.getApplicationContext();
        final double finalTotal = totalAmount;
        final double finalPaid = paidAmount;
        final int finalType = selectedDebtType;

        executor.execute(() -> {
            try (DebtRepository repo = new DebtRepository(appContext)) {
                if (debtIdToEdit != null) {
                    Debt existing = repo.getDebt(debtIdToEdit);
                    if (existing != null) {
                        existing.setName(name);
                        existing.setTotalAmount(finalTotal);
                        existing.setAmountPaid(finalPaid);
                        existing.setCategory(selectedCategory);
                        existing.setDeadline(selectedDeadline);
                        existing.setNotes(notes);
                        existing.setDebtType(finalType);
                        repo.edit(existing);
                    }
                } else {
                    Debt newDebt = new Debt();
                    newDebt.setId(UUID.randomUUID().toString());
                    newDebt.setName(name);
                    newDebt.setTotalAmount(finalTotal);
                    newDebt.setAmountPaid(finalPaid);
                    newDebt.setCategory(selectedCategory);
                    newDebt.setDeadline(selectedDeadline);
                    newDebt.setNotes(notes);
                    newDebt.setCreatedAt(System.currentTimeMillis());
                    newDebt.setDebtType(finalType);
                    repo.create(newDebt);
                }
            }

            if (isAdded() && getActivity() != null) {
                requireActivity().runOnUiThread(() -> {
                    String msg = finalType == Debt.TYPE_OWED_TO_ME
                            ? (debtIdToEdit != null ? "Owed entry updated" : "Owed entry saved")
                            : (debtIdToEdit != null ? "Debt updated" : "Debt saved");
                    Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).show();
                    if (listener != null) listener.onDebtSaved();
                    dismiss();
                });
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
