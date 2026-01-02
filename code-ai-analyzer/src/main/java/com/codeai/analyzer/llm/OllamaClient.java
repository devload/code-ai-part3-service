package com.codeai.analyzer.llm;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Ollama 로컬 LLM 클라이언트
 *
 * 로컬에서 실행되는 Ollama 서버를 통해 LLM을 사용합니다.
 * 인터넷 연결 없이 코드 리뷰를 수행할 수 있습니다.
 *
 * 권장 모델:
 * - codellama:13b (코드 특화)
 * - deepseek-coder:6.7b (코드 특화)
 * - llama3:8b (범용)
 * - qwen2.5-coder:7b (코드 특화)
 *
 * 설치:
 * - brew install ollama
 * - ollama pull codellama:13b
 * - ollama serve
 */
public class OllamaClient implements LLMClient {

    private static final String DEFAULT_BASE_URL = "http://localhost:11434";
    private static final String DEFAULT_MODEL = "codellama:13b";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String baseUrl;
    private final String model;
    private final OkHttpClient httpClient;
    private final Gson gson;

    public OllamaClient() {
        this(DEFAULT_BASE_URL, DEFAULT_MODEL);
    }

    public OllamaClient(String model) {
        this(DEFAULT_BASE_URL, model);
    }

    public OllamaClient(String baseUrl, String model) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.gson = new Gson();
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS) // 로컬 모델은 느릴 수 있음
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    }

    @Override
    public LLMResponse chat(LLMRequest request) {
        try {
            JsonObject requestBody = buildRequestBody(request, false);

            Request httpRequest = new Request.Builder()
                .url(baseUrl + "/api/chat")
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody.toString(), JSON))
                .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                String body = response.body().string();

                if (!response.isSuccessful()) {
                    return LLMResponse.error("Ollama Error: " + response.code() + " - " + body);
                }

                return parseResponse(body);
            }
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("Connection refused")) {
                return LLMResponse.error("Ollama 서버가 실행되지 않았습니다. 'ollama serve' 명령으로 시작해주세요.");
            }
            return LLMResponse.error("Network error: " + e.getMessage());
        }
    }

    @Override
    public CompletableFuture<LLMResponse> chatAsync(LLMRequest request) {
        return CompletableFuture.supplyAsync(() -> chat(request));
    }

    @Override
    public void chatStream(LLMRequest request, StreamHandler handler) {
        try {
            JsonObject requestBody = buildRequestBody(request, true);

            Request httpRequest = new Request.Builder()
                .url(baseUrl + "/api/chat")
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody.toString(), JSON))
                .build();

            httpClient.newCall(httpRequest).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    handler.onError(e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        handler.onError(new IOException("Ollama Error: " + response.code()));
                        return;
                    }

                    StringBuilder fullContent = new StringBuilder();
                    int totalTokens = 0;

                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.body().byteStream()))) {

                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.isEmpty()) continue;

                            JsonObject event = gson.fromJson(line, JsonObject.class);

                            if (event.has("message")) {
                                JsonObject message = event.getAsJsonObject("message");
                                if (message.has("content")) {
                                    String content = message.get("content").getAsString();
                                    fullContent.append(content);
                                    handler.onToken(content);
                                }
                            }

                            if (event.has("done") && event.get("done").getAsBoolean()) {
                                if (event.has("eval_count")) {
                                    totalTokens = event.get("eval_count").getAsInt();
                                }
                                break;
                            }
                        }
                    }

                    handler.onComplete(LLMResponse.success(
                        fullContent.toString(),
                        model,
                        new Usage(0, totalTokens)
                    ));
                }
            });
        } catch (Exception e) {
            handler.onError(e);
        }
    }

    @Override
    public String getName() {
        return "Ollama (" + model + ")";
    }

    @Override
    public boolean isAvailable() {
        try {
            Request request = new Request.Builder()
                .url(baseUrl + "/api/tags")
                .get()
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 사용 가능한 모델 목록 조회
     */
    public String[] listModels() {
        try {
            Request request = new Request.Builder()
                .url(baseUrl + "/api/tags")
                .get()
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String body = response.body().string();
                    JsonObject json = gson.fromJson(body, JsonObject.class);
                    return gson.fromJson(json.getAsJsonArray("models"), String[].class);
                }
            }
        } catch (IOException e) {
            // ignore
        }
        return new String[0];
    }

    private JsonObject buildRequestBody(LLMRequest request, boolean stream) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("stream", stream);

        // 메시지 구성
        com.google.gson.JsonArray messages = new com.google.gson.JsonArray();

        // System prompt
        if (request.systemPrompt() != null && !request.systemPrompt().isEmpty()) {
            JsonObject systemMsg = new JsonObject();
            systemMsg.addProperty("role", "system");
            systemMsg.addProperty("content", request.systemPrompt());
            messages.add(systemMsg);
        }

        // User messages
        for (Message msg : request.messages()) {
            JsonObject msgObj = new JsonObject();
            msgObj.addProperty("role", msg.role());
            msgObj.addProperty("content", msg.content());
            messages.add(msgObj);
        }
        body.add("messages", messages);

        // 옵션
        JsonObject options = new JsonObject();
        options.addProperty("temperature", request.temperature());
        options.addProperty("num_predict", request.maxTokens());
        body.add("options", options);

        return body;
    }

    private LLMResponse parseResponse(String body) {
        try {
            // Ollama는 스트리밍 형식으로 응답 (각 줄이 JSON)
            StringBuilder content = new StringBuilder();
            int totalTokens = 0;

            for (String line : body.split("\n")) {
                if (line.isEmpty()) continue;

                JsonObject json = gson.fromJson(line, JsonObject.class);

                if (json.has("message")) {
                    JsonObject message = json.getAsJsonObject("message");
                    if (message.has("content")) {
                        content.append(message.get("content").getAsString());
                    }
                }

                if (json.has("eval_count")) {
                    totalTokens = json.get("eval_count").getAsInt();
                }
            }

            return LLMResponse.success(
                content.toString(),
                model,
                new Usage(0, totalTokens)
            );
        } catch (Exception e) {
            return LLMResponse.error("Parse error: " + e.getMessage());
        }
    }

    /**
     * Builder 패턴
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String baseUrl = DEFAULT_BASE_URL;
        private String model = DEFAULT_MODEL;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public OllamaClient build() {
            String envUrl = System.getenv("OLLAMA_HOST");
            if (envUrl != null && !envUrl.isEmpty()) {
                baseUrl = envUrl;
            }
            return new OllamaClient(baseUrl, model);
        }
    }
}
