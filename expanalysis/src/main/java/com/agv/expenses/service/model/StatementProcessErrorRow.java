package com.agv.expenses.service.model;

import java.io.Serializable;

@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.Data
@lombok.Builder
public class StatementProcessErrorRow implements Serializable {
    private String date;
    private String messageId;
    private String errorMessage;
    private String referenceLine;
    private String errorStack;
}
