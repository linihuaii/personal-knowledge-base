# S02 Java 兼容性原型

使用 JDK 17、Maven Wrapper 3.3.4 / Maven 3.9.11，验证以下锁定依赖：

- Apache Tika 3.2.2：解析自制有效 PDF 与 DOCX。
- PDFBox 3.0.5：生成 PDF 并按第一页提取文本。
- H2 2.3.232：内存数据库建表、写入和查询。

运行：

```powershell
.\mvnw.cmd -B clean compile exec:java
```

成功输出包含 `tikaPdf=PDFBox page one text`、`tikaDocx=Tika DOCX paragraph`、`pdfboxPage1=PDFBox page one text`、`h2Rows=1` 和 `BUILD SUCCESS`。
