package com.miniai.server.controller;

import com.codeai.tokenizer.CodeTokenizer;
import com.miniai.core.model.LanguageModel;
import com.miniai.core.tokenizer.Tokenizer;
import com.miniai.core.types.GenerateRequest;
import com.miniai.core.types.GenerateResponse;
import com.miniai.model.BigramModel;
import com.miniai.model.BigramTrainer;
import com.miniai.model.TrigramModel;
import com.miniai.model.TrigramTrainer;
import com.miniai.model.ngram.NgramModel;
import com.miniai.model.ngram.NgramTrainer;
import com.miniai.model.smoothing.KneserNey;
import com.miniai.model.smoothing.SimpleBackoff;
import com.miniai.model.smoothing.SmoothingStrategy;
import com.miniai.server.dto.GenerateRequestDto;
import com.miniai.server.dto.GenerateResponseDto;
import com.miniai.server.dto.TrainRequest;
import com.miniai.tokenizer.WhitespaceTokenizer;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Code AI REST API Controller
 *
 * Supports Bigram, Trigram, and N-gram models
 * With SimpleBackoff and Kneser-Ney smoothing
 */
@RestController
@RequestMapping("/v1")
public class MiniAiController {

    private LanguageModel model;
    private final String defaultArtifactPath = "data/sample-bigram.json";

    public MiniAiController() {
        // 기본 모델 로드 시도
        try {
            Path artifactPath = Paths.get(defaultArtifactPath);
            if (Files.exists(artifactPath)) {
                this.model = BigramModel.fromArtifact(artifactPath);
                System.out.println("✅ 기본 모델 로드: " + defaultArtifactPath);
            } else {
                System.out.println("⚠️  기본 모델 없음. /v1/train으로 학습 필요");
            }
        } catch (Exception e) {
            System.err.println("⚠️  모델 로드 실패: " + e.getMessage());
        }
    }

    /**
     * POST /v1/train
     * 모델 학습
     */
    @PostMapping("/train")
    public Map<String, Object> train(@RequestBody TrainRequest request) {
        try {
            Path corpusPath = Paths.get(request.getCorpusPath());
            Path outputPath = Paths.get(request.getOutputPath());

            // Corpus 읽기
            String corpus = Files.readString(corpusPath);

            // 토크나이저 선택
            Tokenizer tokenizer;
            String tokenizerName;
            if (request.useCodeTokenizer()) {
                tokenizer = CodeTokenizer.fromCode(corpus);
                tokenizerName = "CodeTokenizer";
                System.out.println("🔧 Using CodeTokenizer (code-aware)");
            } else {
                tokenizer = WhitespaceTokenizer.fromText(corpus);
                tokenizerName = "WhitespaceTokenizer";
                System.out.println("📝 Using WhitespaceTokenizer (default)");
            }

            // 학습 (Bigram, Trigram, or N-gram)
            long startTime = System.currentTimeMillis();
            String modelTypeName;
            String smoothingName = "none";

            if (request.useNgram()) {
                // N-gram with configurable smoothing
                int n = request.getN();
                NgramTrainer trainer = new NgramTrainer(n, tokenizer);
                trainer.train(corpusPath, outputPath);

                // Smoothing 전략 선택
                SmoothingStrategy smoothing;
                if (request.useKneserNey()) {
                    smoothing = new KneserNey();
                    smoothingName = "kneser-ney";
                    System.out.println("🎯 Using Kneser-Ney smoothing");
                } else {
                    smoothing = new SimpleBackoff();
                    smoothingName = "simple-backoff";
                    System.out.println("📊 Using Simple Backoff smoothing");
                }

                this.model = NgramModel.fromArtifact(outputPath, smoothing);
                modelTypeName = n + "-gram";
                System.out.println("📊 Using " + n + "-gram model (" + (n-1) + "-token context)");

            } else if (request.useTrigram()) {
                TrigramTrainer trainer = new TrigramTrainer(tokenizer);
                trainer.train(corpusPath, outputPath);
                this.model = TrigramModel.fromArtifact(outputPath);
                modelTypeName = "trigram";
                System.out.println("📊 Using Trigram model (2-token context)");
            } else {
                BigramTrainer trainer = new BigramTrainer(tokenizer);
                trainer.train(corpusPath, outputPath);
                this.model = BigramModel.fromArtifact(outputPath);
                modelTypeName = "bigram";
                System.out.println("📈 Using Bigram model (1-token context)");
            }

            long latency = System.currentTimeMillis() - startTime;

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "학습 완료");
            response.put("artifactPath", outputPath.toString());
            response.put("vocabSize", tokenizer.vocabSize());
            response.put("tokenizer", tokenizerName);
            response.put("modelType", modelTypeName);
            response.put("smoothing", smoothingName);
            response.put("latencyMs", latency);

            return response;

        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return response;
        }
    }

    /**
     * POST /v1/generate
     * 텍스트 생성
     */
    @PostMapping("/generate")
    public GenerateResponseDto generate(@RequestBody GenerateRequestDto request) {
        if (model == null) {
            throw new IllegalStateException("모델이 로드되지 않았습니다. /v1/train을 먼저 호출하세요.");
        }

        // DTO → Core Request 변환
        GenerateRequest.Builder builder = GenerateRequest.builder(request.getPrompt())
            .maxTokens(request.getMaxTokens())
            .temperature(request.getTemperature())
            .topK(request.getTopK());

        if (request.getSeed() != null) {
            builder.seed(request.getSeed());
        }

        if (request.getStopSequences() != null) {
            builder.stopSequences(request.getStopSequences());
        }

        GenerateRequest coreRequest = builder.build();

        // 생성
        GenerateResponse coreResponse = model.generate(coreRequest);

        // Core Response → DTO 변환
        GenerateResponseDto.UsageDto usageDto = new GenerateResponseDto.UsageDto(
            coreResponse.getUsage().getInputTokens(),
            coreResponse.getUsage().getOutputTokens(),
            coreResponse.getUsage().getTotalTokens()
        );

        return new GenerateResponseDto(
            coreResponse.getGeneratedText(),
            usageDto,
            coreResponse.getLatencyMs(),
            coreResponse.getModel()
        );
    }

    /**
     * GET /v1/health
     * 헬스 체크
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("modelLoaded", model != null);
        if (model != null) {
            response.put("model", model.toString());
        }
        return response;
    }
}
