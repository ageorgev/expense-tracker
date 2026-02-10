package com.agv.expenses.service.model;

import java.io.Serializable;

@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.Data
@lombok.Builder
public class StatementProcessRequest implements Serializable{
    
    private String messageId;
    private String filePath;

}
