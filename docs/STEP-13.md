# STEP 13: AI에게 말 걸기 - API 호출

> Part 2에서 우리는 코드를 분석하고 점수까지 매겼어요.
> 이제 진짜 AI에게 "이 코드 어때?"라고 물어볼 차례예요!

---

## AI와 대화하려면?

AI는 웹사이트에만 있는 게 아니에요. **API**를 통해 우리 프로그램에서 직접 호출할 수 있어요.

```
┌──────────┐     HTTP POST      ┌──────────┐
│ 우리 앱  │ ─────────────────► │   AI     │
│ (질문)   │                    │ (Claude) │
└──────────┘                    └──────────┘
      ▲                               │
      │     JSON 응답                 ▼
      │ ◄───────────────────── ┌──────────┐
      │                        │   답변   │
      └────────────────────────└──────────┘
```

마치 카카오톡 메시지 보내듯이, AI에게 메시지를 보내고 답변을 받는 거예요.

---

## 어디에 말을 걸지?

여러 AI 제공자가 있어요:

| 제공자 | 모델 | 특징 |
|--------|------|------|
| **Anthropic** | Claude | 코드 이해 뛰어남, 안전함 |
| **OpenAI** | GPT-4 | 범용적, 널리 사용됨 |
| **Ollama** | 로컬 LLM | 무료! 인터넷 필요 없음 |

오늘은 주로 Claude를 예시로 들게요.

---

## API 키가 뭔데?

AI 서비스를 쓰려면 **API 키**가 필요해요. 마치 집에 들어갈 때 열쇠가 필요한 것처럼요.

```bash
# 환경변수로 설정
export ANTHROPIC_API_KEY=sk-ant-...

# Windows의 경우
set ANTHROPIC_API_KEY=sk-ant-...
```

🚨 **절대로 소스 코드에 API 키를 직접 쓰면 안 돼요!**

```java
// ❌ 절대 하지 마세요!
private String apiKey = "sk-ant-12345...";

// ✅ 환경변수에서 읽어오세요
private String apiKey = System.getenv("ANTHROPIC_API_KEY");
```

---

## 실제로 호출해보기

Claude API를 호출하는 코드를 볼게요:

```java
public class APIClient {
    private final OkHttpClient httpClient = new OkHttpClient();
    private final Gson gson = new Gson();

    public String callClaude(String prompt) throws IOException {
        // 1. 요청 만들기
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", "claude-3-5-sonnet-20241022");
        requestBody.addProperty("max_tokens", 4096);

        JsonArray messages = new JsonArray();
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        messages.add(message);
        requestBody.add("messages", messages);

        // 2. HTTP 요청 보내기
        Request request = new Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", System.getenv("ANTHROPIC_API_KEY"))
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(
                gson.toJson(requestBody),
                MediaType.parse("application/json")
            ))
            .build();

        // 3. 응답 받기
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);

            return json.getAsJsonArray("content")
                .get(0).getAsJsonObject()
                .get("text").getAsString();
        }
    }
}
```

복잡해 보이지만, 핵심은 간단해요:
1. **요청 만들기** - 모델 이름, 질문 담기
2. **HTTP 요청 보내기** - API 키와 함께 전송
3. **응답 받기** - JSON에서 답변 추출

---

## API 요청/응답 형식

Claude에게 이렇게 보내면:

```json
{
  "model": "claude-3-5-sonnet-20241022",
  "max_tokens": 4096,
  "messages": [
    {"role": "user", "content": "Java에서 null을 안전하게 처리하는 방법 알려줘"}
  ]
}
```

이렇게 답이 와요:

```json
{
  "content": [
    {"type": "text", "text": "Java에서 null을 안전하게 처리하는 방법..."}
  ],
  "usage": {
    "input_tokens": 15,
    "output_tokens": 200
  }
}
```

`usage` 필드에 토큰 사용량이 나와요. 이게 곧 비용이에요!

---

## OpenAI는 약간 다르게

OpenAI API는 형식이 조금 달라요:

