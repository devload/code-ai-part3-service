package com.codeai.analyzer.fix;

import com.codeai.analyzer.llm.*;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 기반 자동 수정기
 *
 * LLM을 사용하여 복잡한 코드 이슈를 자동으로 수정합니다.
 *
 * 지원하는 수정:
 * - 복잡한 리팩토링 (메서드 추출, 클래스 분리)
 * - 알고리즘 최적화
 * - 보안 취약점 수정
 * - 코드 스타일 개선
 * - 버그 수정
 */
public class LLMAutoFixer {

    private static final String SYSTEM_PROMPT = """
        You are an expert code refactoring assistant. Your task is to fix code issues.

        Rules:
        1. Only modify the specific issues mentioned
        2. Preserve the original code structure and style as much as possible
        3. Keep variable and method names consistent
        4. Add necessary imports if needed
        5. Ensure the fixed code compiles

        Respond in the following JSON format:
        {
          "success": true,
          "fixedCode": "// the complete fixed code",
          "changes": [
            {
              "line": 10,
              "description": "수정 설명 (한국어)",
              "before": "original code snippet",
              "after": "fixed code snippet"
            }
          ],
          "explanation": "Overall explanation of changes in Korean"
        }

        IMPORTANT:
        - Return the COMPLETE fixed code, not just the changed parts
        - Ensure proper indentation and formatting
        - Keep all original comments
        """;

    private final LLMClient client;
    private final AutoFixer ruleFixer;
    private final Gson gson;

    public LLMAutoFixer(LLMClient client) {
        this.client = client;
        this.ruleFixer = new AutoFixer();
        this.gson = new Gson();
    }

    /**
     * 이슈 목록을 기반으로 자동 수정
     */
    public LLMFixResult fix(String code, List<String> issues) {
        // 1. 먼저 규칙 기반 수정 적용
        AutoFixer.FixReport ruleReport = ruleFixer.fix(code);
        String partiallyFixed = ruleReport.fixedCode();
        List<FixChange> allChanges = new ArrayList<>();

        // 규칙 기반 수정 결과 추가
        for (AutoFixer.FixResult fix : ruleReport.fixes()) {
            allChanges.add(new FixChange(
                fix.line(),
                "[규칙 기반] " + fix.description(),
                fix.before(),
                fix.after()
            ));
        }

        // 2. 남은 이슈에 대해 LLM 수정 요청
        List<String> remainingIssues = filterRemainingIssues(issues, ruleReport.fixes());

        if (remainingIssues.isEmpty()) {
            return new LLMFixResult(
                partiallyFixed,
                allChanges,
                "규칙 기반 수정으로 모든 이슈 해결",
                true,
                null
            );
        }

        // LLM 호출
        LLMClient.LLMResponse response = callLLM(partiallyFixed, remainingIssues);

        if (!response.success()) {
            return new LLMFixResult(
                partiallyFixed,
                allChanges,
                "LLM 수정 실패: " + response.error(),
                true,
                new LLMMetadata(client.getName(), 0)
            );
        }

        // 3. LLM 응답 파싱
        return parseFixResponse(response, allChanges);
    }

    /**
     * 특정 이슈 하나를 수정
     */
    public LLMFixResult fixIssue(String code, String issue) {
        return fix(code, List.of(issue));
    }

    /**
     * 전체 코드 개선
     */
    public LLMFixResult improve(String code) {
        String prompt = buildImprovePrompt(code);

        LLMClient.LLMResponse response = client.chat(
            new LLMClient.LLMRequest(SYSTEM_PROMPT, prompt, 0.2)
        );

        if (!response.success()) {
            return LLMFixResult.error(response.error());
        }

        return parseFixResponse(response, new ArrayList<>());
    }

