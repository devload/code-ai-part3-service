package com.miniai.model;

import com.codeai.tokenizer.CodeTokenizer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.miniai.core.model.Trainer;
import com.miniai.core.tokenizer.Tokenizer;
import com.miniai.tokenizer.WhitespaceTokenizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Trigram 모델 학습기
 *
 * 학습 포인트:
 * - Trigram: "A, B 다음에 C가 몇 번 나왔는지" 세기
 * - Bigram도 함께 저장 (backoff용)
 * - 더 긴 문맥 = 더 정확한 예측 (but 희소성 증가)
 */
public class TrigramTrainer implements Trainer {

    private final Tokenizer tokenizer;
    private final Gson gson;

    public TrigramTrainer(Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    }

    @Override
    public void train(Path corpusPath, Path outputPath) {
        try {
            // 1. Corpus 읽기
            String corpus = Files.readString(corpusPath);

            // 2. Trigram 학습
            TrigramArtifact artifact = trainFromText(corpus, tokenizer);

            // 3. JSON으로 저장
            String json = gson.toJson(artifact);
            Files.writeString(outputPath, json);

            System.out.println("✅ Trigram 학습 완료: " + outputPath);
            System.out.println("   Vocabulary: " + artifact.getVocabulary().size());
            System.out.println("   Total tokens: " + artifact.getMetadata().getTotalTokens());
            System.out.println("   Total trigrams: " + artifact.getMetadata().getTotalTrigrams());
            System.out.println("   Total bigrams: " + artifact.getMetadata().getTotalBigrams());

        } catch (IOException e) {
            throw new RuntimeException("학습 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 텍스트로부터 Trigram 학습
     */
    public TrigramArtifact trainFromText(String corpus, Tokenizer tokenizer) {
        // 1. 토큰화
        List<Integer> tokens = tokenizer.encode(corpus);

        // 2. Trigram 카운트
        Map<String, Map<Integer, Integer>> trigramCounts = new HashMap<>();

        for (int i = 0; i < tokens.size() - 2; i++) {
            int prev1 = tokens.get(i);
            int prev2 = tokens.get(i + 1);
            int next = tokens.get(i + 2);

            String key = TrigramArtifact.makeKey(prev1, prev2);

            trigramCounts.putIfAbsent(key, new HashMap<>());
            Map<Integer, Integer> nextCounts = trigramCounts.get(key);
            nextCounts.put(next, nextCounts.getOrDefault(next, 0) + 1);
        }

        // 3. Bigram 카운트 (backoff용)
        Map<Integer, Map<Integer, Integer>> bigramCounts = new HashMap<>();

        for (int i = 0; i < tokens.size() - 1; i++) {
            int prev = tokens.get(i);
            int next = tokens.get(i + 1);

            bigramCounts.putIfAbsent(prev, new HashMap<>());
            Map<Integer, Integer> nextCounts = bigramCounts.get(prev);
            nextCounts.put(next, nextCounts.getOrDefault(next, 0) + 1);
        }

        // 4. Vocabulary 추출 및 토크나이저 타입 결정
        Map<String, Integer> vocabulary = new HashMap<>();
        String tokenizerType;

        if (tokenizer instanceof CodeTokenizer) {
            vocabulary = ((CodeTokenizer) tokenizer).getVocabulary();
            tokenizerType = "CodeTokenizer";
        } else if (tokenizer instanceof WhitespaceTokenizer) {
            vocabulary = ((WhitespaceTokenizer) tokenizer).getVocabulary();
            tokenizerType = "WhitespaceTokenizer";
        } else {
            tokenizerType = tokenizer.getClass().getSimpleName();
        }

        // 5. Metadata 생성
        TrigramArtifact.Metadata metadata = new TrigramArtifact.Metadata();
        metadata.setTokenizerType(tokenizerType);
        metadata.setVocabSize(tokenizer.vocabSize());
        metadata.setTotalTokens(tokens.size());
        metadata.setTotalTrigrams(tokens.size() - 2);
        metadata.setTotalBigrams(tokens.size() - 1);
        metadata.setCorpusInfo(String.format("%d characters, %d tokens",
            corpus.length(), tokens.size()));

        // 6. Artifact 생성
        return new TrigramArtifact(trigramCounts, bigramCounts, vocabulary, metadata);
    }

    /**
     * JSON 파일로부터 Artifact 로드
     */
    public static TrigramArtifact loadArtifact(Path artifactPath) {
        try {
            String json = Files.readString(artifactPath);
            Gson gson = new Gson();
            return gson.fromJson(json, TrigramArtifact.class);
        } catch (IOException e) {
            throw new RuntimeException("Artifact 로드 실패: " + e.getMessage(), e);
        }
    }

    @Override
    public String trainerName() {
        return "TrigramTrainer";
    }
}
