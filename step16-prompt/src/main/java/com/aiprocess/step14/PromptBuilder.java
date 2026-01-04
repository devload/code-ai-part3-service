package com.aiprocess.step14;

import java.util.*;

/**
 * STEP 14: 프롬프트 구성
 *
 * 핵심 질문: 좋은 프롬프트는 어떻게 만드는가?
 *
 * LLM에게 효과적으로 지시하기 위한 프롬프트 엔지니어링입니다.
 *
 * ┌─────────────────────────────────────────────────────────┐
 * │ 프롬프트 구성 요소                                        │
 * │                                                          │
 * │ 1. System Prompt (역할 정의)                             │
 * │    - AI의 역할과 제약사항 설정                           │
 * │    - 응답 형식 지정                                      │
 * │                                                          │
 * │ 2. Context (배경 정보)                                   │
 * │    - 코드 정보, 분석 결과                                │
 * │    - 이전 대화 기록                                      │
 * │                                                          │
 * │ 3. Task (작업 지시)                                      │
 * │    - 구체적인 수행 요청                                  │
 * │    - 예시 포함 (Few-shot)                                │
 * │                                                          │
 * │ 4. Output Format (출력 형식)                             │
 * │    - JSON, Markdown 등                                   │
 * │    - 구조화된 응답 요청                                  │
 * └─────────────────────────────────────────────────────────┘
 */
public class PromptBuilder {

    private String systemPrompt;
    private final List<ContextItem> context;
    private String task;
    private OutputFormat outputFormat;
    private final List<Example> examples;

    public PromptBuilder() {
        this.context = new ArrayList<>();
        this.examples = new ArrayList<>();
        this.outputFormat = OutputFormat.TEXT;
    }

