package com.webdynamo.document_insight.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentParserService {

    /**
     * Parse document and extract text based on file type
     */
    public String parseDocument(Path filePath, String contentType) {
        log.info("Parsing document: {} with type: {}", filePath.getFileName(), contentType);

        try {
            String text = switch (contentType) {
                case "application/pdf" -> parsePdf(filePath);
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> parseDocx(filePath);
                case "text/plain" -> parseTextFile(filePath);
                default -> throw new RuntimeException("Unsupported file type: " + contentType);
            };

            log.info("Extracted {} characters from document", text.length());
            return text;

        } catch (IOException e) {
            log.error("Error parsing document: {}", filePath, e);
            throw new RuntimeException("Failed to parse document", e);
        }
    }

    /**
     * Parse PDF file using Apache PDFBox
     */
    private String parsePdf(Path filePath) throws IOException {
        log.debug("Parsing PDF: {}", filePath);

        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document).trim();

            log.debug("PDF parsed: {} pages, {} characters",
                    document.getNumberOfPages(), text.length());

            return text;
        }
    }

    /**
     * Parse DOCX file using Apache POI
     */
    private String parseDocx(Path filePath) throws IOException {
        log.debug("Parsing DOCX: {}", filePath);

        try (FileInputStream fis = new FileInputStream(filePath.toFile());
             XWPFDocument document = new XWPFDocument(fis)) {

            StringBuilder text = new StringBuilder();
            List<XWPFParagraph> paragraphs = document.getParagraphs();

            for (XWPFParagraph paragraph : paragraphs) {
                text.append(paragraph.getText()).append("\n");
            }

            log.debug("DOCX parsed: {} paragraphs, {} characters",
                    paragraphs.size(), text.length());

            return text.toString();
        }
    }

    /**
     * Parse plain text file
     */
    private String parseTextFile(Path filePath) throws IOException {
        log.debug("Parsing text file: {}", filePath);

        String text = Files.readString(filePath);

        log.debug("Text file parsed: {} characters", text.length());
        return text;
    }
}
