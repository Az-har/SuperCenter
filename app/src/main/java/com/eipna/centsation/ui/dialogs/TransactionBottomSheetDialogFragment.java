package com.eipna.centsation.ui.dialogs;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eipna.centsation.R;
import com.eipna.centsation.data.Currency;
import com.eipna.centsation.data.saving.Saving;
import com.eipna.centsation.databinding.DialogBottomSheetTransactionBinding;
import com.eipna.centsation.util.AnimationUtil;
import com.eipna.centsation.util.FormatUtil;
import com.eipna.centsation.util.PreferenceUtil;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Objects;

public class TransactionBottomSheetDialogFragment extends BottomSheetDialogFragment {

    public interface OnTransactionListener {
        void onDeposit(Saving saving, double amount);
        boolean onWithdraw(Saving saving, double amount);
    }

    private DialogBottomSheetTransactionBinding binding;
    private Saving saving;
    private OnTransactionListener listener;

    public static TransactionBottomSheetDialogFragment newInstance(Saving saving) {
        TransactionBottomSheetDialogFragment fragment = new TransactionBottomSheetDialogFragment();
        Bundle args = new Bundle();
        args.putString("saving_id", saving.getID());
        args.putString("saving_name", saving.getName());
        args.putDouble("saving_current", saving.getCurrentSaving());
        args.putDouble("saving_goal", saving.getGoal());
        fragment.setArguments(args);
        fragment.saving = saving;
        return fragment;
    }

    public void setOnTransactionListener(OnTransactionListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogBottomSheetTransactionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (saving == null && getArguments() != null) {
            saving = new Saving();
            saving.setID(getArguments().getString("saving_id"));
            saving.setName(getArguments().getString("saving_name"));
            saving.setCurrentSaving(getArguments().getDouble("saving_current"));
            saving.setGoal(getArguments().getDouble("saving_goal"));
        }

        if (saving == null) {
            dismiss();
            return;
        }

        PreferenceUtil preferenceUtil = new PreferenceUtil(requireContext());
        String currencySymbol = Currency.getSymbol(preferenceUtil.getCurrency());

        binding.transactionTitle.setText(R.string.dialog_title_create_transaction);
        binding.transactionSubtitle.setText(String.format("%s • %s%s / %s%s",
                saving.getName(),
                currencySymbol,
                FormatUtil.formatAmount(saving.getCurrentSaving(), requireContext()),
                currencySymbol,
                FormatUtil.formatAmount(saving.getGoal(), requireContext())));

        binding.fieldAmountLayout.setPrefixText(currencySymbol);
        binding.toggleType.check(R.id.btn_type_deposit);

        // Tap on non-input areas to dismiss software keyboard
        binding.getRoot().setOnClickListener(v -> hideKeyboard());
        binding.dragHandle.setOnClickListener(v -> hideKeyboard());
        binding.transactionTitle.setOnClickListener(v -> hideKeyboard());
        binding.transactionSubtitle.setOnClickListener(v -> hideKeyboard());
        binding.toggleType.addOnButtonCheckedListener((group, checkedId, isChecked) -> hideKeyboard());

        // Keyboard 'Done' action submits or dismisses keyboard cleanly
        binding.fieldAmountText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard();
                binding.btnSubmit.performClick();
                return true;
            }
            return false;
        });

        setupQuickChips();
        AnimationUtil.applySpringPressEffect(binding.btnSubmit);
        AnimationUtil.applySpringPressEffect(binding.btnCancel);

        binding.btnCancel.setOnClickListener(v -> {
            hideKeyboard();
            dismiss();
        });

        binding.btnSubmit.setOnClickListener(v -> {
            String text = Objects.requireNonNull(binding.fieldAmountText.getText()).toString().trim().replace(',', '.');
            if (text.isEmpty()) {
                binding.fieldAmountLayout.setError(getString(R.string.field_error_required));
                return;
            }

            double amount;
            try {
                amount = FormatUtil.parseDouble(text);
                if (amount <= 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
                    binding.fieldAmountLayout.setError(getString(R.string.field_error_empty_saving));
                    return;
                }
            } catch (NumberFormatException e) {
                binding.fieldAmountLayout.setError(getString(R.string.field_error_invalid_number));
                return;
            }

            binding.fieldAmountLayout.setError(null);
            hideKeyboard();

            int checkedId = binding.toggleType.getCheckedButtonId();
            if (checkedId == R.id.btn_type_withdraw) {
                if (listener != null) {
                    boolean success = listener.onWithdraw(saving, amount);
                    if (!success) {
                        binding.fieldAmountLayout.setError(getString(R.string.field_error_negative_saving));
                        return;
                    }
                }
            } else {
                if (listener != null) {
                    listener.onDeposit(saving, amount);
                }
            }

            dismiss();
        });

        binding.fieldAmountText.post(() -> {
            if (!isAdded() || getContext() == null || binding == null) return;
            binding.fieldAmountText.requestFocus();
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(binding.fieldAmountText, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private void setupQuickChips() {
        int[] chipIds = {R.id.chip_10, R.id.chip_25, R.id.chip_50, R.id.chip_100, R.id.chip_500};
        double[] chipValues = {10.0, 25.0, 50.0, 100.0, 500.0};

        for (int i = 0; i < chipIds.length; i++) {
            final double addVal = chipValues[i];
            View chip = binding.getRoot().findViewById(chipIds[i]);
            if (chip != null) {
                AnimationUtil.applySpringPressEffect(chip);
                chip.setOnClickListener(v -> {
                    hideKeyboard();
                    addAmount(addVal);
                });
            }
        }
    }

    private void addAmount(double addVal) {
        String current = Objects.requireNonNull(binding.fieldAmountText.getText()).toString().trim().replace(',', '.');
        double currentVal = 0.0;
        if (!current.isEmpty()) {
            try {
                currentVal = FormatUtil.parseDouble(current);
            } catch (NumberFormatException ignored) {}
        }
        double newVal = currentVal + addVal;
        binding.fieldAmountText.setText(String.format(java.util.Locale.US, "%.2f", newVal).replaceAll("\\.00$", ""));
        binding.fieldAmountText.setSelection(binding.fieldAmountText.getText().length());
    }

    public void hideKeyboard() {
        if (binding != null && binding.fieldAmountText != null) {
            binding.fieldAmountText.clearFocus();
        }
        Context ctx = getContext();
        if (ctx != null) {
            InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                View root = getView();
                if (root != null) {
                    imm.hideSoftInputFromWindow(root.getWindowToken(), 0);
                }
                if (binding != null && binding.fieldAmountText != null) {
                    imm.hideSoftInputFromWindow(binding.fieldAmountText.getWindowToken(), 0);
                }
            }
        }
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        hideKeyboard();
        super.onDismiss(dialog);
    }

    @Override
    public void onDestroyView() {
        hideKeyboard();
        super.onDestroyView();
        binding = null;
    }
}
