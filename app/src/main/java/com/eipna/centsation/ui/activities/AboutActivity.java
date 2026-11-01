package com.eipna.centsation.ui.activities;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.eipna.centsation.R;
import com.eipna.centsation.databinding.ActivityAboutBinding;
import com.eipna.centsation.util.AnimationUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class AboutActivity extends AppCompatActivity {

    private ActivityAboutBinding binding;
    private int easterEggCounter = 0;

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAboutBinding.inflate(getLayoutInflater());
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

        setupViews();
    }

    private void setupViews() {
        String versionName = "2.0.0";
        try {
            PackageManager packageManager = getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(getPackageName(), 0);
            versionName = packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException ignored) {}

        binding.chipAppVersion.setText(String.format("v%s • Material 3", versionName));
        binding.aboutVersionSummary.setText(String.format("%s (Release)", versionName));

        AnimationUtil.applySpringPressEffect(binding.btnAuthorGithub);
        AnimationUtil.applySpringPressEffect(binding.btnAuthorRepo);
        AnimationUtil.applySpringPressEffect(binding.btnAuthorEmail);

        binding.btnAuthorGithub.setOnClickListener(v -> openUrl(getString(R.string.url_author)));
        binding.btnAuthorRepo.setOnClickListener(v -> openUrl(getString(R.string.url_source_code)));
        binding.btnAuthorEmail.setOnClickListener(v -> openEmail(getString(R.string.developer_email)));

        binding.rowLicense.setOnClickListener(v -> showLicenseDialog());

        binding.rowVersion.setOnClickListener(v -> {
            easterEggCounter++;
            if (easterEggCounter >= 7) {
                Toast.makeText(this, R.string.app_easter_egg, Toast.LENGTH_SHORT).show();
                easterEggCounter = 0;
            }
        });
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show();
        }
    }

    private void openEmail(String email) {
        try {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:" + email));
            intent.putExtra(Intent.EXTRA_SUBJECT, "Centsation Feedback");
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show();
        }
    }

    private void showLicenseDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.preference_app_license)
                .setIcon(R.drawable.ic_license)
                .setMessage(readLicenseFromAssets())
                .setPositiveButton(R.string.dialog_button_close, null)
                .show();
    }

    private String readLicenseFromAssets() {
        StringBuilder stringBuilder = new StringBuilder();
        AssetManager assetManager = getAssets();
        try (InputStream inputStream = assetManager.open("license.txt")) {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }
        } catch (IOException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        return stringBuilder.toString();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
