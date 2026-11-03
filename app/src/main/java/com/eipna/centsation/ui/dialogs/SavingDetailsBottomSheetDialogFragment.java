package com.eipna.centsation.ui.dialogs;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.icu.text.NumberFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import android.widget.FrameLayout;
import android.widget.Toast;

import com.eipna.centsation.R;
import com.eipna.centsation.data.Currency;
import com.eipna.centsation.data.saving.Saving;
import com.eipna.centsation.data.saving.SavingCategory;
import com.eipna.centsation.data.saving.SavingRepository;
import com.eipna.centsation.databinding.DialogBottomSheetSavingDetailsBinding;
import com.eipna.centsation.util.AlarmSetter;
import com.eipna.centsation.util.AnimationUtil;
import com.eipna.centsation.util.BackupManager;
import com.eipna.centsation.util.DateParser;
import com.eipna.centsation.util.NotificationHandler;
import com.eipna.centsation.util.PreferenceUtil;
import com.eipna.centsation.util.SavingsCalculator;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SavingDetailsBottomSheetDialogFragment extends BottomSheetDialogFragment {

    public interface OnDetailsActionListener {
        void onTransact(Saving saving);
        void onHistory(Saving saving);
        void onEdit(Saving saving);
        void onShare(Saving saving);
        void onArchive(Saving saving);
        void onDelete(Saving saving);
        default void onSavingUpdated(Saving saving) {}
    }

    private DialogBottomSheetSavingDetailsBinding binding;
    private Saving saving;
    private OnDetailsActionListener listener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static SavingDetailsBottomSheetDialogFragment newInstance(String savingID) {
        SavingDetailsBottomSheetDialogFragment fragment = new SavingDetailsBottomSheetDialogFragment();
        Bundle args = new Bundle();
        args.putString("saving_id", savingID);
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnDetailsActionListener(OnDetailsActionListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogBottomSheetSavingDetailsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String savingID = getArguments() != null ? getArguments().getString("saving_id") : null;
        if (savingID == null) {
            dismiss();
            return;
        }

        final Context appContext = requireContext().getApplicationContext();
        executor.execute(() -> {
            Saving loadedSaving;
            try (SavingRepository repo = new SavingRepository(appContext)) {
                loadedSaving = repo.getSaving(savingID);
            }
            mainHandler.post(() -> {
                if (!isAdded() || binding == null) return;
                if (loadedSaving == null) {
                    dismiss();
                    return;
                }
                saving = loadedSaving;
                bindSavingDetails();
                setupActionListeners();
            });
        });
    }

    private void bindSavingDetails() {
        Context context = requireContext();
        PreferenceUtil preferenceUtil = new PreferenceUtil(context);
        String currencySymbol = Currency.getSymbol(preferenceUtil.getCurrency());
        String deadlineFormat = preferenceUtil.getDeadlineFormat();

        binding.detailsName.setText(saving.getName());

        int percent = SavingsCalculator.getPercent(saving);
        boolean isCompleted = SavingsCalculator.isCompleted(saving);
        double remaining = SavingsCalculator.getRemainingAmount(saving);

        // Category Chip
        SavingCategory category = SavingCategory.fromId(saving.getCategory());
        binding.detailsCategoryChip.setText(category.getFormattedLabel());

        // Status Chip
        if (saving.getIsArchived() == Saving.IS_ARCHIVE) {
            binding.detailsStatusChip.setText("Archived");
            binding.detailsBtnArchive.setText("Unarchive");
            binding.detailsBtnArchive.setIconResource(R.drawable.ic_unarchive);
            binding.detailsBtnTransact.setEnabled(false);
        } else if (isCompleted) {
            binding.detailsStatusChip.setText("Completed 🎉");
            binding.detailsStatusChip.setChipIconResource(R.drawable.ic_check_circle);
        } else {
            long days = SavingsCalculator.getDaysRemaining(saving.getDeadline());
            if (saving.getDeadline() != AlarmSetter.NO_ALARM && days < 0) {
                binding.detailsStatusChip.setText("Overdue ⚠️");
            } else {
                binding.detailsStatusChip.setText("In Progress");
            }
        }

        // Amount & Progress
        binding.detailsCurrentAmount.setText(String.format("%s%s", currencySymbol, NumberFormat.getInstance().format(saving.getCurrentSaving())));
        binding.detailsGoalAmount.setText(String.format(" of %s%s", currencySymbol, NumberFormat.getInstance().format(saving.getGoal())));
        binding.detailsPercent.setText(String.format("%d%%", percent));

        AnimationUtil.animateProgress(binding.detailsProgress, Math.min(100, percent), 500);

        if (isCompleted) {
            binding.detailsRemainingSubtext.setText("Goal achieved! Outstanding work! 🎉");
        } else {
            binding.detailsRemainingSubtext.setText(String.format("%s%s left to reach target goal", currencySymbol, NumberFormat.getInstance().format(remaining)));
        }

        // Smart Insight Card (Deadline & Recommended Daily Pace)
        if (saving.getDeadline() != AlarmSetter.NO_ALARM) {
            binding.detailsInsightCard.setVisibility(View.VISIBLE);
            long days = SavingsCalculator.getDaysRemaining(saving.getDeadline());
            String dateStr = DateParser.getStringDate(saving.getDeadline(), deadlineFormat);

            if (saving.getIsArchived() == Saving.IS_ARCHIVE) {
                binding.detailsDeadlineText.setText(String.format("📅 Target was %s • Goal is archived", dateStr));
                binding.detailsPaceText.setVisibility(View.GONE);
            } else if (isCompleted) {
                binding.detailsDeadlineText.setText(String.format("📅 Target was %s • Goal already reached!", dateStr));
                binding.detailsPaceText.setVisibility(View.GONE);
            } else if (days < 0) {
                binding.detailsDeadlineText.setText(String.format("⚠️ Deadline was %s (Overdue by %d days)", dateStr, Math.abs(days)));
                binding.detailsPaceText.setText("Goal deadline has passed. Continue at your own pace!");
            } else if (days == 0) {
                binding.detailsDeadlineText.setText(String.format("📅 Deadline: %s (Due today!)", dateStr));
                binding.detailsPaceText.setText(String.format("💡 %s%s remaining to complete goal today", currencySymbol, NumberFormat.getInstance().format(remaining)));
            } else {
                double dailyRate = SavingsCalculator.getDailySavingsRate(saving);
                binding.detailsDeadlineText.setText(String.format("📅 Deadline: %s (%d days left)", dateStr, days));
                binding.detailsPaceText.setText(String.format("💡 Recommended pace: Save %s%s / day to finish on time",
                        currencySymbol, NumberFormat.getInstance().format(Math.ceil(dailyRate * 100.0) / 100.0)));
            }
        } else {
            binding.detailsInsightCard.setVisibility(View.GONE);
        }

        // Notes Section
        String notes = saving.getNotes();
        if (notes != null && !notes.trim().isEmpty()) {
            binding.detailsNotesCard.setVisibility(View.VISIBLE);
            binding.detailsBtnAddNote.setVisibility(View.GONE);
            binding.detailsNotesText.setText(notes.trim());
        } else {
            binding.detailsNotesCard.setVisibility(View.GONE);
            binding.detailsBtnAddNote.setVisibility(View.VISIBLE);
        }
    }

    private void setupActionListeners() {
        AnimationUtil.applySpringPressEffect(binding.detailsBtnTransact);
        AnimationUtil.applySpringPressEffect(binding.detailsBtnHistory);
        AnimationUtil.applySpringPressEffect(binding.detailsBtnEdit);
        AnimationUtil.applySpringPressEffect(binding.detailsBtnShare);
        AnimationUtil.applySpringPressEffect(binding.detailsBtnArchive);
        AnimationUtil.applySpringPressEffect(binding.detailsBtnCopyNote);
        AnimationUtil.applySpringPressEffect(binding.detailsBtnEditNote);
        AnimationUtil.applySpringPressEffect(binding.detailsBtnAddNote);

        binding.detailsBtnCopyNote.setOnClickListener(v -> copyNotesToClipboard());
        binding.detailsBtnEditNote.setOnClickListener(v -> showQuickEditNotesDialog());
        binding.detailsBtnAddNote.setOnClickListener(v -> showQuickEditNotesDialog());

        binding.detailsBtnTransact.setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onTransact(saving);
        });

        binding.detailsBtnHistory.setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onHistory(saving);
        });

        binding.detailsBtnEdit.setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onEdit(saving);
        });

        binding.detailsBtnShare.setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onShare(saving);
        });


        binding.detailsBtnArchive.setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onArchive(saving);
        });

    }

    private void copyNotesToClipboard() {
        if (saving == null || saving.getNotes() == null || saving.getNotes().trim().isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager)
                requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("Goal Note", saving.getNotes().trim());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(requireContext(), "Note copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void showQuickEditNotesDialog() {
        if (saving == null || !isAdded()) return;

        Context context = requireContext();
        final TextInputEditText editText =
                new TextInputEditText(context);
        editText.setText(saving.getNotes() != null ? saving.getNotes() : "");
        editText.setHint("Notes, milestones, or reminders");
        editText.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editText.setMinLines(3);
        editText.setMaxLines(7);
        editText.setGravity(Gravity.TOP | Gravity.START);

        TextInputLayout inputLayout =
                new TextInputLayout(context, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        float cornerRadius = 16 * getResources().getDisplayMetrics().density;
        inputLayout.setBoxCornerRadii(cornerRadius, cornerRadius, cornerRadius, cornerRadius);
        inputLayout.setCounterEnabled(true);
        inputLayout.setCounterMaxLength(500);
        inputLayout.addView(editText);

        FrameLayout container = new FrameLayout(context);
        int paddingHorizontal = (int) (24 * getResources().getDisplayMetrics().density);
        int paddingTop = (int) (12 * getResources().getDisplayMetrics().density);
        container.setPadding(paddingHorizontal, paddingTop, paddingHorizontal, 0);
        container.addView(inputLayout);

        new MaterialAlertDialogBuilder(context)
                .setTitle("Goal Note")
                .setView(container)
                .setPositiveButton("Save Note", (dialog, which) -> {
                    String updatedNotes = editText.getText() != null ? editText.getText().toString().trim() : "";
                    saving.setNotes(updatedNotes);
                    final Context appContext = context.getApplicationContext();
                    executor.execute(() -> {
                        try (SavingRepository repo = new SavingRepository(appContext)) {
                            repo.edit(saving);
                        }
                        BackupManager.triggerAutoBackup(appContext);
                        mainHandler.post(() -> {
                            if (!isAdded() || binding == null) return;
                            bindSavingDetails();
                            Toast.makeText(appContext, "Note updated", Toast.LENGTH_SHORT).show();
                            if (listener != null) listener.onSavingUpdated(saving);
                        });
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        executor.shutdown();
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