    /**
     * 특정 라인 범위만 수정
     */
    public LLMFixResult fixLines(String code, int startLine, int endLine, String issue) {
        String[] lines = code.split("\n");
        StringBuilder context = new StringBuilder();

        // 컨텍스트 구성 (수정 대상 라인 주변)
        int contextStart = Math.max(0, startLine - 5);
        int contextEnd = Math.min(lines.length, endLine + 5);

        for (int i = contextStart; i < contextEnd; i++) {
            String marker = (i >= startLine - 1 && i <= endLine - 1) ? ">>>" : "   ";
            context.append(String.format("%s %4d: %s\n", marker, i + 1, lines[i]));
        }

        String prompt = String.format("""
            Fix the following issue in the marked lines (>>>):

            Issue: %s

            Code context:
            ```
            %s
            ```

            Provide the fixed version of ONLY the marked lines.
            """, issue, context);

        LLMClient.LLMResponse response = client.chat(
            new LLMClient.LLMRequest(SYSTEM_PROMPT, prompt, 0.2)
        );

        if (!response.success()) {
            return LLMFixResult.error(response.error());
        }

        return parseFixResponse(response, new ArrayList<>());
    }

    private List<String> filterRemainingIssues(List<String> issues, List<AutoFixer.FixResult> fixes) {
        List<String> remaining = new ArrayList<>();

        for (String issue : issues) {
            boolean fixed = fixes.stream().anyMatch(fix ->
                issue.toLowerCase().contains(fix.type().name().toLowerCase().replace("_", " "))
            );
            if (!fixed) {
                remaining.add(issue);
            }
        }

        return remaining;
    }

    private LLMClient.LLMResponse callLLM(String code, List<String> issues) {
        String prompt = buildFixPrompt(code, issues);

        return client.chat(new LLMClient.LLMRequest(
            SYSTEM_PROMPT,
            prompt,
            0.2  // 낮은 temperature로 일관된 출력
        ));
    }

    private String buildFixPrompt(String code, List<String> issues) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Fix the following issues in the code:\n\n");
        prompt.append("Issues to fix:\n");
        for (int i = 0; i < issues.size(); i++) {
            prompt.append(String.format("%d. %s\n", i + 1, issues.get(i)));
        }

        prompt.append("\nOriginal code:\n```java\n");
        prompt.append(code);
        prompt.append("\n```\n");

