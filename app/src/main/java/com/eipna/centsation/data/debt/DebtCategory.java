package com.eipna.centsation.data.debt;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public enum DebtCategory {
    GENERAL("general", "General Debt", "💳", "#B3261E"),
    CREDIT_CARD("credit_card", "Credit Card", "💳", "#984061"),
    PERSONAL_LOAN("personal_loan", "Personal Loan", "🤝", "#00639B"),
    STUDENT_LOAN("student_loan", "Student Loan", "🎓", "#006A6A"),
    MORTGAGE("mortgage", "Mortgage", "🏡", "#2E6B47"),
    CAR_LOAN("car_loan", "Auto Loan", "🚗", "#A04000"),
    MEDICAL("medical", "Medical", "🏥", "#BA1A1A"),
    FAMILY_FRIEND("family_friend", "Family / Friend", "👥", "#6750A4");

    private final String id;
    private final String displayName;
    private final String emoji;
    private final String colorHex;

    DebtCategory(String id, String displayName, String emoji, String colorHex) {
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
    public static DebtCategory fromId(String id) {
        if (id == null) return GENERAL;
        for (DebtCategory cat : values()) {
            if (cat.id.equalsIgnoreCase(id) || cat.name().equalsIgnoreCase(id)) {
                return cat;
            }
        }
        return GENERAL;
    }

    public static List<DebtCategory> getAll() {
        List<DebtCategory> list = new ArrayList<>();
        for (DebtCategory cat : values()) {
            list.add(cat);
        }
        return list;
    }
}