```java
public String callOpenAI(String prompt) throws IOException {
    JsonObject requestBody = new JsonObject();
    requestBody.addProperty("model", "gpt-4o");

    JsonArray messages = new JsonArray();
    JsonObject message = new JsonObject();
    message.addProperty("role", "user");
    message.addProperty("content", prompt);
    messages.add(message);
    requestBody.add("messages", messages);

    Request request = new Request.Builder()
        .url("https://api.openai.com/v1/chat/completions")
        .addHeader("Authorization", "Bearer " + System.getenv("OPENAI_API_KEY"))
        .addHeader("Content-Type", "application/json")
        .post(...)
        .build();

    // 응답에서 추출하는 부분도 조금 다름
    return json.getAsJsonArray("choices")
        .get(0).getAsJsonObject()
        .getAsJsonObject("message")
        .get("content").getAsString();
}
```

헤더가 `Authorization: Bearer ...`이고, 응답에서 `choices[0].message.content`를 찾아요.

---

## Ollama: 무료로 로컬에서!

인터넷 없이, 무료로 AI를 쓰고 싶다면 **Ollama**예요:

```bash
# Ollama 설치 후
ollama run codellama:13b
```

API 호출은 더 간단해요:

```java
public String callOllama(String prompt) throws IOException {
    JsonObject requestBody = new JsonObject();
    requestBody.addProperty("model", "codellama:13b");
    requestBody.addProperty("prompt", prompt);
    requestBody.addProperty("stream", false);

    Request request = new Request.Builder()
        .url("http://localhost:11434/api/generate")  // 로컬!
        .post(...)
        .build();

    return json.get("response").getAsString();
}
```

API 키도 필요 없고, `localhost`에서 돌아가요!

---

## 에러가 나면?

API 호출은 실패할 수 있어요:

| 에러 코드 | 의미 | 해결책 |
|-----------|------|--------|
| **401** | API 키가 잘못됨 | 키 확인 |
| **429** | 너무 많이 호출함 | 잠시 기다려 |
| **500** | 서버 문제 | 재시도 |

재시도 로직을 넣으면 좋아요:

```java
public String callWithRetry(String prompt, int maxRetries) {
    for (int attempt = 0; attempt < maxRetries; attempt++) {
        try {
            return call(prompt);
        } catch (IOException e) {
            // 2, 4, 8초... 점점 길게 대기
            long waitTime = (long) Math.pow(2, attempt) * 1000;
            Thread.sleep(waitTime);
        }
    }
    throw new RuntimeException("Max retries exceeded");
}
```

이걸 **지수 백오프(Exponential Backoff)**라고 해요.

---

## 통합 클라이언트

여러 제공자를 하나로 묶으면 편해요:

```java
public class APIClient {
    private String provider = "claude";

    public String call(String prompt) throws IOException {
        return switch (provider) {
            case "claude" -> callClaude(prompt);
            case "openai" -> callOpenAI(prompt);
            case "ollama" -> callOllama(prompt);
            default -> throw new IllegalArgumentException("Unknown: " + provider);
        };
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }
}
```

`setProvider("ollama")`만 하면 로컬 LLM으로 바뀌어요!

---

## 핵심 정리

1. **API = AI에게 메시지 보내기** → HTTP POST로 질문, JSON으로 답변
2. **API 키 = 열쇠** → 환경변수로 관리, 절대 코드에 직접 쓰지 않기
3. **여러 제공자** → Claude, OpenAI, Ollama 각각 장단점 있음
4. **에러 처리** → 재시도 로직으로 안정성 확보

---

## 다음 시간 예고

API 호출은 됐어요. 근데 뭘 물어볼지가 중요하죠!

"코드 리뷰해줘"라고 하면 AI도 뭘 해야 할지 모를 거예요. "보안 관점에서 SQL Injection 위험을 찾아서 라인 번호와 함께 JSON 형식으로..."라고 해야 제대로 된 답이 나와요.

다음 STEP에서는 **좋은 프롬프트 만드는 법**을 알아볼게요!

---

## 실습

```bash
# 환경변수 설정 후
cd code-ai-part3-service
../gradlew :step13-api:run
```

직접 AI에게 질문을 보내고 답변을 받아보세요!
