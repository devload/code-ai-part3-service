package com.codeai.analyzer.llm;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * LLM 클라이언트 인터페이스
 *
 * 다양한 LLM 제공자 (Claude, OpenAI, Ollama 등)를 추상화합니다.
 */
public interface LLMClient {

    /**
     * 동기 방식 메시지 전송
     */
    LLMResponse chat(LLMRequest request);

    /**
     * 비동기 방식 메시지 전송
     */
    CompletableFuture<LLMResponse> chatAsync(LLMRequest request);

    /**
     * 스트리밍 방식 메시지 전송
     */
    void chatStream(LLMRequest request, StreamHandler handler);

    /**
     * 클라이언트 이름
     */
    String getName();

    /**
     * 사용 가능 여부 확인
     */
    boolean isAvailable();

    /**
     * 요청 메시지
     */
    record LLMRequest(
        String systemPrompt,
        List<Message> messages,
        double temperature,
        int maxTokens,
        Map<String, Object> options
    ) {
        public LLMRequest(String systemPrompt, String userMessage) {
            this(systemPrompt, List.of(new Message("user", userMessage)), 0.7, 4096, Map.of());
        }

        public LLMRequest(String systemPrompt, String userMessage, double temperature) {
            this(systemPrompt, List.of(new Message("user", userMessage)), temperature, 4096, Map.of());
        }
    }

    /**
     * 메시지
     */
    record Message(String role, String content) {}

    /**
     * 응답
     */
    record LLMResponse(
        String content,
        String model,
        Usage usage,
        boolean success,
        String error
    ) {
        public static LLMResponse error(String error) {
            return new LLMResponse(null, null, null, false, error);
        }

        public static LLMResponse success(String content, String model, Usage usage) {
            return new LLMResponse(content, model, usage, true, null);
        }
    }

    /**
     * 토큰 사용량
     */
    record Usage(int inputTokens, int outputTokens) {
        public int totalTokens() {
            return inputTokens + outputTokens;
        }
    }

    /**
     * 스트리밍 핸들러
     */
    interface StreamHandler {
        void onToken(String token);
        void onComplete(LLMResponse response);
        void onError(Throwable error);
    }
}
