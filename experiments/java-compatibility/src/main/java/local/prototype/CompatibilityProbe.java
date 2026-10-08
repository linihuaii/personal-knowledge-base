package local.prototype;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.DriverManager;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.Tika;

public final class CompatibilityProbe {
  private static byte[] pdf() throws Exception {
    try (var document = new PDDocument(); var out = new ByteArrayOutputStream()) {
      var page = new PDPage(); document.addPage(page);
      try (var stream = new PDPageContentStream(document, page)) {
        stream.beginText(); stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        stream.newLineAtOffset(72, 720); stream.showText("PDFBox page one text"); stream.endText();
      }
      document.save(out); return out.toByteArray();
    }
  }

  private static byte[] docx() throws Exception {
    var out = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(out)) {
      zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
      zip.write("<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>".getBytes()); zip.closeEntry();
      zip.putNextEntry(new ZipEntry("_rels/.rels"));
      zip.write("<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>".getBytes()); zip.closeEntry();
      zip.putNextEntry(new ZipEntry("word/document.xml"));
      zip.write("<?xml version=\"1.0\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>Tika DOCX paragraph</w:t></w:r></w:p></w:body></w:document>".getBytes()); zip.closeEntry();
    }
    return out.toByteArray();
  }

  public static void main(String[] args) throws Exception {
    var pdf = pdf(); var docx = docx(); var tika = new Tika();
    String tikaPdf = tika.parseToString(new ByteArrayInputStream(pdf)).trim();
    String tikaDocx = tika.parseToString(new ByteArrayInputStream(docx)).trim();
    String pdfbox;
    try (var document = Loader.loadPDF(pdf)) { pdfbox = new PDFTextStripper().getText(document).trim(); }
    int h2Rows;
    try (var connection = DriverManager.getConnection("jdbc:h2:mem:s02;DB_CLOSE_DELAY=-1");
         var statement = connection.createStatement()) {
      statement.execute("create table evidence(id int primary key, content varchar(100))");
      statement.execute("insert into evidence values(1, 'h2-persisted')");
      try (var result = statement.executeQuery("select count(*) from evidence")) { result.next(); h2Rows = result.getInt(1); }
    }
    if (!tikaPdf.contains("PDFBox page one text") || !tikaDocx.contains("Tika DOCX paragraph") ||
        !pdfbox.contains("PDFBox page one text") || h2Rows != 1) throw new IllegalStateException("compatibility assertion failed");
    System.out.println(Map.of("tikaPdf", tikaPdf, "tikaDocx", tikaDocx, "pdfboxPage1", pdfbox, "h2Rows", h2Rows));
  }
}
