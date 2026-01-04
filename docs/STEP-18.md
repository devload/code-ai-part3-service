# STEP 18: 더 나아지기 위해 - 피드백 루프

> AI가 코드를 고쳤어요. 근데 **제대로 고친 건지** 어떻게 알까요?
> 빌드가 되나요? 테스트가 통과하나요? 점수가 올랐나요?

---

## 왜 피드백이 필요할까?

AI가 코드를 수정했다고 끝이 아니에요:

```
AI: "이렇게 고치면 됩니다!"
     ↓
개발자: "오 감사합니다"
     ↓
빌드: "컴파일 에러 87개"
     ↓
개발자: "..."
```

수정 후에 **검증**이 필요해요!

---

## 피드백 루프가 뭔데?

**수정 → 검증 → 재시도**를 반복하는 거예요:

```
┌─────────────────────────────────────────────────────┐
│                   피드백 루프                        │
│                                                     │
│   ┌───────┐    ┌───────┐    ┌───────┐    ┌──────┐  │
│   │ 분석  │ →  │ 수정  │ →  │ 검증  │ →  │ 평가 │  │
│   └───────┘    └───────┘    └───────┘    └──────┘  │
│       ↑                                     │       │
│       │         목표 미달성? 재시도!         │       │
│       └─────────────────────────────────────┘       │
│                                                     │
└─────────────────────────────────────────────────────┘
```

목표(80점, CRITICAL 이슈 0개)를 달성할 때까지 반복해요.

---

## 검증의 종류

| 검증 | 설명 | 예시 |
|------|------|------|
| **구문 검증** | 파싱 가능? | 문법 오류 확인 |
| **컴파일 검증** | 빌드 성공? | 타입 오류 확인 |
| **테스트 검증** | 테스트 통과? | 기존 기능 동작 |
| **점수 검증** | 품질 향상? | 80점 이상 달성 |

모든 검증을 통과해야 "성공"이에요!

---

## 기본 피드백 루프

```java
public class FeedbackLoop {
    private int maxAttempts = 3;      // 최대 시도 횟수
    private int targetScore = 80;     // 목표 점수

    public FeedbackResult runLoop(String code) {
        String currentCode = code;
        int attempt = 0;

        while (attempt < maxAttempts) {
            attempt++;
            System.out.println("=== 시도 " + attempt + "/" + maxAttempts + " ===");

            // 1. 분석
            AnalysisResult analysis = analyze(currentCode);

            // 2. 목표 달성했나?
            if (isGoalAchieved(analysis)) {
                System.out.println("목표 달성!");
                return FeedbackResult.success(currentCode, analysis.score);
            }

            // 3. AI에게 개선 요청
            String improvedCode = askAIToImprove(currentCode, analysis);

            // 4. 검증
            if (!validate(improvedCode)) {
                System.out.println("검증 실패, 롤백");
                continue;  // 다시 시도
            }

            // 5. 코드 업데이트
            currentCode = improvedCode;
        }

        return FeedbackResult.partial(currentCode, analyze(currentCode).score);
    }
}
```

---

## 목표 달성 확인

```java
private boolean isGoalAchieved(AnalysisResult analysis) {
    // 점수 확인
    if (analysis.score < targetScore) {
        System.out.println("점수 미달: " + analysis.score + "/" + targetScore);
        return false;
    }

    // CRITICAL 이슈 확인
    long criticalCount = analysis.issues.stream()
        .filter(i -> i.getSeverity() == Severity.CRITICAL)
        .count();

    if (criticalCount > 0) {
        System.out.println("CRITICAL 이슈 " + criticalCount + "개 남음");
        return false;
    }

    return true;  // 목표 달성!
}
```

두 조건 모두 만족해야 해요:
- 점수 80점 이상
- CRITICAL 이슈 0개

---

## AI에게 피드백 전달하기

이전 시도 결과를 알려주면 AI가 더 잘 고쳐요:

