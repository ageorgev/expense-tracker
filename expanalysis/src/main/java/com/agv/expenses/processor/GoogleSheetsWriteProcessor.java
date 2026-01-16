package com.agv.expenses.processor;

import java.util.List;
import java.util.Map;

import com.google.api.services.sheets.v4.model.ValueRange;

public class GoogleSheetsWriteProcessor implements org.apache.camel.Processor {
    
    
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(GoogleSheetsWriteProcessor.class);

    @Override
    public void process(org.apache.camel.Exchange exchange) throws Exception {
        Map<String,List<Object>> masterDataMap =  (Map<String, List<Object>>) exchange.getProperty("MASTER_DATA_MAP");
        /* 
        for(List<Object> rowData : masterDataMap.values()){
            if(!"alerts@phonepe.com".equals(rowData.get(1))){
                LOG.info("Skipping PhonePe alert row for To: {}", rowData.get(2));
            }else{
                LOG.info("Processing PhonePe alert row for To: {}", rowData.get(2));
            }
            LOG.info("Key: {}, Value: ", rowData.get(5));
        } */
        if(masterDataMap!=null){
            // 2. Wrap it in the official Google ValueRange object
            ValueRange gsheetsData = new ValueRange();
            gsheetsData.setValues(masterDataMap.values().stream().toList());
            exchange.getIn().setHeader("CamelGoogleSheets.spreadsheetId", "1vNudr4ijhD8bo_NvCnphnIoiKRz3zjW-FTGVcK45a9c");
            exchange.getIn().setHeader("CamelGoogleSheets.range", "Sheet1!A1");
            exchange.getIn().setHeader("CamelGoogleSheets.values", gsheetsData);
            exchange.getIn().setBody(gsheetsData);
        }else{
            LOG.warn("MASTER_DATA_MAP not found in exchange properties.");
        }
    }
    
}
