package com.aiprocess.step18;

/**
 * STEP 18: 피드백 루프 데모
 */
public class FeedbackDemo {

    public static void main(String[] args) {
        System.out.println("═".repeat(60));
        System.out.println("STEP 18: 피드백 루프 (Feedback Loop)");
        System.out.println("═".repeat(60));
        System.out.println();
        System.out.println("핵심 질문: 결과를 어떻게 개선하는가?");
        System.out.println();

        // 피드백 루프 데모
        demoFeedbackProcess();
        demoRetryStrategies();
        demoStatistics();
    }

    private static void demoFeedbackProcess() {
        System.out.println("─".repeat(60));
        System.out.println("1. 피드백 프로세스");
        System.out.println("─".repeat(60));
        System.out.println();

        System.out.println("  ┌─────────────────────────────────────────────────────┐");
        System.out.println("  │ AI 결과 개선 사이클                                  │");
        System.out.println("  └─────────────────────────────────────────────────────┘");
        System.out.println();

        System.out.println("       ┌──────────────┐");
        System.out.println("       │   AI 실행    │");
        System.out.println("       └──────┬───────┘");
        System.out.println("              │");
        System.out.println("              ▼");
        System.out.println("       ┌──────────────┐");
        System.out.println("       │   결과 평가  │◀────────────┐");
        System.out.println("       └──────┬───────┘            │");
        System.out.println("              │                    │");
        System.out.println("        ┌─────┴─────┐              │");
        System.out.println("        │           │              │");
        System.out.println("        ▼           ▼              │");
        System.out.println("    ┌───────┐  ┌───────┐           │");
        System.out.println("    │ 성공  │  │ 실패  │───────────┤");
        System.out.println("    └───────┘  └───┬───┘           │");
        System.out.println("                   │               │");
        System.out.println("                   ▼               │");
        System.out.println("            ┌──────────────┐       │");
        System.out.println("            │  전략 조정   │───────┘");
        System.out.println("            └──────────────┘");
        System.out.println();

        // 실제 평가 시뮬레이션
        FeedbackLoop loop = new FeedbackLoop();

        System.out.println("  📋 시나리오: SQL Injection 수정");
        System.out.println();

        String originalCode = """
            String query = "SELECT * FROM users WHERE id=" + id;
            """;

        String fixedCode = """
            String query = "SELECT * FROM users WHERE id=?";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, id);
            """;

        System.out.println("  원본 코드:");
        System.out.println("    " + originalCode.trim());
        System.out.println();
        System.out.println("  수정된 코드:");
        for (String line : fixedCode.split("\n")) {
            System.out.println("    " + line);
        }
        System.out.println();

        FeedbackLoop.EvaluationInput input = new FeedbackLoop.EvaluationInput(
            "CODE_FIX",
            "SQL_INJECTION",
            originalCode,
            fixedCode
        );

        FeedbackLoop.FeedbackResult result = loop.evaluate(input);

        System.out.println("  📊 평가 결과:");
        System.out.println("     성공: " + (result.success() ? "✅" : "❌"));
        System.out.println("     점수: " + String.format("%.1f", result.score()) + "/100");
        System.out.println();

        System.out.println("  ✔️ 검증 항목:");
        for (FeedbackLoop.ValidationResult v : result.validations()) {
            String emoji = v.passed() ? "✅" : "❌";
            System.out.println("     " + emoji + " " + v.checkType() + ": " + v.message());
        }
        System.out.println();
    }

