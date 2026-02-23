package com.agv.expenses.service.model;

import java.io.Serializable;

import com.agv.expenses.util.DatePattern;
import com.agv.expenses.util.ExpenseUtil;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class PDFExtractPayload implements Serializable {
    private String txnDate;
    private String from;
    private String to;
    private String subject;
    private String paidTo;
    private String amount;
    private String bodyCleaned;
    private String subscriberID;
    private String orderID;
    private String link;
    private String message;
    private String transactionFlag;

    public void validate() {
        if (orderID == null || orderID.isBlank()) {
            orderID = ExpenseUtil.generateOrderId(
                    ExpenseUtil.convertStringToDate(txnDate, DatePattern.REPORT_DATE),
                    amount);
        }
    }
}
