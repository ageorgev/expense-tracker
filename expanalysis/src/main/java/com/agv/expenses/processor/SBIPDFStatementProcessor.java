package com.agv.expenses.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.util.*;
import java.util.regex.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

import com.agv.expenses.service.model.PDFExtractPayload;
import com.agv.expenses.service.model.StatementProcessErrorRow;
import com.agv.expenses.util.DatePattern;
import com.agv.expenses.util.ExpenseUtil;

import lombok.experimental.var;

@Component("SBI_Savings_StatementProcessor")
public class SBIPDFStatementProcessor implements Processor {

    // Standard SLF4J Logger
    private static final Logger LOG = LoggerFactory.getLogger(SBIPDFStatementProcessor.class);
    private static final String ACCOUNT_NO_CHANGE_TIRGGER = "TRANSACTION DETAILS";
    private static final String TXN_LINE_END_MARKER = "Your Closing Balance ";
    private static final String[] IGNORE_TEXT_ARRAY = new String[] {
            "TRANSACTION OVERVIEW",
            "All dates are in DD-MM-YY format",
            "Visit https://sbi.co.in",
            "Customer Care 1800 1234",
            "Customer Care customercare@sbi.co.in"
    };
    private static final String START_PATTERN = "^\\d{2}-\\d{2}-\\d{2}\\s+.*";
    private static final Pattern SBI_TXN_LINE_REGEX = Pattern
            // .compile("^(\\d{2}-\\d{2}-\\d{2})\\s+(.*?)\\s+(?:(\\d{6})|(-))\\s+([\\d,.]+)\\s+([\\d,.]+)\\s+([\\d,.]+)$");
            .compile(
                    "^(\\d{2}-\\d{2}-\\d{2})\\s+(.*?)\\s+(?:(\\d{6})|(-)|(?=\\d))\s+([\\d,.]+)\\s+([\\d,.]+)\\s+([\\d,.]+)\\s*$");

