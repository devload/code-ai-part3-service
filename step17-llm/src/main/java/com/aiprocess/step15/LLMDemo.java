package com.aiprocess.step15;

/**
 * STEP 15: LLM 처리 데모
 */
public class LLMDemo {

    public static void main(String[] args) {
        System.out.println("═".repeat(60));
        System.out.println("STEP 15: LLM 처리 (LLM Processing)");
        System.out.println("═".repeat(60));
        System.out.println();
        System.out.println("핵심 질문: 어떤 모델을 언제 쓰는가?");
        System.out.println();

        // LLM 처리 데모
        demoModelComparison();
        demoModelSelection();
        demoCostEstimation();
    }

    private static void demoModelComparison() {
        System.out.println("─".repeat(60));
        System.out.println("1. LLM 모델 비교");
        System.out.println("─".repeat(60));
        System.out.println();

        LLMRouter router = new LLMRouter();

        System.out.println("  ┌─────────────────────────────────────────────────────┐");
        System.out.println("  │ 사용 가능한 모델들                                   │");
        System.out.println("  └─────────────────────────────────────────────────────┘");
        System.out.println();

        // 프로바이더별 그룹화
        for (LLMRouter.Provider provider : LLMRouter.Provider.values()) {
            System.out.println("  📦 " + provider.getDisplayName());
            System.out.println("  ─────────────────────────────────────────────────────");

            for (LLMRouter.ModelConfig model : router.getAllModels()) {
                if (model.provider() == provider) {
                    System.out.printf("     %s %s%n",
                        model.tier().getEmoji(),
                        model.modelId());
                    System.out.printf("        Context: %,d tokens%n", model.contextWindow());
                    if (model.inputCostPer1K() > 0) {
                        System.out.printf("        Cost: $%.4f/1K in, $%.4f/1K out%n",
                            model.inputCostPer1K(), model.outputCostPer1K());
                    } else {
                        System.out.println("        Cost: 무료 (로컬)");
                    }
                    System.out.println("        " + model.description());
                    System.out.println();
                }
            }
        }
    }

    private static void demoModelSelection() {
        System.out.println("─".repeat(60));
        System.out.println("2. 작업별 모델 선택");
        System.out.println("─".repeat(60));
        System.out.println();

        LLMRouter router = new LLMRouter();

        System.out.println("  ┌─────────────────────────────────────────────────────┐");
        System.out.println("  │ 작업 유형에 따른 자동 모델 선택                       │");
        System.out.println("  └─────────────────────────────────────────────────────┘");
        System.out.println();

        for (LLMRouter.TaskType taskType : LLMRouter.TaskType.values()) {
            LLMRouter.ModelConfig selected = router.selectModel(
                taskType,
                LLMRouter.RoutingOptions.defaults()
            );

            String taskDesc = switch (taskType) {
                case COMPLEX_ANALYSIS -> "복잡한 코드 분석";
                case SIMPLE_FIX -> "단순 버그 수정";
                case CODE_GENERATION -> "코드 생성";
                case QUICK_RESPONSE -> "빠른 응답 필요";
                case SECURITY_AUDIT -> "보안 감사";
            };

            System.out.printf("  📋 %s%n", taskDesc);
            System.out.printf("     → 선택: %s %s%n",
                selected.tier().getEmoji(),
                selected.modelId());
            System.out.println();
        }

        // 조건별 선택 데모
        System.out.println("  ┌─────────────────────────────────────────────────────┐");
        System.out.println("  │ 조건에 따른 모델 선택                                │");
        System.out.println("  └─────────────────────────────────────────────────────┘");
        System.out.println();

        // 로컬 전용
        System.out.println("  🔒 로컬 전용 (민감 데이터):");
        LLMRouter.ModelConfig localModel = router.selectModel(
            LLMRouter.TaskType.CODE_GENERATION,
            LLMRouter.RoutingOptions.localOnly()
        );
        System.out.println("     → " + localModel.modelId());
        System.out.println();

        // 예산 제한
        System.out.println("  💰 예산 제한 ($0.01 이하):");
        LLMRouter.ModelConfig budgetModel = router.selectModel(
            LLMRouter.TaskType.COMPLEX_ANALYSIS,
            LLMRouter.RoutingOptions.budgetFriendly(0.01)
        );
        System.out.println("     → " + budgetModel.modelId());
        System.out.println();
    }

