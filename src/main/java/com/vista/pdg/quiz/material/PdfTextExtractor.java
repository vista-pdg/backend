package com.vista.pdg.quiz.material;

import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/** PDF con capa de texto. Un PDF escaneado sin texto se rechaza con un mensaje claro. */
@Component
class PdfTextExtractor implements TextExtractor {
  static final int MAX_PAGES = 300;

  @Override
  public boolean supports(MaterialFormat format) {
    return format == MaterialFormat.PDF;
  }

  @Override
  public ExtractedText extract(byte[] bytes, String name) {
    try (PDDocument document = Loader.loadPDF(bytes)) {
      int pages = Math.min(document.getNumberOfPages(), MAX_PAGES);
      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setSortByPosition(true);
      stripper.setEndPage(pages);
      String text = TextCleanup.tidy(stripper.getText(document));
      if (text.isBlank()) {
        throw new MaterialException(
            name + ": el PDF no tiene texto extraíble; puede ser una imagen escaneada.");
      }
      String title = document.getDocumentInformation().getTitle();
      return new ExtractedText(
          title == null || title.isBlank() ? name : title.strip(), text, pages, MaterialFormat.PDF);
    } catch (InvalidPasswordException e) {
      throw new MaterialException(name + " está protegido con contraseña.", e);
    } catch (IOException e) {
      throw new MaterialException(name + " no es un PDF válido.", e);
    }
  }
}
