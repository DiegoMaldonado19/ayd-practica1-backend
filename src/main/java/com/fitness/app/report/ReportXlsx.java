package com.fitness.app.report;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Renders a report as a single-sheet .xlsx workbook.
 *
 * Numbers are written as numeric cells and not as text, so the sheet can be summed
 * and sorted in Excel; that is the whole point of exporting to Excel instead of CSV.
 */
@Component
public class ReportXlsx
{
    public byte[] render(ReportTable table, String sheetName)
    {
        try (var workbook = new XSSFWorkbook();
             var out      = new ByteArrayOutputStream())
        {
            var sheet      = workbook.createSheet(sheetName);
            var headerFont = workbook.createFont();
            var headerBold = workbook.createCellStyle();

            headerFont.setBold(true);
            headerBold.setFont(headerFont);

            var headerRow = sheet.createRow(0);

            for (var column = 0; column < table.headers().size(); column++)
            {
                var cell = headerRow.createCell(column);

                cell.setCellValue(table.headers().get(column));
                cell.setCellStyle(headerBold);
            }

            for (var index = 0; index < table.rows().size(); index++)
            {
                var row    = sheet.createRow(index + 1);
                var values = table.rows().get(index);

                for (var column = 0; column < values.size(); column++)
                {
                    write(row.createCell(column), values.get(column));
                }
            }

            workbook.write(out);

            return out.toByteArray();
        }
        catch (IOException ex)
        {
            // ByteArrayOutputStream does not do I/O; this cannot happen in practice.
            throw new UncheckedIOException(ex);
        }
    }

    private void write(Cell cell, Object value)
    {
        switch (value)
        {
            case null            -> cell.setBlank();
            case Number number   -> cell.setCellValue(number.doubleValue());
            case Boolean flag    -> cell.setCellValue(flag);
            default              -> cell.setCellValue(value.toString());
        }
    }
}
