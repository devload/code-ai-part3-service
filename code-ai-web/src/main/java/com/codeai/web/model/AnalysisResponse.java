package com.codeai.web.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 코드 분석 응답
 */
public record AnalysisResponse(
    String id,
    String filename,
    LocalDateTime timestamp,
    ScoreSummary scores,
    List<Issue> issues,
    List<String> positives,
    Statistics statistics,
    LLMInfo llmInfo,
    String fixedCode
) {
    public record ScoreSummary(
        int overall,
        String grade,
        Map<String, Integer> categories
    ) {}

    public record Issue(
        String code,
        String severity,
        int line,
        int column,
        String message,
        String suggestion,
        String category
    ) {}

    public record Statistics(
        int totalLines,
        int codeLines,
        int commentLines,
        int methods,
        int classes,
        int complexity
    ) {}

    public record LLMInfo(
        String provider,
        String model,
        int tokens,
        long latencyMs
    ) {}

    public static AnalysisResponse error(String message) {
        return new AnalysisResponse(
            null, null, LocalDateTime.now(),
            new ScoreSummary(0, "F", Map.of()),
            List.of(new Issue("ERROR", "CRITICAL", 0, 0, message, null, "system")),
            List.of(),
            null, null, null
        );
    }
}
