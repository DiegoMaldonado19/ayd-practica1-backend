package com.fitness.app.report;

import com.fitness.app.report.dto.ReportFormat;
import com.fitness.app.report.dto.RevenueResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The four renderers share one flattening step, so one report exercises all of them.
 * Each format is checked by its magic number: a file that does not start with its own
 * signature will not open, whatever the HTTP status said.
 */
class ReportExportTest
{
    // Same naming strategy as application.yml, so headers come out snake_case here too.
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    private final ReportExporter exporter = new ReportExporter(objectMapper,
                                                               new ReportCsv(),
                                                               new ReportXlsx(),
                                                               new ReportPdf(),
                                                               new ReportPng());

    private static final List<RevenueResponse> ROWS = List.of(
            new RevenueResponse(LocalDate.of(2026, 8, 1), "Plan Elite",
                                new BigDecimal("1200.00"), new BigDecimal("200.00"), new BigDecimal("1000.00")),
            new RevenueResponse(LocalDate.of(2026, 9, 1), "Plan Basico, mensual",
                                new BigDecimal("300.00"), BigDecimal.ZERO, new BigDecimal("300.00")));

    @Test
    void flattensTheReportIntoHeadersAndCells()
    {
        var table = ReportTable.of(objectMapper, ROWS);

        assertEquals(List.of("period", "plan_name", "gross_amount", "discount_amount", "net_amount"),
                     table.headers());
        assertEquals(2, table.rows().size());
        assertEquals("Plan Elite", ReportTable.text(table.rows().get(0).get(1)));
    }

    @Test
    void quotesTheCellThatCarriesASeparator()
    {
        var csv = new ReportCsv().toCsv(ReportTable.of(objectMapper, ROWS)).lines().toList();

        assertEquals("period,plan_name,gross_amount,discount_amount,net_amount", csv.get(0));
        assertTrue(csv.get(2).contains("\"Plan Basico, mensual\""),
                   "una celda con coma debe salir entrecomillada: " + csv.get(2));
    }

    @Test
    void everyBinaryFormatWritesItsOwnSignature()
    {
        assertSignature(ReportFormat.XLSX, new byte[] {'P', 'K', 3, 4});
        assertSignature(ReportFormat.PDF,  new byte[] {'%', 'P', 'D', 'F'});
        assertSignature(ReportFormat.PNG,  new byte[] {(byte) 0x89, 'P', 'N', 'G'});
    }

    @Test
    void anEmptyReportStillProducesAFileInEveryFormat()
    {
        for (var format : ReportFormat.values())
        {
            var body = exporter.export(List.of(), format, "revenue").getBody();

            assertTrue(body != null, "el formato " + format + " no devolvió cuerpo");
        }
    }

    @Test
    void theNumericCellsTravelAsNumbersAndNotAsText()
    {
        var table = ReportTable.of(objectMapper, ROWS);

        assertTrue(table.rows().get(0).get(2) instanceof Number,
                   "gross_amount debe llegar al exportador como número, no como texto, "
                 + "para que la celda de Excel se pueda sumar; llegó como "
                 + table.rows().get(0).get(2).getClass());
    }

    private void assertSignature(ReportFormat format, byte[] expected)
    {
        var body = (byte[]) exporter.export(ROWS, format, "revenue").getBody();
        var head = new byte[expected.length];

        System.arraycopy(body, 0, head, 0, expected.length);

        assertArrayEquals(expected, head, "firma inesperada para " + format);
    }
}