        return prompt.toString();
    }

    private String buildImprovePrompt(String code) {
        return String.format("""
            Analyze and improve the following code:

            ```java
            %s
            ```

            Focus on:
            1. Code quality and readability
            2. Performance optimizations
            3. Best practices
            4. Security improvements

            Return the improved code with explanations.
            """, code);
    }

    private LLMFixResult parseFixResponse(LLMClient.LLMResponse response, List<FixChange> existingChanges) {
        try {
            String content = response.content();

            // JSON 추출
            String json = extractJson(content);
            if (json == null) {
                // JSON이 없으면 코드 블록 추출 시도
                String codeBlock = extractCodeBlock(content);
                if (codeBlock != null) {
                    return new LLMFixResult(
                        codeBlock,
                        existingChanges,
                        "LLM이 수정된 코드를 반환했습니다.",
                        true,
                        new LLMMetadata(
                            client.getName(),
                            response.usage() != null ? response.usage().totalTokens() : 0
                        )
                    );
                }
                return LLMFixResult.error("응답에서 수정된 코드를 찾을 수 없습니다.");
            }

            JsonObject obj = gson.fromJson(json, JsonObject.class);

            if (obj.has("success") && !obj.get("success").getAsBoolean()) {
                String error = obj.has("error") ? obj.get("error").getAsString() : "Unknown error";
                return LLMFixResult.error(error);
            }

            String fixedCode = obj.has("fixedCode") ? obj.get("fixedCode").getAsString() : "";
            String explanation = obj.has("explanation") ? obj.get("explanation").getAsString() : "";

            List<FixChange> changes = new ArrayList<>(existingChanges);
            if (obj.has("changes")) {
                JsonArray changesArray = obj.getAsJsonArray("changes");
                for (int i = 0; i < changesArray.size(); i++) {
                    JsonObject changeObj = changesArray.get(i).getAsJsonObject();
                    changes.add(new FixChange(
                        changeObj.has("line") ? changeObj.get("line").getAsInt() : 0,
                        changeObj.has("description") ? changeObj.get("description").getAsString() : "",
                        changeObj.has("before") ? changeObj.get("before").getAsString() : "",
                        changeObj.has("after") ? changeObj.get("after").getAsString() : ""
                    ));
                }
            }

            return new LLMFixResult(
                fixedCode,
                changes,
                explanation,
                true,
                new LLMMetadata(
                    client.getName(),
                    response.usage() != null ? response.usage().totalTokens() : 0
                )
            );

        } catch (Exception e) {
            return LLMFixResult.error("응답 파싱 실패: " + e.getMessage());
        }
    }

    private String extractJson(String text) {
        Pattern pattern = Pattern.compile("\\{[\\s\\S]*\\}", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private String extractCodeBlock(String text) {
        Pattern pattern = Pattern.compile("```(?:java)?\\n([\\s\\S]*?)```", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    // ============ Inner Classes ============

    public record LLMFixResult(
        String fixedCode,
        List<FixChange> changes,
        String explanation,
        boolean success,
        LLMMetadata metadata
    ) {
        public static LLMFixResult error(String message) {
            return new LLMFixResult("", List.of(), message, false, null);
        }

        public String formatReport() {
            StringBuilder sb = new StringBuilder();

            sb.append("\n").append("=".repeat(60)).append("\n");
            sb.append("🤖 LLM 자동 수정 결과\n");
            sb.append("=".repeat(60)).append("\n\n");

            if (!success) {
                sb.append("❌ 수정 실패: ").append(explanation).append("\n");
                return sb.toString();
            }

            if (changes.isEmpty()) {
                sb.append("✅ 수정할 이슈가 없습니다.\n");
            } else {
                sb.append(String.format("📝 총 %d개 변경\n\n", changes.size()));

                for (FixChange change : changes) {
                    sb.append(String.format("• Line %d: %s\n", change.line(), change.description()));
                    if (!change.before().isEmpty()) {
                        sb.append(String.format("  - %s\n", truncate(change.before(), 60)));
                    }
                    if (!change.after().isEmpty()) {
                        sb.append(String.format("  + %s\n", truncate(change.after(), 60)));
                    }
                    sb.append("\n");
                }
            }

            if (explanation != null && !explanation.isEmpty()) {
                sb.append("💡 설명:\n");
                sb.append("   ").append(explanation).append("\n\n");
            }

            if (metadata != null) {
                sb.append("📌 분석 정보:\n");
                sb.append(String.format("   모델: %s\n", metadata.model()));
                sb.append(String.format("   토큰: %d\n", metadata.tokens()));
            }

            return sb.toString();
        }

        private String truncate(String text, int maxLen) {
            if (text.length() <= maxLen) return text;
            return text.substring(0, maxLen - 3) + "...";
        }

        public String formatDiff() {
            if (!success || fixedCode.isEmpty()) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("\n").append("=".repeat(60)).append("\n");
            sb.append("📄 수정된 코드\n");
            sb.append("=".repeat(60)).append("\n\n");
            sb.append(fixedCode);
            return sb.toString();
        }
    }

    public record FixChange(
        int line,
        String description,
        String before,
        String after
    ) {}

    public record LLMMetadata(
        String model,
        int tokens
    ) {}

    /**
     * 팩토리 메서드들
     */
    public static LLMAutoFixer withClaude(String apiKey) {
        return new LLMAutoFixer(ClaudeClient.builder().apiKey(apiKey).build());
    }

    public static LLMAutoFixer withClaude() {
        return new LLMAutoFixer(ClaudeClient.builder().build());
    }

    public static LLMAutoFixer withOpenAI(String apiKey) {
        return new LLMAutoFixer(OpenAIClient.builder().apiKey(apiKey).build());
    }

    public static LLMAutoFixer withOpenAI() {
        return new LLMAutoFixer(OpenAIClient.builder().build());
    }

    public static LLMAutoFixer withOllama(String model) {
        return new LLMAutoFixer(OllamaClient.builder().model(model).build());
    }

    public static LLMAutoFixer withOllama() {
        return new LLMAutoFixer(OllamaClient.builder().build());
    }
}
