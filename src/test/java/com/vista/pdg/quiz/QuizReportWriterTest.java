package com.vista.pdg.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import com.vista.pdg.quiz.dto.QuizDtos.QuizResultsDto;
import com.vista.pdg.quiz.dto.QuizDtos.QuizSummaryDto;
import com.vista.pdg.quiz.dto.QuizDtos.ResultStats;
import com.vista.pdg.quiz.dto.QuizDtos.StudentResultRow;
import com.vista.pdg.quiz.entity.StringListJsonConverter;
import com.vista.pdg.quiz.service.QuizMapper;
import com.vista.pdg.quiz.service.QuizReportWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * HU-34 · CA-9: el reporte no rompe la hoja ni ejecuta lo que un estudiante escriba en su nombre.
 */
class QuizReportWriterTest {
  private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

  private static QuizResultsDto results(StudentResultRow... rows) {
    QuizSummaryDto quiz =
        new QuizSummaryDto(
            1L,
            "CEDI-G1",
            "Quiz",
            "PUBLISHED",
            "OPEN",
            List.of(),
            2,
            30,
            60,
            60,
            30,
            Instant.EPOCH,
            Instant.EPOCH,
            true,
            30,
            1,
            1);
    return new QuizResultsDto(quiz, new ResultStats(null, null, null, 0, 0, 0, 0), List.of(rows));
  }

  @Test
  void csvQuotesTextNeutralisesFormulasAndUsesBogotaTime() {
    StudentResultRow row =
        new StudentResultRow(
            1L,
            "=HYPERLINK(\"http://x\"), \"Ana\"",
            "ana@u.icesi.edu.co",
            "SUBMITTED",
            1,
            2,
            2.5,
            0,
            Instant.parse("2026-11-17T13:00:00Z"),
            Instant.parse("2026-11-17T13:20:00Z"),
            List.of("CORRECT", "INCORRECT"));
    String csv = new String(QuizReportWriter.csv(results(row), BOGOTA), StandardCharsets.UTF_8);

    assertThat(csv.lines().toList()).hasSize(2);
    assertThat(csv).contains("\"'=HYPERLINK(\"\"http://x\"\"), \"\"Ana\"\"\"");
    assertThat(csv).contains(",1,2,2.5,0,\"2026-11-17 08:00\",\"2026-11-17 08:20\",");
    assertThat(csv).endsWith("\"Correcta\",\"Incorrecta\"\r\n");
  }

  @Test
  void xlsxEscapesMarkupAndAddressesColumnsBeyondZ() {
    StudentResultRow row =
        new StudentResultRow(
            1L,
            "<Ana & Luis>",
            "a@u.icesi.edu.co",
            "IN_PROGRESS",
            null,
            2,
            null,
            3,
            null,
            null,
            List.of());
    byte[] xlsx = QuizReportWriter.xlsx(results(row), BOGOTA);

    assertThat(new String(xlsx, 0, 2, StandardCharsets.ISO_8859_1)).isEqualTo("PK");
    assertThat(new String(xlsx, StandardCharsets.ISO_8859_1)).doesNotContain("<Ana & Luis>");
  }

  @Test
  void gradeScaleAndListStorageRoundTrip() {
    assertThat(QuizMapper.grade(2, 3)).isEqualTo(3.3);
    assertThat(QuizMapper.grade(3, 3)).isEqualTo(5.0);
    assertThat(QuizMapper.grade(0, 3)).isEqualTo(0.0);
    assertThat(QuizMapper.grade(null, 3)).isNull();
    assertThat(QuizMapper.grade(1, 0)).isNull();

    StringListJsonConverter converter = new StringListJsonConverter();
    List<String> options = List.of("a, \"b\"", "c");
    assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(options)))
        .isEqualTo(options);
    assertThat(converter.convertToDatabaseColumn(null)).isEqualTo("[]");
    assertThat(converter.convertToEntityAttribute(" ")).isEmpty();
  }
}
