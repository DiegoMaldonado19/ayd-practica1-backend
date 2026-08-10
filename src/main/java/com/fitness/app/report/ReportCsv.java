package com.fitness.app.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

/**
 * Converts a list of report rows to RFC 4180 compliant CSV format.
 * Headers are derived from JSON property names via ObjectMapper.
 */
@Component
public class ReportCsv
{
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public String toCsv(List<?> rows)
    {
        if (rows.isEmpty())
        {
            return "";
        }

        var stringWriter = new StringWriter();
        var writer = new PrintWriter(stringWriter);

        var firstRow = objectMapper.convertValue(rows.get(0), Map.class);
        var headers = firstRow.keySet();

        // Write headers
        var headerLine = String.join(",", headers.stream()
                .map(this::escapeCsvValue)
                .toList());
        writer.println(headerLine);

        // Write data rows
        for (var row : rows)
        {
            var map = objectMapper.convertValue(row, Map.class);
            var values = headers.stream()
                    .map(header -> map.get(header))
                    .map(this::escapeCsvValue)
                    .toList();
            var dataLine = String.join(",", values);
            writer.println(dataLine);
        }

        writer.flush();
        return stringWriter.toString();
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
