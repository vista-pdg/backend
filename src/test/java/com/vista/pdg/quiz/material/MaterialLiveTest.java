package com.vista.pdg.quiz.material;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vista.pdg.service.algorithm.def.AlgorithmDescriptor;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * HU-36 · CA-2, prueba opcional con red: lee el libro del curso (CyED1) recorriendo el sitio, con
 * la guardia de red real, y comprueba que el tema de tablas hash recibe fragmentos suyos. Se activa
 * con {@code VISTA_LIVE_MATERIAL=1}; CI no la corre.
 */
@EnabledIfEnvironmentVariable(named = "VISTA_LIVE_MATERIAL", matches = "1")
class MaterialLiveTest {
  private final HtmlTextExtractor html = new HtmlTextExtractor();
  private final MaterialReader reader =
      new MaterialReader(
          new HttpMaterialFetcher(false, 15000),
          List.of(
              html,
              new PdfTextExtractor(),
              new DocxTextExtractor(),
              new PlainTextExtractor(),
              new NotebookTextExtractor(new ObjectMapper())),
          html,
          Clock.systemUTC(),
          40000);

  @Test
  void readsTheCourseBookAndSelectsHashTableExcerpts() {
    long start = System.nanoTime();
    ExtractedText book = reader.readLink("https://cyedbooks.github.io/CyED1/", true);
    long seconds = (System.nanoTime() - start) / 1_000_000_000;
    System.out.printf(
        "CyED1: %s · %d páginas · %d caracteres · %d s%n",
        book.title(), book.pages(), book.text().length(), seconds);
    assertThat(book.format()).isEqualTo(MaterialFormat.SITE);
    assertThat(book.pages()).isGreaterThan(5);
    assertThat(seconds).isLessThan(55);
    assertThat(book.text()).containsIgnoringCase("tabla").containsIgnoringCase("hash");

    AlgorithmDescriptor hash =
        new AlgorithmDescriptor(
            "hash-table",
            "chaining",
            "insert",
            "hash-table",
            "Inserción",
            "Inserta claves en una tabla hash con encadenamiento",
            "values");
    var excerpts =
        new MaterialExcerpts()
            .select(
                List.of(new MaterialExcerpts.Source(book.title(), book.text())),
                hash,
                MaterialExcerpts.BUDGET);
    excerpts.forEach(
        e ->
            System.out.println(
                "· " + e.text().substring(0, Math.min(160, e.text().length())).replace('\n', ' ')));
    assertThat(excerpts).isNotEmpty();
    assertThat(excerpts).allSatisfy(e -> assertThat(e.text().toLowerCase()).contains("hash"));
  }
}
