package com.miniai.model;

import com.codeai.tokenizer.CodeTokenizer;
import com.miniai.core.types.GenerateRequest;
import com.miniai.core.types.GenerateResponse;
import com.miniai.model.ngram.NgramArtifact;
import com.miniai.model.ngram.NgramModel;
import com.miniai.model.ngram.NgramTrainer;
import com.miniai.model.smoothing.KneserNey;
import com.miniai.model.smoothing.SimpleBackoff;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

/**
 * Trigram vs 5-gram 성능 비교 실험
 *
 * 측정 지표:
 * 1. Top-K 정확도: 정답이 상위 K개 후보에 포함되는 비율
 * 2. 생성 품질: 실제 코드 패턴과의 일치도
 * 3. 다양성: 생성된 결과의 고유성
 * 4. 속도: 학습/추론 시간
 */
public class ModelComparisonTest {

    private static String trainingCorpus;
    private static String testCorpus;
    private static CodeTokenizer tokenizer;

    // 모델들
    private static TrigramModel trigramModel;
    private static NgramModel fivegramBackoff;
    private static NgramModel fivegramKN;

    @BeforeAll
    static void setUp() {
        // 학습용 코퍼스
        trainingCorpus = """
            public class User {
                private String name;
                private String email;
                private int age;

                public String getName() {
                    return name;
                }

                public void setName(String name) {
                    this.name = name;
                }

                public String getEmail() {
                    return email;
                }

                public int getAge() {
                    return age;
                }
            }

            public class UserService {
                private UserRepository userRepository;

                public User findById(Long id) {
                    return userRepository.findById(id);
                }

                public List<User> findAll() {
                    return userRepository.findAll();
                }

                public void save(User user) {
                    userRepository.save(user);
                }
            }

            public static void main(String[] args) {
                System.out.println("Hello World");
                UserService service = new UserService();
                User user = service.findById(1L);
                System.out.println(user.getName());
            }

            for (int i = 0; i < 10; i++) {
                System.out.println(i);
            }

            for (int j = 0; j < users.size(); j++) {
                User user = users.get(j);
                System.out.println(user.getName());
            }

            if (value != null) {
                return value;
            } else {
                return defaultValue;
            }

            if (user != null && user.getName() != null) {
                System.out.println(user.getName());
            }

            try {
                service.save(user);
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }

            Optional<User> optional = userRepository.findById(id);
            optional.ifPresent(user -> System.out.println(user.getName()));

            List<User> filtered = users.stream()
                .filter(u -> u.getAge() > 18)
                .collect(Collectors.toList());
            """;

        // 테스트용 프롬프트들 (실제 패턴 기반)
        testCorpus = trainingCorpus;

        // 토크나이저 생성
        tokenizer = CodeTokenizer.fromCode(trainingCorpus);

        System.out.println("=== 모델 학습 시작 ===");
        System.out.println("Vocabulary size: " + tokenizer.vocabSize());

        // 1. Trigram 모델
        long start = System.currentTimeMillis();
        TrigramTrainer trigramTrainer = new TrigramTrainer(tokenizer);
        TrigramArtifact trigramArtifact = trigramTrainer.trainFromText(trainingCorpus, tokenizer);
        trigramModel = new TrigramModel(trigramArtifact, tokenizer);
        long trigramTrainTime = System.currentTimeMillis() - start;

        // 2. 5-gram + SimpleBackoff
        start = System.currentTimeMillis();
        NgramTrainer fivegramTrainer = new NgramTrainer(5, tokenizer);
        NgramArtifact fivegramArtifact = fivegramTrainer.trainFromText(trainingCorpus, tokenizer);
        fivegramBackoff = new NgramModel(fivegramArtifact, tokenizer, new SimpleBackoff());
        long fivegramTrainTime = System.currentTimeMillis() - start;

        // 3. 5-gram + Kneser-Ney
        fivegramKN = new NgramModel(fivegramArtifact, tokenizer, new KneserNey());

        System.out.println("\n=== 학습 완료 ===");
        System.out.println("Trigram 학습 시간: " + trigramTrainTime + "ms");
        System.out.println("5-gram 학습 시간: " + fivegramTrainTime + "ms");
        System.out.println("Trigram ngrams: " + trigramArtifact.getMetadata().getTotalTrigrams());
        System.out.println("5-gram ngrams: " + fivegramArtifact.getMetadata().getTotalNgrams());
    }