```java
private String askAIToImprove(String code, AnalysisResult analysis) {
    String prompt = new PromptBuilder()
        .forCodeReview()
        .withCode(code, "code.java")
        .withFeedback(buildFeedback(analysis))  // 피드백 추가!
        .withInstruction("위 피드백을 바탕으로 코드를 수정해주세요")
        .build();

    return apiClient.call(prompt);
}

private String buildFeedback(AnalysisResult analysis) {
    StringBuilder fb = new StringBuilder();
    fb.append("## 이전 분석 결과\n\n");
    fb.append("현재 점수: ").append(analysis.score).append("/100\n");
    fb.append("목표 점수: ").append(targetScore).append("/100\n\n");

    fb.append("해결해야 할 이슈:\n");

    // CRITICAL 먼저!
    for (Issue issue : analysis.issues) {
        if (issue.getSeverity() == Severity.CRITICAL) {
            fb.append("- [CRITICAL] ").append(issue.getMessage()).append("\n");
        }
    }

    return fb.toString();
}
```

AI: "아, 아까 이 부분이 문제였구나. 다시 고쳐볼게요!"

---

## 검증하기

수정된 코드가 제대로 된 건지 확인:

```java
private boolean validate(String code) {
    // 1. 파싱 가능?
    try {
        new CodeParser().parse(code);
    } catch (Exception e) {
        System.out.println("파싱 실패: " + e.getMessage());
        return false;
    }

    // 2. 컴파일 성공?
    CompileResult compile = tryCompile(code);
    if (!compile.success) {
        System.out.println("컴파일 실패: " + compile.error);
        return false;
    }

    // 3. 테스트 통과?
    TestResult test = runTests();
    if (!test.allPassed) {
        System.out.println("테스트 실패: " + test.failedCount + "개");
        return false;
    }

    return true;  // 모든 검증 통과!
}
```

검증 실패하면 롤백하고 다시 시도해요.

---

## 실제 동작 예시

문제 있는 코드로 시작:

```java
String badCode = """
    public class UserService {
        private String password = "admin123";  // CRITICAL!

        public User findUser(String userId) {
            String sql = "SELECT * FROM users WHERE id = '" + userId + "'";  // CRITICAL!
            return db.query(sql);
        }
    }
    """;

FeedbackLoop loop = new FeedbackLoop();
loop.setTargetScore(80);
loop.setMaxAttempts(3);

FeedbackResult result = loop.runLoop(badCode);
```

실행 결과:

```
=== 시도 1/3 ===
점수 미달: 35/80
CRITICAL 이슈 2개 남음
AI에게 개선 요청...
검증 성공

=== 시도 2/3 ===
점수 미달: 65/80
CRITICAL 이슈 0개
AI에게 개선 요청...
검증 성공

=== 시도 3/3 ===
목표 달성!

=== 결과 ===
성공 여부: 성공
총 시도: 3회
점수 변화: 35 → 85 (+50점)

=== 최종 코드 ===
public class UserService {
    private String password = System.getenv("DB_PASSWORD");

    public User findUser(String userId) {
        String sql = "SELECT * FROM users WHERE id = ?";
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setString(1, userId);
        return mapToUser(stmt.executeQuery());
    }
}
```

3번 만에 목표 달성!

---

## 조기 종료 조건

무한 루프를 막아야 해요:

```java
// 1. 최대 시도 횟수
if (attempt >= maxAttempts) {
    System.out.println("최대 시도 도달. 종료.");
    break;
}

// 2. 더 이상 개선 안 됨
if (currentScore <= previousScore) {
    System.out.println("개선 없음. 종료.");
    break;
}

// 3. 동일한 코드 반복
if (improvedCode.equals(currentCode)) {
    System.out.println("변화 없음. 종료.");
    break;
}
```

AI가 계속 같은 실수를 반복하면 멈춰야 해요.

---

## 점진적 개선 전략

한 번에 다 고치려 하면 오히려 꼬여요:

```
시도 1: CRITICAL 이슈만 집중
        - 하드코딩된 비밀번호 수정
        - SQL Injection 수정

시도 2: ERROR 이슈 처리
        - 리소스 누수 수정
        - null 처리 추가

시도 3: 품질 개선
        - 코드 정리
        - 명명 규칙 개선
```

중요한 것부터 차근차근!

---

## 피드백 결과 기록

나중에 분석하려면 기록해둬야 해요:

