package com.vista.pdg.quiz.service;

import com.vista.pdg.quiz.dto.QuizDtos.QuizResultsDto;
import com.vista.pdg.quiz.dto.QuizDtos.StudentResultRow;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Reporte de resultados en CSV y XLSX.
 *
 * <p>El XLSX es SpreadsheetML mínimo escrito con {@code java.util.zip}: una hoja con cadenas en
 * línea y números. Evita sumar Apache POI (~15 MB) para un único reporte tabular.
 */
public final class QuizReportWriter {
  private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private QuizReportWriter() {}

  /** Filas del reporte; un {@link Number} se escribe como número y lo demás como texto. */
  static List<List<Object>> table(QuizResultsDto results, ZoneId zone) {
    int questions = results.quiz().questionsPerStudent();
    List<Object> header =
        new ArrayList<>(
            List.of(
                "Nombre",
                "Correo",
                "Estado",
                "Aciertos",
                "Total",
                "Nota (0-5)",
                "Incidentes",
                "Inicio",
                "Entrega"));
    for (int i = 1; i <= questions; i++) header.add("P" + i);
    List<List<Object>> table = new ArrayList<>();
    table.add(header);
    for (StudentResultRow row : results.rows()) {
      List<Object> cells = new ArrayList<>();
      cells.add(row.displayName());
      cells.add(row.email());
      cells.add(status(row.status()));
      cells.add(row.score());
      cells.add(row.total());
      cells.add(row.grade());
      cells.add(row.integrityEvents());
      cells.add(stamp(row.startedAt(), zone));
      cells.add(stamp(row.finishedAt(), zone));
      for (int i = 0; i < questions; i++) {
        cells.add(i < row.answers().size() ? answer(row.answers().get(i)) : "");
      }
      table.add(cells);
    }
    return table;
  }

  public static byte[] csv(QuizResultsDto results, ZoneId zone) {
    // BOM: sin él Excel abre el archivo como Latin-1 y rompe las tildes.
    StringBuilder out = new StringBuilder("﻿");
    for (List<Object> row : table(results, zone)) {
      List<String> cells = new ArrayList<>();
      for (Object cell : row) cells.add(csvCell(cell));
      out.append(String.join(",", cells)).append("\r\n");
    }
    return out.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static String csvCell(Object cell) {
    if (cell == null) return "";
    if (cell instanceof Number number) return number(number);
    return '"' + guard(cell.toString()).replace("\"", "\"\"") + '"';
  }

  /** Un nombre que empiece por =, +, - o @ se ejecutaría como fórmula al abrir la hoja. */
  static String guard(String text) {
    return !text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0 ? "'" + text : text;
  }

  public static byte[] xlsx(QuizResultsDto results, ZoneId zone) {
    StringBuilder sheet =
        new StringBuilder(
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<sheetData>");
    List<List<Object>> table = table(results, zone);
    for (int r = 0; r < table.size(); r++) {
      sheet.append("<row r=\"").append(r + 1).append("\">");
      List<Object> row = table.get(r);
      for (int c = 0; c < row.size(); c++) {
        Object cell = row.get(c);
        if (cell == null) continue;
        String ref = column(c) + (r + 1);
        if (cell instanceof Number number) {
          sheet
              .append("<c r=\"")
              .append(ref)
              .append("\"><v>")
              .append(number(number))
              .append("</v></c>");
        } else {
          sheet
              .append("<c r=\"")
              .append(ref)
              .append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
              .append(xml(guard(cell.toString())))
              .append("</t></is></c>");
        }
      }
      sheet.append("</row>");
    }
    sheet.append("</sheetData></worksheet>");

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
      entry(
          zip,
          "[Content_Types].xml",
          "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
              + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
              + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
              + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
              + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
              + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
              + "</Types>");
      entry(
          zip,
          "_rels/.rels",
          "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
              + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
              + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
              + "</Relationships>");
      entry(
          zip,
          "xl/workbook.xml",
          "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
              + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
              + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
              + "<sheets><sheet name=\"Resultados\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
      entry(
          zip,
          "xl/_rels/workbook.xml.rels",
          "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
              + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
              + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
              + "</Relationships>");
      entry(zip, "xl/worksheets/sheet1.xml", sheet.toString());
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo escribir el reporte XLSX", e);
    }
    return bytes.toByteArray();
  }

  private static void entry(ZipOutputStream zip, String name, String content) throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(content.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }

  /** 0 → A, 25 → Z, 26 → AA. */
  static String column(int index) {
    StringBuilder name = new StringBuilder();
    for (int i = index; i >= 0; i = i / 26 - 1) name.insert(0, (char) ('A' + i % 26));
    return name.toString();
  }

  private static String number(Number number) {
    return number instanceof Double || number instanceof Float
        ? String.format(Locale.ROOT, "%.1f", number.doubleValue())
        : number.toString();
  }

  private static String xml(String text) {
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
  }

  private static String stamp(Instant instant, ZoneId zone) {
    return instant == null ? "" : STAMP.format(instant.atZone(zone));
  }

  private static String status(String status) {
    return switch (status) {
      case "SUBMITTED" -> "Entregado";
      case "EXPIRED" -> "Tiempo agotado";
      case "IN_PROGRESS" -> "En curso";
      default -> "Sin presentar";
    };
  }

  private static String answer(String answer) {
    return switch (answer) {
      case "CORRECT" -> "Correcta";
      case "INCORRECT" -> "Incorrecta";
      default -> "Sin responder";
    };
  }
}