    private static void demoCostEstimation() {
        System.out.println("─".repeat(60));
        System.out.println("3. 비용 추정");
        System.out.println("─".repeat(60));
        System.out.println();

        LLMRouter router = new LLMRouter();

        System.out.println("  ┌─────────────────────────────────────────────────────┐");
        System.out.println("  │ 시나리오: 코드 리뷰 1000회                           │");
        System.out.println("  │ 입력: 평균 2000 토큰, 출력: 평균 500 토큰            │");
        System.out.println("  └─────────────────────────────────────────────────────┘");
        System.out.println();

        int inputTokens = 2000;
        int outputTokens = 500;
        int reviews = 1000;

        System.out.println("  📊 모델별 예상 비용 (1000회 기준):");
        System.out.println();
        System.out.println("  ┌────────────────────────┬────────────┬────────────┐");
        System.out.println("  │ 모델                   │ 1회 비용   │ 1000회     │");
        System.out.println("  ├────────────────────────┼────────────┼────────────┤");

        String[] modelNames = {"claude-3-opus", "claude-3-sonnet", "claude-3-haiku",
                               "gpt-4-turbo", "gpt-3.5-turbo", "codellama"};

        for (String name : modelNames) {
            LLMRouter.ModelConfig model = router.getModel(name);
            if (model != null) {
                double singleCost = router.estimateCost(model, inputTokens, outputTokens);
                double totalCost = singleCost * reviews;

                System.out.printf("  │ %-22s │ $%-9.4f │ $%-9.2f │%n",
                    truncate(model.modelId(), 22),
                    singleCost,
                    totalCost);
            }
        }

        System.out.println("  └────────────────────────┴────────────┴────────────┘");
        System.out.println();

        System.out.println("  💡 비용 최적화 전략:");
        System.out.println("     • 단순 작업은 Haiku/GPT-3.5 사용");
        System.out.println("     • 민감 데이터는 로컬 모델 활용");
        System.out.println("     • 배치 처리로 API 호출 최소화");
        System.out.println("     • 프롬프트 최적화로 토큰 절약");
        System.out.println();

        System.out.println("  ┌─────────────────────────────────────────────────────┐");
        System.out.println("  │ 모델 선택 가이드                                     │");
        System.out.println("  └─────────────────────────────────────────────────────┘");
        System.out.println();
        System.out.println("  🚀 Flagship (Opus, GPT-4):");
        System.out.println("     • 복잡한 아키텍처 리뷰");
        System.out.println("     • 보안 취약점 심층 분석");
        System.out.println("     • 대규모 리팩토링 제안");
        System.out.println();
        System.out.println("  ⚖️  Balanced (Sonnet, GPT-4-Turbo):");
        System.out.println("     • 일반 코드 리뷰");
        System.out.println("     • 버그 분석 및 수정");
        System.out.println("     • 문서화 생성");
        System.out.println();
        System.out.println("  ⚡ Fast (Haiku, GPT-3.5):");
        System.out.println("     • 간단한 포맷팅");
        System.out.println("     • 변수명 제안");
        System.out.println("     • 빠른 피드백 필요시");
        System.out.println();
        System.out.println("  🏠 Local (Ollama):");
        System.out.println("     • 민감한 코드 (비공개 프로젝트)");
        System.out.println("     • 오프라인 환경");
        System.out.println("     • 비용 제한 없는 실험");
        System.out.println();
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 2) + "..";
    }
}
