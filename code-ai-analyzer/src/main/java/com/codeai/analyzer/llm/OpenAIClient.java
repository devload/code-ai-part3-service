package com.codeai.analyzer.llm;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI API 클라이언트
 *
 * OpenAI GPT API를 사용하여 코드 리뷰를 수행합니다.
 *
 * 지원 모델:
 * - gpt-4o (권장)
 * - gpt-4-turbo
 * - gpt-3.5-turbo
 */
public class OpenAIClient implements LLMClient {

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "gpt-4o";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String apiKey;
    private final String model;
    private final OkHttpClient httpClient;
    private final Gson gson;

    public OpenAIClient(String apiKey) {
        this(apiKey, DEFAULT_MODEL);
    }

    public OpenAIClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.gson = new Gson();
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    }

    @Override
    public LLMResponse chat(LLMRequest request) {
        try {
            JsonObject requestBody = buildRequestBody(request, false);

            Request httpRequest = new Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody.toString(), JSON))
                .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                String body = response.body().string();

                if (!response.isSuccessful()) {
                    return LLMResponse.error("API Error: " + response.code() + " - " + body);
                }

                return parseResponse(body);
            }
        } catch (IOException e) {
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
                .url(API_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
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
                        handler.onError(new IOException("API Error: " + response.code()));
                        return;
                    }

                    StringBuilder fullContent = new StringBuilder();

                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.body().byteStream()))) {

                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6);
                                if (data.equals("[DONE]")) break;

                                JsonObject event = gson.fromJson(data, JsonObject.class);
                                JsonArray choices = event.getAsJsonArray("choices");

                                if (choices != null && choices.size() > 0) {
                                    JsonObject delta = choices.get(0).getAsJsonObject()
                                        .getAsJsonObject("delta");
                                    if (delta != null && delta.has("content")) {
                                        String content = delta.get("content").getAsString();
                                        fullContent.append(content);
                                        handler.onToken(content);
                                    }
                                }
                            }
                        }
                    }

                    handler.onComplete(LLMResponse.success(
                        fullContent.toString(),
                        model,
                        new Usage(0, 0) // 스트리밍에서는 토큰 수 미제공
                    ));
                }
            });
        } catch (Exception e) {
            handler.onError(e);
        }
    }

    @Override
    public String getName() {
        return "OpenAI (" + model + ")";
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty();
    }

    private JsonObject buildRequestBody(LLMRequest request, boolean stream) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", request.maxTokens());

        JsonArray messages = new JsonArray();

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

        if (request.temperature() >= 0) {
            body.addProperty("temperature", request.temperature());
        }

        if (stream) {
            body.addProperty("stream", true);
        }

        return body;
    }

    private LLMResponse parseResponse(String body) {
        try {
            JsonObject json = gson.fromJson(body, JsonObject.class);

            if (json.has("error")) {
                return LLMResponse.error(json.getAsJsonObject("error").get("message").getAsString());
            }

            JsonArray choices = json.getAsJsonArray("choices");
            String content = choices.get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString();

            JsonObject usage = json.getAsJsonObject("usage");
            int promptTokens = usage.get("prompt_tokens").getAsInt();
            int completionTokens = usage.get("completion_tokens").getAsInt();

            return LLMResponse.success(
                content,
                json.get("model").getAsString(),
                new Usage(promptTokens, completionTokens)
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
        private String apiKey;
        private String model = DEFAULT_MODEL;

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public OpenAIClient build() {
            if (apiKey == null || apiKey.isEmpty()) {
                apiKey = System.getenv("OPENAI_API_KEY");
            }
            return new OpenAIClient(apiKey, model);
        }
    }
}
