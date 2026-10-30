package com.eipna.centsation.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.eipna.centsation.data.Contrast;
import com.eipna.centsation.data.Currency;
import com.eipna.centsation.data.DateFormat;
import com.eipna.centsation.data.Theme;
import com.eipna.centsation.data.saving.SavingSort;

public class PreferenceUtil {

    private final SharedPreferences sharedPreferences;

    public PreferenceUtil(Context context) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public boolean isDynamicColors() {
        return sharedPreferences.getBoolean("dynamic_colors", false);
    }

    public void setDynamicColors(boolean value) {
        sharedPreferences.edit().putBoolean("dynamic_colors", value).apply();
    }

    public String getTheme() {
        return sharedPreferences.getString("theme", Theme.SYSTEM.VALUE);
    }

    public void setTheme(String value) {
        sharedPreferences.edit().putString("theme", value).apply();
    }

    public String getCurrency() {
        return sharedPreferences.getString("currency", Currency.PHILIPPINE_PESO.CODE);
    }

    public void setCurrency(String value) {
        sharedPreferences.edit().putString("currency", value).apply();
    }

    public void setSortCriteria(String value) {
        sharedPreferences.edit().putString("sort_criteria", value).apply();
    }

    public String getSortCriteria() {
        return sharedPreferences.getString("sort_criteria", SavingSort.NAME.SORT);
    }

    public void setSortOrder(boolean value) {
        sharedPreferences.edit().putBoolean("sort_order", value).apply();
    }

    public boolean getSortOrder() {
        return sharedPreferences.getBoolean("sort_order", true);
    }

    public String getContrast() {
        return sharedPreferences.getString("contrast", Contrast.LOW.VALUE);
    }

    public void setContrast(String value) {
        sharedPreferences.edit().putString("contrast", value).apply();
    }

    public void setDeadlineFormat(String value) {
        sharedPreferences.edit().putString("deadline_format", value).apply();
    }

    public String getDeadlineFormat() {
        return sharedPreferences.getString("deadline_format", DateFormat.MM_DD_YYYY.PATTERN);
    }

    public void setScreenPrivacy(boolean value) {
        sharedPreferences.edit().putBoolean("screen_privacy", value).apply();
    }

    public boolean isScreenPrivacyEnabled() {
        return sharedPreferences.getBoolean("screen_privacy", false);
    }

    public boolean isAutoBackupEnabled() {
        return sharedPreferences.getBoolean("auto_backup", true);
    }

    public void setAutoBackupEnabled(boolean value) {
        sharedPreferences.edit().putBoolean("auto_backup", value).apply();
    }

    public long getLastBackupTimestamp() {
        return sharedPreferences.getLong("last_backup_timestamp", 0L);
    }

    public void setLastBackupTimestamp(long timestamp) {
        sharedPreferences.edit().putLong("last_backup_timestamp", timestamp).apply();
    }

    public boolean hasDismissedReinstallRestore() {
        return sharedPreferences.getBoolean("dismissed_reinstall_restore", false);
    }

    public void setDismissedReinstallRestore(boolean value) {
        sharedPreferences.edit().putBoolean("dismissed_reinstall_restore", value).apply();
    }
}
