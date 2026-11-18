package com.vista.pdg.quiz.material;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Cuadernos Jupyter: celdas de Markdown y de código, en orden; las salidas se ignoran. */
@Component
@RequiredArgsConstructor
class NotebookTextExtractor implements TextExtractor {
  private final ObjectMapper mapper;

  @Override
  public boolean supports(MaterialFormat format) {
    return format == MaterialFormat.NOTEBOOK;
  }

  @Override
  public ExtractedText extract(byte[] bytes, String name) {
    JsonNode cells;
    try {
      cells = mapper.readTree(bytes).path("cells");
    } catch (IOException e) {
      throw new MaterialException(name + " no es un cuaderno .ipynb válido.", e);
    }
    if (!cells.isArray()) throw new MaterialException(name + " no es un cuaderno .ipynb válido.");
    StringBuilder text = new StringBuilder();
    for (JsonNode cell : cells) {
      String source = source(cell.path("source"));
      if (source.isBlank()) continue;
      if ("code".equals(cell.path("cell_type").asText())) {
        text.append("```\n").append(source.strip()).append("\n```\n\n");
      } else {
        text.append(source.strip()).append("\n\n");
      }
    }
    String clean = text.toString().strip();
    String title = TextCleanup.firstHeading(clean);
    return new ExtractedText(title == null ? name : title, clean, 1, MaterialFormat.NOTEBOOK);
  }

  private static String source(JsonNode node) {
    if (node.isTextual()) return node.asText();
    StringBuilder out = new StringBuilder();
    node.forEach(line -> out.append(line.asText()));
    return out.toString();
  }
}
