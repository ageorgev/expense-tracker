package com.agv.expenses.util;

import java.time.format.DateTimeFormatter;

public enum DatePattern {
    ISO_DATE("yyyy-MM-dd"),
    ICICI_SAC_DATE("dd-MM-yyyy"),
    ORDER_ID_DATE("yyyyMMdd"),
    FILE_TIMESTAMP("yyyyMMdd_HHmmss"),
    DISPLAY_TIME("HH:mm:ss"),
    REPORT_DATE("dd MMM yyyy");

    private final String pattern;
    private final DateTimeFormatter formatter;

    DatePattern(String pattern) {
        this.pattern = pattern;
        // Pre-compile the formatter for performance
        this.formatter = DateTimeFormatter.ofPattern(pattern);
    }

    public String getPattern() {
        return pattern;
    }

    public DateTimeFormatter getFormatter() {
        return formatter;
    }
}
