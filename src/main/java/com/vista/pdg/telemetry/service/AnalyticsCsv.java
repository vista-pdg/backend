package com.vista.pdg.telemetry.service;

import com.vista.pdg.telemetry.dto.PedagogicalReport;
import com.vista.pdg.telemetry.dto.PedagogicalReport.Level;
import com.vista.pdg.telemetry.dto.PedagogicalReport.QuestionInsight;
import com.vista.pdg.telemetry.dto.PedagogicalReport.Status;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Recibe exclusivamente el reporte ya protegido. No tiene acceso a eventos ni repositorios. HU-36:
 * una sección por vista, cada una con su encabezado, separadas por una línea vacía.
 */
public final class AnalyticsCsv {
  private AnalyticsCsv() {}

  /** {@code vista-analitica-CURSO-PERIODO.csv} o con las fechas aplicadas. */
  public static String filename(PedagogicalReport report) {
    String scope =
        report.wholeTerm()
            ? report.course().termCode()
            : (report.from() == null ? "inicio" : report.from().toString())
                + "-"
                + (report.to() == null ? "hoy" : report.to().toString());
    return "vista-analitica-" + report.course().code() + "-" + scope + ".csv";
  }

  public static String render(PedagogicalReport report) {
    StringBuilder csv = new StringBuilder("﻿");
    String from = report.wholeTerm() || report.from() == null ? "" : report.from().toString();
    String to = report.wholeTerm() || report.to() == null ? "" : report.to().toString();
    String period = report.wholeTerm() ? "Todo el periodo" : "Fechas";
    row(
        csv,
        "seccion",
        "curso",
        "periodo",
        "alcance",
        "desde",
        "hasta",
        "quiz",
        "estado",
        "estudiantes_activos",
        "matriculados",
        "solicitudes_de_estructuras",
        "ejecuciones_de_algoritmos",
        "respuestas_en_quizzes",
        "acierto_en_quizzes",
        "sin_responder",
        "intentos_finalizados",
        "respuestas_de_estudio",
        "acierto_en_estudio",
        "tiempo_mediano_s",
        "respuestas_con_tiempo");
    var o = report.overview();
    row(
        csv,
        "Resumen",
        report.course().code(),
        report.course().termCode(),
        period,
        from,
        to,
        report.quiz() == null ? "Todos los quizzes" : report.quiz().title(),
        label(report.status()),
        report.activeStudents(),
        report.enrolledStudents(),
        o == null ? null : o.structureRequests(),
        o == null ? null : o.algorithmExecutions(),
        o == null ? null : o.quizAnswers(),
        o == null ? null : o.quizAccuracy(),
        o == null ? null : o.unansweredRate(),
        o == null ? null : o.finishedAttempts(),
        o == null ? null : o.studyAnswers(),
        o == null ? null : o.studyAccuracy(),
        o == null ? null : o.medianSeconds(),
        o == null ? null : o.timedShare());
    if (report.status() != Status.AVAILABLE) return csv.toString();

    csv.append("\r\n");
    row(
        csv,
        "seccion",
        "tema",
        "nivel",
        "muestra_pequena",
        "respuestas_en_quizzes",
        "acierto_en_quizzes",
        "sin_responder",
        "tiempo_mediano_s",
        "respuestas_de_estudio",
        "acierto_en_estudio",
        "uso_de_pistas",
        "soluciones_sin_responder",
        "ejecuciones_en_vista",
        "acierto_con_practica",
        "acierto_sin_practica",
        "error_frecuente",
        "proporcion_error_frecuente");
    for (var t : report.topics()) {
      row(
          csv,
          "Temas",
          t.topicLabel(),
          level(t.level()),
          t.smallSample(),
          t.quizAnswers(),
          t.quizAccuracy(),
          t.unansweredRate(),
          t.medianSeconds(),
          t.studyAnswers(),
          t.studyAccuracy(),
          t.hintRate(),
          t.solutionRate(),
          t.practiceExecutions(),
          t.practicedAccuracy(),
          t.unpracticedAccuracy(),
          t.commonMistake(),
          t.commonMistakeShare());
    }

    csv.append("\r\n");
    row(
        csv,
        "seccion",
        "quiz",
        "tema",
        "enunciado",
        "respuestas",
        "acierto",
        "sin_responder",
        "tiempo_mediano_s",
        "opcion_correcta",
        "opcion_incorrecta_mas_elegida",
        "proporcion_incorrecta");
    questions(csv, "Preguntas más difíciles", report.hardestQuestions());
    questions(csv, "Preguntas más fáciles", report.easiestQuestions());

    csv.append("\r\n");
    row(
        csv,
        "seccion",
        "quiz",
        "fase",
        "entregas",
        "matriculados",
        "nota_promedio",
        "acierto",
        "tiempo_mediano_s",
        "tema_mas_debil",
        "tema_mas_fuerte");
    for (var q : report.quizzes()) {
      row(
          csv,
          "Quizzes",
          q.title(),
          q.phase(),
          q.finished(),
          q.enrolled(),
          q.averageGrade(),
          q.accuracy(),
          q.medianSeconds(),
          q.weakestTopic(),
          q.strongestTopic());
    }

    csv.append("\r\n");
    row(
        csv,
        "seccion",
        "tema",
        "frecuencia",
        "porcentaje",
        "estado_pasos",
        "promedio_pasos_completados",
        "sesiones",
        "sesiones_con_reintentos",
        "tasa_reintento",
        "supera_umbral",
        "umbral_reintento");
    for (var r : report.structures()) {
      row(
          csv,
          "Estructuras",
          r.structure(),
          r.frequency(),
          r.percentage(),
          null,
          null,
          null,
          null,
          null,
          null,
          report.retryThreshold());
    }
    for (var r : report.algorithms()) {
      row(
          csv,
          "Algoritmos",
          r.algorithm(),
          r.executions(),
          null,
          label(r.stepsStatus()),
          r.averageCompletedSteps(),
          null,
          null,
          null,
          null,
          report.retryThreshold());
    }
    for (var r : report.retries()) {
      row(
          csv,
          "Reintentos",
          r.structure(),
          null,
          null,
          null,
          null,
          r.sessions(),
          r.sessionsWithRetries(),
          r.retryRate(),
          r.highlighted(),
          report.retryThreshold());
    }
    return csv.toString();
  }

