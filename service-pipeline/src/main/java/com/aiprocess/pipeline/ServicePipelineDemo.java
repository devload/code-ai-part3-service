package com.aiprocess.pipeline;

import com.aiprocess.step13.APIClient;
import com.aiprocess.step14.PromptBuilder;
import com.aiprocess.step15.LLMRouter;
import com.aiprocess.step16.ResponseParser;
import com.aiprocess.step17.ActionExecutor;
import com.aiprocess.step18.FeedbackLoop;

import java.util.*;

/**
 * AI 서비스 파이프라인 통합 데모
 *
 * 전체 흐름:
 * API 호출 → 프롬프트 구성 → LLM 처리 → 응답 파싱 → 액션 실행 → 피드백
 */
public class ServicePipelineDemo {

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║       AI Process - Part 3: AI 서비스 프로세스               ║");
        System.out.println("║                   통합 파이프라인 데모                      ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println();

        demoFullPipeline();
    }

    private static void demoFullPipeline() {
        System.out.println("═".repeat(60));
        System.out.println("AI 서비스 파이프라인 전체 흐름");
        System.out.println("═".repeat(60));
        System.out.println();

        // 입력 코드
        String inputCode = """
            public class UserService {
                private String password = "admin123";

                public User findUser(String id) {
                    System.out.println("Finding user: " + id);
                    String query = "SELECT * FROM users WHERE id=" + id;
                    return db.execute(query);
                }
            }
            """;

        System.out.println("📝 입력 코드:");
        System.out.println("─".repeat(60));
        int lineNum = 1;
        for (String line : inputCode.split("\n")) {
            System.out.printf("  %3d │ %s%n", lineNum++, line);
        }
        System.out.println("─".repeat(60));
        System.out.println();

        // ═══════════════════════════════════════════════════════════
        // STEP 13: API 호출 준비
        // ═══════════════════════════════════════════════════════════
        System.out.println("┌─────────────────────────────────────────────────────────┐");
        System.out.println("│ STEP 13: API 호출 (API Calling)                         │");
        System.out.println("│ 핵심: LLM API는 어떻게 사용하는가?                       │");
        System.out.println("└─────────────────────────────────────────────────────────┘");
        System.out.println();

        APIClient apiClient = new APIClient();

        System.out.println("  📡 프로바이더 선택:");
        for (APIClient.Provider provider : APIClient.Provider.values()) {
            System.out.println("     • " + provider.getDisplayName());
        }
        System.out.println();

        // ═══════════════════════════════════════════════════════════
        // STEP 14: 프롬프트 구성
        // ═══════════════════════════════════════════════════════════
        System.out.println("┌─────────────────────────────────────────────────────────┐");
        System.out.println("│ STEP 14: 프롬프트 구성 (Prompt Engineering)             │");
        System.out.println("│ 핵심: 좋은 프롬프트는 어떻게 만드는가?                   │");
        System.out.println("└─────────────────────────────────────────────────────────┘");
        System.out.println();

        PromptBuilder.Prompt prompt = PromptBuilder.codeReviewPrompt(inputCode, "java");

        System.out.println("  🔧 생성된 프롬프트:");
        System.out.println("     역할: CODE_REVIEWER");
        System.out.println("     출력 형식: " + prompt.outputFormat());
        System.out.println("     예상 토큰: ~" + prompt.estimateTokens());
        System.out.println();

        // ═══════════════════════════════════════════════════════════
        // STEP 15: LLM 모델 선택
        // ═══════════════════════════════════════════════════════════
        System.out.println("┌─────────────────────────────────────────────────────────┐");
        System.out.println("│ STEP 15: LLM 처리 (LLM Processing)                      │");
        System.out.println("│ 핵심: 어떤 모델을 언제 쓰는가?                           │");
        System.out.println("└─────────────────────────────────────────────────────────┘");
        System.out.println();

        LLMRouter router = new LLMRouter();
        LLMRouter.ModelConfig selectedModel = router.selectModel(
            LLMRouter.TaskType.SECURITY_AUDIT,
            LLMRouter.RoutingOptions.defaults()
        );

        System.out.println("  🎯 선택된 모델:");
        System.out.println("     " + selectedModel.tier().getEmoji() + " " + selectedModel.modelId());
        System.out.println("     이유: 보안 감사 → Flagship 모델 권장");
        System.out.println();

        // API 호출 시뮬레이션
        System.out.println("  🔄 API 호출 시뮬레이션...");

        APIClient.APIRequest request = APIClient.APIRequest.forClaude(
            "demo-key",
            selectedModel.modelId(),
            List.of(Map.of("role", "user", "content", prompt.userPrompt()))
        );

        APIClient.APIResponse apiResponse = apiClient.simulateCall(request);

        System.out.println("     응답 시간: " + apiResponse.latencyMs() + "ms");
        System.out.println("     상태: " + (apiResponse.success() ? "✅ 성공" : "❌ 실패"));
        System.out.println();

        // ═══════════════════════════════════════════════════════════
        // STEP 16: 응답 파싱
        // ═══════════════════════════════════════════════════════════
        System.out.println("┌─────────────────────────────────────────────────────────┐");
        System.out.println("│ STEP 16: 응답 파싱 (Response Parsing)                   │");
        System.out.println("│ 핵심: AI 응답을 어떻게 처리하는가?                       │");
        System.out.println("└─────────────────────────────────────────────────────────┘");
        System.out.println();

        // 시뮬레이션된 AI 응답
        String simulatedResponse = """
            {
              "summary": "심각한 보안 이슈 2개와 코드 품질 이슈 1개가 발견되었습니다.",
              "issues": [
                {"type": "HARDCODED_SECRET", "severity": "CRITICAL", "message": "비밀번호가 하드코딩됨", "line": 2},
                {"type": "SQL_INJECTION", "severity": "CRITICAL", "message": "SQL Injection 취약점", "line": 6},
                {"type": "SYSTEM_OUT", "severity": "WARNING", "message": "System.out 사용", "line": 5}
              ],
              "suggestions": [
                "환경변수로 비밀번호 관리",
                "PreparedStatement 사용",
                "Logger 도입"
              ],
              "score": 25
            }
            """;

        ResponseParser parser = new ResponseParser();
        ResponseParser.ParsedResponse parsed = parser.parse(simulatedResponse);

        System.out.println("  📥 파싱된 응답:");
        System.out.println("     형식: " + parsed.format());
        System.out.println("     요약: " + parsed.summary());
        System.out.println("     점수: " + parsed.score() + "/100");
        System.out.println();

        System.out.println("  🔍 발견된 이슈:");
        for (ResponseParser.Issue issue : parsed.issues()) {
            String emoji = issue.severity().equals("CRITICAL") ? "🚨" : "⚠️";
            System.out.printf("     %s [%s] Line %d: %s%n",
                emoji, issue.type(), issue.line(), issue.message());
        }
        System.out.println();

        // ═══════════════════════════════════════════════════════════
        // STEP 17: 액션 실행
        // ═══════════════════════════════════════════════════════════
        System.out.println("┌─────────────────────────────────────────────────────────┐");
        System.out.println("│ STEP 17: 액션 실행 (Action Execution)                   │");
        System.out.println("│ 핵심: AI가 도구를 어떻게 사용하는가?                     │");
        System.out.println("└─────────────────────────────────────────────────────────┘");
        System.out.println();

        ActionExecutor executor = new ActionExecutor();

        List<ActionExecutor.Action> actions = List.of(
            new ActionExecutor.Action(
                ActionExecutor.ActionType.REPLACE_CODE,
                Map.of(
                    "old_code", "System.out.println(\"Finding user: \" + id);",
                    "new_code", "logger.info(\"Finding user: {}\", id);"
                ),
                null
            ),
            new ActionExecutor.Action(
                ActionExecutor.ActionType.REPLACE_CODE,
                Map.of(
                    "old_code", "String query = \"SELECT * FROM users WHERE id=\" + id;",
                    "new_code", "String query = \"SELECT * FROM users WHERE id=?\";"
                ),
                null
            )
        );

        System.out.println("  🔧 수정 액션 실행:");
        ActionExecutor.BatchResult batchResult = executor.executeBatch(actions, inputCode);

        for (int i = 0; i < batchResult.results().size(); i++) {
            ActionExecutor.ActionResult result = batchResult.results().get(i);
            String emoji = result.success() ? "✅" : "❌";
            System.out.println("     " + (i + 1) + ". " + emoji + " " + result.message());
        }
        System.out.println();

        System.out.println("  ✨ 수정된 코드:");
        System.out.println("  ─────────────────────────────────────────────────────");
        lineNum = 1;
        for (String line : batchResult.finalCode().split("\n")) {
            System.out.printf("  %3d │ %s%n", lineNum++, line);
        }
        System.out.println("  ─────────────────────────────────────────────────────");
        System.out.println();

        // ═══════════════════════════════════════════════════════════
        // STEP 18: 피드백 루프
        // ═══════════════════════════════════════════════════════════
        System.out.println("┌─────────────────────────────────────────────────────────┐");
        System.out.println("│ STEP 18: 피드백 루프 (Feedback Loop)                    │");
        System.out.println("│ 핵심: 결과를 어떻게 개선하는가?                          │");
        System.out.println("└─────────────────────────────────────────────────────────┘");
        System.out.println();

        FeedbackLoop feedbackLoop = new FeedbackLoop();

        FeedbackLoop.EvaluationInput evalInput = new FeedbackLoop.EvaluationInput(
            "CODE_FIX",
            "SQL_INJECTION",
            inputCode,
            batchResult.finalCode()
        );

        FeedbackLoop.FeedbackResult feedbackResult = feedbackLoop.evaluate(evalInput);

        System.out.println("  📊 평가 결과:");
        System.out.println("     성공: " + (feedbackResult.success() ? "✅" : "❌"));
        System.out.println("     점수: " + String.format("%.1f", feedbackResult.score()) + "/100");
        System.out.println();

        System.out.println("  ✔️ 검증 항목:");
        for (FeedbackLoop.ValidationResult v : feedbackResult.validations()) {
            String emoji = v.passed() ? "✅" : "❌";
            System.out.println("     " + emoji + " " + v.checkType() + ": " + v.message());
        }
        System.out.println();

        // ═══════════════════════════════════════════════════════════
        // 최종 요약
        // ═══════════════════════════════════════════════════════════
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║                     파이프라인 완료                         ║");
        System.out.println("╠════════════════════════════════════════════════════════════╣");
        System.out.println("║                                                            ║");
        System.out.println("║  입력 → API → 프롬프트 → LLM → 파싱 → 액션 → 피드백        ║");
        System.out.println("║                                                            ║");
        System.out.println("║  ✅ 발견된 이슈: " + parsed.issues().size() + "개" + " ".repeat(40) + "║");
        System.out.println("║  ✅ 수정 완료: " + batchResult.successCount() + "개" + " ".repeat(41) + "║");
        System.out.println("║  ✅ 검증 통과: " + (feedbackResult.success() ? "예" : "아니오") + " ".repeat(41) + "║");
        System.out.println("║                                                            ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println();

        System.out.println("  🎓 Part 3 학습 완료!");
        System.out.println();
        System.out.println("     STEP 13: LLM API 호출 방법");
        System.out.println("     STEP 14: 효과적인 프롬프트 작성");
        System.out.println("     STEP 15: 상황별 모델 선택");
        System.out.println("     STEP 16: 응답 파싱 및 구조화");
        System.out.println("     STEP 17: 코드 수정 자동화");
        System.out.println("     STEP 18: 결과 검증 및 개선");
        System.out.println();
    }
}
