package com.agv.expenses.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Date;
import java.util.Objects;

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
    public static final String EXCH_PROPERTY_RES_PAYLOAD = "EXCH_PROPERTY_RES_PAYLOAD";
    public static final String EXCH_HEADER_PROPERTY_PDF_FILE_ID = "CamelGoogleDrive.fileId";

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

    public static LocalDate convertStringToDate(String strDate, DatePattern datePattern) {
        if (strDate == null || datePattern == null || strDate.isBlank()) {
            return null;
        }
        try {
            // This will parse the string EXACTLY as written.
            // No timezone math will ever occur.
            return LocalDate.parse(strDate, datePattern.getFormatter());
        } catch (DateTimeParseException e) {
            // Handle or log the error
            throw new IllegalArgumentException("Input Date: " + strDate+" Not as per format "+datePattern.getPattern());
        }
    }

    public static String convertStrngDateFormat(String strDate, DatePattern inPattern, DatePattern ouDatePattern){
        if (strDate == null || inPattern == null || strDate.isBlank() || ouDatePattern == null) {
            return strDate;
        }
        return toDateString(convertStringToDate(strDate,inPattern),ouDatePattern);
    }

    public static String toDateString(LocalDate date, DatePattern pattern) {
        return (date == null) ? null : date.format(pattern.getFormatter());
    }
/**
     * Generates a unique Order ID string.
     * @param date The LocalDate (e.g., 2026-02-23)
     * @param amountStr The raw string (e.g., " 4,234.02 ")
     * @return String in format yyyyMMddamt
     * @throws IllegalArgumentException if amount is invalid or null
     */
    public static String generateOrderId(LocalDate date, String amountStr) {
        // 1. Basic Null Checks
        Objects.requireNonNull(date, "Date cannot be null");
        
        if (amountStr == null ) {
            throw new IllegalArgumentException("Amount string cannot be null or empty");
        }
        if(amountStr.isBlank())
        {
            amountStr="0";
        }

        // 2. Clean and Trim the input
        // Remove commas and whitespace; keep periods for decimal parsing
        String cleanedAmount = amountStr.trim().replace(",", "");

        BigDecimal amount;
        try {
            // 3. Convert to BigDecimal
            amount = new BigDecimal(cleanedAmount);
            
            // 4. Validation: No negative amounts for Order IDs
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Amount cannot be negative: " + amountStr);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid numeric format for amount: " + amountStr);
        }

        // 5. Format the Date (yyyyMMdd)
        String datePart = toDateString(date,DatePattern.ORDER_ID_DATE);

        // 6. Format the Amount
        // Scale to 2 decimal places, remove the dot to get "cents" representation
        String amountPart = amount.setScale(2, RoundingMode.HALF_UP)
                                  .toPlainString()
                                  .replace(".", "");

        return datePart + amountPart;
    }
}
