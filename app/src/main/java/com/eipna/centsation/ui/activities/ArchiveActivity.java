package com.eipna.centsation.ui.activities;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.eipna.centsation.R;
import com.eipna.centsation.data.Currency;
import com.eipna.centsation.data.Database;
import com.eipna.centsation.data.saving.Saving;
import com.eipna.centsation.data.saving.SavingOperation;
import com.eipna.centsation.databinding.ActivityArchiveBinding;
import com.eipna.centsation.ui.adapters.SavingAdapter;
import com.eipna.centsation.ui.dialogs.SavingDetailsBottomSheetDialogFragment;
import com.eipna.centsation.ui.viewmodel.ArchiveViewModel;
import com.eipna.centsation.util.AlarmSetter;
import com.eipna.centsation.util.AnimationUtil;
import com.eipna.centsation.util.DateParser;
import com.eipna.centsation.util.SavingsCalculator;
import com.eipna.centsation.util.SwipeActionCallback;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

public class ArchiveActivity extends CentsationActivity implements SavingAdapter.Listener {

    private ActivityArchiveBinding binding;
    private ArchiveViewModel viewModel;
    private SavingAdapter savingAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityArchiveBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });

        Drawable appBarDrawable = MaterialShapeDrawable.createWithElevationOverlay(this);
        binding.appBar.setStatusBarForeground(appBarDrawable);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        viewModel = new ViewModelProvider(this).get(ArchiveViewModel.class);

        savingAdapter = new SavingAdapter(this, this);
        binding.savingList.setLayoutManager(new LinearLayoutManager(this));
        binding.savingList.setAdapter(savingAdapter);

        // Fluid swipe gestures (Right = Restore, Left = Delete Permanently)
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new SwipeActionCallback(
                this,
                R.drawable.ic_unarchive,
                R.drawable.ic_delete_forever,
                com.google.android.material.R.attr.colorPrimaryContainer,
                com.google.android.material.R.attr.colorErrorContainer,
                com.google.android.material.R.attr.colorOnPrimaryContainer,
                com.google.android.material.R.attr.colorOnErrorContainer,
                new SwipeActionCallback.SwipeListener() {
                    @Override
                    public void onSwipeRight(int position) {
                        Saving saving = savingAdapter.getSavingAt(position);
                        unarchiveWithUndo(saving);
                    }

                    @Override
                    public void onSwipeLeft(int position) {
                        Saving saving = savingAdapter.getSavingAt(position);
                        savingAdapter.notifyItemChanged(position);
                        showDeleteDialog(saving);
                    }
                }
        ));
        itemTouchHelper.attachToRecyclerView(binding.savingList);

        viewModel.getSavings().observe(this, this::onSavingsChanged);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.loadSavings();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    private void onSavingsChanged(List<Saving> updated) {
        savingAdapter.submitList(updated);

        boolean empty = updated.isEmpty();
        if (empty) {
            AnimationUtil.crossfade(binding.savingList, binding.emptyIndicator, 200);
        } else {
            AnimationUtil.crossfade(binding.emptyIndicator, binding.savingList, 200);
        }
    }

    @Override
    public void onClick(int position) {
        Saving selectedSaving = savingAdapter.getSavingAt(position);
        showSavingDetails(selectedSaving.getID());
    }

    private void showSavingDetails(String savingId) {
        SavingDetailsBottomSheetDialogFragment sheet = SavingDetailsBottomSheetDialogFragment.newInstance(savingId);
        sheet.setOnDetailsActionListener(new SavingDetailsBottomSheetDialogFragment.OnDetailsActionListener() {
            @Override
            public void onTransact(Saving s) {}

            @Override
            public void onHistory(Saving s) {
                showHistoryActivity(s);
            }

            @Override
            public void onEdit(Saving s) {
                Intent editIntent = new Intent(ArchiveActivity.this, EditActivity.class);
                editIntent.putExtra(Database.COLUMN_SAVING_ID, s.getID());
                startActivity(editIntent);
            }

            @Override
            public void onShare(Saving s) {
                shareSaving(s);
            }

            @Override
            public void onArchive(Saving s) {
                unarchiveWithUndo(s);
            }

            @Override
            public void onDelete(Saving s) {
                showDeleteDialog(s);
            }

            @Override
            public void onSavingUpdated(Saving s) {
                viewModel.loadSavings();
            }
        });
        sheet.show(getSupportFragmentManager(), "archived_saving_details_sheet");
    }

    @Override
    public void onOperationClick(SavingOperation operation, int position) {
        Saving selectedSaving = savingAdapter.getSavingAt(position);
        if (operation.equals(SavingOperation.UNARCHIVE)) unarchiveWithUndo(selectedSaving);
        if (operation.equals(SavingOperation.SHARE)) shareSaving(selectedSaving);
        if (operation.equals(SavingOperation.DELETE)) showDeleteDialog(selectedSaving);
        if (operation.equals(SavingOperation.HISTORY)) showHistoryActivity(selectedSaving);
    }

    private void unarchiveWithUndo(Saving saving) {
        viewModel.unarchiveSaving(saving);
        Snackbar.make(binding.getRoot(), getString(R.string.snackbar_unarchived, saving.getName()), Snackbar.LENGTH_LONG)
                .setAction(R.string.snackbar_undo, v -> viewModel.archiveSaving(saving))
                .show();
    }

    private void shareSaving(Saving saving) {
        if (saving == null) return;
        String currencySymbol = Currency.getSymbol(preferenceUtil.getCurrency());
        int percent = SavingsCalculator.getPercent(saving);

        StringBuilder shareText = new StringBuilder();
        shareText.append("🎯 Goal: ").append(saving.getName()).append(" (Archived)\n");
        shareText.append("💰 Saved: ").append(currencySymbol)
                .append(com.eipna.centsation.util.FormatUtil.formatAmount(saving.getCurrentSaving(), this))
                .append(" of ")
                .append(currencySymbol)
                .append(com.eipna.centsation.util.FormatUtil.formatAmount(saving.getGoal(), this))
                .append(" (").append(percent).append("%)\n");

        if (saving.getDeadline() != AlarmSetter.NO_ALARM) {
            shareText.append("📅 Target Date: ")
                    .append(DateParser.getStringDate(saving.getDeadline(), preferenceUtil.getDeadlineFormat()))
                    .append("\n");
        }
        if (saving.getNotes() != null && !saving.getNotes().trim().isEmpty()) {
            shareText.append("📝 Notes: ").append(saving.getNotes().trim()).append("\n");
        }
        shareText.append("\nTracked with Centsation ✨");

        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_SUBJECT, "Savings Goal: " + saving.getName());
        sendIntent.putExtra(Intent.EXTRA_TEXT, shareText.toString());
        startActivity(Intent.createChooser(sendIntent, "Share Savings Goal"));
    }

    private void showDeleteDialog(Saving saving) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_title_delete_saving)
                .setIcon(R.drawable.ic_delete_forever)
                .setMessage(R.string.dialog_message_delete_saving)
                .setNegativeButton(R.string.dialog_button_cancel, null)
                .setPositiveButton(R.string.dialog_button_delete, (dialogInterface, i) ->
                        viewModel.deleteSaving(saving));
        builder.create().show();
    }

    private void showHistoryActivity(Saving saving) {
        Intent historyIntent = new Intent(this, HistoryActivity.class);
        historyIntent.putExtra(Database.COLUMN_SAVING_ID, saving.getID());
        startActivity(historyIntent);
    }
}
