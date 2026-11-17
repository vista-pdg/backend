package com.vista.pdg.quiz.service;

/** Error del contrato de quizzes, traducido a HTTP por GlobalExceptionHandler. */
public class QuizException extends RuntimeException {
  private final int status;
  private final String code;
  private final String field;

  public QuizException(int status, String code, String message, String field) {
    super(message);
    this.status = status;
    this.code = code;
    this.field = field;
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }

  /** Campo del formulario al que corresponde el error; nulo si no es de campo. */
  public String field() {
    return field;
  }

  public static QuizException invalid(String field, String message) {
    return new QuizException(400, "INVALID_QUIZ", message, field);
  }

  public static QuizException forbidden() {
    return new QuizException(403, "QUIZ_FORBIDDEN", "No tienes acceso a este quiz", null);
  }

  public static QuizException notFound() {
    return new QuizException(404, "QUIZ_NOT_FOUND", "Este quiz no está disponible", null);
  }

  public static QuizException conflict(String code, String message) {
    return new QuizException(409, code, message, null);
  }

  public static QuizException unavailable(String message) {
    return new QuizException(503, "QUIZ_GENERATION_UNAVAILABLE", message, null);
  }
}
