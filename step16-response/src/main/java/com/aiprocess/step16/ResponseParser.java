package com.aiprocess.step16;

import com.google.gson.*;
import java.util.*;
import java.util.regex.*;

/**
 * STEP 16: 응답 파싱
 *
 * 핵심 질문: AI 응답을 어떻게 처리하는가?
 *
 * LLM의 다양한 응답 형식을 파싱하고 구조화합니다.
 *
 * ┌─────────────────────────────────────────────────────────┐
 * │ 응답 파싱 과정                                           │
 * │                                                          │
 * │ 1. 형식 감지                                             │
 * │    - JSON, Markdown, Plain Text                          │
 * │    - 코드 블록 추출                                      │
 * │                                                          │
 * │ 2. 구조 추출                                             │
 * │    - 이슈 목록                                           │
 * │    - 코드 수정 제안                                      │
 * │    - 점수/등급                                           │
 * │                                                          │
 * │ 3. 검증 및 정규화                                        │
 * │    - 필수 필드 확인                                      │
 * │    - 타입 변환                                           │
 * │    - 기본값 적용                                         │
 * └─────────────────────────────────────────────────────────┘
 */
public class ResponseParser {

    private final Gson gson;

    public ResponseParser() {
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    }

    /**
     * 응답 파싱 (자동 형식 감지)
     */
    public ParsedResponse parse(String rawResponse) {
        ResponseFormat format = detectFormat(rawResponse);

        return switch (format) {
            case JSON -> parseJson(rawResponse);
            case MARKDOWN -> parseMarkdown(rawResponse);
            case PLAIN_TEXT -> parsePlainText(rawResponse);
        };
    }

    /**
     * 응답 형식 감지
     */
    public ResponseFormat detectFormat(String response) {
        String trimmed = response.trim();

        // JSON 감지
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            try {
                JsonParser.parseString(trimmed);
                return ResponseFormat.JSON;
            } catch (JsonSyntaxException e) {
                // JSON이 아님
            }
        }

        // JSON 코드 블록 감지
        if (trimmed.contains("```json")) {
            return ResponseFormat.JSON;
        }

        // Markdown 감지
        if (trimmed.contains("##") || trimmed.contains("**") ||
            trimmed.contains("```") || trimmed.contains("- ")) {
            return ResponseFormat.MARKDOWN;
        }

