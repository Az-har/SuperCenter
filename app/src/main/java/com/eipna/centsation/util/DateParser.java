package com.eipna.centsation.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class DateParser {
    private DateParser() {}

    public static String format(long timeStamp, String pattern) {
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date(timeStamp));
    }

    public static String getStringDate(long timeStamp, String pattern) {
        return format(timeStamp, pattern);
    }
}
