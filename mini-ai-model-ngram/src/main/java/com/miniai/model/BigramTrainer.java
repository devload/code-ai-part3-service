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
 * Bigram 모델 학습기
 *
 * 학습 포인트:
 * - 학습 = 데이터에서 패턴 추출
 * - Bigram 학습 = "A 다음에 B가 몇 번 나왔는지" 세기
 * - 결과를 JSON으로 저장하여 재사용
 */
public class BigramTrainer implements Trainer {

    private final Tokenizer tokenizer;
    private final Gson gson;

    public BigramTrainer(Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    }

    /**
     * 텍스트로부터 Tokenizer 생성 후 학습
     */
    public BigramTrainer(String corpus) {
        this(WhitespaceTokenizer.fromText(corpus));
    }

    @Override
    public void train(Path corpusPath, Path outputPath) {
        try {
            // 1. Corpus 읽기
            String corpus = Files.readString(corpusPath);

            // 2. Tokenizer가 아직 vocabulary를 모르면 corpus로부터 생성
            Tokenizer finalTokenizer = tokenizer;
            if (tokenizer.vocabSize() == 1) { // only [UNK]
                finalTokenizer = WhitespaceTokenizer.fromText(corpus);
            }

            // 3. Bigram 카운트 생성
            BigramArtifact artifact = trainFromText(corpus, finalTokenizer);

            // 4. JSON으로 저장
            String json = gson.toJson(artifact);
            Files.writeString(outputPath, json);

            System.out.println("✅ 학습 완료: " + outputPath);
            System.out.println("   Vocabulary: " + artifact.getVocabulary().size());
            System.out.println("   Total tokens: " + artifact.getMetadata().getTotalTokens());
            System.out.println("   Total bigrams: " + artifact.getMetadata().getTotalBigrams());

        } catch (IOException e) {
            throw new RuntimeException("학습 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 텍스트로부터 Bigram 학습
     */
    public BigramArtifact trainFromText(String corpus, Tokenizer tokenizer) {
        // 1. 토큰화
        List<Integer> tokens = tokenizer.encode(corpus);

        // 2. Bigram 카운트
        Map<Integer, Map<Integer, Integer>> counts = new HashMap<>();

        for (int i = 0; i < tokens.size() - 1; i++) {
            int prevToken = tokens.get(i);
            int nextToken = tokens.get(i + 1);

            // counts[prev][next]++
            counts.putIfAbsent(prevToken, new HashMap<>());
            Map<Integer, Integer> nextCounts = counts.get(prevToken);
            nextCounts.put(nextToken, nextCounts.getOrDefault(nextToken, 0) + 1);
        }

        // 3. Vocabulary 추출 및 토크나이저 타입 결정
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

        // 4. Metadata 생성
        BigramArtifact.Metadata metadata = new BigramArtifact.Metadata();
        metadata.setTokenizerType(tokenizerType);
        metadata.setVocabSize(tokenizer.vocabSize());
        metadata.setTotalTokens(tokens.size());
        metadata.setTotalBigrams(tokens.size() - 1);
        metadata.setCorpusInfo(String.format("%d characters, %d tokens",
            corpus.length(), tokens.size()));

        // 5. Artifact 생성
        BigramArtifact artifact = new BigramArtifact(counts, vocabulary, metadata);

        return artifact;
    }

    /**
     * JSON 파일로부터 Artifact 로드
     */
    public static BigramArtifact loadArtifact(Path artifactPath) {
        try {
            String json = Files.readString(artifactPath);
            Gson gson = new Gson();
            return gson.fromJson(json, BigramArtifact.class);
        } catch (IOException e) {
            throw new RuntimeException("Artifact 로드 실패: " + e.getMessage(), e);
        }
    }

    @Override
    public String trainerName() {
        return "BigramTrainer";
    }

    /**
     * 학습 결과 요약 출력
     */
    public void printSummary(BigramArtifact artifact, int topN) {
        System.out.println("\n=== Bigram 학습 결과 요약 ===");
        System.out.println(artifact.getMetadata());

        // 가장 빈도 높은 bigram 출력
        System.out.println("\n가장 빈도 높은 Bigram (상위 " + topN + "개):");

        artifact.getCounts().entrySet().stream()
            .flatMap(entry -> {
                int prevToken = entry.getKey();
                return entry.getValue().entrySet().stream()
                    .map(nextEntry -> new BigramCount(
                        prevToken,
                        nextEntry.getKey(),
                        nextEntry.getValue()
                    ));
            })
            .sorted((a, b) -> Integer.compare(b.count, a.count))
            .limit(topN)
            .forEach(bc -> {
                String prevWord = getWord(artifact.getVocabulary(), bc.prevToken);
                String nextWord = getWord(artifact.getVocabulary(), bc.nextToken);
                System.out.println(String.format("  [%s] → [%s] : %d회",
                    prevWord, nextWord, bc.count));
            });
    }

    private String getWord(Map<String, Integer> vocabulary, int tokenId) {
        return vocabulary.entrySet().stream()
            .filter(e -> e.getValue() == tokenId)
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse("[UNK]");
    }

    /**
     * Bigram 카운트를 위한 헬퍼 클래스
     */
    private static class BigramCount {
        int prevToken;
        int nextToken;
        int count;

        BigramCount(int prevToken, int nextToken, int count) {
            this.prevToken = prevToken;
            this.nextToken = nextToken;
            this.count = count;
        }
    }
}