  private static void questions(StringBuilder csv, String section, List<QuestionInsight> list) {
    for (var q : list) {
      row(
          csv,
          section,
          q.quizTitle(),
          q.topicLabel(),
          q.statement(),
          q.answers(),
          q.accuracy(),
          q.unansweredRate(),
          q.medianSeconds(),
          q.correctOption(),
          q.commonWrongOption(),
          q.commonWrongShare());
    }
  }

  private static String level(Level level) {
    return switch (level) {
      case DIFICIL -> "Cuesta trabajo";
      case EN_PROGRESO -> "En progreso";
      case DOMINADO -> "Dominado";
      case SIN_DATOS -> "Sin respuestas";
    };
  }

  private static String label(Status status) {
    return switch (status) {
      case AVAILABLE -> "Disponible";
      case SUPPRESSED -> AnalyticsDisclosure.INSUFFICIENT;
      case UNAVAILABLE -> "Sin medición de pasos para todas las ejecuciones del periodo";
    };
  }

  private static void row(StringBuilder csv, Object... values) {
    csv.append(Arrays.stream(values).map(AnalyticsCsv::cell).collect(Collectors.joining(",")))
        .append("\r\n");
  }

  static String cell(Object value) {
    if (value == null) return "";
    String text = value.toString();
    String trimmed = text.stripLeading();
    if (value instanceof String && !trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0)
      text = "'" + text;
    return "\"" + text.replace("\"", "\"\"") + "\"";
  }
}
