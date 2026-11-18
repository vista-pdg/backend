package com.vista.pdg.quiz.service;

import com.vista.pdg.auth.entity.User;
import com.vista.pdg.quiz.dto.QuizDtos.GuidanceDto;
import com.vista.pdg.quiz.dto.QuizDtos.MaterialDto;
import com.vista.pdg.quiz.dto.QuizDtos.MaterialLinkRequest;
import com.vista.pdg.quiz.entity.Quiz;
import com.vista.pdg.quiz.entity.QuizMaterial;
import com.vista.pdg.quiz.material.ExtractedText;
import com.vista.pdg.quiz.material.MaterialException;
import com.vista.pdg.quiz.material.MaterialReader;
import com.vista.pdg.quiz.repository.QuizMaterialRepository;
import com.vista.pdg.quiz.repository.QuizRepository;
import java.io.IOException;
import java.time.Clock;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * HU-36 · CA-1 y CA-2: instrucciones y material de un quiz en borrador. Leer un enlace o un archivo
 * ocurre fuera de transacción; solo el guardado la abre y vuelve a comprobar el borrador y el cupo.
 */
@Service
public class QuizMaterialService {
  public static final int MAX_SOURCES = 5;

  private final QuizAccess access;
  private final QuizManagementService management;
  private final QuizRepository quizzes;
  private final QuizMaterialRepository materials;
  private final MaterialReader reader;
  private final Clock clock;
  private final TransactionTemplate transaction;

  public QuizMaterialService(
      QuizAccess access,
      QuizManagementService management,
      QuizRepository quizzes,
      QuizMaterialRepository materials,
      MaterialReader reader,
      Clock clock,
      PlatformTransactionManager transactionManager) {
    this.access = access;
    this.management = management;
    this.quizzes = quizzes;
    this.materials = materials;
    this.reader = reader;
    this.clock = clock;
    this.transaction = new TransactionTemplate(transactionManager);
  }

  @Transactional(readOnly = true)
  public GuidanceDto guidance(User actor, Long quizId) {
    return dto(access.requireManagedQuiz(actor, quizId));
  }

  @Transactional
  public GuidanceDto updateInstructions(User actor, Long quizId, String instructions) {
    Quiz quiz = management.requireDraft(actor, quizId);
    quiz.setInstructions(QuizGuidance.clean(instructions));
    return dto(quizzes.save(quiz));
  }

  public MaterialDto addLink(User actor, Long quizId, MaterialLinkRequest request) {
    requireRoom(actor, quizId);
    ExtractedText text = read("url", () -> reader.readLink(request.url(), request.crawl()));
    return save(actor, quizId, "LINK", text.title(), request.url().strip(), text);
  }

  public MaterialDto addFile(User actor, Long quizId, MultipartFile file) {
    requireRoom(actor, quizId);
    if (file == null || file.isEmpty()) {
      throw QuizException.invalid("file", "Elige un archivo con contenido");
    }
    String name = filename(file.getOriginalFilename());
    if (file.getSize() > MaterialReader.MAX_BYTES) {
      throw QuizException.invalid("file", name + " supera el máximo de 10 MB");
    }
    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException e) {
      throw QuizException.invalid("file", "No se pudo leer " + name);
    }
    ExtractedText text = read("file", () -> reader.readFile(name, bytes));
    return save(actor, quizId, "FILE", name, null, text);
  }

  @Transactional
  public void delete(User actor, Long quizId, Long materialId) {
    management.requireDraft(actor, quizId);
    QuizMaterial material =
        materials
            .findById(materialId)
            .filter(m -> m.getQuiz().getId().equals(quizId))
            .orElseThrow(QuizException::notFound);
    materials.delete(material);
  }

  private void requireRoom(User actor, Long quizId) {
    management.requireDraft(actor, quizId);
    if (materials.countByQuiz_Id(quizId) >= MAX_SOURCES) {
      throw QuizException.conflict(
          "MATERIAL_LIMIT", "Ya hay " + MAX_SOURCES + " fuentes. Quita una para añadir otra");
    }
  }

  private static ExtractedText read(String field, Supplier<ExtractedText> reading) {
    try {
      ExtractedText text = reading.get();
      if (text.text() == null || text.text().isBlank()) {
        throw QuizException.invalid(field, "No se encontró texto en esa fuente");
      }
      return text;
    } catch (MaterialException e) {
      throw QuizException.invalid(field, e.getMessage());
    }
  }

  private MaterialDto save(
      User actor, Long quizId, String kind, String name, String source, ExtractedText text) {
    return transaction.execute(
        status -> {
          Quiz quiz = management.requireDraft(actor, quizId);
          if (materials.countByQuiz_Id(quizId) >= MAX_SOURCES) {
            throw QuizException.conflict(
                "MATERIAL_LIMIT", "Ya hay " + MAX_SOURCES + " fuentes. Quita una para añadir otra");
          }
          String content = text.text();
          boolean truncated = content.length() > MaterialReader.MAX_CHARS;
          if (truncated) content = content.substring(0, MaterialReader.MAX_CHARS);
          QuizMaterial saved =
              materials.save(
                  QuizMaterial.builder()
                      .quiz(quiz)
                      .kind(kind)
                      .name(limit(name == null || name.isBlank() ? "Material" : name.strip(), 200))
                      .source(source)
                      .format(text.format().name())
                      .pages(text.pages())
                      .characters(content.length())
                      .truncated(truncated)
                      .content(content)
                      .createdAt(clock.instant())
                      .build());
          return QuizMapper.material(saved);
        });
  }

  private GuidanceDto dto(Quiz quiz) {
    return new GuidanceDto(
        quiz.getInstructions(),
        materials.findByQuiz_IdOrderByIdAsc(quiz.getId()).stream()
            .map(QuizMapper::material)
            .toList());
  }

  private static String filename(String original) {
    if (original == null || original.isBlank()) return "archivo";
    String clean = original.replace('\\', '/');
    return limit(clean.substring(clean.lastIndexOf('/') + 1).strip(), 200);
  }

  private static String limit(String text, int max) {
    return text.length() <= max ? text : text.substring(0, max);
  }
}
