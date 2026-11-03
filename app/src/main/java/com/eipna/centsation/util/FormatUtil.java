package com.eipna.centsation.util;

import android.content.Context;

import com.eipna.centsation.data.Currency;

import java.text.NumberFormat;
import java.util.Locale;

public class FormatUtil {

    public static final String FORMAT_AUTO = "auto";
    public static final String FORMAT_INDIAN = "indian";
    public static final String FORMAT_WESTERN = "western";
    public static final String FORMAT_SYSTEM = "system";

    /**
     * Formats an amount using the regional number grouping system (e.g. 1,00,000 for Indian, 100,000 for Western).
     */
    public static String formatAmount(double amount, Context context) {
        if (context == null) {
            return formatAmount(amount);
        }
        try {
            PreferenceUtil prefUtil = new PreferenceUtil(context);
            String formatPref = prefUtil.getRegionalNumberFormat();
            String currencyCode = prefUtil.getCurrency();
            return formatAmountInternal(amount, formatPref, currencyCode);
        } catch (Exception e) {
            return formatAmount(amount);
        }
    }

    /**
     * Formats an amount using the specified currency code.
     */
    public static String formatAmount(double amount, String currencyCode) {
        return formatAmountInternal(amount, FORMAT_AUTO, currencyCode);
    }

    /**
     * Fallback format using default currency.
     */
    public static String formatAmount(double amount) {
        return formatAmountInternal(amount, FORMAT_AUTO, Currency.INDIAN_RUPEE.CODE);
    }

    private static String formatAmountInternal(double amount, String formatPref, String currencyCode) {
        boolean useIndian;
        if (FORMAT_INDIAN.equals(formatPref)) {
            useIndian = true;
        } else if (FORMAT_WESTERN.equals(formatPref)) {
            useIndian = false;
        } else if (FORMAT_SYSTEM.equals(formatPref)) {
            useIndian = isIndianLocale(Locale.getDefault());
        } else { // AUTO
            if (currencyCode != null && (Currency.INDIAN_RUPEE.CODE.equalsIgnoreCase(currencyCode)
                    || "NPR".equalsIgnoreCase(currencyCode)
                    || "PKR".equalsIgnoreCase(currencyCode)
                    || "BDT".equalsIgnoreCase(currencyCode)
                    || "LKR".equalsIgnoreCase(currencyCode))) {
                useIndian = true;
            } else {
                useIndian = isIndianLocale(Locale.getDefault());
            }
        }

        if (useIndian) {
            return formatIndianSystem(amount);
        } else {
            return formatWesternSystem(amount);
        }
    }

    private static boolean isIndianLocale(Locale locale) {
        if (locale == null) return false;
        String country = locale.getCountry();
        return "IN".equalsIgnoreCase(country);
    }

    /**
     * Indian numbering system: 1,00,000 / 1,00,00,000 (Lakhs and Crores).
     */
    public static String formatIndianSystem(double amount) {
        boolean isNegative = amount < 0;
        double abs = Math.abs(amount);

        long integerPart = (long) abs;
        double fraction = abs - integerPart;

        String intStr = Long.toString(integerPart);
        StringBuilder sb = new StringBuilder();

        int len = intStr.length();
        if (len <= 3) {
            sb.append(intStr);
        } else {
            // Rightmost 3 digits
            String lastThree = intStr.substring(len - 3);
            String remaining = intStr.substring(0, len - 3);

            StringBuilder rev = new StringBuilder();
            int count = 0;
            for (int i = remaining.length() - 1; i >= 0; i--) {
                rev.append(remaining.charAt(i));
                count++;
                if (count % 2 == 0 && i > 0) {
                    rev.append(',');
                }
            }
            sb.append(rev.reverse().toString());
            sb.append(',');
            sb.append(lastThree);
        }

        // Decimal fraction (if any)
        if (Math.round(fraction * 100) > 0) {
            long cents = Math.round(fraction * 100);
            if (cents == 100) {
                return formatIndianSystem(isNegative ? -(integerPart + 1) : (integerPart + 1));
            }
            sb.append(String.format(Locale.US, ".%02d", cents));
        }

        if (isNegative) {
            return "-" + sb.toString();
        }
        return sb.toString();
    }

    /**
     * Western numbering system: 100,000 / 10,000,000 (Thousands and Millions).
     */
    public static String formatWesternSystem(double amount) {
        boolean isNegative = amount < 0;
        double abs = Math.abs(amount);

        long integerPart = (long) abs;
        double fraction = abs - integerPart;

        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
        String intFormatted = nf.format(integerPart);

        StringBuilder sb = new StringBuilder(intFormatted);
        if (Math.round(fraction * 100) > 0) {
            long cents = Math.round(fraction * 100);
            if (cents == 100) {
                return formatWesternSystem(isNegative ? -(integerPart + 1) : (integerPart + 1));
            }
            sb.append(String.format(Locale.US, ".%02d", cents));
        }

        if (isNegative) {
            return "-" + sb.toString();
        }
        return sb.toString();
    }

    /**
     * Robustly parses numeric strings typed or pasted by users, ignoring grouping commas,
     * currency symbols, and spaces.
     */
    public static double parseDouble(String text) throws NumberFormatException {
        if (text == null) throw new NumberFormatException("Null input");
        String clean = text.trim();
        if (clean.isEmpty()) throw new NumberFormatException("Empty input");

        // Strip commas appropriately
        if (clean.contains(",") && !clean.contains(".")) {
            int commaIdx = clean.lastIndexOf(',');
            int digitsAfter = clean.length() - commaIdx - 1;
            if (digitsAfter == 1 || digitsAfter == 2) {
                // Potential European decimal comma e.g. "10,5" or "10,50"
                clean = clean.replace(',', '.');
            } else {
                clean = clean.replace(",", "");
            }
        } else {
            clean = clean.replace(",", "");
        }

        // Clean out spaces and stray currency symbols
        clean = clean.replaceAll("[^0-9.-]", "");
        if (clean.isEmpty() || clean.equals("-") || clean.equals(".")) {
            throw new NumberFormatException("No valid digits");
        }
        return Double.parseDouble(clean);
    }

    /**
     * Parses numeric strings, falling back to a default value if invalid.
     */
    public static double parseDouble(String text, double defaultValue) {
        try {
            return parseDouble(text);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
