package com.fitness.app.report;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;

/**
 * Renders a report as a landscape A4 PDF table.
 *
 * Landscape because the widest report has nine columns and portrait A4 squeezes them
 * to the point of wrapping every header.
 */
@Component
public class ReportPdf
{
    private static final Font TITLE_FONT  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
    private static final Font HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
    private static final Font CELL_FONT   = FontFactory.getFont(FontFactory.HELVETICA, 8);

    private static final Color HEADER_BACKGROUND = new Color(0xE8, 0xE8, 0xE8);

    public byte[] render(ReportTable table, String title)
    {
        var out      = new ByteArrayOutputStream();
        var document = new Document(PageSize.A4.rotate(), 24, 24, 24, 24);

        PdfWriter.getInstance(document, out);
        document.open();
        document.add(new Paragraph(title, TITLE_FONT));
        document.add(new Paragraph(" "));

        if (table.isEmpty())
        {
            // A PdfPTable of zero columns throws, and an empty result still deserves a file.
            document.add(new Paragraph("El reporte no devolvió filas.", CELL_FONT));
        }
        else
        {
            document.add(toPdfTable(table));
        }

        document.close();

        return out.toByteArray();
    }

    private PdfPTable toPdfTable(ReportTable table)
    {
        var pdfTable = new PdfPTable(table.headers().size());

        pdfTable.setWidthPercentage(100);

        for (var header : table.headers())
        {
            var cell = new PdfPCell(new Phrase(header, HEADER_FONT));

            cell.setBackgroundColor(HEADER_BACKGROUND);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            pdfTable.addCell(cell);
        }

        pdfTable.setHeaderRows(1);

        for (var row : table.rows())
        {
            for (var value : row)
            {
                pdfTable.addCell(new PdfPCell(new Phrase(ReportTable.text(value), CELL_FONT)));
            }
        }

        return pdfTable;
    }
}