    @Override
    public void process(Exchange exchange) throws Exception {
        String rawText = exchange.getIn().getBody(String.class);
        // LOG.debug(rawText);
        List<PDFExtractPayload> allRows = new ArrayList<>();
        List<StatementProcessErrorRow> errorRowsList = new ArrayList<StatementProcessErrorRow>();
        Map<String, List<PDFExtractPayload>> masterDataMap = new HashMap<>();
        Set<String> orderIDSet = new HashSet<>();
        boolean isCaptureMode = false;
        // Regex to find segments starting with Date (e.g., Dec 07, 2025)
        String[] lines = rawText.split("\\n");
        StringBuilder buffer = new StringBuilder();
        String accountType = "";
        String accountNo = "";
        int accountNoIndex = -1;
        PDFExtractPayload payload = new PDFExtractPayload();
        allRows.add(payload);
        for (String rawLine : lines) {
            try {
                rawLine = rawLine.trim();
                if (shouldIgnoreLine(rawLine)) {
                    continue;
                }
                if (ACCOUNT_NO_CHANGE_TIRGGER.equals(rawLine)) {
                    accountNoIndex++;
                    continue;
                }
                if (accountNoIndex > -1) {
                    accountNoIndex++;
                    if (accountNoIndex == 1) {
                        accountType = rawLine;
                    } else if (accountNoIndex == 2) {
                        accountNo = rawLine;
                        accountNoIndex = -1;
                        payload.setSubscriberID(accountNo + " (" + accountType + ")");
                    }
                }
                if ((!rawLine.matches(START_PATTERN) && !isCaptureMode)) {
                    continue;
                }
                if (rawLine.matches(START_PATTERN)) {

                    // If there is something in buffer it means it is from previous transaction
                    // line.
                    if (buffer.length() > 0) {
                        processBuffer(buffer.toString(), payload, errorRowsList);
                        if (payload.getOrderID() == null || payload.getOrderID().isBlank()
                                || "null".equalsIgnoreCase(payload.getOrderID().trim())) {
                            payload.setOrderID(ExpenseUtil.generateOrderId(
                                    ExpenseUtil.convertStringToDate(payload.getTxnDate(), DatePattern.REPORT_DATE),
                                    payload.getAmount()));
                        }
                        if (orderIDSet.contains(payload.getOrderID())) {
                            LOG.warn("Duplicate Order ID found: {} in line: {}", payload.getOrderID(),
                                    buffer.toString());
                            allRows.remove(payload);
                        } else {
                            orderIDSet.add(payload.getOrderID());
                        }
                        buffer.setLength(0);
                        payload = new PDFExtractPayload();
                        payload.setSubscriberID(accountNo + " (" + accountType + ")");
                        allRows.add(payload);
                    }
                    if (rawLine.matches(SBI_TXN_LINE_REGEX.pattern())) {
                        processBuffer(rawLine, payload, errorRowsList);
                        if (payload.getOrderID() == null || payload.getOrderID().isBlank()
                                || "null".equalsIgnoreCase(payload.getOrderID().trim())) {
                            payload.setOrderID(ExpenseUtil.generateOrderId(
                                    ExpenseUtil.convertStringToDate(payload.getTxnDate(), DatePattern.REPORT_DATE),
                                    payload.getAmount()));
                        }
                        if (orderIDSet.contains(payload.getOrderID())) {
                            LOG.warn("Duplicate Order ID found: {} in line: {}", payload.getOrderID(),
                                    buffer.toString());
                            allRows.remove(payload);
                        } else {
                            orderIDSet.add(payload.getOrderID());
                        }
                        // TODO Call to process buffer
                        isCaptureMode = false;
                        buffer.setLength(0);
                        payload = new PDFExtractPayload();
                        payload.setSubscriberID(accountNo + " (" + accountType + ")");
                        allRows.add(payload);
                        continue;
                    } else {
                        isCaptureMode = true;
                        buffer.append(rawLine).append(" ");
                        continue;
                    }

                } else if (isCaptureMode) {
                    if (rawLine.contains(TXN_LINE_END_MARKER)) {
                        processBuffer(buffer.toString(), payload, errorRowsList);
                        if (payload.getOrderID() == null || payload.getOrderID().isBlank()
                                || "null".equalsIgnoreCase(payload.getOrderID().trim())) {
                            payload.setOrderID(ExpenseUtil.generateOrderId(
                                    ExpenseUtil.convertStringToDate(payload.getTxnDate(), DatePattern.REPORT_DATE),
                                    payload.getAmount()));
                        }
                        if (orderIDSet.contains(payload.getOrderID())) {
                            LOG.warn("Duplicate Order ID found: {} in line: {}", payload.getOrderID(),
                                    buffer.toString());
                            allRows.remove(payload);
                        } else {
                            orderIDSet.add(payload.getOrderID());
                        }
                        isCaptureMode = false;
                        buffer.setLength(0);
                        payload = new PDFExtractPayload();
                        payload.setSubscriberID(accountNo + " (" + accountType + ")");
                        allRows.add(payload);
                        continue;
                    }
                    buffer.append(rawLine).append(" ");
                    if (buffer.toString().matches(SBI_TXN_LINE_REGEX.pattern())) {
                        processBuffer(buffer.toString(), payload, errorRowsList);
                        if (payload.getOrderID() == null || payload.getOrderID().isBlank()
                                || "null".equalsIgnoreCase(payload.getOrderID().trim())) {
                            payload.setOrderID(ExpenseUtil.generateOrderId(
                                    ExpenseUtil.convertStringToDate(payload.getTxnDate(), DatePattern.REPORT_DATE),
                                    payload.getAmount()));
                        }
                        if (orderIDSet.contains(payload.getOrderID())) {
                            LOG.warn("Duplicate Order ID found: {} in line: {}", payload.getOrderID(),
                                    buffer.toString());
                            allRows.remove(payload);
                        } else {
                            orderIDSet.add(payload.getOrderID());
                        }
                        isCaptureMode = false;
                        buffer.setLength(0);
                        payload = new PDFExtractPayload();
                        payload.setSubscriberID(accountNo + " (" + accountType + ")");
                        allRows.add(payload);
                        continue;
                    }
                    continue;
                }
            } catch (Exception e) {
                LOG.error("Error processing line: {}", rawLine, e);
                StatementProcessErrorRow errorRow = StatementProcessErrorRow.builder()
                        .date(ExpenseUtil.getCurrentDateTimeString(null))
                        .errorMessage("Exception while processing line: " + e.getMessage())
                        .referenceLine(rawLine)
                        .errorStack(ExpenseUtil.getStackTrace(e))
                        .build();
                errorRowsList.add(errorRow);
            }

        }
        // The Last Payload will always be empty because of the way we are adding
        // payload to list at the end of loop, so removing it.
        if (payload != null && payload.getBodyCleaned() == null) {
            allRows.remove(payload);
        }
        exchange.setProperty(ExpenseUtil.EXCH_PROPERTY_RES_PAYLOAD, allRows.toArray(PDFExtractPayload[]::new));

        exchange.setProperty(ExpenseUtil.EXCH_PROPERTY_RES_ERR_LIST,
                errorRowsList.toArray(StatementProcessErrorRow[]::new));

    }

