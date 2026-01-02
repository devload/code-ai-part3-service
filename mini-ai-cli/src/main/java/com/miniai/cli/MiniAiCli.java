package com.miniai.cli;

import com.codeai.analyzer.CodeAnalyzer;
import com.codeai.analyzer.RefactoringSuggester;
import com.codeai.analyzer.ast.ASTAnalyzer;
import com.codeai.analyzer.project.ProjectAnalyzer;
import com.codeai.analyzer.type.TypeResolver;
import com.codeai.analyzer.ai.AICodeReviewer;
import com.codeai.analyzer.llm.*;
import com.codeai.analyzer.fix.*;
import com.google.gson.Gson;
import okhttp3.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Code AI CLI
 * 코드 특화 AI 어시스턴트 CLI
 */
@Command(name = "code-ai", version = "10.0",
         description = "Code AI CLI - 코드 자동완성, AI/LLM 리뷰, 자동 수정",
         subcommands = {
             MiniAiCli.Train.class,
             MiniAiCli.Run.class,
             MiniAiCli.Complete.class,
             MiniAiCli.Tokenize.class,
             MiniAiCli.Review.class,
             MiniAiCli.Refactor.class,
             MiniAiCli.ASTReview.class,
             MiniAiCli.ProjectReview.class,
             MiniAiCli.TypeCheck.class,
             MiniAiCli.AIReview.class,
             MiniAiCli.LLMReview.class,
             MiniAiCli.AutoFixCmd.class
         })
public class MiniAiCli implements Callable<Integer> {

    private static final String API_BASE = "http://localhost:8080/v1";
    private static final OkHttpClient client = new OkHttpClient();
    private static final Gson gson = new Gson();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Override
    public Integer call() {
        System.out.println("🔧 Code AI CLI v10.0");
        System.out.println("사용법: code-ai [command]");
        System.out.println("\n명령어:");
        System.out.println("  train          - 모델 학습 (Bigram/Trigram/N-gram)");
        System.out.println("  run            - 텍스트 생성");
        System.out.println("  complete       - 코드 자동완성");
        System.out.println("  tokenize       - 텍스트 토큰화");
        System.out.println("  review         - 코드 리뷰 (정규식 기반)");
        System.out.println("  refactor       - 리팩토링 제안");
        System.out.println("  ast-review     - AST 기반 코드 리뷰");
        System.out.println("  project-review - 프로젝트 전체 분석");
        System.out.println("  type-check     - 타입 분석 (Symbol Solver)");
        System.out.println("  ai-review      - AI 코드 리뷰 (규칙 기반)");
        System.out.println("  llm-review     - LLM 코드 리뷰 (Claude/GPT/Ollama)");
        System.out.println("  auto-fix       - 코드 자동 수정");
        System.out.println("\n예시:");
        System.out.println("  code-ai ai-review src/MyClass.java");
        System.out.println("  code-ai auto-fix src/MyClass.java --write");
        System.out.println("  code-ai auto-fix src/MyClass.java --llm --provider claude");
        return 0;
    }

    /**
     * train 명령어 - Bigram/Trigram/N-gram, Code/Whitespace, Smoothing 선택 가능
     */
    @Command(name = "train", description = "모델 학습 (Bigram/Trigram/N-gram)")
    static class Train implements Callable<Integer> {
        @Option(names = {"--corpus"}, required = true, description = "Corpus 파일 경로")
        String corpusPath;

        @Option(names = {"--output"}, description = "Artifact 출력 경로",
                defaultValue = "data/code-model.json")
        String outputPath;

        @Option(names = {"--model"}, description = "모델 타입 (bigram/trigram/ngram)",
                defaultValue = "ngram")
        String modelType;

        @Option(names = {"-n"}, description = "N-gram 크기 (ngram 모델용)",
                defaultValue = "5")
        int n;

        @Option(names = {"--tokenizer"}, description = "토크나이저 (whitespace/code)",
                defaultValue = "code")
        String tokenizerType;

        @Option(names = {"--smoothing"}, description = "Smoothing (simple/kneser-ney)",
                defaultValue = "kneser-ney")
        String smoothingType;

