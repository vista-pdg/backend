package com.vista.pdg.academic.dto;

import com.vista.pdg.academic.entity.Course;

public record CourseDto(String code, String name, String termCode) {
  public static CourseDto from(Course c) {
    return new CourseDto(c.getCode(), c.getName(), c.getTerm().getCode());
  }
}