    private void parseDescription(String description, PDFExtractPayload payload,
            List<StatementProcessErrorRow> errorRowsList) {
        if ("INTEREST CREDIT".equals(description)) {
            payload.setTo("Interest Credit");
            payload.setPaidTo(description);
            return;
        }
        for (Map.Entry<String, Pattern> entry : ExpenseUtil.DESCRIPTION_PATTERN_MAP.entrySet()) {
            Matcher matcher = entry.getValue().matcher(description);
            if (matcher.find()) {
                StringBuilder paidToBuilder = new StringBuilder();
                switch (entry.getKey()) {
                    case "UPI_PATTERN":
                        payload.setTo("UPI");
                        paidToBuilder.setLength(0);
                        for (int i = 3; i <= matcher.groupCount(); i++) {
                            paidToBuilder.append(matcher.group(i)).append(ExpenseUtil.TEXT_DELIM);
                        }
                        payload.setPaidTo(paidToBuilder.toString());
                        payload.setOrderID(matcher.group(2));
                        break;
                    case "NEFT_PATTERN":
                        payload.setPaidTo(matcher.group(3) + ExpenseUtil.TEXT_DELIM + matcher.group(1));
                        payload.setOrderID(matcher.group(2));
                        payload.setTo("NEFT");
                        break;
                    case "IMPS_PATTERN":
                        payload.setPaidTo(matcher.group(2) + ExpenseUtil.TEXT_DELIM + matcher.group(3));
                        payload.setOrderID(matcher.group(1));
                        payload.setTo("IMPS");
                        break;
                    case "CHQ_DESC_PATTERN":
                        payload.setPaidTo(matcher.group(2));
                        payload.setOrderID(matcher.group(1));
                        payload.setTo("CHEQUE");
                        break;
                    case "OTHPOS_PATTERN":
                        payload.setTo("OTHPOS");
                        paidToBuilder.setLength(0);
                        for (int i = 2; i <= matcher.groupCount(); i++) {
                            paidToBuilder.append(matcher.group(i)).append(ExpenseUtil.TEXT_DELIM);
                        }
                        payload.setPaidTo(paidToBuilder.toString());
                        payload.setOrderID(matcher.group(1));
                        break;
                    case "OTHPG_PATTERN":
                        payload.setTo("OTHPG");
                        paidToBuilder.setLength(0);
                        for (int i = 2; i <= matcher.groupCount(); i++) {
                            paidToBuilder.append(matcher.group(i)).append(ExpenseUtil.TEXT_DELIM);
                        }
                        payload.setPaidTo(paidToBuilder.toString());
                        payload.setOrderID(matcher.group(1));
                        break;
                    case "SBI_INTERNAL_PATTERN":
                        payload.setTo(matcher.group(1));
                        for (int i = 3; i <= matcher.groupCount(); i++) {
                            paidToBuilder.append(matcher.group(i)).append(ExpenseUtil.TEXT_DELIM);
                        }
                        payload.setPaidTo(paidToBuilder.toString());
                        payload.setOrderID(matcher.group(2));
                        break;
                    case "APY_TXN_PATTERN":
                        payload.setTo("APY");
                        paidToBuilder.setLength(0);
                        for (int i = 3; i <= matcher.groupCount(); i++) {
                            paidToBuilder.append(matcher.group(i)).append(ExpenseUtil.TEXT_DELIM);
                        }
                        payload.setPaidTo(paidToBuilder.toString());
                        payload.setOrderID(matcher.group(4));
                        break;
                    default:
                        StatementProcessErrorRow errorRow = StatementProcessErrorRow.builder()
                                .date(ExpenseUtil.getCurrentDateTimeString(null))
                                .errorMessage("Description does not match any known pattern: " + description)
                                .referenceLine(payload.toString())
                                .build();
                        errorRowsList.add(errorRow);
                        break;
                }
                return; // Exit after first match
            }
        }
    }

