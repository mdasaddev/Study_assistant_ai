package com.study.controller;

import com.study.model.FlashcardResponse;
import com.study.model.StudyRequest;
import com.study.model.StudyResponse;
import com.study.service.DocumentExtractorService;
import com.study.service.StudyAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;


@Slf4j
@RestController
@RequestMapping("/api/study")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class StudyController {

    private final StudyAiService studyAiService;
    private final DocumentExtractorService extractorService;

  
    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(@RequestParam("file") MultipartFile file) {
        try {
            log.info("Uploading file: {} ({} bytes)", file.getOriginalFilename(), file.getSize());

            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
            }

            String extractedText = extractorService.extractText(file);

            if (extractedText.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Could not extract text from file. Is it a scanned image PDF?"));
            }

            return ResponseEntity.ok(Map.of(
                    "filename", file.getOriginalFilename(),
                    "characters", extractedText.length(),
                    "wordCount", extractedText.split("\\s+").length,
                    "content", extractedText,
                    "preview", extractedText.substring(0, Math.min(300, extractedText.length())) + "..."
            ));

        } catch (Exception e) {
            log.error("Upload failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

   
    @PostMapping("/summarize")
    public ResponseEntity<StudyResponse> summarize(@RequestBody StudyRequest request) {
        if (isBlank(request.getContent())) {
            return ResponseEntity.badRequest().body(StudyResponse.error("Content is required"));
        }
        return ResponseEntity.ok(studyAiService.summarize(request));
    }

   
    @PostMapping("/questions")
    public ResponseEntity<StudyResponse> generateQuestions(@RequestBody StudyRequest request) {
        if (isBlank(request.getContent())) {
            return ResponseEntity.badRequest().body(StudyResponse.error("Content is required"));
        }
        return ResponseEntity.ok(studyAiService.generateQuestions(request));
    }

    
    @PostMapping("/explain")
    public ResponseEntity<StudyResponse> explainTopic(@RequestBody StudyRequest request) {
        if (isBlank(request.getContent())) {
            return ResponseEntity.badRequest().body(StudyResponse.error("Content is required"));
        }
        if (isBlank(request.getTopic())) {
            return ResponseEntity.badRequest().body(StudyResponse.error("Topic to explain is required"));
        }
        return ResponseEntity.ok(studyAiService.explainTopic(request));
    }

  
    @PostMapping("/flashcards")
    public ResponseEntity<FlashcardResponse> generateFlashcards(@RequestBody StudyRequest request) {
        if (isBlank(request.getContent())) {
            return ResponseEntity.badRequest().body(
                    FlashcardResponse.builder().success(false).error("Content is required").build()
            );
        }
        return ResponseEntity.ok(studyAiService.generateFlashcards(request));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "app", "AI Study Assistant",
                "features", new String[]{"upload", "summarize", "questions", "explain", "flashcards"},
                "supportedFormats", new String[]{"PDF", "DOCX", "TXT", "MD"},
                "model", "llama3.2 via Ollama"
        ));
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
