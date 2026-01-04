# STEP 14: AI에게 잘 물어보기 - 프롬프트 구성

> API 호출은 할 수 있게 됐어요. 근데 뭘 물어보냐에 따라 답이 완전히 달라져요.
> "코드 봐줘"라고 하면 AI도 뭘 봐야 할지 모르잖아요?

---

## 나쁜 질문 vs 좋은 질문

| 나쁜 질문 | 좋은 질문 |
|-----------|-----------|
| "코드 리뷰해줘" | "이 Java 코드를 보안, 성능, 가독성 관점에서 리뷰해주세요" |
| "버그 찾아줘" | "NullPointerException이 발생할 수 있는 위치를 찾고 수정 방법을 제안해주세요" |
| "개선해줘" | "이 메서드의 순환 복잡도를 10 이하로 낮추는 리팩토링을 제안해주세요" |

구체적일수록 AI가 정확한 답을 줘요.

---

## 프롬프트의 5가지 구성요소

좋은 프롬프트는 이런 구조를 가져요:

```
┌────────────────────────────────────────────────────────┐
│ 1. 시스템 프롬프트 (System)                            │
│    → "넌 10년 경력의 시니어 개발자야"                  │
├────────────────────────────────────────────────────────┤
│ 2. 컨텍스트 (Context)                                 │
│    → "이 Java 코드를 분석해줘: ..."                   │
├────────────────────────────────────────────────────────┤
│ 3. 작업 지시 (Task)                                   │
│    → "보안 취약점을 찾아줘"                           │
├────────────────────────────────────────────────────────┤
│ 4. 출력 형식 (Format)                                 │
│    → "JSON 형식으로 응답해줘"                         │
├────────────────────────────────────────────────────────┤
│ 5. 예시 (Examples)                                    │
│    → "예를 들어 이런 식으로..."                       │
└────────────────────────────────────────────────────────┘
```

---

## 실제 프롬프트 만들기

프롬프트 빌더를 만들어볼게요:

```java
public class PromptBuilder {
    private StringBuilder systemPrompt = new StringBuilder();
    private StringBuilder userPrompt = new StringBuilder();

    /**
     * 역할 부여: "넌 코드 리뷰 전문가야"
     */
    public PromptBuilder forCodeReview() {
        systemPrompt.append("""
            You are an expert code reviewer with deep knowledge of
            software engineering best practices.

            Your review should focus on:
            1. Code Quality - bugs, anti-patterns, code smells
            2. Security - vulnerabilities, hardcoded secrets
            3. Performance - inefficient algorithms, memory leaks
            4. Maintainability - readability, naming

            Guidelines:
            - Be constructive and educational
            - Prioritize by severity (CRITICAL > ERROR > WARNING > INFO)
            - Provide specific line numbers
            - Include code examples for fixes
            - Respond in Korean
            """);
        return this;
    }

    /**
     * 분석할 코드 추가
     */
    public PromptBuilder withCode(String code, String filename) {
        userPrompt.append("\n## 분석할 코드\n\n");
        userPrompt.append("파일: ").append(filename).append("\n");
        userPrompt.append("```java\n").append(code).append("\n```\n");
        return this;
    }

    /**
     * 최종 프롬프트 생성
     */
    public String build() {
        return systemPrompt.toString() + "\n\n" + userPrompt.toString();
    }
}
```

---

## 사용 예시

이렇게 쓰면:

```java
String code = """
    public class UserService {
        private String dbPassword = "admin123";

        public User findUser(String userId) {
            String sql = "SELECT * FROM users WHERE id = '" + userId + "'";
            return db.query(sql);
        }
    }
    """;

String prompt = new PromptBuilder()
    .forCodeReview()
    .withCode(code, "UserService.java")
    .build();
```

이런 프롬프트가 만들어져요:

```
You are an expert code reviewer with deep knowledge of
software engineering best practices.

Your review should focus on:
1. Code Quality - bugs, anti-patterns, code smells
2. Security - vulnerabilities, hardcoded secrets
...

## 분석할 코드

파일: UserService.java
```java
public class UserService {
    private String dbPassword = "admin123";
    ...
}
```

---

## JSON 출력 요청하기

AI 답변을 프로그램에서 쓰려면 **구조화된 형식**이 필요해요:

```java
public PromptBuilder withJsonOutput() {
    userPrompt.append("""

        ## 출력 형식

        다음 JSON 형식으로 응답해주세요:

        ```json
        {
          "summary": "전체 요약 (1-2문장)",
          "grade": "A/B/C/D/F",
          "score": 0-100,
          "issues": [
            {
              "severity": "CRITICAL|ERROR|WARNING|INFO",
              "line": 라인번호,
              "message": "이슈 설명",
              "suggestion": "수정 제안"
            }
          ],
          "positives": ["좋은 점 1", "좋은 점 2"]
        }
        ```
        """);
    return this;
}
```

이렇게 하면 AI가 JSON으로 답해줘서 바로 파싱할 수 있어요.

---

## 특정 관점에 집중하기

보안만 집중적으로 보고 싶을 때:

```java
public PromptBuilder focusOn(ReviewFocus... focuses) {
    userPrompt.append("\n## 집중 분석 영역\n");
    for (ReviewFocus focus : focuses) {
        switch (focus) {
            case SECURITY ->
                userPrompt.append("- 보안 취약점 (SQL Injection, XSS 등)\n");
            case PERFORMANCE ->
                userPrompt.append("- 성능 이슈 (알고리즘 효율성)\n");
            case READABILITY ->
                userPrompt.append("- 가독성 (명명 규칙, 코드 구조)\n");
        }
    }
    return this;
}

