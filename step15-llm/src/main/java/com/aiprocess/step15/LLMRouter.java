package com.aiprocess.step15;

import java.util.*;

/**
 * STEP 15: LLM 처리
 *
 * 핵심 질문: 어떤 모델을 언제 쓰는가?
 *
 * 용도에 따라 적절한 LLM을 선택하는 라우터입니다.
 *
 * ┌─────────────────────────────────────────────────────────┐
 * │ LLM 선택 기준                                            │
 * │                                                          │
 * │ 1. 작업 복잡도                                           │
 * │    - 단순: 작은 모델 (빠름, 저렴)                        │
 * │    - 복잡: 큰 모델 (정확, 고비용)                        │
 * │                                                          │
 * │ 2. 응답 속도 요구                                        │
 * │    - 실시간: 작은 모델, 스트리밍                         │
 * │    - 배치: 큰 모델 허용                                  │
 * │                                                          │
 * │ 3. 비용 제약                                             │
 * │    - 무료: Ollama (로컬)                                 │
 * │    - 유료: Claude, OpenAI (품질)                         │
 * │                                                          │
 * │ 4. 보안/프라이버시                                       │
 * │    - 민감 데이터: 로컬 모델                              │
 * │    - 일반 데이터: 클라우드 API                           │
 * └─────────────────────────────────────────────────────────┘
 */
public class LLMRouter {

    private final Map<String, ModelConfig> models;
    private String defaultModel;

    public LLMRouter() {
        this.models = new LinkedHashMap<>();
        initializeModels();
    }

    private void initializeModels() {
        // Claude 모델들
        models.put("claude-3-opus", new ModelConfig(
            "claude-3-opus-20240229",
            Provider.CLAUDE,
            Tier.FLAGSHIP,
            200000,
            0.015,  // input per 1K tokens
            0.075,  // output per 1K tokens
            "가장 강력, 복잡한 작업에 적합"
        ));

        models.put("claude-3-sonnet", new ModelConfig(
            "claude-3-sonnet-20240229",
            Provider.CLAUDE,
            Tier.BALANCED,
            200000,
            0.003,
            0.015,
            "균형 잡힌 성능과 비용"
        ));

        models.put("claude-3-haiku", new ModelConfig(
            "claude-3-haiku-20240307",
            Provider.CLAUDE,
            Tier.FAST,
            200000,
            0.00025,
            0.00125,
            "빠르고 저렴, 단순 작업에 적합"
        ));

        // OpenAI 모델들
        models.put("gpt-4-turbo", new ModelConfig(
            "gpt-4-turbo-preview",
            Provider.OPENAI,
            Tier.FLAGSHIP,
            128000,
            0.01,
            0.03,
            "강력한 추론 능력"
        ));

        models.put("gpt-4", new ModelConfig(
            "gpt-4",
            Provider.OPENAI,
            Tier.BALANCED,
            8192,
            0.03,
            0.06,
            "안정적인 성능"
        ));

        models.put("gpt-3.5-turbo", new ModelConfig(
            "gpt-3.5-turbo",
            Provider.OPENAI,
            Tier.FAST,
            16385,
            0.0005,
            0.0015,
            "빠르고 저렴"
        ));

        // Ollama (로컬)
        models.put("codellama", new ModelConfig(
            "codellama:13b",
            Provider.OLLAMA,
            Tier.LOCAL,
            16000,
            0.0,
            0.0,
            "로컬 코드 특화 모델"
        ));

        models.put("llama2", new ModelConfig(
            "llama2:13b",
            Provider.OLLAMA,
            Tier.LOCAL,
            4096,
            0.0,
            0.0,
            "로컬 범용 모델"
        ));

        this.defaultModel = "claude-3-sonnet";
    }