        return ResponseFormat.PLAIN_TEXT;
    }

    /**
     * JSON 응답 파싱
     */
    private ParsedResponse parseJson(String response) {
        try {
            // JSON 코드 블록에서 추출
            String jsonContent = extractJsonBlock(response);

            JsonObject json = JsonParser.parseString(jsonContent).getAsJsonObject();

            // 표준 필드 추출
            String summary = getStringOrNull(json, "summary");
            List<Issue> issues = parseIssues(json);
            List<String> suggestions = parseStringArray(json, "suggestions");
            String fixedCode = getStringOrNull(json, "fixed_code");
            Integer score = getIntOrNull(json, "score");
            String grade = getStringOrNull(json, "grade");

            return new ParsedResponse(
                ResponseFormat.JSON,
                true,
                summary,
                issues,
                suggestions,
                fixedCode,
                score,
                grade,
                null
            );
        } catch (Exception e) {
            return ParsedResponse.error("JSON parsing failed: " + e.getMessage());
        }
    }

    /**
     * Markdown 응답 파싱
     */
    private ParsedResponse parseMarkdown(String response) {
        String summary = extractSection(response, "Summary", "##");
        List<Issue> issues = extractIssuesFromMarkdown(response);
        List<String> suggestions = extractBulletPoints(response, "Suggestions");
        String fixedCode = extractCodeBlock(response);
        Integer score = extractScore(response);

        return new ParsedResponse(
            ResponseFormat.MARKDOWN,
            true,
            summary,
            issues,
            suggestions,
            fixedCode,
            score,
            null,
            null
        );
    }

    /**
     * Plain text 응답 파싱
     */
    private ParsedResponse parsePlainText(String response) {
        // 단순 텍스트를 요약으로 처리
        return new ParsedResponse(
            ResponseFormat.PLAIN_TEXT,
            true,
            response.trim(),
            List.of(),
            List.of(),
            null,
            null,
            null,
            null
        );
    }

    /**
     * Claude/OpenAI 응답에서 텍스트 추출
     */
    public String extractContent(JsonObject apiResponse, String provider) {
        try {
            return switch (provider.toLowerCase()) {
                case "claude" -> {
                    JsonArray content = apiResponse.getAsJsonArray("content");
                    if (content != null && !content.isEmpty()) {
                        yield content.get(0).getAsJsonObject().get("text").getAsString();
                    }
                    yield "";
                }
                case "openai" -> {
                    JsonArray choices = apiResponse.getAsJsonArray("choices");
                    if (choices != null && !choices.isEmpty()) {
                        yield choices.get(0).getAsJsonObject()
                            .getAsJsonObject("message")
                            .get("content").getAsString();
                    }
                    yield "";
                }
                case "ollama" -> {
                    if (apiResponse.has("response")) {
                        yield apiResponse.get("response").getAsString();
                    }
                    yield "";
                }
                default -> "";
            };
        } catch (Exception e) {
            return "";
        }
    }

    // Helper methods

    private String extractJsonBlock(String response) {
        Pattern pattern = Pattern.compile("```json\\s*([\\s\\S]*?)```");
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // JSON 블록이 없으면 전체가 JSON인지 확인
        String trimmed = response.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed;
        }

        return response;
    }

    private List<Issue> parseIssues(JsonObject json) {
        List<Issue> issues = new ArrayList<>();
        if (json.has("issues") && json.get("issues").isJsonArray()) {
            for (JsonElement elem : json.getAsJsonArray("issues")) {
                JsonObject issueObj = elem.getAsJsonObject();
                issues.add(new Issue(
                    getStringOrNull(issueObj, "type"),
                    getStringOrNull(issueObj, "severity"),
                    getStringOrNull(issueObj, "message"),
                    getIntOrNull(issueObj, "line")
                ));
            }
        }
        return issues;
    }

    private List<String> parseStringArray(JsonObject json, String field) {
        List<String> result = new ArrayList<>();
        if (json.has(field) && json.get(field).isJsonArray()) {
            for (JsonElement elem : json.getAsJsonArray(field)) {
                result.add(elem.getAsString());
            }
        }
        return result;
    }

    private String getStringOrNull(JsonObject json, String field) {
        return json.has(field) && !json.get(field).isJsonNull()
            ? json.get(field).getAsString()
            : null;
    }

    private Integer getIntOrNull(JsonObject json, String field) {
        return json.has(field) && !json.get(field).isJsonNull()
            ? json.get(field).getAsInt()
            : null;
    }

    private String extractSection(String markdown, String header, String marker) {
        Pattern pattern = Pattern.compile(
            marker + "\\s*" + header + "[\\s\\S]*?\\n([\\s\\S]*?)(?=" + marker + "|$)",
            Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(markdown);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private List<Issue> extractIssuesFromMarkdown(String markdown) {
        List<Issue> issues = new ArrayList<>();

        // 패턴: - [SEVERITY] TYPE: message (line N)
        Pattern pattern = Pattern.compile(
            "-\\s*\\[?(CRITICAL|WARNING|INFO)\\]?\\s*([A-Z_]+)?:?\\s*(.+?)(?:\\(line\\s*(\\d+)\\))?$",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = pattern.matcher(markdown);
        while (matcher.find()) {
            issues.add(new Issue(
                matcher.group(2),
                matcher.group(1).toUpperCase(),
                matcher.group(3).trim(),
                matcher.group(4) != null ? Integer.parseInt(matcher.group(4)) : null
            ));
        }

        return issues;
    }

    private List<String> extractBulletPoints(String markdown, String section) {
        List<String> points = new ArrayList<>();
        String sectionContent = extractSection(markdown, section, "##");
        if (sectionContent != null) {
            Pattern pattern = Pattern.compile("^\\s*[-*]\\s+(.+)$", Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(sectionContent);
            while (matcher.find()) {
                points.add(matcher.group(1).trim());
            }
        }
        return points;
    }

    private String extractCodeBlock(String markdown) {
        Pattern pattern = Pattern.compile("```(?:java|python|javascript)?\\s*([\\s\\S]*?)```");
        Matcher matcher = pattern.matcher(markdown);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private Integer extractScore(String markdown) {
        Pattern pattern = Pattern.compile("(?:score|점수)[:\\s]*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(markdown);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    /**
     * 응답 형식
     */
    public enum ResponseFormat {
        JSON, MARKDOWN, PLAIN_TEXT
    }

    /**
     * 파싱된 이슈
     */
    public record Issue(
        String type,
        String severity,
        String message,
        Integer line
    ) {}

    /**
     * 파싱된 응답
     */
    public record ParsedResponse(
        ResponseFormat format,
        boolean success,
        String summary,
        List<Issue> issues,
        List<String> suggestions,
        String fixedCode,
        Integer score,
        String grade,
        String error
    ) {
        public static ParsedResponse error(String errorMessage) {
            return new ParsedResponse(
                ResponseFormat.PLAIN_TEXT,
                false,
                null,
                List.of(),
                List.of(),
                null,
                null,
                null,
                errorMessage
            );
        }
    }
}
