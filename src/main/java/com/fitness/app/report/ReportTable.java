package com.fitness.app.report;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * A report flattened into a grid: the column names and the raw value of every cell.
 *
 * The nine report DTOs are flat records of scalars, so the application mapper already
 * knows how to turn any of them into an ordered map keyed by the same snake_case names
 * the JSON response uses. This is the only place that conversion happens: CSV, XLSX,
 * PDF and PNG all render a ReportTable and none of them knows the DTOs exist.
 *
 * Cells keep their original type instead of being stringified here, so a renderer that
 * can do better than text — XLSX writes real numeric cells — still has the number.
 */
public record ReportTable(List<String> headers, List<List<Object>> rows)
{
    private static final TypeReference<Map<String, Object>> ROW_TYPE = new TypeReference<>() {};

    public static ReportTable of(ObjectMapper objectMapper, List<?> source)
    {
        if (source.isEmpty())
        {
            return new ReportTable(List.of(), List.of());
        }

        // Every row is the same record type, so the first one fixes the column order.
        var headers = List.copyOf(objectMapper.convertValue(source.get(0), ROW_TYPE).keySet());

        var rows = source.stream()
                .map(row -> objectMapper.convertValue(row, ROW_TYPE))
                .map(values -> headers.stream().map(values::get).toList())
                .toList();

        return new ReportTable(headers, rows);
    }

    public boolean isEmpty()
    {
        return headers.isEmpty();
    }

    /** The cell as it should read on screen or on paper; null becomes an empty cell. */
    public static String text(Object value)
    {
        return value == null ? "" : value.toString();
    }
}