    /**
     * 작업 유형에 따른 최적 모델 선택
     */
    public ModelConfig selectModel(TaskType taskType, RoutingOptions options) {
        List<ModelConfig> candidates = new ArrayList<>();

        for (ModelConfig model : models.values()) {
            // 프로바이더 필터
            if (options.preferredProvider() != null &&
                model.provider() != options.preferredProvider()) {
                continue;
            }

            // 컨텍스트 크기 필터
            if (options.requiredContextSize() > model.contextWindow()) {
                continue;
            }

            // 비용 필터
            if (options.maxCostPerRequest() > 0) {
                double estimatedCost = estimateCost(model,
                    options.estimatedInputTokens(),
                    options.estimatedOutputTokens());
                if (estimatedCost > options.maxCostPerRequest()) {
                    continue;
                }
            }

            // 로컬 전용 필터
            if (options.requireLocal() && model.provider() != Provider.OLLAMA) {
                continue;
            }

            candidates.add(model);
        }

        if (candidates.isEmpty()) {
            return models.get(defaultModel);
        }

        // 작업 유형에 따른 정렬
        return switch (taskType) {
            case COMPLEX_ANALYSIS -> candidates.stream()
                .filter(m -> m.tier() == Tier.FLAGSHIP || m.tier() == Tier.BALANCED)
                .findFirst()
                .orElse(candidates.get(0));

            case SIMPLE_FIX -> candidates.stream()
                .filter(m -> m.tier() == Tier.FAST || m.tier() == Tier.LOCAL)
                .findFirst()
                .orElse(candidates.get(0));

            case CODE_GENERATION -> candidates.stream()
                .filter(m -> m.modelId().contains("code") ||
                            m.tier() == Tier.FLAGSHIP)
                .findFirst()
                .orElse(candidates.get(0));

            case QUICK_RESPONSE -> candidates.stream()
                .min(Comparator.comparingDouble(
                    m -> m.inputCostPer1K() + m.outputCostPer1K()))
                .orElse(candidates.get(0));

            case SECURITY_AUDIT -> candidates.stream()
                .filter(m -> m.tier() == Tier.FLAGSHIP)
                .findFirst()
                .orElse(candidates.get(0));
        };
    }

    /**
     * 비용 추정
     */
    public double estimateCost(ModelConfig model, int inputTokens, int outputTokens) {
        return (inputTokens / 1000.0 * model.inputCostPer1K()) +
               (outputTokens / 1000.0 * model.outputCostPer1K());
    }

    /**
     * 모든 모델 목록
     */
    public Collection<ModelConfig> getAllModels() {
        return models.values();
    }

    /**
     * 특정 모델 조회
     */
    public ModelConfig getModel(String name) {
        return models.get(name);
    }

    /**
     * LLM 프로바이더
     */
    public enum Provider {
        CLAUDE("Anthropic"),
        OPENAI("OpenAI"),
        OLLAMA("Ollama (Local)");

        private final String displayName;

        Provider(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }
    }

    /**
     * 모델 티어
     */
    public enum Tier {
        FLAGSHIP("🚀 Flagship", "최고 성능, 복잡한 작업"),
        BALANCED("⚖️ Balanced", "균형 잡힌 성능/비용"),
        FAST("⚡ Fast", "빠르고 저렴"),
        LOCAL("🏠 Local", "로컬 실행, 무료");

        private final String emoji;
        private final String description;

        Tier(String emoji, String description) {
            this.emoji = emoji;
            this.description = description;
        }

        public String getEmoji() { return emoji; }
        public String getDescription() { return description; }
    }

    /**
     * 작업 유형
     */
    public enum TaskType {
        COMPLEX_ANALYSIS,   // 복잡한 코드 분석
        SIMPLE_FIX,         // 단순 수정
        CODE_GENERATION,    // 코드 생성
        QUICK_RESPONSE,     // 빠른 응답 필요
        SECURITY_AUDIT      // 보안 감사
    }

    /**
     * 모델 설정
     */
    public record ModelConfig(
        String modelId,
        Provider provider,
        Tier tier,
        int contextWindow,
        double inputCostPer1K,
        double outputCostPer1K,
        String description
    ) {}

    /**
     * 라우팅 옵션
     */
    public record RoutingOptions(
        Provider preferredProvider,
        int requiredContextSize,
        double maxCostPerRequest,
        int estimatedInputTokens,
        int estimatedOutputTokens,
        boolean requireLocal
    ) {
        public static RoutingOptions defaults() {
            return new RoutingOptions(null, 0, 0, 1000, 500, false);
        }

        public static RoutingOptions localOnly() {
            return new RoutingOptions(Provider.OLLAMA, 0, 0, 1000, 500, true);
        }

        public static RoutingOptions budgetFriendly(double maxCost) {
            return new RoutingOptions(null, 0, maxCost, 1000, 500, false);
        }
    }
}
