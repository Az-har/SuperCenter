package com.eipna.centsation.ui.activities;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.eipna.centsation.R;
import com.eipna.centsation.data.Database;
import com.eipna.centsation.data.saving.Saving;
import com.eipna.centsation.data.saving.SavingRepository;
import com.eipna.centsation.data.transaction.Transaction;
import com.eipna.centsation.databinding.ActivityHistoryBinding;
import com.eipna.centsation.ui.adapters.TransactionAdapter;
import com.eipna.centsation.ui.viewmodel.HistoryViewModel;
import com.eipna.centsation.util.AnimationUtil;

import java.util.List;
import java.util.concurrent.Executors;

public class HistoryActivity extends CentsationActivity {

    private ActivityHistoryBinding binding;
    private HistoryViewModel viewModel;
    private TransactionAdapter transactionAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHistoryBinding.inflate(getLayoutInflater());
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

        String selectedSavingID = getIntent().getStringExtra(Database.COLUMN_SAVING_ID);

        viewModel = new ViewModelProvider(this).get(HistoryViewModel.class);
        viewModel.setSavingID(selectedSavingID);

        transactionAdapter = new TransactionAdapter(this);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(transactionAdapter);

        loadSavingTitle(selectedSavingID);
        viewModel.getTransactions().observe(this, this::onTransactionsChanged);
    }

    private void loadSavingTitle(String savingID) {
        if (savingID == null) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            SavingRepository repo = new SavingRepository(this);
            Saving saving = repo.getSaving(savingID);
            if (saving != null) {
                runOnUiThread(() -> {
                    if (binding != null && binding.toolbar != null) {
                        binding.toolbar.setTitle(saving.getName());
                    }
                });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.loadTransactions();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    private void onTransactionsChanged(List<Transaction> updated) {
        transactionAdapter.submitList(updated);
        boolean empty = updated == null || updated.isEmpty();
        if (empty) {
            AnimationUtil.crossfade(binding.recyclerView, binding.emptyIndicator, 200);
            if (binding.toolbar != null) binding.toolbar.setSubtitle(null);
        } else {
            AnimationUtil.crossfade(binding.emptyIndicator, binding.recyclerView, 200);
            if (binding.toolbar != null) {
                binding.toolbar.setSubtitle(updated.size() + (updated.size() == 1 ? " transaction" : " transactions"));
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) finish();
        return true;
    }
}
