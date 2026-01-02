package com.codeai.analyzer.llm;

import com.codeai.analyzer.ai.AICodeReviewer;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 기반 코드 리뷰어
 *
 * 실제 LLM (Claude, GPT, Ollama)을 사용하여 코드 리뷰를 수행합니다.
 * 규칙 기반 분석과 LLM 분석을 결합하여 더 정확한 리뷰를 제공합니다.
 */
public class LLMCodeReviewer {

    private static final String SYSTEM_PROMPT = """
        You are an expert code reviewer. Analyze the provided code and give constructive feedback.

        Your review should include:
        1. Code quality issues (bugs, anti-patterns, code smells)
        2. Security vulnerabilities
        3. Performance issues
        4. Best practice violations
        5. Suggestions for improvement
        6. Positive aspects of the code

        For each issue, provide:
        - Line number (if applicable)
        - Severity: CRITICAL, ISSUE, SUGGESTION, or PRAISE
        - Clear explanation in Korean
        - Suggested fix (if applicable)

        Respond in the following JSON format:
        {
          "summary": "Brief overall assessment in Korean",
          "grade": "A/B/C/D/F",
          "score": 0-100,
          "issues": [
            {
              "type": "CRITICAL|ISSUE|SUGGESTION|PRAISE",
              "line": 0,
              "message": "설명 (한국어)",
              "suggestion": "개선 방법 또는 코드 (선택)"
            }
          ],
          "positives": ["좋은 점 1", "좋은 점 2"]
        }

        Be constructive and encouraging. Focus on the most important issues first.
        Always respond in Korean for messages and suggestions.
        """;

    private final LLMClient client;
    private final AICodeReviewer ruleBasedReviewer;
    private final Gson gson;

    public LLMCodeReviewer(LLMClient client) {
        this.client = client;
        this.ruleBasedReviewer = new AICodeReviewer();
        this.gson = new Gson();
    }

    /**
     * LLM 코드 리뷰 수행
     */
    public LLMReviewResult review(String code) {
        return review(code, null);
    }

    /**
     * 컨텍스트와 함께 LLM 코드 리뷰 수행
     */
    public LLMReviewResult review(String code, String context) {
        // 1. 규칙 기반 분석 먼저 수행
        AICodeReviewer.AIReviewResult ruleResult = ruleBasedReviewer.review(code);

        // 2. LLM 분석
        LLMClient.LLMResponse llmResponse = callLLM(code, context, ruleResult);

        if (!llmResponse.success()) {
            // LLM 실패 시 규칙 기반 결과만 반환
            return LLMReviewResult.fromRuleBasedResult(ruleResult, llmResponse.error());
        }

        // 3. LLM 응답 파싱
        LLMReviewResult llmResult = parseReviewResponse(llmResponse.content());

        // 4. 규칙 기반 결과와 병합
        return mergeResults(ruleResult, llmResult, llmResponse);
    }

    /**
     * 스트리밍 리뷰 (실시간 출력용)
     */
    public void reviewStream(String code, ReviewStreamHandler handler) {
        StringBuilder responseBuilder = new StringBuilder();

        String prompt = buildPrompt(code, null, null);

        client.chatStream(
            new LLMClient.LLMRequest(SYSTEM_PROMPT, prompt, 0.3),
            new LLMClient.StreamHandler() {
                @Override
                public void onToken(String token) {
                    responseBuilder.append(token);
                    handler.onToken(token);
                }

                @Override
                public void onComplete(LLMClient.LLMResponse response) {
                    LLMReviewResult result = parseReviewResponse(responseBuilder.toString());
                    handler.onComplete(result);
                }

                @Override
                public void onError(Throwable error) {
                    handler.onError(error);
                }
            }
        );
    }

    private LLMClient.LLMResponse callLLM(String code, String context, AICodeReviewer.AIReviewResult ruleResult) {
        String prompt = buildPrompt(code, context, ruleResult);

        return client.chat(new LLMClient.LLMRequest(
            SYSTEM_PROMPT,
            prompt,
            0.3  // 일관된 응답을 위해 낮은 temperature
        ));
    }

    private String buildPrompt(String code, String context, AICodeReviewer.AIReviewResult ruleResult) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Please review the following code:\n\n");
        prompt.append("```java\n");
        prompt.append(code);
        prompt.append("\n```\n\n");

