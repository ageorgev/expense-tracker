package com.agv.expenses.service.model;

import java.io.Serializable;

@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.Data
@lombok.Builder
public class Health implements Serializable {
    private String status;
    private String camel;
}
