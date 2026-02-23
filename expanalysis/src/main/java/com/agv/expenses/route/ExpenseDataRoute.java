package com.agv.expenses.route;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.PropertyInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.StandardDecryptionMaterial;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.agv.expenses.processor.GoogleSheetsWriteProcessor;
import com.agv.expenses.processor.ICICISacStmtPDFProcessor;
import com.agv.expenses.processor.ICICITransactionProcessor;
import com.agv.expenses.processor.PhonePePDFProcessor;
import com.agv.expenses.processor.PhonePePDFProcessorGSheets;
import com.agv.expenses.processor.SBIPDFStatementProcessor;
import com.agv.expenses.service.model.Health;
import com.agv.expenses.service.model.PDFExtractPayload;
import com.agv.expenses.service.model.StatementProcessRequest;
import com.agv.expenses.service.model.StatementProcessResponse;
import com.agv.expenses.util.ExpenseUtil;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.api.services.drive.Drive;

@Component
public class ExpenseDataRoute extends RouteBuilder {
        @Value("${my.spreadsheet.id}")
        private String spreadsheetId;
        @PropertyInject("sbi.pdf.password")
        private String sbiPDFPassword;
        @PropertyInject("icici.pdf.password")
        private String iciciPDFPassword;
        @Autowired
        private Drive driveService;        
        @Autowired
        private ICICITransactionProcessor iciciTransactionProcessor;
        @Autowired
        private SBIPDFStatementProcessor sbiPDFStatementProcessor;
        @Autowired
        private ICICISacStmtPDFProcessor iciciPDFStmtProcessor;
        private static final Logger LOG = LoggerFactory.getLogger(ExpenseDataRoute.class);
        @Override
        public void configure() throws Exception {
                /** ############### REST Configurations ########################## */
                // Configure REST to use JSON (Jackson)
                restConfiguration()
                                .component("servlet")
                                .bindingMode(RestBindingMode.json)
                                .contextPath("/expense");

                // Define REST endpoint
                rest("/api")
                                .post("/processMessage")
                                .type(StatementProcessRequest.class) // incoming JSON → Request
                                .outType(StatementProcessResponse.class) // outgoing Response → JSON
                                .to("direct:processRequest");

                // Extended Health endpoint
                rest("/api")
                                .get("/health")
                                .to("direct:health");

                from("direct:health")
                                .process(e -> {
                                        var ctx = e.getContext();
                                        boolean camelUp = ctx != null && ctx.isStarted();
                                        e.getMessage().setHeader(org.apache.camel.Exchange.CONTENT_TYPE,
                                                        "application/json");
                                        e.getMessage().setBody(Health.builder().status("UP")
                                                        .camel(camelUp ? "STARTED" : "STOPPED").build());
                                });

                // Actual processor route
                from("direct:processRequest")
                        .doTry()
                                .process(exchange -> {
                                        StatementProcessRequest req = exchange.getIn()
                                                        .getBody(StatementProcessRequest.class);
                                        exchange.getIn().setHeader(ExpenseUtil.EXCH_HEADER_PROPERTY_PDF_FILE_ID,
                                                        req.getFileId());
                                })
                                .log("${header.CamelGoogleDrive.fileId}")
                                // Step 2: Call Google Drive to get the file
                                // 'drive-files/get' fetches the file metadata and content
                                .to("google-drive://drive-files/get?scopes=https://www.googleapis.com/auth/drive.readonly")
                                .process(exchange -> {
                                        // After the .to(), the body contains the File Metadata. 
                                        // To get the media, we access the underlying Google SDK Request object.
                                        com.google.api.services.drive.model.File fileMetadata = exchange.getIn().getBody(com.google.api.services.drive.model.File.class);

                                        // Execute the download
                                        try (java.io.InputStream is = driveService.files().get(fileMetadata.getId()).executeMediaAsInputStream()) {
                                        byte[] pdfBytes = is.readAllBytes();

                                        // Step 4: Parse PDF using PDFBox
                                        try (PDDocument document = Loader.loadPDF(pdfBytes, iciciPDFPassword)) {
                                                        PDFTextStripper stripper = new PDFTextStripper();
                                                        String text = stripper.getText(document);
                                                        exchange.getIn().setBody(text);
                                                }
                                        }
                                })
                                .process(iciciPDFStmtProcessor)
                                .process(exchange -> {
                                        StatementProcessRequest req = exchange.getIn()
                                                        .getBody(StatementProcessRequest.class);
                                        StatementProcessResponse resp = StatementProcessResponse.builder()
                                                        .status("Success")
                                                        .responsePayLoad((PDFExtractPayload[]) exchange.getProperty(
                                                                        ExpenseUtil.EXCH_PROPERTY_RES_PAYLOAD))
                                                        .build();
                                        //LOG.debug(resp.toString());
                                        exchange.getMessage().setBody(resp);
                                })
                        .doCatch(Exception.class)
                                .process(exchange -> {
                                        // 1. Get the exception
                                        Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT,
                                                        Exception.class);

                                        // 2. Build the stack trace string
                                        java.io.StringWriter sw = new java.io.StringWriter();
                                        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
                                        cause.printStackTrace(pw);
                                        String stackTrace = sw.toString();

                                        // 3. Create the single error response object
                                        StatementProcessResponse error = new StatementProcessResponse();
                                        error.setStatus("Failed");
                                        error.setErrorMessage(cause.getMessage());
                                        error.setExceptionStack(stackTrace);

                                        // 4. Wrap in a List if your API outType expects a List
                                        // If outType is just StatementProcessResponse.class, remove the List.of
                                        exchange.getIn().setBody(java.util.List.of(error));

                                        // 5. Set HTTP status to 500
                                        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 500);
                                })
                        .log("Extracted Body: ${body}")
                        .end(); // End of doTry-doCatch block

                /** ############### END of REST Configurations ########################## */
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