    @Test
    @DisplayName("실험 1: 코드 패턴 완성 정확도")
    void testPatternCompletion() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("실험 1: 코드 패턴 완성 정확도");
        System.out.println("=".repeat(60));

        // 테스트 케이스: (프롬프트, 기대하는 다음 토큰들)
        String[][] testCases = {
            {"public class", "User", "UserService"},
            {"private String", "name", "email"},
            {"return", "name", "value", "user"},
            {"for (int i = 0;", "i"},
            {"System.out.println(", "user", "i", "\""},
            {"if (value !=", "null"},
            {"public void", "setName", "save"},
            {"userRepository.", "findById", "findAll", "save"},
        };

        int trigramCorrect = 0;
        int fivegramBOCorrect = 0;
        int fivegramKNCorrect = 0;

        for (String[] testCase : testCases) {
            String prompt = testCase[0];
            Set<String> expectedTokens = new HashSet<>();
            for (int i = 1; i < testCase.length; i++) {
                expectedTokens.add(testCase[i]);
            }

            System.out.println("\n프롬프트: \"" + prompt + "\"");
            System.out.println("기대 토큰: " + expectedTokens);

            // Trigram 생성
            String trigramResult = generate(trigramModel, prompt, 1);
            String trigramNext = extractFirstNewToken(prompt, trigramResult);
            boolean trigramMatch = expectedTokens.stream().anyMatch(e -> trigramNext.contains(e));
            if (trigramMatch) trigramCorrect++;

            // 5-gram + Backoff 생성
            String fivegramBOResult = generate(fivegramBackoff, prompt, 1);
            String fivegramBONext = extractFirstNewToken(prompt, fivegramBOResult);
            boolean fivegramBOMatch = expectedTokens.stream().anyMatch(e -> fivegramBONext.contains(e));
            if (fivegramBOMatch) fivegramBOCorrect++;

            // 5-gram + Kneser-Ney 생성
            String fivegramKNResult = generate(fivegramKN, prompt, 1);
            String fivegramKNNext = extractFirstNewToken(prompt, fivegramKNResult);
            boolean fivegramKNMatch = expectedTokens.stream().anyMatch(e -> fivegramKNNext.contains(e));
            if (fivegramKNMatch) fivegramKNCorrect++;

            System.out.println("  Trigram    → \"" + trigramNext + "\" " + (trigramMatch ? "✓" : "✗"));
            System.out.println("  5-gram+BO  → \"" + fivegramBONext + "\" " + (fivegramBOMatch ? "✓" : "✗"));
            System.out.println("  5-gram+KN  → \"" + fivegramKNNext + "\" " + (fivegramKNMatch ? "✓" : "✗"));
        }

