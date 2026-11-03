package com.eipna.centsation.ui.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.eipna.centsation.data.saving.Saving;
import com.eipna.centsation.data.saving.SavingCategory;
import com.eipna.centsation.data.saving.SavingRepository;
import com.eipna.centsation.data.saving.SavingSort;
import com.eipna.centsation.data.transaction.TransactionType;
import com.eipna.centsation.util.AlarmSetter;
import com.eipna.centsation.util.AppExecutors;
import com.eipna.centsation.util.BackupManager;
import com.eipna.centsation.util.PreferenceUtil;
import com.eipna.centsation.util.SavingsCalculator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class MainViewModel extends AndroidViewModel {

    public enum StatusFilter {
        ALL,
        IN_PROGRESS,
        DUE_SOON,
        COMPLETED
    }

    private final SavingRepository savingRepository;
    private final PreferenceUtil preferences;

    private final ExecutorService executor = AppExecutors.getInstance().diskIO();

    private final MutableLiveData<List<Saving>> savingsLiveData = new MutableLiveData<>();

    private volatile String sortCriteria;
    private volatile boolean isSortAscending;
    private volatile String searchQuery = "";
    private volatile StatusFilter statusFilter = StatusFilter.ALL;

    public MainViewModel(@NonNull Application application) {
        super(application);
        savingRepository = new SavingRepository(application);
        preferences = new PreferenceUtil(application);
        sortCriteria = preferences.getSortCriteria();
        isSortAscending = preferences.getSortOrder();
    }

    public LiveData<List<Saving>> getSavings() {
        return savingsLiveData;
    }

    public String getSortCriteria() {
        return sortCriteria;
    }

    public boolean isSortAscending() {
        return isSortAscending;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public StatusFilter getStatusFilter() {
        return statusFilter;
    }

    public void setStatusFilter(StatusFilter filter) {
        this.statusFilter = filter != null ? filter : StatusFilter.ALL;
        executor.execute(this::loadSavingsBlocking);
    }

    public void loadSavings() {
        executor.execute(this::loadSavingsBlocking);
    }

    public void setSearchQuery(String query) {
        this.searchQuery = query == null ? "" : query.trim().toLowerCase();
        executor.execute(this::loadSavingsBlocking);
    }

    private void loadSavingsBlocking() {
        List<Saving> list = savingRepository.getSavings(Saving.NOT_ARCHIVE);
        sortInPlace(list);

        // Apply Status Filter
        if (statusFilter != StatusFilter.ALL) {
            List<Saving> statusFiltered = new ArrayList<>();
            for (Saving s : list) {
                boolean completed = SavingsCalculator.isCompleted(s);
                if (statusFilter == StatusFilter.COMPLETED && completed) {
                    statusFiltered.add(s);
                } else if (statusFilter == StatusFilter.IN_PROGRESS && !completed) {
                    statusFiltered.add(s);
                } else if (statusFilter == StatusFilter.DUE_SOON && !completed) {
                    long days = SavingsCalculator.getDaysRemaining(s.getDeadline());
                    if (s.getDeadline() != AlarmSetter.NO_ALARM && days <= 7) {
                        statusFiltered.add(s);
                    }
                }
            }
            list = statusFiltered;
        }

        // Apply Search Filter (matches name, notes, category display name)
        if (!searchQuery.isEmpty()) {
            List<Saving> filtered = new ArrayList<>();
            for (Saving s : list) {
                boolean matchName = s.getName() != null && s.getName().toLowerCase().contains(searchQuery);
                boolean matchNotes = s.getNotes() != null && s.getNotes().toLowerCase().contains(searchQuery);
                SavingCategory cat = SavingCategory.fromId(s.getCategory());
                boolean matchCat = cat.getDisplayName().toLowerCase().contains(searchQuery)
                        || (s.getCategory() != null && s.getCategory().toLowerCase().contains(searchQuery));
                if (matchName || matchNotes || matchCat) {
                    filtered.add(s);
                }
            }
            list = filtered;
        }

        savingsLiveData.postValue(list);
        BackupManager.triggerAutoBackup(getApplication());
    }

    public void setSortCriteria(String criteria) {
        this.sortCriteria = criteria;
        executor.execute(() -> {
            preferences.setSortCriteria(criteria);
            resortCurrentList();
        });
    }

    public void setSortAscending(boolean ascending) {
        this.isSortAscending = ascending;
        executor.execute(() -> {
            preferences.setSortOrder(ascending);
            resortCurrentList();
        });
    }

    private void resortCurrentList() {
        loadSavingsBlocking();
    }

    private void sortInPlace(List<Saving> list) {
        Comparator<Saving> comparator = comparatorFor(sortCriteria);
        if (comparator == null) return;
        if (!isSortAscending) comparator = comparator.reversed();
        list.sort(comparator);
    }

    private Comparator<Saving> comparatorFor(String criteria) {
        if (SavingSort.NAME.SORT.equals(criteria)) return Saving.SORT_NAME;
        if (SavingSort.VALUE.SORT.equals(criteria)) return Saving.SORT_VALUE;
        if (SavingSort.GOAL.SORT.equals(criteria)) return Saving.SORT_GOAL;
        if (SavingSort.DEADLINE.SORT.equals(criteria)) return Saving.SORT_DEADLINE;
        return null;
    }

    public void deleteSaving(Saving saving) {
        executor.execute(() -> {
            AlarmSetter.cancel(getApplication(), saving);
            savingRepository.delete(saving.getID());
            loadSavingsBlocking();
        });
    }

    public void archiveSaving(Saving saving) {
        Saving archived = new Saving(saving);
        archived.setIsArchived(Saving.IS_ARCHIVE);
        AlarmSetter.cancel(getApplication(), saving);
        executor.execute(() -> {
            savingRepository.edit(archived);
            loadSavingsBlocking();
        });
    }

    public void unarchiveSaving(Saving saving) {
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
        });
    }

    public void deposit(Saving saving, double amount) {
        Saving updated = new Saving(saving);
        double newBalance = saving.getCurrentSaving() + amount;
        updated.setCurrentSaving(newBalance);

        // If goal is achieved, cancel the deadline notification
        if (newBalance >= updated.getGoal()) {
            AlarmSetter.cancel(getApplication(), updated);
        }

        executor.execute(() -> {
            savingRepository.makeTransaction(updated, amount, TransactionType.DEPOSIT);
            loadSavingsBlocking();
        });
    }

    public boolean withdraw(Saving saving, double amount) {
        double newBalance = saving.getCurrentSaving() - amount;
        if (newBalance < 0) return false;
        Saving updated = new Saving(saving);
        updated.setCurrentSaving(newBalance);

        // If previously achieved and balance dropped below goal, re-arm alarm if future
        if (saving.getCurrentSaving() >= saving.getGoal() && newBalance < updated.getGoal()) {
            if (updated.getDeadline() != AlarmSetter.NO_ALARM && updated.getDeadline() > System.currentTimeMillis()) {
                AlarmSetter.set(getApplication(), updated);
            }
        }

        executor.execute(() -> {
            savingRepository.makeTransaction(updated, amount, TransactionType.WITHDRAW);
            loadSavingsBlocking();
        });
        return true;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
        savingRepository.close();
    }
}