        @Override
        public Integer call() {
            try {
                System.out.println("🚀 모델 학습 시작...");
                System.out.println("  Corpus: " + corpusPath);
                System.out.println("  Output: " + outputPath);
                System.out.println("  Model: " + modelType + (modelType.equals("ngram") ? " (n=" + n + ")" : ""));
                System.out.println("  Tokenizer: " + tokenizerType);
                System.out.println("  Smoothing: " + smoothingType);

                java.util.Map<String, Object> requestMap = new java.util.HashMap<>();
                requestMap.put("corpusPath", corpusPath);
                requestMap.put("outputPath", outputPath);
                requestMap.put("modelType", modelType);
                requestMap.put("tokenizerType", tokenizerType);
                requestMap.put("n", n);
                requestMap.put("smoothingType", smoothingType);

                String json = gson.toJson(requestMap);

                Request request = new Request.Builder()
                    .url(API_BASE + "/train")
                    .post(RequestBody.create(json, JSON))
                    .build();

                try (Response response = client.newCall(request).execute()) {
                    String body = response.body().string();
                    Map<String, Object> result = gson.fromJson(body, Map.class);

                    if ("success".equals(result.get("status"))) {
                        System.out.println("\n✅ 학습 완료!");
                        System.out.println("  Model: " + result.get("modelType"));
                        System.out.println("  Tokenizer: " + result.get("tokenizer"));
                        System.out.println("  Smoothing: " + result.get("smoothing"));
                        System.out.println("  Vocabulary: " + result.get("vocabSize"));
                        System.out.println("  Latency: " + result.get("latencyMs") + "ms");
                    } else {
                        System.err.println("❌ 학습 실패: " + result.get("message"));
                        return 1;
                    }
                }

                return 0;
            } catch (Exception e) {
                System.err.println("❌ 오류: " + e.getMessage());
                return 1;
            }
        }
    }

    /**
     * run 명령어
     */
    @Command(name = "run", description = "텍스트 생성")
    static class Run implements Callable<Integer> {
        @Option(names = {"-p", "--prompt"}, required = true, description = "프롬프트")
        String prompt;

        @Option(names = {"--max-tokens"}, description = "최대 토큰 수", defaultValue = "20")
        int maxTokens;

        @Option(names = {"--temperature"}, description = "Temperature", defaultValue = "1.0")
        double temperature;

        @Option(names = {"--seed"}, description = "Random seed")
        Long seed;

        @Override
        public Integer call() {
            try {
                System.out.println("💬 텍스트 생성...");
                System.out.println("  Prompt: \"" + prompt + "\"");

                Map<String, Object> requestMap = Map.of(
                    "prompt", prompt,
                    "maxTokens", maxTokens,
                    "temperature", temperature,
                    "seed", seed != null ? seed : System.currentTimeMillis()
                );

                String json = gson.toJson(requestMap);

                Request request = new Request.Builder()
                    .url(API_BASE + "/generate")
                    .post(RequestBody.create(json, JSON))
                    .build();

                try (Response response = client.newCall(request).execute()) {
                    String body = response.body().string();
                    Map<String, Object> result = gson.fromJson(body, Map.class);

                    System.out.println("\n📝 생성 결과:");
                    System.out.println("  " + result.get("generatedText"));

                    Map<String, Object> usage = (Map<String, Object>) result.get("usage");
                    System.out.println("\n📊 Usage:");
                    System.out.println("  Input:  " + usage.get("inputTokens") + " tokens");
                    System.out.println("  Output: " + usage.get("outputTokens") + " tokens");
                    System.out.println("  Total:  " + usage.get("totalTokens") + " tokens");
                }

                return 0;
            } catch (Exception e) {
                System.err.println("❌ 오류: " + e.getMessage());
                e.printStackTrace();
                return 1;
            }
        }
    }

    /**
     * complete 명령어 - 코드 자동완성
     */
    @Command(name = "complete", description = "코드 자동완성")
    static class Complete implements Callable<Integer> {
        @Parameters(index = "0", description = "완성할 코드 조각")
        String code;

