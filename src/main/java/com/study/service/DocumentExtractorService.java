package com.study.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * DocumentExtractorService — extracts plain text from uploaded files.
 *
 * Supports:
 *  - PDF (.pdf) via Apache PDFBox
 *  - Word documents (.docx) via Apache POI
 *  - Plain text (.txt, .md)
 */
@Slf4j
@Service
public class DocumentExtractorService {

    /**
     * Extract all text from an uploaded file.
     * Returns the raw text content ready for AI processing.
     */
    public String extractText(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase()
                : "";

        log.info("Extracting text from: {} ({} bytes)", filename, file.getSize());

        if (filename.endsWith(".pdf")) {
            return extractFromPdf(file.getInputStream());
        } else if (filename.endsWith(".docx")) {
            return extractFromDocx(file.getInputStream());
        } else {
            // Plain text, markdown, etc.
            return new String(file.getBytes());
        }
    }

    private String extractFromPdf(InputStream inputStream) throws IOException {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            log.info("Extracted {} characters from PDF ({} pages)", text.length(), document.getNumberOfPages());
            return text;
        }
    }

    private String extractFromDocx(InputStream inputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            String text = extractor.getText();
            log.info("Extracted {} characters from DOCX", text.length());
            return text;
        }
    }

    /**
     * Truncate content to avoid overwhelming the LLM context window.
     * Ollama's llama3.2 handles ~8k tokens — roughly 6000 words.
     */
    public String truncateIfNeeded(String text, int maxChars) {
        if (text.length() <= maxChars) return text;

        log.warn("Content truncated from {} to {} chars for LLM context", text.length(), maxChars);
        return text.substring(0, maxChars) + "\n\n[Content truncated for processing...]";
    }
}
