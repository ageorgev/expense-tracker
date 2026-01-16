package com.agv.expenses.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.math3.analysis.function.Exp;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.agv.expenses.util.ExpenseUtil;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ICICITransactionProcessor implements Processor {
    private static final Logger LOG = LoggerFactory.getLogger(ICICITransactionProcessor.class);
    // Define the specific date format requested
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MMM-yyyy");
    private final int DATA_ARRAY_SIZE = 12;

    @Override
    public void process(Exchange exchange) throws Exception {
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
        String[] rowDataArr = null;
        String ACCOUNT_NO_STRING_PREFIX = "Transactions List -";
        InputStream is = exchange.getIn().getBody(InputStream.class);
        Map<String, List<Object>> masterDataMap = exchange.getProperty("MASTER_DATA_MAP", Map.class);
        try (Workbook workbook = WorkbookFactory.create(is)) {
            // 1. Get sheet by specific name
            Sheet sheet = workbook.getSheet("OpTransactionHistory");

            if (sheet == null) {
                throw new IllegalArgumentException("Sheet 'OpTransactionHistory' not found in the Excel file!");
            }

            String accountNumber = "";
            String accountName = "";
            int dataRowIdx = -1;
            // 2. Iterate through all rows
            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null)
                    continue;

                Cell cell2 = row.getCell(1);
                if (getCellValueAsString(cell2).indexOf(ACCOUNT_NO_STRING_PREFIX) != -1) {
                    String cellValueStr = getCellValueAsString(cell2);
                    String[] parts = cellValueStr.split(" - ");
                    accountNumber = parts[parts.length - 1].trim();
                    LOG.info("Found Account Number: {}", accountNumber);
                    if ("021201554990".equals(accountNumber)) {
                        accountName = "ICICI - Santhome";
                    } else {
                        accountName = "ICICI - Tripunitura";
                    }
                    dataRowIdx++;
                    continue;
                }
                if (dataRowIdx >= 0) {
                    // Data Rows
                    dataRowIdx++;
                }
                if (dataRowIdx > 0) {
                    rowDataArr = ExpenseUtil.initializeDataArray(null);
                    rowDataArr[0] = getCellValueAsString(row.getCell(2));
                    String cellValue = getCellValueAsString(row.getCell(5));
                    String[] txnDetails = getCellValueAsString(row.getCell(5)).split("/");
                    /*
                     * if (txnDetails.length < 5) {
                     * LOG.warn("Skipping row due to unexpected Txn Details format: {}",
                     * getCellValueAsString(row.getCell(5)));
                     * continue;
                     * }
                     */
                    if (txnDetails.length > 4 && ExpenseUtil.isOnlyNumbers(txnDetails[4])) {
                        rowDataArr[8] = txnDetails[4];
                    } else {
                        for (int idx = txnDetails.length - 1; idx >= 0; idx--) {
                            if (ExpenseUtil.isOnlyNumbers(txnDetails[idx])) {
                                rowDataArr[8] = txnDetails[idx];
                                break;
                            }
                        }
                    }
                    if (rowDataArr[8] == null || rowDataArr[8].isEmpty()) {
                        rowDataArr[8] = "";
                    }

                    if (masterDataMap != null && masterDataMap.containsKey(rowDataArr[8])) {
                        List<Object> phonePeRow = masterDataMap.get(rowDataArr[8]);
                        String enrichmentInfo = "";
                        for (int idx = 1; idx < txnDetails.length; idx++) {
                            if (txnDetails[idx].indexOf("@") == -1 && !ExpenseUtil.isOnlyNumbers(txnDetails[idx])) {
                                enrichmentInfo = txnDetails[idx];
                                break;
                            }
                        }
                        String paidToEnrich = String.valueOf(phonePeRow.get(4)) + " | "
                                + enrichmentInfo;
                        phonePeRow.set(4, paidToEnrich);
                        // IF this row is existing there is no need to add again
                        continue;
                    } else {
                        LOG.info("No matching PhonePe entry found for Order ID: {}", rowDataArr[8]);
                        rowDataArr[1] = accountName; // From
                        rowDataArr[2] = txnDetails[0]; // To
                        rowDataArr[3] = "";
                        rowDataArr[4] = Arrays.stream(txnDetails)
                                .skip(1) // Skips the first element (20250111)
                                .collect(Collectors.joining(" | ")); // Paid To
                        rowDataArr[5] = getCellValueAsString(row.getCell(6));
                        rowDataArr[7] = accountNumber;
                        StringBuilder bodyCleaned = new StringBuilder();
                        for (int col = 1; col <= 8; col++) {
                            Cell cell = row.getCell(col);
                            bodyCleaned.append(getCellValueAsString(cell)).append(" | ");
                        }
                        rowDataArr[6] = bodyCleaned.toString();
                        // Checking whether transaction is debit.
                        if (!"".equals(rowDataArr[5]) && !"".equals(rowDataArr[0]) && ExpenseUtil.isOnlyNumbers(rowDataArr[5]) 
                            && Double.parseDouble(rowDataArr[5]) > 0) {
                            String key = ExpenseUtil.reformatDate(rowDataArr[0]) + rowDataArr[5];
                            masterDataMap.put(ExpenseUtil.removePeriod(key), Arrays.asList((Object[]) rowDataArr));
                        }

                    }

                }

            }
        } catch (Exception e) {
            LOG.error("Error processing Excel file: {}", e.getMessage(), e);
            // throw e;
        }
    }

    // Helper method to handle different cell types (String, Numeric, etc.)
    private String getCellValueAsString(Cell cell) {
        if (cell == null)
            return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? dateFormat.format(cell.getDateCellValue())
                    : String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

}