// 사용
String prompt = new PromptBuilder()
    .forCodeReview()
    .withCode(code, "UserService.java")
    .focusOn(ReviewFocus.SECURITY)  // 보안만!
    .withJsonOutput()
    .build();
```

---

## 하이브리드 분석: 우리 분석 + AI

Part 2에서 우리가 분석한 결과를 AI에게 전달하면 더 똑똑한 리뷰가 나와요:

```java
public PromptBuilder withExistingAnalysis(ScoreResult score, List<Issue> issues) {
    userPrompt.append("\n## 기존 분석 결과\n\n");
    userPrompt.append("점수: ").append(score.overallScore).append("/100\n");
    userPrompt.append("등급: ").append(score.grade).append("\n\n");

    if (!issues.isEmpty()) {
        userPrompt.append("발견된 이슈:\n");
        issues.forEach(issue ->
            userPrompt.append("- [").append(issue.getSeverity())
                .append("] ").append(issue.getMessage()).append("\n")
        );
    }

    userPrompt.append("\n이 분석을 바탕으로 추가적인 인사이트를 제공해주세요.\n");
    return this;
}
```

우리가 찾은 문제를 AI가 보완해주는 거예요!

---

## Few-shot: 예시 보여주기

AI에게 "이런 식으로 해줘"라고 예시를 보여주면 더 정확해요:

```java
public PromptBuilder withExample(String inputCode, String expectedOutput) {
    userPrompt.append("\n## 예시\n\n");
    userPrompt.append("입력:\n```java\n").append(inputCode).append("\n```\n");
    userPrompt.append("출력:\n").append(expectedOutput).append("\n");
    return this;
}
```

이걸 **Few-shot Learning**이라고 해요.

---

## 프롬프트 최적화 팁

### 1. 명확한 역할 부여
```
❌ "코드를 봐줘"
✅ "당신은 10년 경력의 시니어 Java 개발자입니다. 보안 전문가로서..."
```

### 2. 구체적인 지시
```
❌ "문제를 찾아줘"
✅ "SQL Injection, XSS, 하드코딩된 비밀정보를 찾아서 라인 번호와 함께 보고해줘"
```

### 3. 출력 형식 지정
```
❌ "결과를 알려줘"
✅ "다음 JSON 형식으로 응답해줘: { ... }"
```

### 4. 언어 지정
```
✅ "한국어로 응답해주세요"
```

---

## 토큰과 비용

프롬프트가 길수록 돈이 많이 들어요:

| 모델 | 입력 비용 | 출력 비용 |
|------|----------|----------|
| Claude 3.5 Sonnet | $3/100만 토큰 | $15/100만 토큰 |
| GPT-4o | $2.5/100만 토큰 | $10/100만 토큰 |

대략 **4글자 = 1토큰**이에요.

```java
private int estimateTokens(String text) {
    return text.length() / 4;
}
```

코드가 너무 길면 잘라야 해요:

```java
public PromptBuilder withTokenLimit(int maxTokens) {
    if (estimateTokens(code) > maxTokens) {
        code = truncateCode(code, maxTokens);
        userPrompt.append("(코드가 길어 일부만 포함됨)\n");
    }
    return this;
}
```

---

## 핵심 정리

1. **구조화된 프롬프트** → 시스템 + 컨텍스트 + 작업 + 형식 + 예시
2. **빌더 패턴** → 유연하게 프롬프트 조립
3. **JSON 출력** → 프로그램에서 바로 파싱 가능
4. **하이브리드** → 우리 분석 + AI 인사이트

---

## 다음 시간 예고

좋은 프롬프트는 만들었어요. 근데...

- 간단한 작업에 비싼 모델 쓸 필요 있나요?
- 복잡한 분석은 더 강력한 모델이 좋지 않을까요?
- 비용은 어떻게 관리하죠?

다음 STEP에서는 **작업에 맞는 AI 모델 선택하기**를 알아볼게요!

---

## 실습

```bash
cd code-ai-part3-service
../gradlew :step14-prompt:run
```

여러 가지 프롬프트를 만들어보고, AI 응답이 어떻게 달라지는지 비교해보세요!
