package com.vista.pdg.quiz.material;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.springframework.stereotype.Component;

/**
 * Documentos de Word: lee {@code word/document.xml} sin dependencias nuevas. El XML se procesa sin
 * DTD ni entidades externas y con un tope de tamaño descomprimido, contra bombas zip.
 */
@Component
class DocxTextExtractor implements TextExtractor {
  static final long MAX_XML_BYTES = 25L * 1024 * 1024;

  @Override
  public boolean supports(MaterialFormat format) {
    return format == MaterialFormat.DOCX;
  }

  @Override
  public ExtractedText extract(byte[] bytes, String name) {
    byte[] xml = documentXml(bytes, name);
    String text = TextCleanup.tidy(paragraphs(xml, name));
    if (text.isBlank()) throw new MaterialException(name + " no tiene texto.");
    return new ExtractedText(name, text, 1, MaterialFormat.DOCX);
  }

  private static byte[] documentXml(byte[] bytes, String name) {
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if ("word/document.xml".equals(entry.getName())) return limited(zip, name);
      }
    } catch (IOException e) {
      throw new MaterialException(name + " no es un documento .docx válido.", e);
    }
    throw new MaterialException(name + " no es un documento .docx válido.");
  }

  private static byte[] limited(InputStream in, String name) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    long total = 0;
    int read;
    while ((read = in.read(buffer)) != -1) {
      total += read;
      if (total > MAX_XML_BYTES) throw new MaterialException(name + " es demasiado grande.");
      out.write(buffer, 0, read);
    }
    return out.toByteArray();
  }

  private static String paragraphs(byte[] xml, String name) {
    XMLInputFactory factory = XMLInputFactory.newFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    StringBuilder text = new StringBuilder();
    try {
      XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(xml));
      boolean inText = false;
      while (reader.hasNext()) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT) {
          switch (reader.getLocalName()) {
            case "t" -> inText = true;
            case "tab" -> text.append(' ');
            case "br", "cr" -> text.append('\n');
            default -> {}
          }
        } else if (event == XMLStreamConstants.END_ELEMENT) {
          if ("t".equals(reader.getLocalName())) inText = false;
          if ("p".equals(reader.getLocalName())) text.append('\n');
        } else if (inText && event == XMLStreamConstants.CHARACTERS) {
          text.append(reader.getText());
        }
      }
      reader.close();
    } catch (XMLStreamException e) {
      throw new MaterialException(name + " no es un documento .docx válido.", e);
    }
    return text.toString();
  }
}
