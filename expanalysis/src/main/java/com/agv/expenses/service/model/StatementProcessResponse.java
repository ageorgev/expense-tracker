package com.agv.expenses.service.model;

import java.io.Serializable;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class StatementProcessResponse implements Serializable {

    private String status;
    private String errorMessage;
    private String exceptionStack;
    private PDFExtractPayload[] responsePayLoad;
    
}