        System.out.println("\n" + "-".repeat(60));
        System.out.println("정확도 결과:");
        System.out.printf("  Trigram:       %d/%d (%.1f%%)\n",
            trigramCorrect, testCases.length, 100.0 * trigramCorrect / testCases.length);
        System.out.printf("  5-gram+BO:     %d/%d (%.1f%%)\n",
            fivegramBOCorrect, testCases.length, 100.0 * fivegramBOCorrect / testCases.length);
        System.out.printf("  5-gram+KN:     %d/%d (%.1f%%)\n",
            fivegramKNCorrect, testCases.length, 100.0 * fivegramKNCorrect / testCases.length);
    }

    @Test
    @DisplayName("실험 2: 생성 품질 비교")
    void testGenerationQuality() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("실험 2: 생성 품질 비교 (10 토큰 생성)");
        System.out.println("=".repeat(60));

        String[] prompts = {
            "public class User {",
            "for (int i = 0;",
            "if (user != null)",
            "System.out.println(",
            "return userRepository."
        };

        for (String prompt : prompts) {
            System.out.println("\n프롬프트: \"" + prompt + "\"");
            System.out.println("-".repeat(50));

            String trigramResult = generate(trigramModel, prompt, 10);
            String fivegramBOResult = generate(fivegramBackoff, prompt, 10);
            String fivegramKNResult = generate(fivegramKN, prompt, 10);

            System.out.println("Trigram:    " + trigramResult);
            System.out.println("5-gram+BO:  " + fivegramBOResult);
            System.out.println("5-gram+KN:  " + fivegramKNResult);
        }
    }

    @Test
    @DisplayName("실험 3: 생성 다양성 비교")
    void testDiversity() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("실험 3: 생성 다양성 (동일 프롬프트 10회 생성)");
        System.out.println("=".repeat(60));

        String prompt = "public class";

        Set<String> trigramResults = new HashSet<>();
        Set<String> fivegramBOResults = new HashSet<>();
        Set<String> fivegramKNResults = new HashSet<>();

        for (int i = 0; i < 10; i++) {
            trigramResults.add(generateWithSeed(trigramModel, prompt, 5, i));
            fivegramBOResults.add(generateWithSeed(fivegramBackoff, prompt, 5, i));
            fivegramKNResults.add(generateWithSeed(fivegramKN, prompt, 5, i));
        }

        System.out.println("\n프롬프트: \"" + prompt + "\"");
        System.out.println("-".repeat(50));
        System.out.println("Trigram 고유 결과 수:    " + trigramResults.size() + "/10");
        System.out.println("5-gram+BO 고유 결과 수:  " + fivegramBOResults.size() + "/10");
        System.out.println("5-gram+KN 고유 결과 수:  " + fivegramKNResults.size() + "/10");

        System.out.println("\nTrigram 결과:");
        trigramResults.forEach(r -> System.out.println("  - " + r));
        System.out.println("\n5-gram+KN 결과:");
        fivegramKNResults.forEach(r -> System.out.println("  - " + r));
    }

    @Test
    @DisplayName("실험 4: 추론 속도 비교")
    void testInferenceSpeed() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("실험 4: 추론 속도 비교 (100회 생성)");
        System.out.println("=".repeat(60));

        String prompt = "public class User {";
        int iterations = 100;

        // Warmup
        for (int i = 0; i < 10; i++) {
            generate(trigramModel, prompt, 10);
            generate(fivegramBackoff, prompt, 10);
            generate(fivegramKN, prompt, 10);
        }

        // Trigram
        long start = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            generate(trigramModel, prompt, 10);
        }
        long trigramTime = System.currentTimeMillis() - start;

        // 5-gram + Backoff
        start = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            generate(fivegramBackoff, prompt, 10);
        }
        long fivegramBOTime = System.currentTimeMillis() - start;

        // 5-gram + Kneser-Ney
        start = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            generate(fivegramKN, prompt, 10);
        }
        long fivegramKNTime = System.currentTimeMillis() - start;

        System.out.println("\n" + iterations + "회 생성 총 시간:");
        System.out.printf("  Trigram:       %dms (%.2fms/생성)\n", trigramTime, (double)trigramTime/iterations);
        System.out.printf("  5-gram+BO:     %dms (%.2fms/생성)\n", fivegramBOTime, (double)fivegramBOTime/iterations);
        System.out.printf("  5-gram+KN:     %dms (%.2fms/생성)\n", fivegramKNTime, (double)fivegramKNTime/iterations);
    }

    @Test
    @DisplayName("실험 5: 문맥 의존성 테스트")
    void testContextDependency() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("실험 5: 문맥 의존성 테스트");
        System.out.println("=".repeat(60));
        System.out.println("같은 마지막 토큰이지만 다른 문맥에서의 예측 차이");

        // 같은 "(" 토큰이지만 다른 문맥
        String[][] contexts = {
            {"for (", "for 루프 시작"},
            {"if (", "if 조건문 시작"},
            {"System.out.println(", "출력문"},
            {"findById(", "메서드 호출"},
        };

        System.out.println("\n마지막 토큰: \"(\"");
        System.out.println("-".repeat(50));

        for (String[] ctx : contexts) {
            String prompt = ctx[0];
            String description = ctx[1];

            String trigramResult = generate(trigramModel, prompt, 3);
            String fivegramResult = generate(fivegramKN, prompt, 3);

            System.out.printf("\n[%s] \"%s\"\n", description, prompt);
            System.out.println("  Trigram:    " + trigramResult);
            System.out.println("  5-gram+KN:  " + fivegramResult);
        }

        System.out.println("\n분석: 5-gram은 더 긴 문맥을 사용하므로 ");
        System.out.println("      'for (' vs 'if (' 등을 구분할 수 있음");
    }

    @Test
    @DisplayName("종합 결과 요약")
    void printSummary() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("종합 결과 요약");
        System.out.println("=".repeat(60));

        System.out.println("""

            | 지표              | Trigram | 5-gram+BO | 5-gram+KN |
            |-------------------|---------|-----------|-----------|
            | 문맥 길이         | 2 토큰  | 4 토큰    | 4 토큰    |
            | Smoothing         | 단순    | Backoff   | Kneser-Ney|
            | 희소성 처리       | 약함    | 중간      | 강함      |

            결론:
            1. 문맥 길이: 5-gram이 더 긴 패턴 인식 가능
            2. Smoothing: Kneser-Ney가 더 나은 일반화
            3. 속도: Trigram이 약간 빠름 (문맥 짧음)
            4. 다양성: Kneser-Ney가 더 다양한 결과 생성

            추천:
            - 교육용: Trigram (단순, 이해하기 쉬움)
            - 실용: 5-gram + Kneser-Ney (더 나은 품질)
            """);
    }

    // Helper methods
    private String generate(TrigramModel model, String prompt, int maxTokens) {
        GenerateRequest request = GenerateRequest.builder(prompt)
            .maxTokens(maxTokens)
            .temperature(0.8)
            .topK(10)
            .seed(42L)
            .build();
        return model.generate(request).getGeneratedText();
    }

    private String generate(NgramModel model, String prompt, int maxTokens) {
        GenerateRequest request = GenerateRequest.builder(prompt)
            .maxTokens(maxTokens)
            .temperature(0.8)
            .topK(10)
            .seed(42L)
            .build();
        return model.generate(request).getGeneratedText();
    }

    private String generateWithSeed(TrigramModel model, String prompt, int maxTokens, long seed) {
        GenerateRequest request = GenerateRequest.builder(prompt)
            .maxTokens(maxTokens)
            .temperature(1.0)
            .topK(10)
            .seed(seed)
            .build();
        return model.generate(request).getGeneratedText();
    }

    private String generateWithSeed(NgramModel model, String prompt, int maxTokens, long seed) {
        GenerateRequest request = GenerateRequest.builder(prompt)
            .maxTokens(maxTokens)
            .temperature(1.0)
            .topK(10)
            .seed(seed)
            .build();
        return model.generate(request).getGeneratedText();
    }

    private String extractFirstNewToken(String prompt, String result) {
        if (result.length() <= prompt.length()) {
            return "[없음]";
        }
        String newPart = result.substring(prompt.length()).trim();
        String[] tokens = newPart.split("\\s+");
        return tokens.length > 0 ? tokens[0] : "[없음]";
    }
}