        @Option(names = {"--tokens"}, description = "생성할 토큰 수", defaultValue = "10")
        int maxTokens;

        @Option(names = {"-n", "--count"}, description = "후보 개수", defaultValue = "3")
        int count;

        @Override
        public Integer call() {
            try {
                System.out.println("🔧 코드 자동완성...");
                System.out.println("  입력: " + code);
                System.out.println();

                for (int i = 0; i < count; i++) {
                    Map<String, Object> requestMap = Map.of(
                        "prompt", code,
                        "maxTokens", maxTokens,
                        "temperature", 1.0,
                        "seed", System.currentTimeMillis() + i * 1000
                    );

                    String json = gson.toJson(requestMap);

                    Request request = new Request.Builder()
                        .url(API_BASE + "/generate")
                        .post(RequestBody.create(json, JSON))
                        .build();

                    try (Response response = client.newCall(request).execute()) {
                        String body = response.body().string();
                        Map<String, Object> result = gson.fromJson(body, Map.class);

                        String generatedText = (String) result.get("generatedText");
                        Map<String, Object> usage = (Map<String, Object>) result.get("usage");

                        System.out.println("  [" + (i + 1) + "] " + generatedText);
                    }
                }

                return 0;
            } catch (Exception e) {
                System.err.println("❌ 오류: " + e.getMessage());
                System.err.println("   서버가 실행 중인지 확인하세요: ./gradlew :mini-ai-server:bootRun");
                return 1;
            }
        }
    }

    /**
     * tokenize 명령어
     */
    @Command(name = "tokenize", description = "텍스트 토큰화")
    static class Tokenize implements Callable<Integer> {
        @Parameters(index = "0", description = "토큰화할 텍스트")
        String text;

        @Override
        public Integer call() {
            // 로컬에서 직접 토큰화
            String[] tokens = text.split("\\s+");

            System.out.println("📌 토큰화 결과:");
            System.out.println("  원본: \"" + text + "\"");
            System.out.println("  토큰 수: " + tokens.length);
            System.out.println("  토큰: [" + String.join(", ", tokens) + "]");

            return 0;
        }
    }

    /**
     * review 명령어 - 코드 리뷰 (품질/보안 분석)
     */
    @Command(name = "review", description = "코드 리뷰 - 품질/보안 분석")
    static class Review implements Callable<Integer> {
        @Parameters(index = "0", description = "분석할 Java 파일 경로")
        String filePath;

        @Option(names = {"--severity"}, description = "최소 심각도 (INFO/WARNING/ERROR/CRITICAL)",
                defaultValue = "INFO")
        String minSeverity;

        @Override
        public Integer call() {
            try {
                System.out.println("🔍 코드 리뷰 시작...");
                System.out.println("  파일: " + filePath);
                System.out.println();

                // 파일 읽기
                String code = Files.readString(Path.of(filePath));

                // 분석 실행
                CodeAnalyzer analyzer = new CodeAnalyzer();
                CodeAnalyzer.AnalysisResult result = analyzer.analyze(code);

                // 심각도 필터링
                CodeAnalyzer.Severity minSev = CodeAnalyzer.Severity.valueOf(minSeverity.toUpperCase());

                // 결과 출력
                System.out.println(result.formatReport(minSev));

                return 0;
            } catch (IOException e) {
                System.err.println("❌ 파일 읽기 오류: " + e.getMessage());
                return 1;
            } catch (Exception e) {
                System.err.println("❌ 분석 오류: " + e.getMessage());
                e.printStackTrace();
                return 1;
            }
        }
    }

    /**
     * refactor 명령어 - 리팩토링 제안
     */
    @Command(name = "refactor", description = "리팩토링 제안")
    static class Refactor implements Callable<Integer> {
        @Parameters(index = "0", description = "분석할 Java 파일 경로")
        String filePath;

        @Option(names = {"--type"}, description = "리팩토링 유형 필터 (optional/stream/builder/conditional/all)",
                defaultValue = "all")
        String filterType;

