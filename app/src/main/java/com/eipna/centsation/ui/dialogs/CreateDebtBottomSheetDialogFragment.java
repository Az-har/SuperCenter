package com.eipna.centsation.ui.dialogs;

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
import com.eipna.centsation.util.DateParser;
import com.eipna.centsation.util.PreferenceUtil;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CreateDebtBottomSheetDialogFragment extends BottomSheetDialogFragment {

    private static final String ARG_DEBT_ID = "arg_debt_id";

    public interface OnDebtSavedListener {
        void onDebtSaved();
    }

    private DialogBottomSheetCreateDebtBinding binding;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private String debtIdToEdit = null;
    private long selectedDeadline = 0;
    private String selectedCategory = DebtCategory.GENERAL.getId();
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

        if (getArguments() != null) {
            debtIdToEdit = getArguments().getString(ARG_DEBT_ID);
        }

        if (debtIdToEdit != null) {
            binding.createDebtTitle.setText("Edit Debt");
            binding.btnSaveDebt.setText("Update Debt");
            loadExistingDebt(debtIdToEdit);
        }

        binding.btnDebtDeadline.setOnClickListener(v -> showDeadlinePicker());
        binding.btnSaveDebt.setOnClickListener(v -> saveDebt());
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
                        binding.debtNameEdit.setText(debt.getName());
                        binding.debtTotalAmountEdit.setText(String.valueOf(debt.getTotalAmount()));
                        binding.debtPaidAmountEdit.setText(String.valueOf(debt.getAmountPaid()));
                        binding.debtNotesEdit.setText(debt.getNotes() != null ? debt.getNotes() : "");

                        selectedDeadline = debt.getDeadline();
                        selectedCategory = debt.getCategory();
                        updateDeadlineButtonText();

                        // Select chip
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
        CalendarConstraints.Builder constraintsBuilder = new CalendarConstraints.Builder()
                .setValidator(DateValidatorPointForward.now());

        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Target Payoff Date")
                .setCalendarConstraints(constraintsBuilder.build())
                .setSelection(selectedDeadline > 0 ? selectedDeadline : MaterialDatePicker.todayInUtcMilliseconds())
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            selectedDeadline = selection != null ? selection : 0;
            updateDeadlineButtonText();
        });

        picker.show(getParentFragmentManager(), "DEBT_DEADLINE_PICKER");
    }

    private void updateDeadlineButtonText() {
        Context context = getContext();
        if (context == null || binding == null) return;
        if (selectedDeadline > 0) {
            PreferenceUtil prefs = new PreferenceUtil(context);
            String formatted = DateParser.format(selectedDeadline, prefs.getDeadlineFormat());
            binding.btnDebtDeadline.setText("Target Payoff: " + formatted);
        } else {
            binding.btnDebtDeadline.setText("Set Target Payoff Date (Optional)");
        }
    }

    private void saveDebt() {
        Context context = getContext();
        if (context == null || binding == null) return;

        String name = binding.debtNameEdit.getText() != null ? binding.debtNameEdit.getText().toString().trim() : "";
        if (name.isEmpty()) {
            binding.debtNameLayout.setError("Debt name cannot be empty");
            return;
        } else {
            binding.debtNameLayout.setError(null);
        }

        String totalStr = binding.debtTotalAmountEdit.getText() != null ? binding.debtTotalAmountEdit.getText().toString().trim() : "";
        double totalAmount = 0.0;
        try {
            totalAmount = Double.parseDouble(totalStr);
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
            paidAmount = Double.parseDouble(paidStr);
            if (paidAmount < 0) paidAmount = 0.0;
        } catch (NumberFormatException ignored) {}

        String notes = binding.debtNotesEdit.getText() != null ? binding.debtNotesEdit.getText().toString().trim() : "";

        final Context appContext = context.getApplicationContext();
        final double finalTotal = totalAmount;
        final double finalPaid = paidAmount;

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
                    repo.create(newDebt);
                }
            }

            if (isAdded() && getActivity() != null) {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(appContext, debtIdToEdit != null ? "Debt updated" : "Debt saved", Toast.LENGTH_SHORT).show();
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
