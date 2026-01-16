package com.agv.expenses.processor;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.pdfbox.pdmodel.PDDocument;

//import technology.tabula.*;
//import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;

public class TabulaPhonePePDFProcessor implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }
    /*
    @Override
    public void process(Exchange exchange) throws Exception {
        byte[] pdfBytes = exchange.getIn().getBody(byte[].class);
        
        try (PDDocument document = PDDocument.load(pdfBytes)) {
            ObjectExtractor oe = new ObjectExtractor(document);
            SpreadsheetExtractionAlgorithm sea = new SpreadsheetExtractionAlgorithm(); 
            
            Page page = oe.extract(1); // Extract page 1
            List<Table> tables = sea.extract(page);
            
            List<List<String>> csvData = new ArrayList<>();
            
            for (Table table : tables) {
                for (List<RectangularTextContainer> row : table.getRows()) {
                    List<String> cells = new ArrayList<>();
                    for (RectangularTextContainer cell : row) {
                        cells.add(cell.getText());
                    }
                    csvData.add(cells);
                }
            }
            exchange.getIn().setBody(csvData);
        }
    } */
    
}