        @Override
        public Integer call() {
            try {
                System.out.println("🔧 리팩토링 분석 시작...");
                System.out.println("  파일: " + filePath);
                System.out.println();

                // 파일 읽기
                String code = Files.readString(Path.of(filePath));

                // 분석 실행
                RefactoringSuggester suggester = new RefactoringSuggester();
                var suggestions = suggester.suggest(code);

                // 필터링
                if (!"all".equalsIgnoreCase(filterType)) {
                    suggestions = suggestions.stream()
                        .filter(s -> matchesFilter(s.type, filterType))
                        .toList();
                }

                // 결과 출력
                System.out.println(RefactoringSuggester.formatSuggestions(suggestions));

                return 0;
            } catch (IOException e) {
                System.err.println("❌ 파일 읽기 오류: " + e.getMessage());
                return 1;
            } catch (Exception e) {
                System.err.println("❌ 분석 오류: " + e.getMessage());
                e.printStackTrace();
                return 1;
            }
        }

        private boolean matchesFilter(RefactoringSuggester.RefactoringType type, String filter) {
            return switch (filter.toLowerCase()) {
                case "optional" -> type == RefactoringSuggester.RefactoringType.USE_OPTIONAL;
                case "stream" -> type == RefactoringSuggester.RefactoringType.USE_STREAM;
                case "builder" -> type == RefactoringSuggester.RefactoringType.USE_BUILDER;
                case "conditional" -> type == RefactoringSuggester.RefactoringType.SIMPLIFY_CONDITIONAL;
                default -> true;
            };
        }
    }

    /**
     * ast-review 명령어 - AST 기반 코드 리뷰 (JavaParser)
     */
    @Command(name = "ast-review", description = "AST 기반 코드 리뷰 (JavaParser)")
    static class ASTReview implements Callable<Integer> {
        @Parameters(index = "0", description = "분석할 Java 파일 경로")
        String filePath;

        @Option(names = {"--severity"}, description = "최소 심각도 (INFO/WARNING/ERROR/CRITICAL)",
                defaultValue = "INFO")
        String minSeverity;

        @Option(names = {"--metrics"}, description = "메서드별 복잡도 상세 출력",
                defaultValue = "false")
        boolean showMetrics;

        @Override
        public Integer call() {
            try {
                System.out.println("🌳 AST 기반 코드 리뷰 시작...");
                System.out.println("  파일: " + filePath);
                System.out.println("  분석기: JavaParser (AST)");
                System.out.println();

                // 파일 읽기
                String code = Files.readString(Path.of(filePath));

                // AST 분석 실행
                ASTAnalyzer analyzer = new ASTAnalyzer();
                ASTAnalyzer.ASTAnalysisResult result = analyzer.analyze(code);

                // 심각도 필터링
                ASTAnalyzer.Severity minSev = ASTAnalyzer.Severity.valueOf(minSeverity.toUpperCase());

                // 결과 출력
                System.out.println(result.formatReport(minSev));

                // 메서드별 복잡도 상세 (옵션)
                if (showMetrics && !result.metrics.methodComplexities.isEmpty()) {
                    System.out.println("\n📊 메서드별 순환 복잡도:");
                    result.metrics.methodComplexities.entrySet().stream()
                        .sorted((a, b) -> b.getValue() - a.getValue())
                        .forEach(e -> {
                            String status = e.getValue() > 10 ? "❌" : e.getValue() > 5 ? "⚠️" : "✅";
                            System.out.printf("   %s %s: %d%n", status, e.getKey(), e.getValue());
                        });
                }

                return 0;
            } catch (IOException e) {
                System.err.println("❌ 파일 읽기 오류: " + e.getMessage());
                return 1;
            } catch (Exception e) {
                System.err.println("❌ 분석 오류: " + e.getMessage());
                e.printStackTrace();
                return 1;
            }
        }
    }

    /**
     * project-review 명령어 - 프로젝트 전체 분석
     */
    @Command(name = "project-review", description = "프로젝트 전체 분석 (멀티파일)")
    static class ProjectReview implements Callable<Integer> {
        @Parameters(index = "0", description = "분석할 프로젝트 디렉토리 경로")
        String projectPath;

