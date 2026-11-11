package com.eipna.centsation.ui.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.eipna.centsation.R;
import com.eipna.centsation.data.Currency;
import com.eipna.centsation.data.saving.Saving;
import com.eipna.centsation.data.saving.SavingCategory;
import com.eipna.centsation.data.saving.SavingRepository;
import com.eipna.centsation.databinding.ActivityCreateBinding;
import com.eipna.centsation.util.AlarmSetter;
import com.eipna.centsation.util.AnimationUtil;
import com.eipna.centsation.util.BackupManager;
import android.content.Context;
import com.eipna.centsation.util.DateParser;
import com.eipna.centsation.util.AppExecutors;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.snackbar.Snackbar;

import java.util.Calendar;
import java.util.Objects;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

public class CreateActivity extends CentsationActivity {

    private ActivityCreateBinding binding;
    private SavingRepository savingRepository;
    private final ExecutorService executor = AppExecutors.getInstance().diskIO();
    private boolean isSaving = false;
    private long selectedDeadline;
    private String selectedCurrencySymbol;

    private final ActivityResultLauncher<String> requestNotificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    showDeadlineDialog();
                } else {
                    Toast.makeText(this, R.string.toast_notification_permission_denied, Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        savingRepository = new SavingRepository(this);
        selectedDeadline = AlarmSetter.NO_ALARM;

        selectedCurrencySymbol = Currency.getSymbol(preferenceUtil.getCurrency());
        binding.fieldSavingCurrentSavingLayout.setPrefixText(selectedCurrencySymbol);
        binding.fieldSavingGoalLayout.setPrefixText(selectedCurrencySymbol);

        binding.fieldSavingDeadlineLayout.setEndIconVisible(false);
        View.OnClickListener deadlineClickListener = v -> {
            if (getSupportFragmentManager().findFragmentByTag("deadline_date_picker") != null) return;
            hasNotificationPermission();
        };
        binding.fieldSavingDeadlineText.setOnClickListener(deadlineClickListener);
        binding.fieldSavingDeadlineLayout.setOnClickListener(deadlineClickListener);
        binding.fieldSavingDeadlineLayout.setEndIconOnClickListener(v -> {
            binding.fieldSavingDeadlineText.setText("");
            binding.fieldSavingDeadlineLayout.setEndIconVisible(false);
            selectedDeadline = AlarmSetter.NO_ALARM;
        });

        binding.btnSave.setOnClickListener(v -> createSaving());
        AnimationUtil.applySpringPressEffect(binding.btnSave);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void createSaving() {
        String nameText = Objects.requireNonNull(binding.fieldSavingNameText.getText()).toString();
        String currentSavingText = Objects.requireNonNull(binding.fieldSavingCurrentSavingText.getText()).toString();
        String goalText = Objects.requireNonNull(binding.fieldSavingGoalText.getText()).toString();
        String notesText = Objects.requireNonNull(binding.fieldSavingNotesText.getText()).toString();
        String deadlineText = Objects.requireNonNull(binding.fieldSavingDeadlineText.getText()).toString();

        binding.fieldSavingNameLayout.setError(nameText.isEmpty() ? getString(R.string.field_error_required) : null);
        binding.fieldSavingCurrentSavingLayout.setError(currentSavingText.isEmpty() ? getString(R.string.field_error_required) : null);
        binding.fieldSavingGoalLayout.setError(goalText.isEmpty() ? getString(R.string.field_error_required) : null);
        if (nameText.isEmpty() || currentSavingText.isEmpty() || goalText.isEmpty()) return;

        double currentSaving;
        try {
            currentSaving = com.eipna.centsation.util.FormatUtil.parseDouble(currentSavingText);
            if (currentSaving < 0) {
                binding.fieldSavingCurrentSavingLayout.setError(getString(R.string.field_error_negative_saving));
                return;
            }
        } catch (NumberFormatException e) {
            binding.fieldSavingCurrentSavingLayout.setError(getString(R.string.field_error_invalid_number));
            return;
        }

        double goal;
        try {
            goal = com.eipna.centsation.util.FormatUtil.parseDouble(goalText);
            if (goal <= 0) {
                binding.fieldSavingGoalLayout.setError(getString(R.string.field_error_positive_goal));
                return;
            }
        } catch (NumberFormatException e) {
            binding.fieldSavingGoalLayout.setError(getString(R.string.field_error_invalid_number));
            return;
        }

        Saving createdSaving = new Saving();
        createdSaving.setID(UUID.randomUUID().toString());
        createdSaving.setName(nameText);
        createdSaving.setCurrentSaving(currentSaving);
        createdSaving.setGoal(goal);
        createdSaving.setNotes(notesText);
        createdSaving.setCategory(getSelectedCategoryId());
        createdSaving.setIsArchived(Saving.NOT_ARCHIVE);
        createdSaving.setDeadline(deadlineText.isEmpty() ? AlarmSetter.NO_ALARM : selectedDeadline);

        if (isSaving) return;
        isSaving = true;

        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }

        if (!deadlineText.isEmpty() && createdSaving.getCurrentSaving() < createdSaving.getGoal()) {
            AlarmSetter.set(this, createdSaving);
        }

        final Context appContext = getApplicationContext();
        executor.execute(() -> {
            savingRepository.create(createdSaving);
            BackupManager.triggerAutoBackup(appContext);
            runOnUiThread(() -> {
                setResult(RESULT_OK);
                finish();
            });
        });
    }

    private void hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                showDeadlineDialog();
            } else if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
                Snackbar.make(binding.getRoot(), getString(R.string.snack_bar_permission_notifications), Snackbar.LENGTH_SHORT)
                        .setAction(R.string.dialog_button_grant, v -> {
                            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                            startActivity(intent);
                        }).show();
            } else {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            showDeadlineDialog();
        }
    }

    private void showDeadlineDialog() {
        if (getSupportFragmentManager().findFragmentByTag("deadline_date_picker") != null) {
            return;
        }

        CalendarConstraints.Builder calendarConstraints = new CalendarConstraints.Builder()
                .setValidator(DateValidatorPointForward.now());

        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setCalendarConstraints(calendarConstraints.build())
                .setTitleText("Select deadline date")
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            Calendar utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            utcCalendar.setTimeInMillis(selection);

            Calendar localCalendar = Calendar.getInstance();
            localCalendar.set(
                    utcCalendar.get(Calendar.YEAR),
                    utcCalendar.get(Calendar.MONTH),
                    utcCalendar.get(Calendar.DAY_OF_MONTH),
                    9, 0, 0
            );
            localCalendar.set(Calendar.MILLISECOND, 0);

            long calculatedDeadline = localCalendar.getTimeInMillis();
            if (calculatedDeadline <= System.currentTimeMillis()) {
                Calendar fallback = Calendar.getInstance();
                fallback.add(Calendar.HOUR_OF_DAY, 1);
                calculatedDeadline = fallback.getTimeInMillis();
            }

            selectedDeadline = calculatedDeadline;
            String deadlineFormat = preferenceUtil.getDeadlineFormat();

            binding.fieldSavingDeadlineText.setText(DateParser.getStringDate(calculatedDeadline, deadlineFormat));
            binding.fieldSavingDeadlineLayout.setEndIconVisible(true);
        });
        datePicker.show(getSupportFragmentManager(), "deadline_date_picker");
    }

    private String getSelectedCategoryId() {
        int checkedId = binding.categoryChipGroup.getCheckedChipId();
        if (checkedId == R.id.cat_emergency) return SavingCategory.EMERGENCY.getId();
        if (checkedId == R.id.cat_travel) return SavingCategory.TRAVEL.getId();
        if (checkedId == R.id.cat_tech) return SavingCategory.TECH.getId();
        if (checkedId == R.id.cat_vehicle) return SavingCategory.VEHICLE.getId();
        if (checkedId == R.id.cat_home) return SavingCategory.HOME.getId();
        if (checkedId == R.id.cat_shopping) return SavingCategory.SHOPPING.getId();
        if (checkedId == R.id.cat_education) return SavingCategory.EDUCATION.getId();
        if (checkedId == R.id.cat_investment) return SavingCategory.INVESTMENT.getId();
        return SavingCategory.GENERAL.getId();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (savingRepository != null) {
            savingRepository.close();
        }
        binding = null;
    }
}
