package com.fitness.app.report;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

/**
 * Converts a list of report rows to RFC 4180 compliant CSV format.
 * Headers are derived from JSON property names via ObjectMapper.
 */
@Component
@RequiredArgsConstructor
public class ReportCsv
{
    private static final TypeReference<Map<String, Object>> ROW_TYPE = new TypeReference<>() {};

    // The application mapper, not a bare one: it already knows java.time, so a CSV
    // cell reads exactly like the same field in the JSON response.
    private final ObjectMapper objectMapper;

    public String toCsv(List<?> rows)
    {
        if (rows.isEmpty())
        {
            return "";
        }

        var stringWriter = new StringWriter();
        var writer = new PrintWriter(stringWriter);

        var firstRow = objectMapper.convertValue(rows.get(0), ROW_TYPE);
        var headers = firstRow.keySet();

        // Write headers
        var headerLine = String.join(",", headers.stream()
                .map(this::escapeCsvValue)
                .toList());
        writer.println(headerLine);

        // Write data rows
        for (var row : rows)
        {
            var map = objectMapper.convertValue(row, ROW_TYPE);
            var values = headers.stream()
                    .map(map::get)
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