        @Option(names = {"--severity"}, description = "최소 심각도 (INFO/WARNING/ERROR/CRITICAL)",
                defaultValue = "INFO")
        String minSeverity;

        @Option(names = {"--details"}, description = "파일별 상세 이슈 출력",
                defaultValue = "false")
        boolean showDetails;

        @Option(names = {"--top"}, description = "문제/복잡도 Top N 파일 출력",
                defaultValue = "5")
        int topN;

        @Override
        public Integer call() {
            try {
                System.out.println("📁 프로젝트 분석 시작...");
                System.out.println("  경로: " + projectPath);
                System.out.println("  분석기: ProjectAnalyzer (멀티파일 AST)");
                System.out.println();

                Path path = Path.of(projectPath);
                if (!Files.isDirectory(path)) {
                    System.err.println("❌ 디렉토리가 아닙니다: " + projectPath);
                    return 1;
                }

                // 프로젝트 분석 실행
                ProjectAnalyzer analyzer = new ProjectAnalyzer();
                ProjectAnalyzer.ProjectAnalysisResult result = analyzer.analyze(path);

                // 심각도 필터링
                ASTAnalyzer.Severity minSev = ASTAnalyzer.Severity.valueOf(minSeverity.toUpperCase());

                // 결과 출력
                System.out.println(result.formatReport(minSev, showDetails));

                // Top N 파일
                if (topN > 0) {
                    System.out.println(result.getTopProblematicFiles(topN));
                    System.out.println(result.getTopComplexFiles(topN));
                }

                return 0;
            } catch (IOException e) {
                System.err.println("❌ 디렉토리 읽기 오류: " + e.getMessage());
                return 1;
            } catch (Exception e) {
                System.err.println("❌ 분석 오류: " + e.getMessage());
                e.printStackTrace();
                return 1;
            }
        }
    }

    /**
     * type-check 명령어 - Symbol Solver 기반 타입 분석
     */
    @Command(name = "type-check", description = "타입 분석 (Symbol Solver)")
    static class TypeCheck implements Callable<Integer> {
        @Parameters(index = "0", description = "분석할 Java 파일 경로")
        String filePath;

        @Option(names = {"--project"}, description = "프로젝트 루트 경로 (타입 해석용)")
        String projectPath;

        @Option(names = {"--severity"}, description = "최소 심각도 (INFO/WARNING/ERROR)",
                defaultValue = "INFO")
        String minSeverity;

        @Option(names = {"--trace"}, description = "특정 메서드 호출 추적")
        String traceMethod;

        @Override
        public Integer call() {
            try {
                System.out.println("🔍 타입 분석 시작...");
                System.out.println("  파일: " + filePath);
                System.out.println("  분석기: Symbol Solver (타입 해석)");
                if (projectPath != null) {
                    System.out.println("  프로젝트: " + projectPath);
                }
                System.out.println();

                // 파일 읽기
                String code = Files.readString(Path.of(filePath));

                // TypeResolver 생성 (프로젝트 경로 있으면 추가)
                TypeResolver resolver = projectPath != null ?
                    new TypeResolver(Path.of(projectPath)) :
                    new TypeResolver();

                // 타입 분석 실행
                TypeResolver.TypeAnalysisResult result = resolver.analyze(code);

                // 심각도 필터링
                TypeResolver.Severity minSev = TypeResolver.Severity.valueOf(minSeverity.toUpperCase());

                // 결과 출력
                System.out.println(result.formatReport(minSev));

                // 메서드 추적 (옵션)
                if (traceMethod != null) {
                    System.out.println(result.traceMethodCalls(traceMethod));
                }

                return 0;
            } catch (IOException e) {
                System.err.println("❌ 파일 읽기 오류: " + e.getMessage());
                return 1;
            } catch (Exception e) {
                System.err.println("❌ 분석 오류: " + e.getMessage());
                e.printStackTrace();
                return 1;
            }
        }
    }

