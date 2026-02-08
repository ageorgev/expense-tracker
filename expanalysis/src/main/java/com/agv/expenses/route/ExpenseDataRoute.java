package com.agv.expenses.route;

import java.util.List;

import org.apache.camel.PropertyInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.StandardDecryptionMaterial;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.agv.expenses.processor.GoogleSheetsWriteProcessor;
import com.agv.expenses.processor.ICICISacStmtPDFProcessor;
import com.agv.expenses.processor.ICICITransactionProcessor;
import com.agv.expenses.processor.PhonePePDFProcessor;
import com.agv.expenses.processor.PhonePePDFProcessorGSheets;
import com.agv.expenses.processor.SBIPDFStatementProcessor;
import com.google.api.services.sheets.v4.model.ValueRange;

@Component
public class ExpenseDataRoute extends RouteBuilder {
        @Value("${my.spreadsheet.id}")
        private String spreadsheetId;
        @PropertyInject("sbi.pdf.password")
        private String sbiPDFPassword;
        @PropertyInject("icici.pdf.password")
        private String iciciPDFPassword;
        @Autowired
        private ICICITransactionProcessor iciciTransactionProcessor;
        @Autowired
        private SBIPDFStatementProcessor sbiPDFStatementProcessor;
        @Autowired
        private ICICISacStmtPDFProcessor iciciPDFStmtProcessor;

        @Override
        public void configure() throws Exception {
                from("file:{{data.input.folder}}?include=.*StmtPDF.pdf&noop=true")
                                .log("Reading file: ${header.CamelFileName}")
                                .convertBodyTo(byte[].class)
                                .to("pdf:extractText")
                                // .log("Extracted Body: ${body}")
                                .process(new PhonePePDFProcessorGSheets())
                                // .marshal().csv() // Converts List<List<String>> to CSV format
                                // .to("file:C:/Anish/Temp/output?fileName=statement.csv")
                                // .log("Finished Extraction");
                                .to("direct:processSBIPDFTransactions")
                                .process(new GoogleSheetsWriteProcessor())
                                // Writing to Google Sheets
                                // .to("google-sheets://data/append?valueInputOption=USER_ENTERED")
                                .log("Data successfully appended to Google Sheets!");

                from("direct:processSanthomeTransactions")
                                .pollEnrich()
                                .simple("file:{{data.input.folder}}?fileName={{santhome.excel.file.name}}&noop=true")
                                .process(iciciTransactionProcessor)
                                .log("Successfully unmarshalled Excel workbook!");

                from("direct:processSBIPDFTransactions")
                                // .setHeader("CamelPdfDecryptionMaterial", constant(new
                                // StandardDecryptionMaterial("42715220579")))
                                .pollEnrich().simple("file:{{data.input.folder}}?fileName=SBI_Statement.pdf&noop=true")
                                // .setHeader("CamelPdfPassword", constant("42715220579"))
                                .process(exchange -> {
                                        byte[] pdfBytes = exchange.getIn().getBody(byte[].class);
                                        try (PDDocument document = Loader.loadPDF(pdfBytes, sbiPDFPassword)) {
                                                PDFTextStripper stripper = new PDFTextStripper();
                                                String text = stripper.getText(document);
                                                exchange.getIn().setBody(text);
                                        }
                                })
                                // .log("${body}")
                                // .convertBodyTo(byte[].class)
                                // .to("pdf:extractText")
                                // .log("Extracted Body: ${body}")
                                .process(sbiPDFStatementProcessor)
                                .log("Successfully unmarshalled Excel workbook!");

                from("file:{{data.input.folder}}?include={{icici.pdf.file.pattern}}&noop=true")
                                .log("Reading file: ${header.CamelFileName}")
                                .process(exchange -> {
                                        byte[] pdfBytes = exchange.getIn().getBody(byte[].class);
                                        try (PDDocument document = Loader.loadPDF(pdfBytes, iciciPDFPassword)) {
                                                PDFTextStripper stripper = new PDFTextStripper();
                                                String text = stripper.getText(document);
                                                exchange.getIn().setBody(text);
                                        }
                                })
                                .process(iciciPDFStmtProcessor)
                                .log("Extracted Body: ${body}");
        }
}
