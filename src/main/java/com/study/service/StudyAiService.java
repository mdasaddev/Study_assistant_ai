package com.study.service;

import com.study.model.FlashcardResponse;
import com.study.model.FlashcardResponse.Flashcard;
import com.study.model.StudyRequest;
import com.study.model.StudyResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Slf4j
@Service
@RequiredArgsConstructor
public class StudyAiService {

    private final ChatClient chatClient;
    private static final int MAX_CONTENT_CHARS = 12000;



    private static final String SUMMARIZE_TEMPLATE = """
            You are an expert study coach creating a thorough summary for a student.
            Adapt the depth of your summary to match the length and complexity of the material.
            
            Structure your summary as follows:
            
            ## Overview
            2-3 sentences describing what this material is about and why it matters.
            
            ## Key Concepts
            List ALL important concepts, ideas, and topics covered. For long documents,
            this should be 10-15 points minimum. Use this format:
            • **Concept name**: explanation in 1-2 sentences
            
            ## Important Details
            - Key definitions, formulas, dates, names, or facts worth remembering
            - Include specific examples or case studies mentioned
            - Note any processes or step-by-step procedures
            
            ## Chapter/Section Breakdown (if applicable)
            If the material has multiple sections or chapters, briefly summarize each one.
            
            ## Key Takeaways
            3-5 most important things the student must remember from this material.
            
            Rules:
            - Be thorough — a long document deserves a detailed summary
            - Never cut content short to save space
            - Use student-friendly language but preserve technical terms
            - If the content has 10 topics, cover all 10 — do not pick only a few
            
            Study Material:
            {content}
            
            Detailed Summary:
            """;

    public StudyResponse summarize(StudyRequest request) {
        long start = System.currentTimeMillis();
        try {
            String content = truncate(request.getContent());
            String result = chatClient.prompt(
                    new PromptTemplate(SUMMARIZE_TEMPLATE)
                            .create(Map.of("content", content))
            ).call().content();

            return StudyResponse.builder()
                    .result(result).type("SUMMARY")
                    .durationMs(System.currentTimeMillis() - start)
                    .success(true).build();
        } catch (Exception e) {
            log.error("Summarize failed", e);
            return StudyResponse.error(e.getMessage());
        }
    }

   

    private static final String QUESTIONS_TEMPLATE = """
            You are a professor creating a practice quiz. Generate {count} practice questions from the content below.
            
            Question types to include (mix them):
            - Multiple choice (label answers A, B, C, D)
            - Short answer
            - True/False
            
            Format each question as:
            Q1. [Question text]
            Type: [Multiple Choice / Short Answer / True-False]
            [For multiple choice, list A B C D options]
            Answer: [Correct answer]
            
            Study Material:
            {content}
            
            Practice Questions:
            """;

    public StudyResponse generateQuestions(StudyRequest request) {
        long start = System.currentTimeMillis();
        try {
            int count = request.getQuestionCount() > 0 ? request.getQuestionCount() : 5;
            String content = truncate(request.getContent());

            String result = chatClient.prompt(
                    new PromptTemplate(QUESTIONS_TEMPLATE)
                            .create(Map.of("content", content, "count", String.valueOf(count)))
            ).call().content();

            return StudyResponse.builder()
                    .result(result).type("QUESTIONS")
                    .durationMs(System.currentTimeMillis() - start)
                    .success(true).build();
        } catch (Exception e) {
            log.error("Question generation failed", e);
            return StudyResponse.error(e.getMessage());
        }
    }

    

    private static final String EXPLAIN_TEMPLATE = """
            You are a patient tutor. A student wants a detailed explanation of: "{topic}"
            
            Use the provided study material as your reference. If the topic isn't directly covered,
            use your general knowledge to explain it clearly.
            
            Explanation structure:
            1. Simple definition (1-2 sentences, plain English)
            2. Why it matters / real-world relevance
            3. How it works (step by step if applicable)
            4. A concrete example or analogy
            5. Common mistakes or misconceptions to avoid
            
            Study Material (for reference):
            {content}
            
            Explanation of "{topic}":
            """;

    public StudyResponse explainTopic(StudyRequest request) {
        long start = System.currentTimeMillis();
        try {
            String topic = request.getTopic() != null ? request.getTopic() : "the main concepts";
            String content = truncate(request.getContent());

            String result = chatClient.prompt(
                    new PromptTemplate(EXPLAIN_TEMPLATE)
                            .create(Map.of("topic", topic, "content", content))
            ).call().content();

            return StudyResponse.builder()
                    .result(result).type("EXPLANATION")
                    .durationMs(System.currentTimeMillis() - start)
                    .success(true).build();
        } catch (Exception e) {
            log.error("Explain failed", e);
            return StudyResponse.error(e.getMessage());
        }
    }

    

    private static final String FLASHCARD_TEMPLATE = """
            You are creating flashcards for a student. Generate {count} flashcards from the study material.
            
            IMPORTANT: Respond ONLY with this exact format, one flashcard per block, no extra text:
            
            CARD_START
            Q: [Question]
            A: [Answer — concise, 1-2 sentences max]
            CARD_END
            
            Focus on: key terms, definitions, important facts, cause-effect relationships.
            
            Study Material:
            {content}
            """;

    public FlashcardResponse generateFlashcards(StudyRequest request) {
        long start = System.currentTimeMillis();
        try {
            int count = request.getQuestionCount() > 0 ? request.getQuestionCount() : 6;
            String content = truncate(request.getContent());

            String raw = chatClient.prompt(
                    new PromptTemplate(FLASHCARD_TEMPLATE)
                            .create(Map.of("content", content, "count", String.valueOf(count)))
            ).call().content();

            List<Flashcard> cards = parseFlashcards(raw);

            return FlashcardResponse.builder()
                    .cards(cards).count(cards.size())
                    .durationMs(System.currentTimeMillis() - start)
                    .success(true).build();
        } catch (Exception e) {
            log.error("Flashcard generation failed", e);
            return FlashcardResponse.builder()
                    .success(false).error(e.getMessage())
                    .cards(List.of()).build();
        }
    }

   
    private List<Flashcard> parseFlashcards(String raw) {
        List<Flashcard> cards = new ArrayList<>();
        String[] blocks = raw.split("CARD_START");

        for (String block : blocks) {
            if (!block.contains("CARD_END")) continue;
            String cardText = block.substring(0, block.indexOf("CARD_END")).trim();

            String question = "";
            String answer = "";

            for (String line : cardText.split("\n")) {
                line = line.trim();
                if (line.startsWith("Q:")) question = line.substring(2).trim();
                else if (line.startsWith("A:")) answer = line.substring(2).trim();
            }

            if (!question.isBlank() && !answer.isBlank()) {
                cards.add(Flashcard.builder().question(question).answer(answer).build());
            }
        }

        return cards;
    }

    private String truncate(String content) {
        if (content == null) return "";
        return content.length() > MAX_CONTENT_CHARS
                ? content.substring(0, MAX_CONTENT_CHARS) + "\n[Content truncated...]"
                : content;
    }
}