    /**
     * ai-review 명령어 - AI 기반 코드 리뷰 (자연어 피드백)
     */
    @Command(name = "ai-review", description = "AI 코드 리뷰 (자연어 피드백, 품질 점수)")
    static class AIReview implements Callable<Integer> {
        @Parameters(index = "0", description = "분석할 Java 파일 경로")
        String filePath;

        @Option(names = {"--json"}, description = "JSON 형식 출력",
                defaultValue = "false")
        boolean jsonOutput;

        @Override
        public Integer call() {
            try {
                System.out.println("🤖 AI 코드 리뷰 시작...");
                System.out.println("  파일: " + filePath);
                System.out.println("  분석기: AICodeReviewer (자연어 피드백)");
                System.out.println();

                // 파일 읽기
                String code = Files.readString(Path.of(filePath));

                // AI 리뷰 실행
                AICodeReviewer reviewer = new AICodeReviewer();
                AICodeReviewer.AIReviewResult result = reviewer.review(code);

                // 결과 출력
                if (jsonOutput) {
                    // JSON 형식 출력
                    Map<String, Object> jsonResult = new java.util.LinkedHashMap<>();
                    jsonResult.put("file", filePath);
                    jsonResult.put("parseSuccess", result.parseSuccess);
                    jsonResult.put("grade", result.score.getGrade());
                    jsonResult.put("overallScore", result.score.getOverallScore());
                    jsonResult.put("scores", Map.of(
                        "structure", result.score.structureScore,
                        "readability", result.score.readabilityScore,
                        "maintainability", result.score.maintainabilityScore,
                        "reliability", result.score.reliabilityScore,
                        "security", result.score.securityScore,
                        "performance", result.score.performanceScore
                    ));
                    jsonResult.put("commentCount", result.comments.size());
                    jsonResult.put("comments", result.comments.stream()
                        .map(c -> Map.of(
                            "type", c.type.name(),
                            "line", c.line,
                            "message", c.message,
                            "suggestion", c.suggestion != null ? c.suggestion : ""
                        ))
                        .toList());
                    System.out.println(gson.toJson(jsonResult));
                } else {
                    // 보고서 형식 출력
                    System.out.println(result.formatReport());
                }

                return 0;
            } catch (IOException e) {
                System.err.println("❌ 파일 읽기 오류: " + e.getMessage());
                return 1;
            } catch (Exception e) {
                System.err.println("❌ 분석 오류: " + e.getMessage());
                e.printStackTrace();
                return 1;
            }
        }
    }

    /**
     * llm-review 명령어 - LLM 기반 코드 리뷰 (Claude/GPT/Ollama)
     */
    @Command(name = "llm-review", description = "LLM 코드 리뷰 (Claude/GPT/Ollama)")
    static class LLMReview implements Callable<Integer> {
        @Parameters(index = "0", description = "분석할 Java 파일 경로")
        String filePath;

        @Option(names = {"--provider", "-p"}, description = "LLM 제공자 (claude/openai/ollama)",
                defaultValue = "claude")
        String provider;

        @Option(names = {"--model", "-m"}, description = "모델 이름 (선택)")
        String model;

        @Option(names = {"--api-key", "-k"}, description = "API 키 (환경변수 대신 사용)")
        String apiKey;

        @Option(names = {"--stream", "-s"}, description = "스트리밍 모드",
                defaultValue = "false")
        boolean stream;