```java
public class FeedbackResult {
    private boolean success;
    private int totalAttempts;
    private int initialScore;
    private int finalScore;
    private List<AttemptRecord> history = new ArrayList<>();

    public String formatReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 피드백 루프 결과 ===\n\n");
        sb.append("성공: ").append(success ? "O" : "X").append("\n");
        sb.append("시도: ").append(totalAttempts).append("회\n");
        sb.append("점수: ").append(initialScore)
          .append(" → ").append(finalScore)
          .append(" (+").append(finalScore - initialScore).append(")\n\n");

        sb.append("시도별 기록:\n");
        for (int i = 0; i < history.size(); i++) {
            AttemptRecord record = history.get(i);
            sb.append("  ").append(i + 1).append(". ")
              .append(record.score).append("점, ")
              .append(record.issueCount).append("개 이슈\n");
        }

        return sb.toString();
    }
}
```

---

## Part 3 완성!

이제 전체 파이프라인이 완성됐어요:

```
┌──────────────────────────────────────────────────────────┐
│               Part 3: AI 서비스 프로세스                   │
├──────────────────────────────────────────────────────────┤
│                                                          │
│   STEP 13        STEP 14        STEP 15        STEP 16   │
│   API 호출   →   프롬프트   →   LLM 처리  →   응답 파싱   │
│   (HTTP)         (구성)         (라우팅)       (JSON)     │
│                                                          │
│        ↓                                                 │
│                                                          │
│   STEP 17        STEP 18                                 │
│   액션 실행  →   피드백 루프                              │
│   (코드 수정)    (반복 개선)                              │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

---

## 전체 18 STEP 요약

```
┌──────────────────────────────────────────────────────────┐
│                AI 코드 리뷰 전체 여정                      │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  Part 1: AI가 글을 쓰는 법 (STEP 1-6)                    │
│  ├─ 토큰화: 글자를 숫자로                                 │
│  ├─ 컨텍스트: 앞의 내용 기억                              │
│  ├─ 확률: 다음 단어 예측                                  │
│  ├─ 샘플링: 적절히 선택                                   │
│  ├─ 생성: 단어 이어붙이기                                 │
│  └─ 후처리: 다듬기                                        │
│                                                          │
│  Part 2: 코드 이해하기 (STEP 7-12)                       │
│  ├─ 파싱: 코드를 트리로                                   │
│  ├─ AST: 트리 탐험                                       │
│  ├─ 의미분석: 변수 추적                                   │
│  ├─ 패턴매칭: 나쁜 코드 찾기                              │
│  ├─ 이슈탐지: 진짜 위험 발견                              │
│  └─ 점수화: 품질 측정                                     │
│                                                          │
│  Part 3: AI 서비스 (STEP 13-18)                          │
│  ├─ API호출: AI에게 말 걸기                               │
│  ├─ 프롬프트: 잘 질문하기                                 │
│  ├─ LLM처리: 모델 선택                                    │
│  ├─ 응답파싱: 답변 이해                                   │
│  ├─ 액션실행: 코드 수정                                   │
│  └─ 피드백: 검증 및 반복                                  │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

---

## 핵심 정리

1. **피드백 루프 = 수정 → 검증 → 재시도**
2. **검증 필수** → 파싱, 컴파일, 테스트, 점수
3. **조기 종료** → 무한 루프 방지
4. **점진적 개선** → CRITICAL부터 차근차근

```
코드 수정 → 검증 성공? → 목표 달성? → 완료!
    ↑         ↓ 실패      ↓ 아니오
    └─────────┴───────── 재시도
```

---

## 축하해요!

18개의 STEP을 모두 마쳤어요!

이제 여러분은:
- AI가 어떻게 글을 쓰는지 이해해요
- 코드를 분석하고 점수를 매길 수 있어요
- AI를 활용한 코드 리뷰 서비스를 만들 수 있어요

다음은 뭘 할까요?
- 웹 대시보드 만들기
- IDE 플러그인 개발
- CI/CD 파이프라인 통합
- 팀 협업 기능 추가

배운 걸 활용해서 멋진 걸 만들어보세요!

---

## 실습

```bash
cd code-ai-part3-service
../gradlew :step18-feedback:run
```

피드백 루프가 코드를 점진적으로 개선하는 과정을 직접 확인해보세요!
