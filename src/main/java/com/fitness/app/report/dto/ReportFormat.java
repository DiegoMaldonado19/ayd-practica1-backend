package com.fitness.app.report.dto;

/**
 * Supported export formats for reports.
 *
 * Binding is case insensitive: WebMvcConfig registers the lenient enum converter, so
 * format=pdf and format=PDF both reach here and an unknown value still answers 400.
 */
public enum ReportFormat
{
    JSON,
    CSV,
    XLSX,
    PDF,
    PNG
}
