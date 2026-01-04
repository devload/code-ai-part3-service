package com.aiprocess.step14;

/**
 * STEP 14: 프롬프트 구성 데모
 */
public class PromptDemo {

    public static void main(String[] args) {
        System.out.println("═".repeat(60));
        System.out.println("STEP 14: 프롬프트 구성 (Prompt Engineering)");
        System.out.println("═".repeat(60));
        System.out.println();
        System.out.println("핵심 질문: 좋은 프롬프트는 어떻게 만드는가?");
        System.out.println();

        // 프롬프트 구성 데모
        demoPromptStructure();
        demoRoles();
        demoCodeReviewPrompt();
        demoPromptTechniques();
    }

    private static void demoPromptStructure() {
        System.out.println("─".repeat(60));
        System.out.println("1. 프롬프트 구조");
        System.out.println("─".repeat(60));
        System.out.println();

        System.out.println("  ┌─────────────────────────────────────────────────────┐");
        System.out.println("  │ 효과적인 프롬프트의 4가지 요소                        │");
        System.out.println("  └─────────────────────────────────────────────────────┘");
        System.out.println();

        System.out.println("  ┌─────────────────────────────────────────────────────┐");
        System.out.println("  │ 1. SYSTEM PROMPT (역할 정의)                         │");
        System.out.println("  │    \"You are an expert code reviewer...\"             │");
        System.out.println("  │                                                      │");
        System.out.println("  │ 2. CONTEXT (배경 정보)                               │");
        System.out.println("  │    코드, 분석 결과, 이전 대화                        │");
        System.out.println("  │                                                      │");
        System.out.println("  │ 3. TASK (작업 지시)                                  │");
        System.out.println("  │    \"Analyze this code for security issues...\"       │");
        System.out.println("  │                                                      │");
        System.out.println("  │ 4. OUTPUT FORMAT (출력 형식)                         │");
        System.out.println("  │    \"Respond in JSON format with...\"                 │");
        System.out.println("  └─────────────────────────────────────────────────────┘");
        System.out.println();
    }

    private static void demoRoles() {
        System.out.println("─".repeat(60));
        System.out.println("2. AI 역할 정의");
        System.out.println("─".repeat(60));
        System.out.println();

        System.out.println("  🎭 사전 정의된 역할들:");
        System.out.println();

        for (PromptBuilder.Role role : PromptBuilder.Role.values()) {
            System.out.println("  ┌─────────────────────────────────────────────────────┐");
            System.out.printf("  │ %-51s │%n", role.name());
            System.out.println("  └─────────────────────────────────────────────────────┘");

            String prompt = role.getSystemPrompt();
            String[] lines = prompt.split("\n");
            for (int i = 0; i < Math.min(2, lines.length); i++) {
                String line = lines[i].trim();
                if (!line.isEmpty()) {
                    System.out.println("     \"" + truncate(line, 45) + "...\"");
                }
            }
            System.out.println();
        }
    }

    private static void demoCodeReviewPrompt() {
        System.out.println("─".repeat(60));
        System.out.println("3. 코드 리뷰 프롬프트 생성");
        System.out.println("─".repeat(60));
        System.out.println();

        String sampleCode = """
            public class UserService {
                private String dbPassword = "admin123";

                public User findUser(String id) {
                    String query = "SELECT * FROM users WHERE id=" + id;
                    return execute(query);
                }
            }
            """;

        System.out.println("  📝 입력 코드:");
        System.out.println("  ─────────────────────────────────────────────────────");
        for (String line : sampleCode.split("\n")) {
            System.out.println("  " + line);
        }
        System.out.println("  ─────────────────────────────────────────────────────");
        System.out.println();

        // 프롬프트 생성
        PromptBuilder.Prompt prompt = PromptBuilder.codeReviewPrompt(sampleCode, "java");

        System.out.println("  🔧 생성된 프롬프트:");
        System.out.println();

        System.out.println("  ┌─ System Prompt ─────────────────────────────────────┐");
        printWrapped(prompt.systemPrompt(), 55, "  │ ");
        System.out.println("  └─────────────────────────────────────────────────────┘");
        System.out.println();

        System.out.println("  ┌─ User Prompt ───────────────────────────────────────┐");
        String[] userLines = prompt.userPrompt().split("\n");
        for (int i = 0; i < Math.min(10, userLines.length); i++) {
            System.out.printf("  │ %-53s │%n", truncate(userLines[i], 53));
        }
        if (userLines.length > 10) {
            System.out.printf("  │ %-53s │%n", "... (" + (userLines.length - 10) + " more lines)");
        }
        System.out.println("  └─────────────────────────────────────────────────────┘");
        System.out.println();

        System.out.println("  📊 토큰 추정: ~" + prompt.estimateTokens() + " tokens");
        System.out.println("  📤 출력 형식: " + prompt.outputFormat());
        System.out.println();
    }