        @Override
        public Integer call() {
            try {
                System.out.println("🤖 LLM 코드 리뷰 시작...");
                System.out.println("  파일: " + filePath);
                System.out.println("  제공자: " + provider);

                // 파일 읽기
                String code = Files.readString(Path.of(filePath));

                // LLM 클라이언트 생성
                LLMClient client = createClient();

                if (!client.isAvailable()) {
                    System.err.println("❌ " + provider + " 클라이언트를 사용할 수 없습니다.");
                    System.err.println("   API 키를 확인하거나 서버 상태를 확인해주세요.");

                    if ("ollama".equals(provider)) {
                        System.err.println("\n   Ollama 설정:");
                        System.err.println("   1. brew install ollama");
                        System.err.println("   2. ollama pull codellama:13b");
                        System.err.println("   3. ollama serve");
                    } else if ("claude".equals(provider)) {
                        System.err.println("\n   환경변수 설정: export ANTHROPIC_API_KEY=your-key");
                    } else if ("openai".equals(provider)) {
                        System.err.println("\n   환경변수 설정: export OPENAI_API_KEY=your-key");
                    }
                    return 1;
                }

                System.out.println("  모델: " + client.getName());
                System.out.println();

                LLMCodeReviewer reviewer = new LLMCodeReviewer(client);

                if (stream) {
                    // 스트리밍 모드
                    System.out.println("📡 스트리밍 응답:\n");
                    reviewer.reviewStream(code, new LLMCodeReviewer.ReviewStreamHandler() {
                        @Override
                        public void onToken(String token) {
                            System.out.print(token);
                        }

                        @Override
                        public void onComplete(LLMCodeReviewer.LLMReviewResult result) {
                            System.out.println("\n\n" + "=".repeat(60));
                            System.out.println("리뷰 완료!");
                        }

                        @Override
                        public void onError(Throwable error) {
                            System.err.println("\n❌ 오류: " + error.getMessage());
                        }
                    });

                    // 스트리밍 완료 대기
                    Thread.sleep(1000);
                    while (true) {
                        Thread.sleep(100);
                    }
                } else {
                    // 일반 모드
                    LLMCodeReviewer.LLMReviewResult result = reviewer.review(code);
                    System.out.println(result.formatReport());
                }

                return 0;
            } catch (IOException e) {
                System.err.println("❌ 파일 읽기 오류: " + e.getMessage());
                return 1;
            } catch (InterruptedException e) {
                return 0;
            } catch (Exception e) {
                System.err.println("❌ 분석 오류: " + e.getMessage());
                e.printStackTrace();
                return 1;
            }
        }

        private LLMClient createClient() {
            return switch (provider.toLowerCase()) {
                case "claude" -> {
                    ClaudeClient.Builder builder = ClaudeClient.builder();
                    if (apiKey != null) builder.apiKey(apiKey);
                    if (model != null) builder.model(model);
                    yield builder.build();
                }
                case "openai", "gpt" -> {
                    OpenAIClient.Builder builder = OpenAIClient.builder();
                    if (apiKey != null) builder.apiKey(apiKey);
                    if (model != null) builder.model(model);
                    yield builder.build();
                }
                case "ollama", "local" -> {
                    OllamaClient.Builder builder = OllamaClient.builder();
                    if (model != null) builder.model(model);
                    yield builder.build();
                }
                default -> throw new IllegalArgumentException("Unknown provider: " + provider);
            };
        }
    }

