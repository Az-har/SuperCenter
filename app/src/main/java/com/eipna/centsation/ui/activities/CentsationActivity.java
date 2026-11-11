package com.eipna.centsation.ui.activities;

import android.os.Bundle;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.eipna.centsation.CentsationApp;
import com.eipna.centsation.R;
import com.eipna.centsation.data.Contrast;
import com.eipna.centsation.util.PreferenceUtil;
import com.google.android.material.color.DynamicColors;

public abstract class CentsationActivity extends AppCompatActivity {

    protected PreferenceUtil preferenceUtil;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        preferenceUtil = new PreferenceUtil(this);
        super.onCreate(savedInstanceState);

        if (preferenceUtil.isScreenPrivacyEnabled()) {
            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
            );
        }

        // Apply theme using shared method without redundant recreation
        CentsationApp.applyTheme(preferenceUtil.getTheme());

        String contrast = preferenceUtil.getContrast();
        if (contrast.equals(Contrast.LOW.VALUE)) {
            setTheme(R.style.Theme_Centsation);
        } else if (contrast.equals(Contrast.MEDIUM.VALUE)) {
            setTheme(R.style.Theme_Centsation_MediumContrast);
        } else if (contrast.equals(Contrast.HIGH.VALUE)) {
            setTheme(R.style.Theme_Centsation_HighContrast);
        }

        if (preferenceUtil.isDynamicColors()) DynamicColors.applyToActivityIfAvailable(this);
    }

    @Override
    public void startActivity(android.content.Intent intent) {
        super.startActivity(intent);
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.slide_in_right, R.anim.slide_out_left, 0);
        } else {
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        }
    }

    @Override
    public void finish() {
        super.finish();
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.slide_in_left, R.anim.slide_out_right, 0);
        } else {
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        }
    }
}
