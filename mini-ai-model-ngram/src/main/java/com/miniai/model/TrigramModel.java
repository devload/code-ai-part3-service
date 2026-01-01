package com.miniai.model;

import com.codeai.tokenizer.CodeTokenizer;
import com.miniai.core.model.LanguageModel;
import com.miniai.core.tokenizer.Tokenizer;
import com.miniai.core.types.GenerateRequest;
import com.miniai.core.types.GenerateResponse;
import com.miniai.core.types.Usage;
import com.miniai.tokenizer.WhitespaceTokenizer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Trigram 언어 모델 (with Backoff)
 *
 * 학습 포인트:
 * - Trigram: 이전 2개 토큰 기반으로 다음 예측
 * - Backoff: Trigram이 없으면 Bigram으로 대체
 * - 더 긴 문맥 = 더 정확한 패턴 ("public static void")
 */
public class TrigramModel implements LanguageModel {

    private final TrigramArtifact artifact;
    private final Tokenizer tokenizer;
    private final String modelName;
    private final double backoffWeight; // Bigram 가중치 (0.0 ~ 1.0)

    public TrigramModel(TrigramArtifact artifact, Tokenizer tokenizer) {
        this(artifact, tokenizer, 0.4); // 기본 backoff 가중치: 40%
    }

    public TrigramModel(TrigramArtifact artifact, Tokenizer tokenizer, double backoffWeight) {
        this.artifact = artifact;
        this.tokenizer = tokenizer;
        this.modelName = "trigram-v1";
        this.backoffWeight = backoffWeight;
    }

    /**
     * Artifact 파일로부터 모델 로드
     */
    public static TrigramModel fromArtifact(Path artifactPath) {
        TrigramArtifact artifact = TrigramTrainer.loadArtifact(artifactPath);

        // Vocabulary와 토크나이저 타입으로 Tokenizer 생성
        Tokenizer tokenizer;
        String tokenizerType = artifact.getMetadata().getTokenizerType();

        if ("CodeTokenizer".equals(tokenizerType)) {
            tokenizer = new CodeTokenizer(artifact.getVocabulary());
            System.out.println("🔧 Trigram 모델 로드: CodeTokenizer 사용");
        } else {
            tokenizer = new WhitespaceTokenizer(artifact.getVocabulary());
            System.out.println("📝 Trigram 모델 로드: WhitespaceTokenizer 사용");
        }

        return new TrigramModel(artifact, tokenizer);
    }

    @Override
    public GenerateResponse generate(GenerateRequest request) {
        long startTime = System.currentTimeMillis();

        // 1. Prompt 토큰화
        List<Integer> promptTokens = tokenizer.encode(request.getPrompt());
        if (promptTokens.isEmpty()) {
            throw new IllegalArgumentException("Prompt가 비어있습니다");
        }

        // 2. Sampler 생성
        Sampler sampler = new Sampler(
            request.getTemperature(),
            request.getTopK(),
            request.getSeed().orElse(null)
        );

        // 3. 생성 루프
        List<Integer> generatedTokens = new ArrayList<>(promptTokens);
        int maxTokens = request.getMaxTokens();
        List<String> stopSequences = request.getStopSequences();

        for (int i = 0; i < maxTokens; i++) {
            // 다음 토큰 후보들 (Trigram + Backoff)
            Map<Integer, Integer> nextCounts = getNextTokenCountsWithBackoff(generatedTokens);

            if (nextCounts.isEmpty()) {
                // 더 이상 생성 불가 (dead end)
                break;
            }

            // 샘플링
            int nextToken = sampler.sample(nextCounts);
            generatedTokens.add(nextToken);

            // Stop sequence 확인
            if (shouldStop(generatedTokens, stopSequences)) {
                break;
            }
        }

        // 4. 토큰 → 텍스트
        String generatedText = tokenizer.decode(generatedTokens);

        // 5. Usage 계산
        Usage usage = new Usage(
            promptTokens.size(),
            generatedTokens.size() - promptTokens.size()
        );

        // 6. Latency 계산
        long latency = System.currentTimeMillis() - startTime;

        return new GenerateResponse(generatedText, usage, latency, modelName);
    }

    /**
     * Trigram + Bigram Backoff로 다음 토큰 후보 계산
     */
    private Map<Integer, Integer> getNextTokenCountsWithBackoff(List<Integer> tokens) {
        int size = tokens.size();

        // Trigram 시도 (최소 2개 토큰 필요)
        Map<Integer, Integer> trigramCounts = new HashMap<>();
        if (size >= 2) {
            int prev1 = tokens.get(size - 2);
            int prev2 = tokens.get(size - 1);
            trigramCounts = artifact.getNextTokenCounts(prev1, prev2);
        }

        // Bigram (backoff)
        int lastToken = tokens.get(size - 1);
        Map<Integer, Integer> bigramCounts = artifact.getBigramNextTokenCounts(lastToken);

        // Trigram이 있으면 Trigram 우선
        if (!trigramCounts.isEmpty()) {
            // Interpolation: Trigram과 Bigram을 섞음
            return interpolate(trigramCounts, bigramCounts);
        }

        // Trigram이 없으면 Bigram만 사용
        return bigramCounts;
    }

    /**
     * Trigram과 Bigram 카운트를 보간
     *
     * 수식: P(w|w1,w2) = (1-λ) * P_trigram(w|w1,w2) + λ * P_bigram(w|w2)
     */
    private Map<Integer, Integer> interpolate(
            Map<Integer, Integer> trigramCounts,
            Map<Integer, Integer> bigramCounts) {

        Map<Integer, Integer> combined = new HashMap<>();

        // Trigram 카운트 추가 (가중치: 1 - backoffWeight)
        double trigramWeight = 1.0 - backoffWeight;
        for (Map.Entry<Integer, Integer> entry : trigramCounts.entrySet()) {
            int weightedCount = (int) Math.ceil(entry.getValue() * trigramWeight * 100);
            combined.put(entry.getKey(), weightedCount);
        }

        // Bigram 카운트 추가 (가중치: backoffWeight)
        for (Map.Entry<Integer, Integer> entry : bigramCounts.entrySet()) {
            int weightedCount = (int) Math.ceil(entry.getValue() * backoffWeight * 100);
            combined.merge(entry.getKey(), weightedCount, Integer::sum);
        }

        return combined;
    }

    /**
     * Stop sequence 확인
     */
    private boolean shouldStop(List<Integer> tokens, List<String> stopSequences) {
        if (stopSequences.isEmpty()) {
            return false;
        }

        String currentText = tokenizer.decode(tokens);

        for (String stopSeq : stopSequences) {
            if (currentText.endsWith(stopSeq)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public String modelName() {
        return modelName;
    }

    public TrigramArtifact getArtifact() {
        return artifact;
    }

    public Tokenizer getTokenizer() {
        return tokenizer;
    }

    public double getBackoffWeight() {
        return backoffWeight;
    }

    @Override
    public String toString() {
        return String.format("TrigramModel(vocab=%d, trigrams=%d, backoff=%.1f%%)",
            artifact.getVocabulary().size(),
            artifact.getTotalTrigramCount(),
            backoffWeight * 100);
    }
}
