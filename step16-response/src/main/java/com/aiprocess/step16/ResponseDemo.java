package com.aiprocess.step16;

/**
 * STEP 16: 응답 파싱 데모
 */
public class ResponseDemo {

    public static void main(String[] args) {
        System.out.println("═".repeat(60));
        System.out.println("STEP 16: 응답 파싱 (Response Parsing)");
        System.out.println("═".repeat(60));
        System.out.println();
        System.out.println("핵심 질문: AI 응답을 어떻게 처리하는가?");
        System.out.println();

        // 응답 파싱 데모
        demoJsonParsing();
        demoMarkdownParsing();
        demoFormatDetection();
    }

    private static void demoJsonParsing() {
        System.out.println("─".repeat(60));
        System.out.println("1. JSON 응답 파싱");
        System.out.println("─".repeat(60));
        System.out.println();

        String jsonResponse = """
            {
              "summary": "코드에서 2개의 심각한 보안 이슈가 발견되었습니다.",
              "issues": [
                {
                  "type": "SQL_INJECTION",
                  "severity": "CRITICAL",
                  "message": "문자열 연결을 사용한 SQL 쿼리는 SQL Injection에 취약합니다.",
                  "line": 8
                },
                {
                  "type": "HARDCODED_SECRET",
                  "severity": "CRITICAL",
                  "message": "비밀번호가 소스 코드에 하드코딩되어 있습니다.",
                  "line": 3
                }
              ],
              "suggestions": [
                "PreparedStatement를 사용하여 SQL Injection을 방지하세요.",
                "환경 변수나 시크릿 매니저를 사용하여 비밀번호를 관리하세요."
              ],
              "score": 35,
              "grade": "F"
            }
            """;

        System.out.println("  📥 JSON 응답:");
        System.out.println("  ─────────────────────────────────────────────────────");
        for (String line : jsonResponse.split("\n")) {
            if (!line.trim().isEmpty()) {
                System.out.println("  " + line);
            }
        }
        System.out.println("  ─────────────────────────────────────────────────────");
        System.out.println();

        ResponseParser parser = new ResponseParser();
        ResponseParser.ParsedResponse parsed = parser.parse(jsonResponse);

        System.out.println("  📤 파싱 결과:");
        System.out.println();
        System.out.println("     Format: " + parsed.format());
        System.out.println("     Success: " + parsed.success());
        System.out.println("     Summary: " + parsed.summary());
        System.out.println("     Score: " + parsed.score() + " (" + parsed.grade() + ")");
        System.out.println();

        System.out.println("     Issues (" + parsed.issues().size() + "개):");
        for (ResponseParser.Issue issue : parsed.issues()) {
            String emoji = issue.severity().equals("CRITICAL") ? "🚨" : "⚠️";
            System.out.printf("       %s [%s] Line %d: %s%n",
                emoji, issue.type(), issue.line(), issue.message());
        }
        System.out.println();

        System.out.println("     Suggestions:");
        for (String suggestion : parsed.suggestions()) {
            System.out.println("       • " + suggestion);
        }
        System.out.println();
    }

    private static void demoMarkdownParsing() {
        System.out.println("─".repeat(60));
        System.out.println("2. Markdown 응답 파싱");
        System.out.println("─".repeat(60));
        System.out.println();

        String markdownResponse = """
            ## Summary
            코드 품질 분석 결과입니다.

            ## Issues
            - [CRITICAL] EMPTY_CATCH: 예외를 무시하면 안 됩니다. (line 15)
            - [WARNING] SYSTEM_OUT: System.out 대신 로거를 사용하세요. (line 8)
            - [INFO] MAGIC_NUMBER: 상수로 추출하세요. (line 22)

            ## Suggestions
            - SLF4J Logger 사용을 권장합니다.
            - 예외 처리 시 최소한 로깅을 수행하세요.
            - 매직 넘버는 의미 있는 상수로 추출하세요.

            ## Fixed Code
            ```java
            private static final Logger logger = LoggerFactory.getLogger(MyClass.class);
            private static final int MAX_RETRY_COUNT = 3;
            ```

            Score: 72/100
            """;

        System.out.println("  📥 Markdown 응답:");
        System.out.println("  ─────────────────────────────────────────────────────");
        for (String line : markdownResponse.split("\n")) {
            System.out.println("  " + line);
        }
        System.out.println("  ─────────────────────────────────────────────────────");
        System.out.println();

        ResponseParser parser = new ResponseParser();
        ResponseParser.ParsedResponse parsed = parser.parse(markdownResponse);

        System.out.println("  📤 파싱 결과:");
        System.out.println();
        System.out.println("     Format: " + parsed.format());
        System.out.println("     Success: " + parsed.success());
        System.out.println("     Score: " + parsed.score());
        System.out.println();

        System.out.println("     Issues (" + parsed.issues().size() + "개):");
        for (ResponseParser.Issue issue : parsed.issues()) {
            String emoji = switch (issue.severity()) {
                case "CRITICAL" -> "🚨";
                case "WARNING" -> "⚠️";
                default -> "💡";
            };
            System.out.printf("       %s [%s] %s%n",
                emoji, issue.severity(), issue.message());
        }
        System.out.println();

        if (parsed.fixedCode() != null) {
            System.out.println("     Fixed Code:");
            for (String line : parsed.fixedCode().split("\n")) {
                System.out.println("       " + line);
            }
            System.out.println();
        }
    }

    private static void demoFormatDetection() {
        System.out.println("─".repeat(60));
        System.out.println("3. 응답 형식 자동 감지");
        System.out.println("─".repeat(60));
        System.out.println();

        ResponseParser parser = new ResponseParser();

        String[] samples = {
            "{\"result\": \"json\"}",
            "## Heading\n- bullet point",
            "This is plain text response."
        };

        System.out.println("  ┌─────────────────────────────────────────────────────┐");
        System.out.println("  │ 형식 자동 감지                                      │");
        System.out.println("  └─────────────────────────────────────────────────────┘");
        System.out.println();

        for (String sample : samples) {
            ResponseParser.ResponseFormat format = parser.detectFormat(sample);
            String emoji = switch (format) {
                case JSON -> "📋";
                case MARKDOWN -> "📝";
                case PLAIN_TEXT -> "📄";
            };

            System.out.println("  " + emoji + " " + format);
            System.out.println("     Input: \"" + truncate(sample.replace("\n", "\\n"), 40) + "\"");
            System.out.println();
        }

        System.out.println("  ┌─────────────────────────────────────────────────────┐");
        System.out.println("  │ 응답 파싱 핵심 포인트                               │");
        System.out.println("  └─────────────────────────────────────────────────────┘");
        System.out.println();
        System.out.println("  ✅ JSON 형식 권장: 파싱 용이, 구조화된 데이터");
        System.out.println("  ✅ 에러 처리: 파싱 실패 시 폴백 처리");
        System.out.println("  ✅ 검증: 필수 필드 존재 여부 확인");
        System.out.println("  ✅ 정규화: 일관된 데이터 구조로 변환");
        System.out.println();
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
    }
}
