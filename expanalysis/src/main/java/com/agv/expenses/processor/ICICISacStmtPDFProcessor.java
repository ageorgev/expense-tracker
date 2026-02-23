package com.agv.expenses.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.math3.analysis.function.Exp;
import org.bouncycastle.jcajce.provider.asymmetric.dsa.DSASigner.stdDSA;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.pulsar.PulsarProperties.Transaction;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.agv.expenses.service.model.PDFExtractPayload;
import com.agv.expenses.util.DatePattern;
import com.agv.expenses.util.ExpenseUtil;
import com.google.api.services.sheets.v4.model.ValueRange;

@Component
public class ICICISacStmtPDFProcessor implements Processor {

    // Standard SLF4J Logger
    private static final Logger LOG = LoggerFactory.getLogger(ICICISacStmtPDFProcessor.class);
    private static final String TXN_LOG_START_INDICATOR = "DATE MODE PARTICULARS DEPOSITS WITHDRAWALS BALANCE";
    private static final Pattern ACC_NO_PATTERN = Pattern.compile("^Savings A/c\\s+(X{8}\\d{4})");
    private static final Pattern TXN_AMT_PATTERN = Pattern
            .compile("(\\d{1,3}(?:,\\d{3})*\\.\\d{2})(\\s+)(\\d{1,3}(?:,\\d{3})*\\.\\d{2})");
    // private static final Pattern CURRENCY_SEARCH_PATTERN =
    // Pattern.compile("\\d{1,3}(?:,\\d{3})*\\.\\d{2}");
    private static final String START_PATTERN = "^\\d{2}-\\d{2}-\\d{4}.*";
    private static final String PAGE_END_PATTERN = "^Total:\\s";
    private static final String FIELD_SEP_TOKEN = "###";
    private static final int DATE_STR_LENGTH = 10;
    @Value("#{${icici.account.map}}")
    private Map<String, String> accountNameMap;

    private static final String EXTRACTION_REGEX = "^(\\d{2}-\\d{2}-\\d{2})\\s+(.*?)\\s+-\\s+.*?-\\s+([\\d\\.]+)";
    private static final Pattern EXTRACTION_PATTERN = Pattern.compile(EXTRACTION_REGEX, Pattern.DOTALL);
    Pattern DEBIT_PATTERN = Pattern.compile("(?<= - - )([^ ]+)");
    // Validation: Date + Space + Alphabet

