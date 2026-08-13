package com.fitness.app.report;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Renders a report as a PNG table, with ImageIO and Graphics2D of the JDK.
 *
 * No dependency and no headless problem: Spring Boot already starts with
 * java.awt.headless=true, and the column widths are measured with FontMetrics so the
 * image fits its own content instead of guessing.
 */
@Component
public class ReportPng
{
    private static final Font TITLE_FONT  = new Font(Font.SANS_SERIF, Font.BOLD,  16);
    private static final Font HEADER_FONT = new Font(Font.SANS_SERIF, Font.BOLD,  12);
    private static final Font CELL_FONT   = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

    private static final Color HEADER_BACKGROUND = new Color(0xE8, 0xE8, 0xE8);
    private static final Color GRID              = new Color(0xC0, 0xC0, 0xC0);

    private static final int PADDING     = 10;
    private static final int ROW_HEIGHT  = 24;
    private static final int TITLE_BAND  = 40;
    private static final int MAX_COLUMN  = 320;

    public byte[] render(ReportTable table, String title)
    {
        if (table.isEmpty())
        {
            return toPng(emptyImage(title));
        }

        // Measured on a throwaway image: FontMetrics needs a graphics context, and the
        // real canvas cannot be sized until the columns are.
        var probe    = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        var probeG   = probe.createGraphics();
        var widths   = columnWidths(table, probeG);
        var totalRow = widths.stream().mapToInt(Integer::intValue).sum();

        probeG.dispose();

        var width  = totalRow + PADDING * 2;
        var height = TITLE_BAND + (table.rows().size() + 1) * ROW_HEIGHT + PADDING * 2;
        var image  = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g      = image.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        g.setColor(Color.BLACK);
        g.setFont(TITLE_FONT);
        g.drawString(title, PADDING, PADDING + 20);

        var y = TITLE_BAND + PADDING;

        g.setColor(HEADER_BACKGROUND);
        g.fillRect(PADDING, y, totalRow, ROW_HEIGHT);
        drawRow(g, table.headers(), widths, y, HEADER_FONT);

        y += ROW_HEIGHT;

        for (var row : table.rows())
        {
            drawRow(g, row.stream().map(ReportTable::text).toList(), widths, y, CELL_FONT);
            y += ROW_HEIGHT;
        }

        g.dispose();

        return toPng(image);
    }

    private List<Integer> columnWidths(ReportTable table, Graphics2D g)
    {
        var headerMetrics = g.getFontMetrics(HEADER_FONT);
        var cellMetrics   = g.getFontMetrics(CELL_FONT);

        return IntStream.range(0, table.headers().size())
                .mapToObj(column ->
                {
                    var widest = headerMetrics.stringWidth(table.headers().get(column));

                    for (var row : table.rows())
                    {
                        widest = Math.max(widest, cellMetrics.stringWidth(ReportTable.text(row.get(column))));
                    }

                    return Math.min(widest + PADDING * 2, MAX_COLUMN);
                })
                .toList();
    }

    private void drawRow(Graphics2D g, List<String> values, List<Integer> widths, int y, Font font)
    {
        var x = PADDING;

        g.setFont(font);

        for (var column = 0; column < values.size(); column++)
        {
            g.setColor(GRID);
            g.drawRect(x, y, widths.get(column), ROW_HEIGHT);

            // A cell wider than its column is cut at the border instead of running over
            // the next one. Graphics2D clips for us; measuring the cut point by hand was
            // twenty lines doing the same thing worse.
            g.setColor(Color.BLACK);
            g.setClip(x + 1, y, widths.get(column) - PADDING, ROW_HEIGHT);
            g.drawString(values.get(column), x + PADDING, y + 17);
            g.setClip(null);

            x += widths.get(column);
        }
    }

    private BufferedImage emptyImage(String title)
    {
        var image = new BufferedImage(420, 70, BufferedImage.TYPE_INT_RGB);
        var g     = image.createGraphics();

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 420, 70);
        g.setColor(Color.BLACK);
        g.setFont(TITLE_FONT);
        g.drawString(title, PADDING, 26);
        g.setFont(CELL_FONT);
        g.drawString("El reporte no devolvió filas.", PADDING, 50);
        g.dispose();

        return image;
    }

    private byte[] toPng(BufferedImage image)
    {
        try (var out = new ByteArrayOutputStream())
        {
            ImageIO.write(image, "png", out);

            return out.toByteArray();
        }
        catch (IOException ex)
        {
            // ByteArrayOutputStream does not do I/O; this cannot happen in practice.
            throw new UncheckedIOException(ex);
        }
    }
}
