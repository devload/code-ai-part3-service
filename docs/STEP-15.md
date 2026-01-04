# STEP 15: 똑똑하게 AI 고르기 - LLM 처리

> 모든 작업에 가장 비싼 AI를 쓸 필요가 있을까요?
> 간단한 질문에 Opus, 복잡한 분석에 Haiku를 쓰면 비효율적이죠.
> 상황에 맞게 AI를 골라서 쓰는 게 핵심이에요.

---

## 왜 모델을 골라야 할까?

| 작업 | 최적 모델 | 이유 |
|------|----------|------|
| 빠른 점수 계산 | Haiku | 저렴하고 빠름 |
| 일반 코드 리뷰 | Sonnet | 속도와 품질 균형 |
| 복잡한 아키텍처 분석 | Opus | 최고 추론 능력 |
| 로컬 테스트 | Ollama | 무료! |

비싼 모델이 항상 좋은 건 아니에요. **작업에 맞는 모델**이 좋은 거예요.

---

## 라우팅이 뭔데?

작업 유형을 보고 적절한 모델로 보내주는 걸 **라우팅**이라고 해요:

```
          ┌───────────────┐
          │   요청 분석    │
          └───────┬───────┘
                  │
          ┌───────▼───────┐
          │  라우터 결정   │
          └───────┬───────┘
                  │
    ┌─────────────┼─────────────┐
    ▼             ▼             ▼
┌───────┐   ┌───────┐   ┌───────┐
│ Opus  │   │Sonnet │   │ Haiku │
│(복잡) │   │(일반) │   │(간단) │
└───────┘   └───────┘   └───────┘
```

---

## 모델 비교

### Claude 모델들
| 모델 | 속도 | 품질 | 비용 | 언제 쓸까? |
|------|------|------|------|-----------|
| **Haiku** | 매우 빠름 | 좋음 | $0.25/100만 | 간단한 작업, 빠른 응답 |
| **Sonnet** | 보통 | 우수 | $3/100만 | 일반 작업, 균형 |
| **Opus** | 느림 | 최고 | $15/100만 | 복잡한 추론, 아키텍처 |

Opus가 Haiku보다 **60배** 비싸요! 간단한 작업에 Opus 쓰면 돈 낭비예요.

### OpenAI 모델들
| 모델 | 특징 |
|------|------|
| **GPT-4o** | 빠르고 똑똑 |
| **GPT-4o-mini** | 저렴하고 빠름 |

### Ollama (로컬)
| 모델 | 크기 | 특징 |
|------|------|------|
| **CodeLlama** | 13B | 코드에 특화 |
| **DeepSeek-Coder** | 6.7B | 경량, 빠름 |

로컬은 **무료**인데 성능이 조금 떨어져요. 테스트할 때 좋아요!

---

## 라우터 만들기

작업 유형에 따라 모델을 선택하는 라우터:

```java
public class LLMRouter {
    public String selectModel(TaskType taskType, RouteOptions options) {

        // 비용 우선 모드
        if (options.prioritizeCost) {
            return switch (taskType) {
                case QUICK_SCORE -> "claude-haiku";
                case CODE_REVIEW -> "claude-haiku";
                case COMPLEX_ANALYSIS -> "claude-sonnet";
                default -> "claude-haiku";
            };
        }

        // 품질 우선 모드
        if (options.prioritizeQuality) {
            return switch (taskType) {
                case QUICK_SCORE -> "claude-sonnet";
                case CODE_REVIEW -> "claude-sonnet";
                case COMPLEX_ANALYSIS -> "claude-opus";
                default -> "claude-opus";
            };
        }

        // 기본 모드 (균형)
        return switch (taskType) {
            case QUICK_SCORE -> "claude-haiku";
            case CODE_REVIEW -> "claude-sonnet";
            case COMPLEX_ANALYSIS -> "claude-sonnet";
            case ARCHITECTURE_REVIEW -> "claude-opus";
        };
    }
}
```

---

## 실제로 라우팅하기

```java
public LLMResponse route(String prompt, TaskType taskType, RouteOptions options) {
    String modelKey = selectModel(taskType, options);

    long startTime = System.currentTimeMillis();

    try {
        apiClient.setProvider(getProvider(modelKey));
        String response = apiClient.call(prompt);

        long latencyMs = System.currentTimeMillis() - startTime;
        int tokens = estimateTokens(prompt, response);
        float cost = tokens * getCostPerToken(modelKey);

        return new LLMResponse(
            response,
            modelKey,
            latencyMs,
            tokens,
            cost,
            true,
            null
        );

    } catch (Exception e) {
        return tryFallback(prompt, modelKey, e);
    }
}
```