    @Override
    public void process(Exchange exchange) throws Exception {
        String rawText = exchange.getIn().getBody(String.class);
        // LOG.debug(rawText);
        List<String[]> allRows = new ArrayList<>();
        Map<String, List<Object>> masterDataMap = exchange.getProperty("MASTER_DATA_MAP", Map.class);
        if (masterDataMap == null) {
            masterDataMap = new HashMap<>();
            exchange.setProperty("MASTER_DATA_MAP", masterDataMap);
        }
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
        boolean txnRowStartFlag = false;
        boolean isCaptureMode = false;
        // Regex to find segments starting with Date (e.g., Dec 07, 2025)
        String[] lines = rawText.split("\\n");
        String[] rowDataStrArr = null;
        StringBuffer rawMsgString = null;
        StringBuffer txnDescString = null;
        String accountNo = "";
        for (String rawLine : lines) {
            rawLine = rawLine.trim();
            if (TXN_LOG_START_INDICATOR.equals(rawLine)) {
                txnRowStartFlag = true;
                continue;
            }
            if (rawLine.matches(PAGE_END_PATTERN)) {
                if (rawMsgString != null && rawMsgString.length() > 0) {
                    // This condition means an error hence capturing full text for review
                    LOG.warn("Row processing error for " + rawMsgString.toString());
                    rowDataStrArr[6] = rawMsgString.toString();
                    allRows.add(rowDataStrArr);
                    rawMsgString = null;
                    txnDescString = null;
                    isCaptureMode = false;
                }
                txnRowStartFlag = false;
            }
            if (!txnRowStartFlag) {
                // If Account no is not extracted check for the matching pattern
                if ("".equals(accountNo)) {
                    Matcher acnoMatcher = ACC_NO_PATTERN.matcher(rawLine);
                    if (acnoMatcher.find()) {
                        accountNo = acnoMatcher.group(1);
                    }
                }
                continue;
            }
            if ((!rawLine.matches(START_PATTERN) && !isCaptureMode)) {
                continue;
            }
            if (rawLine.matches(START_PATTERN) && rawLine.indexOf("  B/F  ") == -1) {
                if (rawMsgString != null && rawMsgString.length() > 0) {
                    // This condition means an error hence capturing full text for review
                    LOG.warn("Row processing error for " + rawMsgString.toString());
                    rowDataStrArr[6] = rawMsgString.toString();
                    allRows.add(rowDataStrArr);
                    rawMsgString = null;
                    txnDescString = null;
                    isCaptureMode = false;
                }
                rawMsgString = new StringBuffer();
                txnDescString = new StringBuffer();
                rowDataStrArr = new String[ExpenseUtil.DATA_ARRAY_SIZE];
                rowDataStrArr[1] = ("".equals(accountNo)) ? "estatement.icicibank.com" : accountNameMap.get(accountNo);
                // if length is matching that of date then processing this line is over
                if (rawLine.length() == DATE_STR_LENGTH) {
                    rowDataStrArr[0] = rawLine;
                    rawMsgString.append(rawLine).append(FIELD_SEP_TOKEN);
                    isCaptureMode = true;
                    continue;
                } else {
                    rowDataStrArr[0] = rawLine.substring(0, DATE_STR_LENGTH);
                    Matcher txtAmountsLineMather = TXN_AMT_PATTERN.matcher(rawLine);
                    rawMsgString.append(rowDataStrArr[0]).append(FIELD_SEP_TOKEN);
                    if (txtAmountsLineMather.find()) {
                        String transactionAmount = txtAmountsLineMather.group(1);
                        String spaces = txtAmountsLineMather.group(2);
                        String balanceAmount = txtAmountsLineMather.group(3);
                        String type = (spaces.length() == 1) ? "DEBIT" : "CREDIT";
                        String txnDesc = rawLine.substring(DATE_STR_LENGTH, rawLine.indexOf(transactionAmount)).trim();
                        rawMsgString.append(txnDesc).append(FIELD_SEP_TOKEN)
                                .append(transactionAmount).append(FIELD_SEP_TOKEN)
                                .append(type).append(FIELD_SEP_TOKEN)
                                .append(balanceAmount);
                        rowDataStrArr[5] = transactionAmount;
                        rowDataStrArr[6] = rawMsgString.toString();
                        rowDataStrArr[7] = accountNo;
                        processTxnMessage(masterDataMap, rowDataStrArr, txnDesc);
                        allRows.add(rowDataStrArr);
                        rawMsgString = null;
                        txnDescString = null;
                        rowDataStrArr = null;
                        isCaptureMode = false;
                    } else {
                        txnDescString.append(rawLine.substring(DATE_STR_LENGTH).trim())
                                .append(" ");
                        isCaptureMode = true;
                        continue;
                    }
                }
            } else if (isCaptureMode) {
                Matcher txtAmountsLineMather = TXN_AMT_PATTERN.matcher(rawLine);
                if (txtAmountsLineMather.find()) {
                    String transactionAmount = txtAmountsLineMather.group(1);
                    String spaces = txtAmountsLineMather.group(2);
                    String balanceAmount = txtAmountsLineMather.group(3);
                    String type = (spaces.length() == 1) ? "DEBIT" : "CREDIT";
                    rowDataStrArr[11] = type;
                    rawMsgString.append(txnDescString).append(FIELD_SEP_TOKEN)
                            .append(transactionAmount).append(FIELD_SEP_TOKEN)
                            .append(type).append(FIELD_SEP_TOKEN)
                            .append(balanceAmount);
                    rowDataStrArr[5] = transactionAmount;
                    rowDataStrArr[6] = rawMsgString.toString();
                    rowDataStrArr[7] = accountNo;
                    processTxnMessage(masterDataMap, rowDataStrArr, txnDescString.toString());
                    allRows.add(rowDataStrArr);
                    rawMsgString = null;
                    txnDescString = null;
                    rowDataStrArr = null;
                    isCaptureMode = false;
                } else {
                    txnDescString.append(rawLine).append("");
                }
            }

        }
        LOG.info("Completed Processing");
        PDFExtractPayload[] payLoadArr = new PDFExtractPayload[allRows.size()];
        int rowIdx = 0;
        for (String[] currentRow : allRows) {
            PDFExtractPayload payLd = PDFExtractPayload.builder().txnDate(currentRow[0]).from(currentRow[1])
                    .to(currentRow[2]).subject(currentRow[3]).paidTo(currentRow[4]).amount(currentRow[5])
                    .bodyCleaned(currentRow[6]).subscriberID(currentRow[7]).orderID(currentRow[8]).link(currentRow[9])
                    .message(currentRow[10]).transactionFlag(currentRow[11]).build();
            var reptDate=ExpenseUtil.convertStrngDateFormat(payLd.getTxnDate(), DatePattern.ICICI_SAC_DATE, DatePattern.REPORT_DATE);
            payLd.setTxnDate(reptDate);
            payLd.validate();
            payLoadArr[rowIdx++] = payLd;
        }
        exchange.setProperty(ExpenseUtil.EXCH_PROPERTY_RES_PAYLOAD, payLoadArr);
        // TODO Adding last row

    }

    private void processTxnMessage(Map<String, List<Object>> masterDataMap, String[] rowDataStrArr,
            String fullDescString) {
        if (fullDescString == null || fullDescString.length() < 1) {
            return;
        }
        String[] txnDetails = fullDescString.split("/");
        if (txnDetails.length > 4 && ExpenseUtil.isOnlyNumbers(txnDetails[4])) {
            rowDataStrArr[8] = txnDetails[4];
        } else {
            for (int idx = txnDetails.length - 1; idx >= 0; idx--) {
                if (ExpenseUtil.isOnlyNumbers(txnDetails[idx])) {
                    rowDataStrArr[8] = txnDetails[idx];
                    break;
                }
            }
        }
        if (rowDataStrArr[8] == null || rowDataStrArr[8].isEmpty()) {
            rowDataStrArr[8] = "";
        }
        if (masterDataMap != null && masterDataMap.containsKey(rowDataStrArr[8])) {
            List<Object> phonePeRow = masterDataMap.get(rowDataStrArr[8]);
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
            return;
        } else {
            LOG.info("No matching PhonePe entry found for Order ID: {}", rowDataStrArr[8]);
            // rowDataStrArr[1] = accountName; // From
            rowDataStrArr[2] = txnDetails[0]; // To
            rowDataStrArr[3] = "";
            rowDataStrArr[4] = Arrays.stream(txnDetails)
                    .skip(1) // Skips the first element (20250111)
                    .collect(Collectors.joining(" | ")); // Paid To

        }
    }

}
