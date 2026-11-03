package com.eipna.centsation.ui.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.eipna.centsation.data.saving.Saving;
import com.eipna.centsation.data.saving.SavingRepository;
import com.eipna.centsation.data.transaction.Transaction;
import com.eipna.centsation.data.transaction.TransactionRepository;
import com.eipna.centsation.util.AppExecutors;

import java.util.List;
import java.util.concurrent.ExecutorService;

public class HistoryViewModel extends AndroidViewModel {

    private final TransactionRepository transactionRepository;
    private final SavingRepository savingRepository;
    private final ExecutorService executor = AppExecutors.getInstance().diskIO();
    private final MutableLiveData<List<Transaction>> transactionsLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> savingTitleLiveData = new MutableLiveData<>();

    private String savingID;

    public HistoryViewModel(@NonNull Application application) {
        super(application);
        transactionRepository = new TransactionRepository(application);
        savingRepository = new SavingRepository(application);
    }

    public LiveData<List<Transaction>> getTransactions() {
        return transactionsLiveData;
    }

    public LiveData<String> getSavingTitle() {
        return savingTitleLiveData;
    }

    public void setSavingID(String savingID) {
        this.savingID = savingID;
    }

    public void loadTransactions() {
        if (savingID == null) return;
        final String id = savingID;
        executor.execute(() -> transactionsLiveData.postValue(transactionRepository.get(id)));
    }

    public void loadSavingTitle() {
        if (savingID == null) return;
        final String id = savingID;
        executor.execute(() -> {
            Saving saving = savingRepository.getSaving(id);
            if (saving != null) {
                savingTitleLiveData.postValue(saving.getName());
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
        savingRepository.close();
        transactionRepository.close();
    }
}