응답에 **어떤 모델이 사용됐는지**, **얼마나 걸렸는지**, **비용이 얼마인지** 다 담아서 반환해요.

---

## 폴백 전략

클라우드 API가 실패하면? **로컬로 폴백!**

```java
private LLMResponse tryFallback(String prompt, String failedModel, Exception error) {
    // 클라우드 실패 → Ollama 시도
    if (!failedModel.equals("ollama") && isOllamaAvailable()) {
        System.out.println("클라우드 실패, Ollama로 폴백...");

        try {
            apiClient.setProvider("ollama");
            String response = apiClient.call(prompt);
            return new LLMResponse(response, "codellama", 0, 0, 0f, true, "Fallback");
        } catch (Exception e) {
            // 폴백도 실패
        }
    }

    return new LLMResponse(null, null, 0, 0, 0f, false, error.getMessage());
}
```

순서:
1. 선택된 클라우드 모델 시도
2. 실패하면 Ollama 로컬 시도
3. 그것도 실패하면 에러 반환

---

## 사용 예시

```java
APIClient apiClient = new APIClient();
LLMRouter router = new LLMRouter(apiClient);

String prompt = new PromptBuilder()
    .forCodeReview()
    .withCode(code, "Calculator.java")
    .build();

// 1. 빠른 점수 (비용 우선)
RouteOptions costOptions = new RouteOptions();
costOptions.prioritizeCost = true;

LLMResponse quickResponse = router.route(prompt, TaskType.QUICK_SCORE, costOptions);
System.out.println("선택된 모델: " + quickResponse.modelKey);  // claude-haiku
System.out.println("예상 비용: $" + quickResponse.estimatedCost);

// 2. 복잡한 분석 (품질 우선)
RouteOptions qualityOptions = new RouteOptions();
qualityOptions.prioritizeQuality = true;

LLMResponse complexResponse = router.route(prompt, TaskType.COMPLEX_ANALYSIS, qualityOptions);
System.out.println("선택된 모델: " + complexResponse.modelKey);  // claude-opus
```

실행 결과:

```
=== 빠른 점수 ===
선택된 모델: claude-haiku
응답 시간: 850ms
예상 비용: $0.0001

=== 복잡한 분석 ===
선택된 모델: claude-opus
응답 시간: 4500ms
예상 비용: $0.0134
```

Haiku가 Opus보다 **5배 빠르고**, **100배 저렴해요!**

---

## 비용 관리

하루에 얼마까지 쓸지 정해두면 좋아요:

```java
public class BudgetManager {
    private float dailyBudget = 10.0f;
    private float usedToday = 0f;

    public boolean canAfford(float estimatedCost) {
        return usedToday + estimatedCost <= dailyBudget;
    }

    public void recordUsage(float cost) {
        usedToday += cost;
    }

    public String getStatus() {
        return String.format("오늘 사용: $%.2f / $%.2f",
            usedToday, dailyBudget);
    }
}
```

예산 초과하면 저렴한 모델로 자동 전환하거나 거부할 수 있어요.

---

## Ollama 가용성 체크

로컬 LLM이 켜져 있는지 확인:

```java
private boolean isOllamaAvailable() {
    try {
        URL url = new URL("http://localhost:11434/api/tags");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(1000);  // 1초 타임아웃
        return conn.getResponseCode() == 200;
    } catch (Exception e) {
        return false;  // 연결 안 됨
    }
}
```

Ollama가 켜져 있으면 폴백으로 쓸 수 있어요.

---

## 핵심 정리

1. **작업에 맞는 모델** → 간단=Haiku, 복잡=Opus
2. **라우터** → 자동으로 최적 모델 선택
3. **폴백** → 클라우드 실패 시 로컬로
4. **비용 관리** → 예산 설정, 사용량 추적

```
빠른 점수 → Haiku ($0.0001)
일반 리뷰 → Sonnet ($0.002)
복잡 분석 → Opus ($0.015)
테스트 → Ollama (무료!)
```

---

## 다음 시간 예고

AI가 답변을 줬어요. 근데...

```
"코드를 분석한 결과, 보안 취약점이 있습니다.
첫째, 5번째 라인에서 SQL Injection 위험이 있고..."
```

이 텍스트를 어떻게 프로그램에서 쓸 수 있는 데이터로 바꿀까요?

다음 STEP에서는 **AI 응답을 파싱해서 구조화된 데이터로 바꾸는 방법**을 알아볼게요!

---

## 실습

```bash
cd code-ai-part3-service
../gradlew :step15-llm:run
```

같은 질문을 다른 모델에 보내보고, 응답 시간과 품질을 비교해보세요!
