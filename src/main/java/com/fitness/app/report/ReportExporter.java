package com.fitness.app.report;

import com.fitness.app.report.dto.ReportFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Turns the rows of any report into the response for the requested format.
 *
 * The nine endpoints all funnel through here, so a new format costs one enum constant
 * and one branch: nothing per report. The switch is exhaustive over ReportFormat, so
 * forgetting a format is a compile error rather than a 500 at runtime.
 */
@Component
@RequiredArgsConstructor
public class ReportExporter
{
    // The application mapper, not a bare one: it already knows java.time, so an exported
    // cell reads exactly like the same field in the JSON response.
    private final ObjectMapper objectMapper;

    private final ReportCsv  reportCsv;
    private final ReportXlsx reportXlsx;
    private final ReportPdf  reportPdf;
    private final ReportPng  reportPng;

    public ResponseEntity<?> export(List<?> rows, ReportFormat format, String fileName)
    {
        return switch (format)
        {
            case JSON -> ResponseEntity.ok(rows);
            case CSV  -> download(reportCsv.toCsv(table(rows)).getBytes(StandardCharsets.UTF_8),
                                  "text/csv; charset=UTF-8", fileName, "csv");
            case XLSX -> download(reportXlsx.render(table(rows), fileName),
                                  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                  fileName, "xlsx");
            case PDF  -> download(reportPdf.render(table(rows), fileName), "application/pdf", fileName, "pdf");
            case PNG  -> download(reportPng.render(table(rows), fileName), "image/png", fileName, "png");
        };
    }

    private ReportTable table(List<?> rows)
    {
        return ReportTable.of(objectMapper, rows);
    }

    private ResponseEntity<byte[]> download(byte[] body, String contentType, String fileName, String extension)
    {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "." + extension + "\"")
                .body(body);
    }
}