    private static void demoRetryStrategies() {
        System.out.println("─".repeat(60));
        System.out.println("2. 재시도 전략");
        System.out.println("─".repeat(60));
        System.out.println();

        System.out.println("  ┌─────────────────────────────────────────────────────┐");
        System.out.println("  │ 실패 시 자동 재시도 전략                             │");
        System.out.println("  └─────────────────────────────────────────────────────┘");
        System.out.println();

        for (FeedbackLoop.RetryStrategy strategy : FeedbackLoop.RetryStrategy.values()) {
            String emoji = switch (strategy) {
                case NONE -> "⏹️";
                case SAME_WITH_CONTEXT -> "🔄";
                case REFINED_PROMPT -> "✏️";
                case DIFFERENT_MODEL -> "🔀";
                case MINIMAL_CHANGE -> "📏";
            };

            System.out.println("  " + emoji + " " + strategy.name());
            System.out.println("     " + strategy.getDescription());
            System.out.println();
        }

        // 실패 케이스 시뮬레이션
        System.out.println("  📋 실패 케이스 시뮬레이션:");
        System.out.println();

        FeedbackLoop loop = new FeedbackLoop();

        // 구문 오류가 있는 수정
        String badFix = """
            String query = "SELECT * FROM users WHERE id=?"
            PreparedStatement stmt = conn.prepareStatement(query;
            """;

        FeedbackLoop.EvaluationInput failedInput = new FeedbackLoop.EvaluationInput(
            "CODE_FIX",
            "SQL_INJECTION",
            "original",
            badFix
        );

        FeedbackLoop.FeedbackResult failedResult = loop.evaluate(failedInput);

        System.out.println("     결과: " + (failedResult.success() ? "성공" : "실패"));
        System.out.println("     점수: " + String.format("%.1f", failedResult.score()));
        System.out.println("     재시도 필요: " + (failedResult.shouldRetry() ? "예" : "아니오"));
        System.out.println("     권장 전략: " + failedResult.retryStrategy().name());
        System.out.println("       → " + failedResult.retryStrategy().getDescription());
        System.out.println();

        if (!failedResult.improvements().isEmpty()) {
            System.out.println("     💡 개선 제안:");
            for (String improvement : failedResult.improvements()) {
                System.out.println("        • " + improvement);
            }
            System.out.println();
        }
    }

    private static void demoStatistics() {
        System.out.println("─".repeat(60));
        System.out.println("3. 학습 및 통계");
        System.out.println("─".repeat(60));
        System.out.println();

        FeedbackLoop loop = new FeedbackLoop();

        // 여러 케이스 시뮬레이션
        String[][] cases = {
            {"CODE_FIX", "SQL_INJECTION", "good fix", "true"},
            {"CODE_FIX", "SQL_INJECTION", "good fix", "true"},
            {"CODE_FIX", "SYSTEM_OUT", "logger.info", "true"},
            {"CODE_FIX", "EMPTY_CATCH", "bad fix with {}", "false"},
            {"CODE_FIX", "EMPTY_CATCH", "bad fix with {}", "false"},
            {"CODE_REVIEW", "GENERAL", "review result", "true"},
        };

        System.out.println("  📊 시뮬레이션 실행 중...");
        System.out.println();

        for (String[] c : cases) {
            String code = c[3].equals("true") ? "fixed properly" : "still has problem {}";
            FeedbackLoop.EvaluationInput input = new FeedbackLoop.EvaluationInput(
                c[0], c[1], "original", code
            );
            loop.evaluate(input);
        }

        FeedbackLoop.Statistics stats = loop.getStatistics();

        System.out.println("  ┌─────────────────────────────────────────────────────┐");
        System.out.println("  │ 전체 통계                                           │");
        System.out.println("  └─────────────────────────────────────────────────────┘");
        System.out.println();
        System.out.println("     총 시도: " + stats.totalAttempts() + "회");
        System.out.println("     성공: " + stats.successfulAttempts() + "회");
        System.out.println("     성공률: " + String.format("%.1f", stats.successRate()) + "%");
        System.out.println();

        System.out.println("  ┌─────────────────────────────────────────────────────┐");
        System.out.println("  │ 패턴별 성공률                                        │");
        System.out.println("  └─────────────────────────────────────────────────────┘");
        System.out.println();

        for (var entry : stats.patternSuccessRates().entrySet()) {
            double rate = entry.getValue();
            String bar = "█".repeat((int)(rate / 10)) + "░".repeat(10 - (int)(rate / 10));
            String emoji = rate >= 80 ? "🟢" : rate >= 50 ? "🟡" : "🔴";

            System.out.printf("     %s %-25s %s %.0f%%%n",
                emoji, entry.getKey(), bar, rate);
        }
        System.out.println();

        System.out.println("  ┌─────────────────────────────────────────────────────┐");
        System.out.println("  │ 피드백 루프 핵심 포인트                              │");
        System.out.println("  └─────────────────────────────────────────────────────┘");
        System.out.println();
        System.out.println("  ✅ 자동 검증: 구문, 이슈 해결, 회귀 테스트");
        System.out.println("  ✅ 패턴 학습: 성공/실패 패턴 기록 및 분석");
        System.out.println("  ✅ 적응형 재시도: 실패 원인에 따른 전략 조정");
        System.out.println("  ✅ 지속적 개선: 통계 기반 프롬프트/모델 최적화");
        System.out.println();

        System.out.println("  💡 실제 적용:");
        System.out.println("     • 낮은 성공률 패턴 → 프롬프트 개선 필요");
        System.out.println("     • 반복 실패 → 다른 모델 시도");
        System.out.println("     • 회귀 발생 → 최소 변경 전략 적용");
        System.out.println();
    }
}
