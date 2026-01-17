package com.agv.expenses.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.math3.analysis.function.Exp;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.pulsar.PulsarProperties.Transaction;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.agv.expenses.util.ExpenseUtil;
import com.google.api.services.sheets.v4.model.ValueRange;

@Component
public class SBIPDFStatementProcessor implements Processor {

    // Standard SLF4J Logger
    private static final Logger LOG = LoggerFactory.getLogger(SBIPDFStatementProcessor.class);
    // Regex for extraction:
    // 1. (\\d{2}-\\d{2}-\\d{2}) -> The Date
    // 2. \\s+ -> Space
    // 3. (.*?) -> The Description (lazy match)
    // 4. \\s+-\\s+.*?-\\s+ -> Skip through the first and second " - "
    // 5. ([\\d\\.]+) -> Capture the number after that second " - "
    private static final String EXTRACTION_REGEX = "^(\\d{2}-\\d{2}-\\d{2})\\s+(.*?)\\s+-\\s+.*?-\\s+([\\d\\.]+)";
    private static final Pattern EXTRACTION_PATTERN = Pattern.compile(EXTRACTION_REGEX, Pattern.DOTALL);
    Pattern DEBIT_PATTERN = Pattern.compile("(?<= - - )([^ ]+)");
    // Validation: Date + Space + Alphabet
    private static final String START_PATTERN = "^\\d{2}-\\d{2}-\\d{2}\\s+[A-Za-z].*";

    @Override
    public void process(Exchange exchange) throws Exception {
        String rawText = exchange.getIn().getBody(String.class);
        // LOG.debug(rawText);
        List<String[]> allRows = new ArrayList<>();
        Map<String, List<Object>> masterDataMap = new HashMap<>();
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

        boolean isCaptureMode = false;
        // Regex to find segments starting with Date (e.g., Dec 07, 2025)
        String[] lines = rawText.split("\\n");
        StringBuffer rawMsgString = null;
        for (String rawLine : lines) {
            rawLine = rawLine.trim();
            if ((!rawLine.matches(START_PATTERN) && !isCaptureMode)) {
                continue;
            }
            if (rawLine.matches(START_PATTERN)) {
                if (rawMsgString != null && rawMsgString.length() > 0) {
                    // Process previous buffer
                    allRows.add(processBuffer(rawMsgString.toString()));
                    rawMsgString = null;
                    isCaptureMode = false;
                }
                int count = StringUtils.countOccurrencesOf(rawLine, " - ");
                if (count < 2) {
                    rawMsgString = new StringBuffer();
                    rawMsgString.append(rawLine).append(" ");
                    isCaptureMode = true;
                    continue;
                } else {
                    allRows.add(processBuffer(rawLine));
                    rawMsgString = null;
                    isCaptureMode = false;
                }
            } else if (isCaptureMode) {
                rawMsgString.append(rawLine).append(" ");
                if (StringUtils.countOccurrencesOf(rawMsgString.toString(), " - ") > 1) {
                    allRows.add(processBuffer(rawMsgString.toString()));
                    rawMsgString = null;
                    isCaptureMode = false;
                }
            }

        }
        // TODO Adding last row
        exchange.setProperty("MASTER_DATA_MAP", masterDataMap);

    }

    private String[] processBuffer(String fullRecord) {
        String[] txnRowArr = new String[4];
        /*
         * Matcher matcher = EXTRACTION_PATTERN.matcher(fullRecord);
         * 
         * if (matcher.find()) {
         * txnRowArr[0]=matcher.group(1); // Date
         * txnRowArr[1]=matcher.group(2).trim(); // Description (everything before first
         * " - ")
         * txnRowArr[2]=matcher.group(3); // Number after
         * }
         */
        // 1. Extract the Date and the "Rest of the line"
        // Using limit 2 to separate the date from the rest
        String[] dateSplit = fullRecord.split("\\s+", 2);
        if (dateSplit.length < 2)
            return txnRowArr;

        String date = dateSplit[0];
        String remaining = dateSplit[1];

        // 2. Split the remaining text by the " - " token
        // We use -1 to ensure we don't discard empty trailing strings
        String[] parts = remaining.split("\\s+-\\s+", -1);

        // parts[0] = Description
        // If credit: parts[1] = Credit Amount, parts[2] = Balance
        // If debit: parts[1] = empty (placeholder), parts[2] = Debit Amount + Balance

        String description = parts[0].trim();
        String amount = "0.00";
        String type = "UNKNOWN";

        Matcher matcher = DEBIT_PATTERN.matcher(remaining);

        if (matcher.find()) {
            amount = matcher.group(1);
            type = "DEBIT";
        } else if (parts.length >= 2) {
            if (!parts[1].trim().isEmpty() && !parts[1].trim().equals("-")) {
                // Case: Date Desc - Credit - Balance
                amount = parts[1].trim();
                type = "CREDIT";
            }

        }
        txnRowArr[0] = date;
        txnRowArr[1] = description;
        txnRowArr[2] = amount;
        txnRowArr[3] = type;
        return txnRowArr;
    }
}