    private static void demoPromptTechniques() {
        System.out.println("─".repeat(60));
        System.out.println("4. 프롬프트 엔지니어링 기법");
        System.out.println("─".repeat(60));
        System.out.println();

        System.out.println("  ┌─────────────────────────────────────────────────────┐");
        System.out.println("  │ 핵심 기법                                            │");
        System.out.println("  └─────────────────────────────────────────────────────┘");
        System.out.println();

        System.out.println("  1️⃣  Zero-shot Prompting");
        System.out.println("     • 예시 없이 직접 지시");
        System.out.println("     • \"Analyze this code for bugs\"");
        System.out.println();

        System.out.println("  2️⃣  Few-shot Prompting");
        System.out.println("     • 몇 가지 예시 제공");
        System.out.println("     • \"Example 1: ... Output: ...\"");
        System.out.println();

        System.out.println("  3️⃣  Chain-of-Thought");
        System.out.println("     • 단계별 사고 유도");
        System.out.println("     • \"Think step by step...\"");
        System.out.println();

        System.out.println("  4️⃣  Structured Output");
        System.out.println("     • JSON/XML 형식 요청");
        System.out.println("     • 파싱 용이, 일관성 확보");
        System.out.println();

        // Few-shot 예시 데모
        System.out.println("  📚 Few-shot 프롬프트 예시:");
        System.out.println();

        PromptBuilder.Prompt fewShotPrompt = new PromptBuilder()
            .withRole(PromptBuilder.Role.CODE_REVIEWER)
            .addExample(
                "System.out.println(\"debug\");",
                "{\"issue\": \"SYSTEM_OUT\", \"severity\": \"WARNING\"}"
            )
            .addExample(
                "catch(Exception e) {}",
                "{\"issue\": \"EMPTY_CATCH\", \"severity\": \"CRITICAL\"}"
            )
            .addCode("String password = \"admin123\";", "java")
            .withTask("Identify the issue in this code")
            .withOutputFormat(PromptBuilder.OutputFormat.STRUCTURED_JSON)
            .build();

        System.out.println("  ─────────────────────────────────────────────────────");
        String[] lines = fewShotPrompt.userPrompt().split("\n");
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                System.out.println("  " + truncate(line, 55));
            }
        }
        System.out.println("  ─────────────────────────────────────────────────────");
        System.out.println();

        System.out.println("  ✨ 프롬프트 작성 팁:");
        System.out.println("     • 구체적이고 명확하게 작성");
        System.out.println("     • 원하는 출력 형식 명시");
        System.out.println("     • 예시 제공으로 품질 향상");
        System.out.println("     • 토큰 수 고려 (비용 최적화)");
        System.out.println();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
    }

    private static void printWrapped(String text, int width, String prefix) {
        if (text == null) return;
        String[] lines = text.split("\n");
        for (String line : lines) {
            while (line.length() > width) {
                System.out.println(prefix + line.substring(0, width));
                line = line.substring(width);
            }
            if (!line.isEmpty()) {
                System.out.printf("%s%-" + width + "s │%n", prefix, line);
            }
        }
    }
}
