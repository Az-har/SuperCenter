package com.eipna.centsation.ui.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.eipna.centsation.data.debt.Debt;
import com.eipna.centsation.data.debt.DebtRepository;
import com.eipna.centsation.data.saving.Saving;
import com.eipna.centsation.data.saving.SavingRepository;
import com.eipna.centsation.util.AlarmSetter;
import com.eipna.centsation.util.AppExecutors;
import com.eipna.centsation.util.BackupManager;

import java.util.List;
import java.util.concurrent.ExecutorService;

public class ArchiveViewModel extends AndroidViewModel {

    private final SavingRepository savingRepository;
    private final DebtRepository debtRepository;
    private final ExecutorService executor = AppExecutors.getInstance().diskIO();
    private final MutableLiveData<List<Saving>> savingsLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<Debt>> debtsLiveData = new MutableLiveData<>();

    public ArchiveViewModel(@NonNull Application application) {
        super(application);
        savingRepository = new SavingRepository(application);
        debtRepository = new DebtRepository(application);
    }

    public LiveData<List<Saving>> getSavings() {
        return savingsLiveData;
    }

    public LiveData<List<Debt>> getDebts() {
        return debtsLiveData;
    }

    public void loadSavings() {
        executor.execute(this::loadSavingsBlocking);
    }

    public void loadDebts() {
        executor.execute(this::loadDebtsBlocking);
    }

    private void loadSavingsBlocking() {
        savingsLiveData.postValue(savingRepository.getSavings(Saving.IS_ARCHIVE));
    }

    private void loadDebtsBlocking() {
        debtsLiveData.postValue(debtRepository.getDebts(Debt.IS_ARCHIVE));
    }

    public void deleteSaving(Saving saving) {
        if (saving == null) return;
        executor.execute(() -> {
            AlarmSetter.cancel(getApplication(), saving);
            savingRepository.delete(saving.getID());
            loadSavingsBlocking();
            BackupManager.triggerAutoBackup(getApplication());
        });
    }

    public void archiveSaving(Saving saving) {
        if (saving == null) return;
        Saving archived = new Saving(saving);
        archived.setIsArchived(Saving.IS_ARCHIVE);
        AlarmSetter.cancel(getApplication(), saving);
        executor.execute(() -> {
            savingRepository.edit(archived);
            loadSavingsBlocking();
            BackupManager.triggerAutoBackup(getApplication());
        });
    }

    public void unarchiveSaving(Saving saving) {
        if (saving == null) return;
        Saving unarchived = new Saving(saving);
        unarchived.setIsArchived(Saving.NOT_ARCHIVE);
        executor.execute(() -> {
            savingRepository.edit(unarchived);
            if (unarchived.getDeadline() != AlarmSetter.NO_ALARM
                    && unarchived.getDeadline() > System.currentTimeMillis()
                    && unarchived.getCurrentSaving() < unarchived.getGoal()) {
                AlarmSetter.set(getApplication(), unarchived);
            }
            loadSavingsBlocking();
            BackupManager.triggerAutoBackup(getApplication());
        });
    }

    public void archiveDebt(Debt debt) {
        if (debt == null) return;
        executor.execute(() -> {
            debtRepository.setArchived(debt.getId(), true);
            loadDebtsBlocking();
            BackupManager.triggerAutoBackup(getApplication());
        });
    }

    public void unarchiveDebt(Debt debt) {
        if (debt == null) return;
        executor.execute(() -> {
            debtRepository.setArchived(debt.getId(), false);
            loadDebtsBlocking();
            BackupManager.triggerAutoBackup(getApplication());
        });
    }

    public void deleteDebt(Debt debt) {
        if (debt == null) return;
        executor.execute(() -> {
            debtRepository.delete(debt.getId());
            loadDebtsBlocking();
            BackupManager.triggerAutoBackup(getApplication());
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        savingRepository.close();
        debtRepository.close();
    }
}
