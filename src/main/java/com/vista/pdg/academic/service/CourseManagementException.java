package com.vista.pdg.academic.service;

public class CourseManagementException extends RuntimeException {
  private final int status;
  private final String code;

  public CourseManagementException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }

  public static CourseManagementException invalid(String message) {
    return new CourseManagementException(400, "INVALID_COURSE_ASSIGNMENT", message);
  }

  public static CourseManagementException notFound(String message) {
    return new CourseManagementException(404, "ACADEMIC_RESOURCE_NOT_FOUND", message);
  }

  public static CourseManagementException conflict(String message) {
    return new CourseManagementException(409, "COURSE_ALREADY_EXISTS", message);
  }
}