    /**
     * 시스템 프롬프트 설정 (AI 역할)
     */
    public PromptBuilder withSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        return this;
    }

    /**
     * 사전 정의된 역할 사용
     */
    public PromptBuilder withRole(Role role) {
        this.systemPrompt = role.getSystemPrompt();
        return this;
    }

    /**
     * 컨텍스트 추가
     */
    public PromptBuilder addContext(String label, String content) {
        this.context.add(new ContextItem(label, content));
        return this;
    }

    /**
     * 코드 컨텍스트 추가
     */
    public PromptBuilder addCode(String code, String language) {
        String formatted = String.format("```%s\n%s\n```", language, code);
        this.context.add(new ContextItem("Code", formatted));
        return this;
    }

    /**
     * 작업 지시 설정
     */
    public PromptBuilder withTask(String task) {
        this.task = task;
        return this;
    }

    /**
     * 출력 형식 설정
     */
    public PromptBuilder withOutputFormat(OutputFormat format) {
        this.outputFormat = format;
        return this;
    }

    /**
     * Few-shot 예시 추가
     */
    public PromptBuilder addExample(String input, String output) {
        this.examples.add(new Example(input, output));
        return this;
    }

    /**
     * 최종 프롬프트 빌드
     */
    public Prompt build() {
        StringBuilder userContent = new StringBuilder();

        // 컨텍스트 추가
        if (!context.isEmpty()) {
            for (ContextItem item : context) {
                userContent.append("## ").append(item.label()).append("\n");
                userContent.append(item.content()).append("\n\n");
            }
        }

        // Few-shot 예시 추가
        if (!examples.isEmpty()) {
            userContent.append("## Examples\n\n");
            for (int i = 0; i < examples.size(); i++) {
                Example ex = examples.get(i);
                userContent.append("### Example ").append(i + 1).append("\n");
                userContent.append("Input: ").append(ex.input()).append("\n");
                userContent.append("Output: ").append(ex.output()).append("\n\n");
            }
        }

        // 작업 지시 추가
        if (task != null) {
            userContent.append("## Task\n");
            userContent.append(task).append("\n\n");
        }

        // 출력 형식 지시
        userContent.append("## Output Format\n");
        userContent.append(outputFormat.getInstruction()).append("\n");

        return new Prompt(
            systemPrompt,
            userContent.toString().trim(),
            outputFormat
        );
    }

    /**
     * 코드 리뷰용 프롬프트 템플릿
     */
    public static Prompt codeReviewPrompt(String code, String language) {
        return new PromptBuilder()
            .withRole(Role.CODE_REVIEWER)
            .addCode(code, language)
            .withTask("Analyze this code and provide a detailed review. Focus on:\n" +
                      "1. Code quality issues\n" +
                      "2. Potential bugs\n" +
                      "3. Security vulnerabilities\n" +
                      "4. Suggestions for improvement")
            .withOutputFormat(OutputFormat.STRUCTURED_JSON)
            .build();
    }

    /**
     * 코드 수정용 프롬프트 템플릿
     */
    public static Prompt codeFixPrompt(String code, String issue, String language) {
        return new PromptBuilder()
            .withRole(Role.CODE_FIXER)
            .addCode(code, language)
            .addContext("Issue", issue)
            .withTask("Fix the identified issue in this code. " +
                      "Provide the corrected code with minimal changes.")
            .withOutputFormat(OutputFormat.CODE_ONLY)
            .build();
    }

    /**
     * 코드 설명용 프롬프트 템플릿
     */
    public static Prompt codeExplainPrompt(String code, String language) {
        return new PromptBuilder()
            .withRole(Role.CODE_EXPLAINER)
            .addCode(code, language)
            .withTask("Explain what this code does in simple terms. " +
                      "Break down the logic step by step.")
            .withOutputFormat(OutputFormat.MARKDOWN)
            .build();
    }

    /**
     * AI 역할 정의
     */
    public enum Role {
        CODE_REVIEWER("""
            You are an expert code reviewer with deep knowledge of software engineering best practices.
            Your role is to analyze code for quality, bugs, and security issues.
            Be constructive and specific in your feedback.
            Always explain WHY something is an issue, not just WHAT the issue is.
            """),

        CODE_FIXER("""
            You are a code fixing assistant.
            Your role is to fix specific issues in code while making minimal changes.
            Preserve the original code style and structure.
            Only fix what is asked - do not refactor unrelated parts.
            """),

        CODE_EXPLAINER("""
            You are a patient programming teacher.
            Your role is to explain code in simple, clear terms.
            Use analogies and examples to make concepts understandable.
            Assume the reader is a beginner.
            """),

        SECURITY_AUDITOR("""
            You are a security expert specializing in code security audits.
            Your role is to identify security vulnerabilities and suggest fixes.
            Focus on OWASP Top 10 and common security anti-patterns.
            Provide severity ratings for each issue found.
            """);

        private final String systemPrompt;

        Role(String systemPrompt) {
            this.systemPrompt = systemPrompt;
        }

        public String getSystemPrompt() { return systemPrompt; }
    }

    /**
     * 출력 형식
     */
    public enum OutputFormat {
        TEXT("Respond in plain text."),
        MARKDOWN("Respond in Markdown format with proper headings and code blocks."),
        CODE_ONLY("Respond with ONLY the code, no explanations or markdown."),
        STRUCTURED_JSON("""
            Respond in JSON format with this structure:
            {
              "summary": "Brief summary",
              "issues": [{"type": "...", "severity": "...", "message": "...", "line": N}],
              "suggestions": ["..."],
              "score": N
            }
            """);

        private final String instruction;

        OutputFormat(String instruction) {
            this.instruction = instruction;
        }

        public String getInstruction() { return instruction; }
    }

    public record ContextItem(String label, String content) {}
    public record Example(String input, String output) {}

    /**
     * 완성된 프롬프트
     */
    public record Prompt(
        String systemPrompt,
        String userPrompt,
        OutputFormat outputFormat
    ) {
        public int estimateTokens() {
            // 대략적인 토큰 추정 (영어 기준 4문자 = 1토큰)
            int systemTokens = systemPrompt != null ? systemPrompt.length() / 4 : 0;
            int userTokens = userPrompt.length() / 4;
            return systemTokens + userTokens;
        }
    }
}