    /**
     * auto-fix 명령어 - 코드 자동 수정
     */
    @Command(name = "auto-fix", description = "코드 자동 수정 (규칙 기반 + LLM)")
    static class AutoFixCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "수정할 Java 파일 경로")
        String filePath;

        @Option(names = {"--write", "-w"}, description = "수정된 코드를 파일에 저장",
                defaultValue = "false")
        boolean write;

        @Option(names = {"--llm"}, description = "LLM 기반 수정 사용",
                defaultValue = "false")
        boolean useLLM;

        @Option(names = {"--provider", "-p"}, description = "LLM 제공자 (claude/openai/ollama)",
                defaultValue = "claude")
        String provider;

        @Option(names = {"--model", "-m"}, description = "모델 이름 (선택)")
        String model;

        @Option(names = {"--api-key", "-k"}, description = "API 키 (환경변수 대신 사용)")
        String apiKey;

        @Option(names = {"--backup", "-b"}, description = "원본 파일 백업 (.bak)",
                defaultValue = "true")
        boolean backup;

        @Option(names = {"--diff"}, description = "diff 형식으로 출력",
                defaultValue = "false")
        boolean showDiff;

        @Override
        public Integer call() {
            try {
                System.out.println("🔧 코드 자동 수정 시작...");
                System.out.println("  파일: " + filePath);
                System.out.println("  모드: " + (useLLM ? "LLM 기반" : "규칙 기반"));
                System.out.println();

                // 파일 읽기
                Path path = Path.of(filePath);
                String originalCode = Files.readString(path);

                String fixedCode;
                String report;

                if (useLLM) {
                    // LLM 기반 수정
                    LLMClient client = createLLMClient();
                    if (!client.isAvailable()) {
                        System.err.println("❌ LLM 클라이언트를 사용할 수 없습니다.");
                        return 1;
                    }

                    System.out.println("  LLM: " + client.getName());
                    System.out.println();

                    LLMAutoFixer fixer = new LLMAutoFixer(client);
                    LLMAutoFixer.LLMFixResult result = fixer.improve(originalCode);

                    if (!result.success()) {
                        System.err.println("❌ 수정 실패: " + result.explanation());
                        return 1;
                    }

                    fixedCode = result.fixedCode();
                    report = result.formatReport();

                } else {
                    // 규칙 기반 수정
                    AutoFixer fixer = new AutoFixer();
                    AutoFixer.FixReport result = fixer.fix(originalCode);

                    if (!result.success()) {
                        System.err.println("❌ 수정 실패: " + result.error());
                        return 1;
                    }

                    fixedCode = result.fixedCode();
                    report = result.formatReport();
                }

                // 결과 출력
                System.out.println(report);

                if (showDiff) {
                    System.out.println("\n" + "=".repeat(60));
                    System.out.println("📄 Diff:");
                    System.out.println("=".repeat(60));
                    printDiff(originalCode, fixedCode);
                }

                // 파일 저장
                if (write && !fixedCode.equals(originalCode)) {
                    if (backup) {
                        Path backupPath = Path.of(filePath + ".bak");
                        Files.writeString(backupPath, originalCode);
                        System.out.println("\n💾 백업 저장: " + backupPath);
                    }

                    Files.writeString(path, fixedCode);
                    System.out.println("✅ 수정된 코드 저장: " + filePath);
                } else if (!write && !fixedCode.equals(originalCode)) {
                    System.out.println("\n💡 --write 옵션으로 파일에 저장할 수 있습니다.");
                }

                return 0;
            } catch (IOException e) {
                System.err.println("❌ 파일 오류: " + e.getMessage());
                return 1;
            } catch (Exception e) {
                System.err.println("❌ 수정 오류: " + e.getMessage());
                e.printStackTrace();
                return 1;
            }
        }

        private LLMClient createLLMClient() {
            return switch (provider.toLowerCase()) {
                case "claude" -> {
                    ClaudeClient.Builder builder = ClaudeClient.builder();
                    if (apiKey != null) builder.apiKey(apiKey);
                    if (model != null) builder.model(model);
                    yield builder.build();
                }
                case "openai", "gpt" -> {
                    OpenAIClient.Builder builder = OpenAIClient.builder();
                    if (apiKey != null) builder.apiKey(apiKey);
                    if (model != null) builder.model(model);
                    yield builder.build();
                }
                case "ollama", "local" -> {
                    OllamaClient.Builder builder = OllamaClient.builder();
                    if (model != null) builder.model(model);
                    yield builder.build();
                }
                default -> throw new IllegalArgumentException("Unknown provider: " + provider);
            };
        }

        private void printDiff(String original, String fixed) {
            String[] origLines = original.split("\n", -1);
            String[] fixedLines = fixed.split("\n", -1);

            int maxLines = Math.max(origLines.length, fixedLines.length);

            for (int i = 0; i < maxLines; i++) {
                String origLine = i < origLines.length ? origLines[i] : "";
                String fixedLine = i < fixedLines.length ? fixedLines[i] : "";

                if (!origLine.equals(fixedLine)) {
                    if (!origLine.isEmpty()) {
                        System.out.printf("- %4d: %s%n", i + 1, origLine);
                    }
                    if (!fixedLine.isEmpty()) {
                        System.out.printf("+ %4d: %s%n", i + 1, fixedLine);
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MiniAiCli()).execute(args);
        System.exit(exitCode);
    }
}
