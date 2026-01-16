package com.agv.expenses.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PhonePePDFProcessor implements Processor {
    // Standard SLF4J Logger
    private static final Logger LOG = LoggerFactory.getLogger(PhonePePDFProcessor.class);
    private static final String DATE_REGEX="^[A-Z][a-z]{2}\\s\\d{2},\\s\\d{4}$";
    //private static final Pattern amount_pattern = Pattern.compile("(?<=DEBIT [?₹])\\s*(\\d+)(?=\\s*Paid to)");
    private static final Pattern amount_pattern = Pattern.compile("(?<=DEBIT [?₹])\\s*([\\d,.]+)(?=\\s*Paid to)");
    private static final Pattern paid_to_pattern = Pattern.compile("(?<=Paid to\s).+$");
    private static final Pattern txn_id_pattern = Pattern.compile("(?<=Transaction ID\s).+$");
    private static final Pattern utr_no_pattern = Pattern.compile("(?<=UTR No.\s).+$");
    private static final Pattern paid_by_pattern = Pattern.compile("(?<=Paid by\s).+$");
    @Override
    public void process(Exchange exchange) throws Exception {
        String rawText = exchange.getIn().getBody(String.class);
        //LOG.debug(rawText);
        List<List<String>> csvData = new ArrayList<>();
        /**
         *  Index position and corresponding values
         *  0. Date
            1. From
            2. To
            3. Subject
            4. Paid To
            5. Amount
            6. Body Cleaned
            7. Subscriber ID
            8. Order ID
            9. Link
            10. Message
         */
        String[] rowDataArr=null;
        
        // Add Header
        csvData.add(Arrays.asList("Date","From","To", "Subject", "Paid To", "Amount", "Body Cleaned", "Subscriber ID", "Order ID", "Link", "Message"));

        // Regex to find segments starting with Date (e.g., Dec 07, 2025)
        String[] lines = rawText.split("\\n");
        StringBuffer rawMsgString=new StringBuffer();
        for (String rawLine: lines){
            rawLine=rawLine.trim();
            //LOG.debug(rawLine);
            rawMsgString.append(rawLine).append(" ");
            if(rawLine.matches(DATE_REGEX)){
                //Before entering a new row if a previous row exists, add to list
                if(rowDataArr!=null){
                    rowDataArr[6]=rawMsgString.toString();
                    csvData.add(Arrays.asList(rowDataArr));
                }
                //Date comes when a new row starts. Hence initializing the array
                rowDataArr=new String[11];
                rawMsgString=new StringBuffer();
                rawMsgString.append(rawLine).append(" ");
                try{
                    rowDataArr[0]=
                        LocalDate.parse(rawLine, 
                                DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH))
                                .format(DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
                } catch (DateTimeParseException e) {
                    LOG.debug("Exception "+e.getMessage()+" while parsing "+rawLine);
                    rowDataArr[0]=rawLine;
                }
                rowDataArr[1]="alerts@phonepe.com";
                rowDataArr[2]="ageorgev@gmail.com";
            }else if (rawLine.startsWith("DEBIT ")){
                Matcher matcher = amount_pattern.matcher(rawLine);
                if (matcher.find()) {
                   rowDataArr[5] = matcher.group().trim();
                }
                matcher = paid_to_pattern.matcher(rawLine);
                if (matcher.find()) {
                   rowDataArr[4] = matcher.group().trim();
                }
            }else if (rawLine.startsWith("Transaction ID ")){
                Matcher matcher = txn_id_pattern.matcher(rawLine);
                if (matcher.find()) {
                   rowDataArr[8] = matcher.group().trim();
                }
            }else if (rawLine.startsWith("UTR No. ")){
                Matcher matcher = utr_no_pattern.matcher(rawLine);
                if (matcher.find()) {
                   rowDataArr[10] = matcher.group().trim();
                }
            }else if (rawLine.startsWith("Paid by ")){
                Matcher matcher = paid_by_pattern.matcher(rawLine);
                if (matcher.find()) {
                   rowDataArr[7] = matcher.group().trim();
                }
            }else if (rowDataArr!=null && rowDataArr[5]!=null && rowDataArr[8]==null){
                rowDataArr[4]=(rowDataArr[4]==null?"":(rowDataArr[4]+" "))+rawLine;
            }

        }
        //Adding last row
        if(rowDataArr!=null){
            rowDataArr[6]=rawMsgString.toString();
            csvData.add(Arrays.asList(rowDataArr));
        }

        exchange.getIn().setBody(csvData);
    }
}