package com.eipna.centsation.data.saving;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public enum SavingCategory {
    GENERAL("general", "General", "🎯", "#415F91"),
    EMERGENCY("emergency", "Emergency", "🛡️", "#B3261E"),
    TRAVEL("travel", "Travel", "✈️", "#00639B"),
    TECH("tech", "Tech", "💻", "#6750A4"),
    VEHICLE("vehicle", "Vehicle", "🚗", "#984061"),
    HOME("home", "Home", "🏠", "#2E6B47"),
    SHOPPING("shopping", "Shopping", "🛍️", "#A04000"),
    EDUCATION("education", "Education", "📚", "#006A6A"),
    INVESTMENT("investment", "Investment", "📈", "#006874");

    private final String id;
    private final String displayName;
    private final String emoji;
    private final String colorHex;

    SavingCategory(String id, String displayName, String emoji, String colorHex) {
        this.id = id;
        this.displayName = displayName;
        this.emoji = emoji;
        this.colorHex = colorHex;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmoji() {
        return emoji;
    }

    public String getColorHex() {
        return colorHex;
    }

    public String getFormattedLabel() {
        return emoji + " " + displayName;
    }

    @NonNull
    public static SavingCategory fromId(String id) {
        if (id == null) return GENERAL;
        for (SavingCategory cat : values()) {
            if (cat.id.equalsIgnoreCase(id) || cat.name().equalsIgnoreCase(id)) {
                return cat;
            }
        }
        return GENERAL;
    }

    public static List<SavingCategory> getAll() {
        List<SavingCategory> list = new ArrayList<>();
        for (SavingCategory cat : values()) {
            list.add(cat);
        }
        return list;
    }
}