        if (context != null && !context.isEmpty()) {
            prompt.append("Additional context:\n");
            prompt.append(context);
            prompt.append("\n\n");
        }

        if (ruleResult != null && !ruleResult.comments.isEmpty()) {
            prompt.append("Rule-based analysis found the following issues (you may confirm or provide additional insights):\n");
            for (AICodeReviewer.ReviewComment comment : ruleResult.comments) {
                prompt.append(String.format("- Line %d [%s]: %s\n",
                    comment.line, comment.type, comment.message));
            }
            prompt.append("\n");
        }

        return prompt.toString();
    }

    private LLMReviewResult parseReviewResponse(String response) {
        try {
            // JSON 부분 추출
            String json = extractJson(response);
            if (json == null) {
                return LLMReviewResult.error("LLM 응답에서 JSON을 찾을 수 없습니다.");
            }

            JsonObject obj = gson.fromJson(json, JsonObject.class);

            String summary = obj.has("summary") ? obj.get("summary").getAsString() : "";
            String grade = obj.has("grade") ? obj.get("grade").getAsString() : "C";
            int score = obj.has("score") ? obj.get("score").getAsInt() : 70;

            List<ReviewIssue> issues = new ArrayList<>();
            if (obj.has("issues")) {
                JsonArray issuesArray = obj.getAsJsonArray("issues");
                for (int i = 0; i < issuesArray.size(); i++) {
                    JsonObject issueObj = issuesArray.get(i).getAsJsonObject();
                    issues.add(new ReviewIssue(
                        issueObj.has("type") ? issueObj.get("type").getAsString() : "SUGGESTION",
                        issueObj.has("line") ? issueObj.get("line").getAsInt() : 0,
                        issueObj.has("message") ? issueObj.get("message").getAsString() : "",
                        issueObj.has("suggestion") ? issueObj.get("suggestion").getAsString() : null
                    ));
                }
            }

            List<String> positives = new ArrayList<>();
            if (obj.has("positives")) {
                JsonArray posArray = obj.getAsJsonArray("positives");
                for (int i = 0; i < posArray.size(); i++) {
                    positives.add(posArray.get(i).getAsString());
                }
            }

            return new LLMReviewResult(summary, grade, score, issues, positives, null, true);

        } catch (Exception e) {
            return LLMReviewResult.error("응답 파싱 실패: " + e.getMessage());
        }
    }

    private String extractJson(String text) {
        // JSON 블록 찾기
        Pattern pattern = Pattern.compile("\\{[\\s\\S]*\\}", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private LLMReviewResult mergeResults(
            AICodeReviewer.AIReviewResult ruleResult,
            LLMReviewResult llmResult,
            LLMClient.LLMResponse llmResponse) {

        // LLM 결과에 규칙 기반 critical 이슈 추가 (놓친 것 확인)
        List<ReviewIssue> mergedIssues = new ArrayList<>(llmResult.issues);

        for (AICodeReviewer.ReviewComment comment : ruleResult.comments) {
            if (comment.type == AICodeReviewer.ReviewType.CRITICAL) {
                // 같은 라인에 이미 있는지 확인
                boolean exists = mergedIssues.stream()
                    .anyMatch(i -> i.line == comment.line && i.type.equals("CRITICAL"));

                if (!exists) {
                    mergedIssues.add(new ReviewIssue(
                        "CRITICAL",
                        comment.line,
                        "[규칙 기반] " + comment.message,
                        comment.suggestion
                    ));
                }
            }
        }

        return new LLMReviewResult(
            llmResult.summary,
            llmResult.grade,
            llmResult.score,
            mergedIssues,
            llmResult.positives,
            new LLMMetadata(
                client.getName(),
                llmResponse.usage() != null ? llmResponse.usage().totalTokens() : 0
            ),
            true
        );
    }

    // ============ Inner Classes ============

    public record LLMReviewResult(
        String summary,
        String grade,
        int score,
        List<ReviewIssue> issues,
        List<String> positives,
        LLMMetadata metadata,
        boolean success
    ) {
        public static LLMReviewResult error(String message) {
            return new LLMReviewResult(
                message, "F", 0, List.of(), List.of(), null, false
            );
        }

        public static LLMReviewResult fromRuleBasedResult(
                AICodeReviewer.AIReviewResult ruleResult, String llmError) {

            List<ReviewIssue> issues = ruleResult.comments.stream()
                .map(c -> new ReviewIssue(c.type.name(), c.line, c.message, c.suggestion))
                .toList();

            return new LLMReviewResult(
                "LLM 분석 실패 (" + llmError + "). 규칙 기반 분석 결과만 표시합니다.",
                ruleResult.score.getGrade(),
                ruleResult.score.getOverallScore(),
                issues,
                List.of(),
                null,
                true
            );
        }

        public String formatReport() {
            StringBuilder sb = new StringBuilder();

            sb.append("\n").append("=".repeat(60)).append("\n");
            sb.append("🤖 LLM 코드 리뷰 결과\n");
            sb.append("=".repeat(60)).append("\n\n");

            // 등급
            String gradeEmoji = switch (grade) {
                case "A" -> "🌟";
                case "B" -> "👍";
                case "C" -> "👌";
                case "D" -> "⚠️";
                default -> "❌";
            };
            sb.append(String.format("📊 등급: %s %s (%d/100)\n\n", gradeEmoji, grade, score));

            // 요약
            if (summary != null && !summary.isEmpty()) {
                sb.append("📝 요약:\n");
                sb.append("   ").append(summary).append("\n\n");
            }

            // 좋은 점
            if (positives != null && !positives.isEmpty()) {
                sb.append("✨ 좋은 점:\n");
                for (String positive : positives) {
                    sb.append("   • ").append(positive).append("\n");
                }
                sb.append("\n");
            }

            // 이슈
            if (issues != null && !issues.isEmpty()) {
                sb.append("🔍 발견된 이슈:\n");
                sb.append("-".repeat(60)).append("\n");

                for (ReviewIssue issue : issues) {
                    String icon = switch (issue.type) {
                        case "CRITICAL" -> "🚨";
                        case "ISSUE" -> "⚠️";
                        case "SUGGESTION" -> "💡";
                        case "PRAISE" -> "👍";
                        default -> "📝";
                    };

                    sb.append(String.format("%s [%s] Line %d:\n", icon, issue.type, issue.line));
                    sb.append("   ").append(issue.message).append("\n");

                    if (issue.suggestion != null && !issue.suggestion.isEmpty()) {
                        sb.append("\n   💡 제안:\n");
                        for (String line : issue.suggestion.split("\n")) {
                            sb.append("      ").append(line).append("\n");
                        }
                    }
                    sb.append("-".repeat(60)).append("\n");
                }
            }

            // 메타데이터
            if (metadata != null) {
                sb.append("\n📌 분석 정보:\n");
                sb.append(String.format("   모델: %s\n", metadata.model));
                sb.append(String.format("   토큰: %d\n", metadata.tokens));
            }

            return sb.toString();
        }
    }

    public record ReviewIssue(
        String type,
        int line,
        String message,
        String suggestion
    ) {}

    public record LLMMetadata(
        String model,
        int tokens
    ) {}

    public interface ReviewStreamHandler {
        void onToken(String token);
        void onComplete(LLMReviewResult result);
        void onError(Throwable error);
    }

    /**
     * 팩토리 메서드들
     */
    public static LLMCodeReviewer withClaude(String apiKey) {
        return new LLMCodeReviewer(ClaudeClient.builder().apiKey(apiKey).build());
    }

    public static LLMCodeReviewer withClaude() {
        return new LLMCodeReviewer(ClaudeClient.builder().build());
    }

    public static LLMCodeReviewer withOpenAI(String apiKey) {
        return new LLMCodeReviewer(OpenAIClient.builder().apiKey(apiKey).build());
    }

    public static LLMCodeReviewer withOpenAI() {
        return new LLMCodeReviewer(OpenAIClient.builder().build());
    }

    public static LLMCodeReviewer withOllama(String model) {
        return new LLMCodeReviewer(OllamaClient.builder().model(model).build());
    }

    public static LLMCodeReviewer withOllama() {
        return new LLMCodeReviewer(OllamaClient.builder().build());
    }
}