    private int processBuffer(String txnLine, PDFExtractPayload payload, List<StatementProcessErrorRow> errorRowsList) {
        payload.setBodyCleaned(txnLine);
        Matcher matcher = SBI_TXN_LINE_REGEX.matcher(txnLine);
        if (matcher.find()) {
            var inputDate = matcher.group(1);
            if (inputDate != null && !inputDate.isBlank()) {
                payload.setTxnDate(ExpenseUtil.convertStrngDateFormat(inputDate, DatePattern.SBI_SAC_DATE,
                        DatePattern.REPORT_DATE));
            } else {
                LOG.error("Date is empty in line: {}", txnLine);
                StatementProcessErrorRow errorRow = StatementProcessErrorRow.builder()
                        .date(ExpenseUtil.getCurrentDateTimeString(null))
                        .errorMessage("Invalid Transaction Date :" + inputDate)
                        .referenceLine(txnLine)
                        .build();
                errorRowsList.add(errorRow);
                return -1;
            }
            var creditAmount = matcher.group(5);
            var debitAmount = matcher.group(6);
            if ((creditAmount == null || "0".equals(creditAmount.trim()))) {
                payload.setAmount(debitAmount.trim().replaceAll(",", ""));
                payload.setTransactionFlag("DEBIT");
            } else {
                payload.setAmount(creditAmount.trim().replaceAll(",", ""));
                payload.setTransactionFlag("CREDIT");
            }
            // Defaulting order id to Cheque no & Subject to description which will be
            // overwritten later.
            payload.setOrderID(matcher.group(3));
            String description = matcher.group(2);
            payload.setSubject(description);
            if (description != null && !description.isBlank()) {
                parseDescription(description, payload, errorRowsList);
            } else {
                LOG.error("Description is empty in line: {}", txnLine);
                StatementProcessErrorRow errorRow = StatementProcessErrorRow.builder()
                        .date(ExpenseUtil.getCurrentDateTimeString(null))
                        .errorMessage("Description is empty in line")
                        .referenceLine(txnLine)
                        .build();
                errorRowsList.add(errorRow);
            }
            return 0;
        } else {
            LOG.error("Line did not match expected format: {}", txnLine);
            StatementProcessErrorRow errorRow = StatementProcessErrorRow.builder()
                    .date(ExpenseUtil.getCurrentDateTimeString(null))
                    .errorMessage("Line did not match expected SBI format")
                    .referenceLine(txnLine)
                    .build();
            errorRowsList.add(errorRow);
            return -1;
        }

    }

    private boolean shouldIgnoreLine(String line) {
        for (String ignoreText : IGNORE_TEXT_ARRAY) {
            if (line.contains(ignoreText)) {
                return true;
            }
        }
        return false;
    }
}