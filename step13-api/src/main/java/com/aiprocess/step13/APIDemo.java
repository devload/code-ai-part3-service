package com.aiprocess.step13;

import java.util.*;

/**
 * STEP 13: API 호출 데모
 */
public class APIDemo {

    public static void main(String[] args) {
        System.out.println("═".repeat(60));
        System.out.println("STEP 13: API 호출 (API Calling)");
        System.out.println("═".repeat(60));
        System.out.println();
        System.out.println("핵심 질문: LLM API는 어떻게 사용하는가?");
        System.out.println();

        // API 호출 데모
        demoAPIStructure();
        demoProviders();
        demoSimulatedCall();
    }

    private static void demoAPIStructure() {
        System.out.println("─".repeat(60));
        System.out.println("1. API 요청 구조");
        System.out.println("─".repeat(60));
        System.out.println();

        System.out.println("  ┌─────────────────────────────────────────────────────┐");
        System.out.println("  │ LLM API 요청의 구성 요소                              │");
        System.out.println("  └─────────────────────────────────────────────────────┘");
        System.out.println();

        System.out.println("  📋 HTTP 요청 구조:");
        System.out.println();
        System.out.println("  ┌─────────────────────────────────────────────────────┐");
        System.out.println("  │ POST /v1/messages HTTP/1.1                          │");
        System.out.println("  │ Host: api.anthropic.com                              │");
        System.out.println("  │ Content-Type: application/json                       │");
        System.out.println("  │ x-api-key: sk-ant-...                                │");
        System.out.println("  │ anthropic-version: 2023-06-01                        │");
        System.out.println("  │                                                      │");
        System.out.println("  │ {                                                    │");
        System.out.println("  │   \"model\": \"claude-3-sonnet-20240229\",             │");
        System.out.println("  │   \"max_tokens\": 1024,                               │");
        System.out.println("  │   \"messages\": [                                     │");
        System.out.println("  │     {\"role\": \"user\", \"content\": \"...\"}            │");
        System.out.println("  │   ]                                                  │");
        System.out.println("  │ }                                                    │");
        System.out.println("  └─────────────────────────────────────────────────────┘");
        System.out.println();

        System.out.println("  🔑 필수 요소:");
        System.out.println("     • Endpoint URL: API 서버 주소");
        System.out.println("     • Headers: 인증 및 메타데이터");
        System.out.println("     • Body: 모델, 프롬프트, 설정");
        System.out.println();
    }

    private static void demoProviders() {
        System.out.println("─".repeat(60));
        System.out.println("2. LLM 프로바이더 비교");
        System.out.println("─".repeat(60));
        System.out.println();

        System.out.println("  ┌───────────┬────────────────────────────────────────┐");
        System.out.println("  │ Provider  │ 특징                                   │");
        System.out.println("  ├───────────┼────────────────────────────────────────┤");
        System.out.println("  │ Claude    │ 긴 컨텍스트, 안전성, 코드 분석 강점    │");
        System.out.println("  │ OpenAI    │ 다양한 모델, Function Calling          │");
        System.out.println("  │ Ollama    │ 로컬 실행, 무료, 프라이버시            │");
        System.out.println("  └───────────┴────────────────────────────────────────┘");
        System.out.println();

        for (APIClient.Provider provider : APIClient.Provider.values()) {
            System.out.println("  📡 " + provider.getDisplayName());
            System.out.println("     Endpoint: " + provider.getEndpoint());
            System.out.println();
        }
    }

    private static void demoSimulatedCall() {
        System.out.println("─".repeat(60));
        System.out.println("3. API 호출 시뮬레이션");
        System.out.println("─".repeat(60));
        System.out.println();

        APIClient client = new APIClient();

        // 각 프로바이더 시뮬레이션
        System.out.println("  🔄 각 프로바이더 호출 시뮬레이션:");
        System.out.println();

        // Claude 시뮬레이션
        List<Map<String, String>> messages = List.of(
            Map.of("role", "user", "content", "Hello, analyze this code.")
        );

        APIClient.APIRequest claudeRequest = APIClient.APIRequest.forClaude(
            "sk-ant-demo-key",
            "claude-3-sonnet-20240229",
            messages
        );

        System.out.println("  📤 Claude 요청:");
        System.out.println("     Model: claude-3-sonnet-20240229");
        System.out.println("     Messages: " + messages.size() + "개");

        APIClient.APIResponse claudeResponse = client.simulateCall(claudeRequest);
        printResponse("Claude", claudeResponse);

        // OpenAI 시뮬레이션
        APIClient.APIRequest openaiRequest = APIClient.APIRequest.forOpenAI(
            "sk-demo-key",
            "gpt-4",
            messages
        );

        System.out.println("  📤 OpenAI 요청:");
        System.out.println("     Model: gpt-4");

        APIClient.APIResponse openaiResponse = client.simulateCall(openaiRequest);
        printResponse("OpenAI", openaiResponse);

        // Ollama 시뮬레이션
        APIClient.APIRequest ollamaRequest = APIClient.APIRequest.forOllama(
            "codellama",
            "Analyze this code"
        );

        System.out.println("  📤 Ollama 요청:");
        System.out.println("     Model: codellama");

        APIClient.APIResponse ollamaResponse = client.simulateCall(ollamaRequest);
        printResponse("Ollama", ollamaResponse);

        // 요약
        System.out.println("  ┌─────────────────────────────────────────────────────┐");
        System.out.println("  │ API 호출 핵심 포인트                                  │");
        System.out.println("  └─────────────────────────────────────────────────────┘");
        System.out.println();
        System.out.println("  ✅ 인증: API 키는 환경변수로 관리");
        System.out.println("  ✅ 타임아웃: 적절한 대기 시간 설정");
        System.out.println("  ✅ 에러 처리: 재시도 로직 구현");
        System.out.println("  ✅ 비용 관리: 토큰 사용량 모니터링");
        System.out.println();
    }

    private static void printResponse(String provider, APIClient.APIResponse response) {
        System.out.println("  📥 " + provider + " 응답:");
        System.out.println("     Status: " + (response.success() ? "✅ 성공" : "❌ 실패"));
        System.out.println("     HTTP Code: " + response.statusCode());
        System.out.println("     Latency: " + response.latencyMs() + "ms");
        if (response.error() != null) {
            System.out.println("     Error: " + response.error());
        }
        System.out.println();
    }
}
