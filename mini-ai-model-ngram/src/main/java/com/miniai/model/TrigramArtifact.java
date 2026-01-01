package com.miniai.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Trigram 학습 결과 (Artifact)
 *
 * 학습 포인트:
 * - Trigram = "A, B 다음에 C가 올 확률"의 카운트 테이블
 * - Bigram보다 더 긴 문맥 (2개 토큰)
 * - 예: "public static" → "void" (높은 확률)
 */
public class TrigramArtifact {

    /**
     * Trigram 카운트: "prev1:prev2" -> next_token -> count
     * 키 형식: prev1_id + ":" + prev2_id
     * 예: counts.get("42:123").get(456) = 5
     *     → 토큰 42, 123 다음에 토큰 456이 5번 나타남
     */
    private Map<String, Map<Integer, Integer>> counts;

    /**
     * Bigram 카운트 (backoff용)
     */
    private Map<Integer, Map<Integer, Integer>> bigramCounts;

    /**
     * Vocabulary: word -> token_id
     */
    private Map<String, Integer> vocabulary;

    /**
     * Metadata: 학습 정보
     */
    private Metadata metadata;

    public TrigramArtifact() {
        this.counts = new HashMap<>();
        this.bigramCounts = new HashMap<>();
        this.vocabulary = new HashMap<>();
        this.metadata = new Metadata();
    }

    public TrigramArtifact(Map<String, Map<Integer, Integer>> counts,
                           Map<Integer, Map<Integer, Integer>> bigramCounts,
                           Map<String, Integer> vocabulary,
                           Metadata metadata) {
        this.counts = counts;
        this.bigramCounts = bigramCounts;
        this.vocabulary = vocabulary;
        this.metadata = metadata;
    }

    /**
     * Trigram 키 생성
     */
    public static String makeKey(int prev1, int prev2) {
        return prev1 + ":" + prev2;
    }

    /**
     * Trigram 카운트 조회
     */
    public int getTrigramCount(int prev1, int prev2, int next) {
        String key = makeKey(prev1, prev2);
        return counts.getOrDefault(key, new HashMap<>())
                     .getOrDefault(next, 0);
    }

    /**
     * Trigram: 특정 토큰쌍 다음에 올 수 있는 모든 토큰과 카운트
     */
    public Map<Integer, Integer> getNextTokenCounts(int prev1, int prev2) {
        String key = makeKey(prev1, prev2);
        return counts.getOrDefault(key, new HashMap<>());
    }

    /**
     * Bigram: 특정 토큰 다음에 올 수 있는 모든 토큰과 카운트 (backoff용)
     */
    public Map<Integer, Integer> getBigramNextTokenCounts(int prev) {
        return bigramCounts.getOrDefault(prev, new HashMap<>());
    }

    /**
     * 전체 trigram 쌍의 개수
     */
    public int getTotalTrigramCount() {
        return counts.values().stream()
            .mapToInt(nextCounts -> nextCounts.values().stream().mapToInt(Integer::intValue).sum())
            .sum();
    }

    // Getters and Setters
    public Map<String, Map<Integer, Integer>> getCounts() {
        return counts;
    }

    public void setCounts(Map<String, Map<Integer, Integer>> counts) {
        this.counts = counts;
    }

    public Map<Integer, Map<Integer, Integer>> getBigramCounts() {
        return bigramCounts;
    }

    public void setBigramCounts(Map<Integer, Map<Integer, Integer>> bigramCounts) {
        this.bigramCounts = bigramCounts;
    }

    public Map<String, Integer> getVocabulary() {
        return vocabulary;
    }

    public void setVocabulary(Map<String, Integer> vocabulary) {
        this.vocabulary = vocabulary;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    /**
     * 학습 메타데이터
     */
    public static class Metadata {
        private String modelType = "trigram";
        private String tokenizerType;
        private int vocabSize;
        private int totalTokens;
        private int totalTrigrams;
        private int totalBigrams;
        private String trainedAt;
        private String corpusInfo;

        public Metadata() {
            this.trainedAt = Instant.now().toString();
        }

        // Getters and Setters
        public String getModelType() { return modelType; }
        public void setModelType(String modelType) { this.modelType = modelType; }

        public String getTokenizerType() { return tokenizerType; }
        public void setTokenizerType(String tokenizerType) { this.tokenizerType = tokenizerType; }

        public int getVocabSize() { return vocabSize; }
        public void setVocabSize(int vocabSize) { this.vocabSize = vocabSize; }

        public int getTotalTokens() { return totalTokens; }
        public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }

        public int getTotalTrigrams() { return totalTrigrams; }
        public void setTotalTrigrams(int totalTrigrams) { this.totalTrigrams = totalTrigrams; }

        public int getTotalBigrams() { return totalBigrams; }
        public void setTotalBigrams(int totalBigrams) { this.totalBigrams = totalBigrams; }

        public String getTrainedAt() { return trainedAt; }
        public void setTrainedAt(String trainedAt) { this.trainedAt = trainedAt; }

        public String getCorpusInfo() { return corpusInfo; }
        public void setCorpusInfo(String corpusInfo) { this.corpusInfo = corpusInfo; }

        @Override
        public String toString() {
            return String.format("Metadata(model=%s, vocab=%d, tokens=%d, trigrams=%d, bigrams=%d)",
                modelType, vocabSize, totalTokens, totalTrigrams, totalBigrams);
        }
    }

    @Override
    public String toString() {
        return String.format("TrigramArtifact(vocab=%d, trigrams=%d, %s)",
            vocabulary.size(), getTotalTrigramCount(), metadata);
    }
}
