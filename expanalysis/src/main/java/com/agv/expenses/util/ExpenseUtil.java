package com.agv.expenses.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExpenseUtil {

    private static final Logger LOG = LoggerFactory.getLogger(ExpenseUtil.class);
    /**
     * Index position and corresponding values
     * 0. Date
     * 1. From
     * 2. To
     * 3. Subject
     * 4. Paid To
     * 5. Amount
     * 6. Body Cleaned
     * 7. Subscriber ID
     * 8. Order ID
     * 9. Link
     * 10. Message
     * 11. Flag
     */
    public static final int DATA_ARRAY_SIZE = 12;

    public static String reformatDate(String inputDate) {

        if (inputDate == null || inputDate.isEmpty()) {
            return "";
        }
        // 1. Define the input format (matching 15-Feb-2025)
        DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);
        try {
            // 2. Parse the string into a LocalDate object
            LocalDate date = LocalDate.parse(inputDate, inputFormatter);

            // 3. Define the output format (matching 20250215)
            DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");

            // 4. Return the formatted string
            return date.format(outputFormatter);
        } catch (Exception e) {
            LOG.error("Error parsing date: {}", inputDate, e);
            return "";
        }
    }

    public static String removePeriod(String input) {
        if (input == null) {
            return "";
        }
        return input.contains(".") ? input.split("\\.")[0] : input;
    }

    public static boolean isOnlyNumbers(String str) {
        if (str == null || str.isEmpty())
            return false;
        try {
            new BigDecimal(str.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static final String[] initializeDataArray(String[] dataArr) {
        if (dataArr == null) {
            dataArr = new String[DATA_ARRAY_SIZE];
        }
        for (int i = 0; i < DATA_ARRAY_SIZE; i++) {
            dataArr[i] = (dataArr[i] == null) ? "" : dataArr[i];
        }
        return dataArr;
    }

}
