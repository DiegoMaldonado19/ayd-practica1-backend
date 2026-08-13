package com.fitness.app.report;

import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

/**
 * Converts a report to RFC 4180 compliant CSV format.
 * Column names and cell order come from {@link ReportTable}.
 */
@Component
public class ReportCsv
{
    public String toCsv(ReportTable table)
    {
        if (table.isEmpty())
        {
            return "";
        }

        var stringWriter = new StringWriter();
        var writer       = new PrintWriter(stringWriter);

        writer.println(toLine(table.headers()));

        for (var row : table.rows())
        {
            writer.println(toLine(row));
        }

        writer.flush();
        return stringWriter.toString();
    }

    private String toLine(List<?> values)
    {
        return String.join(",", values.stream()
                .map(this::escapeCsvValue)
                .toList());
    }

    private String escapeCsvValue(Object value)
    {
        if (value == null)
        {
            return "";
        }

        var strValue = value.toString();

        // RFC 4180: quote if contains comma, quote, or newline
        if (strValue.contains(",") || strValue.contains("\"") || strValue.contains("\n"))
        {
            // Escape internal quotes by doubling them
            strValue = strValue.replace("\"", "\"\"");
            return "\"" + strValue + "\"";
        }

        return strValue;
    }
}
