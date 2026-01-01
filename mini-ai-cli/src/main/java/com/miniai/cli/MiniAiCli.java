package com.miniai.cli;

import com.google.gson.Gson;
import okhttp3.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Code AI CLI
 * 코드 특화 AI 어시스턴트 CLI
 */
@Command(name = "code-ai", version = "3.0",
         description = "Code AI CLI - 코드 자동완성 및 생성 (N-gram + Kneser-Ney)",
         subcommands = {
             MiniAiCli.Train.class,
             MiniAiCli.Run.class,
             MiniAiCli.Complete.class,
             MiniAiCli.Tokenize.class
         })
public class MiniAiCli implements Callable<Integer> {

    private static final String API_BASE = "http://localhost:8080/v1";
    private static final OkHttpClient client = new OkHttpClient();
    private static final Gson gson = new Gson();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Override
    public Integer call() {
        System.out.println("🔧 Code AI CLI v3.0");
        System.out.println("사용법: code-ai [command]");
        System.out.println("\n명령어:");
        System.out.println("  train      - 모델 학습 (Bigram/Trigram/N-gram)");
        System.out.println("  run        - 텍스트 생성");
        System.out.println("  complete   - 코드 자동완성");
        System.out.println("  tokenize   - 텍스트 토큰화");
        System.out.println("\n예시:");
        System.out.println("  code-ai train --corpus data/code.txt --model ngram --n 5 --smoothing kneser-ney");
        System.out.println("  code-ai complete \"public class User {\"");
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

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MiniAiCli()).execute(args);
        System.exit(exitCode);
    }
}
