package com.aiprocess.step13;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.IOException;
import java.time.Duration;
import java.util.*;

/**
 * STEP 13: API 호출
 *
 * 핵심 질문: LLM API는 어떻게 사용하는가?
 *
 * AI 서비스와 통신하는 기본 클라이언트입니다.
 *
 * ┌─────────────────────────────────────────────────────────┐
 * │ API 호출 과정                                            │
 * │                                                          │
 * │ 1. 요청 구성 (Request Building)                          │
 * │    - 엔드포인트 URL                                      │
 * │    - HTTP 메서드 (POST)                                  │
 * │    - 헤더 (Authorization, Content-Type)                  │
 * │    - 바디 (JSON)                                         │
 * │                                                          │
 * │ 2. HTTP 전송                                             │
 * │    - 타임아웃 설정                                       │
 * │    - 재시도 로직                                         │
 * │    - 에러 핸들링                                         │
 * │                                                          │
 * │ 3. 응답 수신                                             │
 * │    - 상태 코드 확인                                      │
 * │    - JSON 파싱                                           │
 * │    - 에러 메시지 추출                                    │
 * └─────────────────────────────────────────────────────────┘
 */
public class APIClient {

    private final OkHttpClient httpClient;
    private final Gson gson;

    public APIClient() {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(30))
            .readTimeout(Duration.ofSeconds(60))
            .writeTimeout(Duration.ofSeconds(60))
            .build();
        this.gson = new Gson();
    }

    /**
     * API 호출 실행
     */
    public APIResponse call(APIRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. HTTP 요청 구성
            Request httpRequest = buildHttpRequest(request);

            // 2. 요청 전송
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                long latency = System.currentTimeMillis() - startTime;

                // 3. 응답 처리
                String responseBody = response.body() != null ? response.body().string() : "";

                if (response.isSuccessful()) {
                    JsonObject json = gson.fromJson(responseBody, JsonObject.class);
                    return new APIResponse(
                        true,
                        response.code(),
                        json,
                        null,
                        latency
                    );
                } else {
                    return new APIResponse(
                        false,
                        response.code(),
                        null,
                        "HTTP " + response.code() + ": " + responseBody,
                        latency
                    );
                }
            }
        } catch (IOException e) {
            long latency = System.currentTimeMillis() - startTime;
            return new APIResponse(
                false,
                0,
                null,
                "Network error: " + e.getMessage(),
                latency
            );
        }
    }

    /**
     * 시뮬레이션 모드 (실제 API 없이 테스트)
     */
    public APIResponse simulateCall(APIRequest request) {
        long startTime = System.currentTimeMillis();

        // 시뮬레이션 딜레이
        try {
            Thread.sleep(100 + new Random().nextInt(200));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long latency = System.currentTimeMillis() - startTime;

        // 시뮬레이션 응답 생성
        JsonObject simulatedResponse = new JsonObject();

        switch (request.provider()) {
            case CLAUDE -> {
                simulatedResponse.addProperty("id", "msg_" + UUID.randomUUID().toString().substring(0, 8));
                simulatedResponse.addProperty("type", "message");
                simulatedResponse.addProperty("role", "assistant");
                JsonObject content = new JsonObject();
                content.addProperty("type", "text");
                content.addProperty("text", "시뮬레이션: Claude 응답입니다. 실제로는 API 키가 필요합니다.");
                simulatedResponse.add("content", content);
                simulatedResponse.addProperty("model", "claude-3-sonnet-20240229");
                simulatedResponse.addProperty("stop_reason", "end_turn");
            }
            case OPENAI -> {
                simulatedResponse.addProperty("id", "chatcmpl-" + UUID.randomUUID().toString().substring(0, 8));
                simulatedResponse.addProperty("object", "chat.completion");
                simulatedResponse.addProperty("model", "gpt-4");
                JsonObject choice = new JsonObject();
                JsonObject message = new JsonObject();
                message.addProperty("role", "assistant");
                message.addProperty("content", "시뮬레이션: OpenAI 응답입니다. 실제로는 API 키가 필요합니다.");
                choice.add("message", message);
                choice.addProperty("finish_reason", "stop");
                simulatedResponse.add("choices", gson.toJsonTree(List.of(choice)));
            }
            case OLLAMA -> {
                simulatedResponse.addProperty("model", "codellama");
                simulatedResponse.addProperty("response", "시뮬레이션: Ollama 응답입니다. 로컬 서버가 필요합니다.");
                simulatedResponse.addProperty("done", true);
            }
        }

        return new APIResponse(true, 200, simulatedResponse, null, latency);
    }

    private Request buildHttpRequest(APIRequest request) {
        RequestBody body = RequestBody.create(
            gson.toJson(request.body()),
            MediaType.parse("application/json")
        );

        Request.Builder builder = new Request.Builder()
            .url(request.endpoint())
            .post(body);

        // 헤더 추가
        for (Map.Entry<String, String> header : request.headers().entrySet()) {
            builder.addHeader(header.getKey(), header.getValue());
        }

        return builder.build();
    }

    /**
     * LLM 프로바이더
     */
    public enum Provider {
        CLAUDE("Claude (Anthropic)", "https://api.anthropic.com/v1/messages"),
        OPENAI("OpenAI (GPT)", "https://api.openai.com/v1/chat/completions"),
        OLLAMA("Ollama (Local)", "http://localhost:11434/api/generate");

        private final String displayName;
        private final String endpoint;

        Provider(String displayName, String endpoint) {
            this.displayName = displayName;
            this.endpoint = endpoint;
        }

        public String getDisplayName() { return displayName; }
        public String getEndpoint() { return endpoint; }
    }

    /**
     * API 요청
     */
    public record APIRequest(
        Provider provider,
        String endpoint,
        Map<String, String> headers,
        Map<String, Object> body
    ) {
        public static APIRequest forClaude(String apiKey, String model, List<Map<String, String>> messages) {
            Map<String, String> headers = Map.of(
                "x-api-key", apiKey,
                "anthropic-version", "2023-06-01",
                "content-type", "application/json"
            );

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("max_tokens", 1024);
            body.put("messages", messages);

            return new APIRequest(Provider.CLAUDE, Provider.CLAUDE.endpoint, headers, body);
        }

        public static APIRequest forOpenAI(String apiKey, String model, List<Map<String, String>> messages) {
            Map<String, String> headers = Map.of(
                "Authorization", "Bearer " + apiKey,
                "Content-Type", "application/json"
            );

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("max_tokens", 1024);

            return new APIRequest(Provider.OPENAI, Provider.OPENAI.endpoint, headers, body);
        }

        public static APIRequest forOllama(String model, String prompt) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("prompt", prompt);
            body.put("stream", false);

            return new APIRequest(Provider.OLLAMA, Provider.OLLAMA.endpoint, Map.of(), body);
        }
    }

    /**
     * API 응답
     */
    public record APIResponse(
        boolean success,
        int statusCode,
        JsonObject data,
        String error,
        long latencyMs
    ) {}
}
