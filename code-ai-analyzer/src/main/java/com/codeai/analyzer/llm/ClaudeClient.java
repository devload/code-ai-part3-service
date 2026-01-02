package com.codeai.analyzer.llm;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Claude API 클라이언트
 *
 * Anthropic Claude API를 사용하여 코드 리뷰를 수행합니다.
 *
 * 지원 모델:
 * - claude-3-5-sonnet-20241022 (권장)
 * - claude-3-opus-20240229
 * - claude-3-haiku-20240307
 */
public class ClaudeClient implements LLMClient {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String DEFAULT_MODEL = "claude-3-5-sonnet-20241022";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String apiKey;
    private final String model;
    private final OkHttpClient httpClient;
    private final Gson gson;

    public ClaudeClient(String apiKey) {
        this(apiKey, DEFAULT_MODEL);
    }

    public ClaudeClient(String apiKey, String model) {
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
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
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
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
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
                    int inputTokens = 0;
                    int outputTokens = 0;

                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.body().byteStream()))) {

                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6);
                                if (data.equals("[DONE]")) break;

                                JsonObject event = gson.fromJson(data, JsonObject.class);
                                String type = event.get("type").getAsString();

                                if ("content_block_delta".equals(type)) {
                                    JsonObject delta = event.getAsJsonObject("delta");
                                    if (delta.has("text")) {
                                        String text = delta.get("text").getAsString();
                                        fullContent.append(text);
                                        handler.onToken(text);
                                    }
                                } else if ("message_delta".equals(type)) {
                                    JsonObject usage = event.getAsJsonObject("usage");
                                    if (usage != null && usage.has("output_tokens")) {
                                        outputTokens = usage.get("output_tokens").getAsInt();
                                    }
                                } else if ("message_start".equals(type)) {
                                    JsonObject message = event.getAsJsonObject("message");
                                    JsonObject usage = message.getAsJsonObject("usage");
                                    if (usage != null && usage.has("input_tokens")) {
                                        inputTokens = usage.get("input_tokens").getAsInt();
                                    }
                                }
                            }
                        }
                    }

                    handler.onComplete(LLMResponse.success(
                        fullContent.toString(),
                        model,
                        new Usage(inputTokens, outputTokens)
                    ));
                }
            });
        } catch (Exception e) {
            handler.onError(e);
        }
    }

    @Override
    public String getName() {
        return "Claude (" + model + ")";
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty();
    }

    private JsonObject buildRequestBody(LLMRequest request, boolean stream) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", request.maxTokens());

        if (request.systemPrompt() != null && !request.systemPrompt().isEmpty()) {
            body.addProperty("system", request.systemPrompt());
        }

        JsonArray messages = new JsonArray();
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

            JsonArray content = json.getAsJsonArray("content");
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < content.size(); i++) {
                JsonObject block = content.get(i).getAsJsonObject();
                if ("text".equals(block.get("type").getAsString())) {
                    text.append(block.get("text").getAsString());
                }
            }

            JsonObject usage = json.getAsJsonObject("usage");
            int inputTokens = usage.get("input_tokens").getAsInt();
            int outputTokens = usage.get("output_tokens").getAsInt();

            return LLMResponse.success(
                text.toString(),
                json.get("model").getAsString(),
                new Usage(inputTokens, outputTokens)
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

        public ClaudeClient build() {
            if (apiKey == null || apiKey.isEmpty()) {
                apiKey = System.getenv("ANTHROPIC_API_KEY");
            }
            return new ClaudeClient(apiKey, model);
        }
    }
}
