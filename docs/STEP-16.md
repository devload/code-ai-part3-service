# STEP 16: AI 답변 알아듣기 - 응답 파싱

> AI가 답변을 줬어요. 근데 이건 그냥 긴 텍스트잖아요.
> "5번째 줄에 문제가 있어요"라는 걸 프로그램이 어떻게 알 수 있을까요?

---

## 문제: AI 답변은 그냥 글자 덩어리

AI가 이렇게 답했어요:

```
코드에 몇 가지 문제가 있습니다. 첫째, 5번째 라인에서 SQL Injection 위험이 있고,
둘째, 2번째 라인에 비밀번호가 하드코딩되어 있습니다...
```

이걸 어떻게 프로그램에서 쓸까요? "5번째 라인"이라는 정보를 추출해야 해요.

---

## 해결책: 구조화된 데이터로 변환

```
LLM 응답 (자연어)              구조화된 데이터
       ↓                            ↓
"코드에 몇 가지 문제가      {
있습니다. 첫째, 5번째        "issues": [
라인에서 SQL Injection       {
위험이 있고..."               "line": 5,
                              "type": "SQL_INJECTION"
                             }
                            ]
                           }
```

---

## 전략 1: JSON 형식으로 요청하기

프롬프트에서 **JSON으로 답해달라고** 하면 파싱이 쉬워요:

```java
String prompt = """
    코드를 분석하고 다음 JSON 형식으로 응답해주세요:

    ```json
    {
      "summary": "요약",
      "score": 0-100,
      "issues": [
        {"severity": "CRITICAL", "line": 5, "message": "..."}
      ]
    }
    ```
    """;
```

AI가 이렇게 답하면:

```json
{
  "summary": "보안 취약점이 발견되었습니다",
  "score": 45,
  "issues": [
    {"severity": "CRITICAL", "line": 5, "message": "SQL Injection"}
  ]
}
```

바로 파싱할 수 있어요!

---

## JSON 파싱하기

```java
public class ResponseParser {
    private final Gson gson = new Gson();

    public ParsedResponse parse(String llmResponse) {
        // 1. JSON 블록 추출
        String jsonStr = extractJsonBlock(llmResponse);

        // 2. JSON 파싱
        JsonObject json = gson.fromJson(jsonStr, JsonObject.class);

        // 3. 데이터 추출
        ParsedResponse result = new ParsedResponse();
        result.summary = json.get("summary").getAsString();
        result.score = json.get("score").getAsInt();

        JsonArray issuesArray = json.getAsJsonArray("issues");
        for (JsonElement elem : issuesArray) {
            JsonObject obj = elem.getAsJsonObject();

            ReviewIssue issue = new ReviewIssue();
            issue.severity = obj.get("severity").getAsString();
            issue.line = obj.get("line").getAsInt();
            issue.message = obj.get("message").getAsString();

            result.issues.add(issue);
        }

        return result;
    }

    private String extractJsonBlock(String response) {
        // ```json ... ``` 블록 추출
        Pattern pattern = Pattern.compile("```json\\s*([\\s\\S]*?)\\s*```");
        Matcher matcher = pattern.matcher(response);

        if (matcher.find()) {
            return matcher.group(1);
        }

        // 블록이 없으면 { } 찾기
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        return response.substring(start, end + 1);
    }
}
```

---

## 전략 2: 정규표현식 폴백

AI가 JSON 형식을 안 지킬 수도 있어요. 그럴 땐 정규표현식으로 추출:

```java
private ParsedResponse parseWithRegex(String response) {
    ParsedResponse result = new ParsedResponse();

    // 이슈 추출: "[WARNING] Line 15: 메서드가 너무 깁니다"
    Pattern issuePattern = Pattern.compile(
        "\\[(CRITICAL|ERROR|WARNING|INFO)\\].*?[Ll]ine\\s*(\\d+).*?:\\s*(.+)",
        Pattern.MULTILINE
    );

    Matcher matcher = issuePattern.matcher(response);
    while (matcher.find()) {
        ReviewIssue issue = new ReviewIssue();
        issue.severity = matcher.group(1);  // WARNING
        issue.line = Integer.parseInt(matcher.group(2));  // 15
        issue.message = matcher.group(3);  // 메서드가 너무 깁니다

        result.issues.add(issue);
    }

    // 점수 추출: "점수: 85/100"
    Pattern scorePattern = Pattern.compile("(?:점수|Score)\\s*:?\\s*(\\d+)");
    Matcher scoreMatcher = scorePattern.matcher(response);
    if (scoreMatcher.find()) {
        result.score = Integer.parseInt(scoreMatcher.group(1));
    }

    return result;
}
```

---

## 하이브리드 접근

JSON 먼저 시도하고, 실패하면 정규표현식으로:

```java
public ParsedResponse parse(String llmResponse) {
    // 1. JSON 파싱 시도
    ParsedResponse jsonResult = tryParseJson(llmResponse);
    if (jsonResult != null && jsonResult.isValid()) {
        return jsonResult;
    }

    // 2. 정규표현식 폴백
    return parseWithRegex(llmResponse);
}
```

---

## 실제 예시

AI가 이렇게 답했다고 해봐요:

```
코드 리뷰 결과입니다.

```json
{
  "summary": "보안 취약점이 발견되었습니다",
  "grade": "D",
  "score": 45,
  "issues": [
    {
      "severity": "CRITICAL",
      "code": "SQL_INJECTION",
      "line": 5,
      "message": "SQL 쿼리에 문자열 연결 사용",
      "suggestion": "PreparedStatement를 사용하세요"
    },
    {
      "severity": "CRITICAL",
      "code": "HARDCODED_SECRET",
      "line": 2,
      "message": "비밀번호가 하드코딩됨",
      "suggestion": "환경변수를 사용하세요"
    }
  ],
  "positives": [
    "메서드가 짧고 읽기 쉽습니다",
    "명명 규칙을 잘 따르고 있습니다"
  ]
}
```

파싱 결과:

```
=== 파싱 결과 (JSON) ===

요약: 보안 취약점이 발견되었습니다
점수: 45/100 (등급: D)

발견된 이슈:
  [CRITICAL] Line 5: SQL 쿼리에 문자열 연결 사용
  [CRITICAL] Line 2: 비밀번호가 하드코딩됨

좋은 점:
  + 메서드가 짧고 읽기 쉽습니다
  + 명명 규칙을 잘 따르고 있습니다
```

이제 프로그램에서 쓸 수 있는 데이터가 됐어요!

---

## 파싱 결과 객체

```java
public class ParsedResponse {
    public String parseMethod;  // "JSON" or "REGEX"
    public String summary;
    public String grade;
    public int score;
    public List<ReviewIssue> issues = new ArrayList<>();
    public List<String> positives = new ArrayList<>();

    public boolean isValid() {
        return score >= 0 && grade != null;
    }

    public String formatReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 파싱 결과 (").append(parseMethod).append(") ===\n");
        sb.append("점수: ").append(score).append("/100\n");
        sb.append("등급: ").append(grade).append("\n");
        // ...
        return sb.toString();
    }
}

public class ReviewIssue {
    public String severity;
    public String code;
    public int line;
    public String message;
    public String suggestion;
    public String fixedCode;  // 수정된 코드 (선택)
}
```

---

## 검증하기

파싱 결과가 올바른지 확인:

```java
public ValidationResult validate(ParsedResponse response) {
    ValidationResult validation = new ValidationResult();

    // 점수 범위 확인
    if (response.score < 0 || response.score > 100) {
        validation.addWarning("점수가 유효 범위를 벗어남: " + response.score);
    }

    // 등급 확인
    if (response.grade == null || !response.grade.matches("[A-F]")) {
        validation.addWarning("등급이 유효하지 않음: " + response.grade);
    }

    // 이슈 라인 번호 확인
    for (ReviewIssue issue : response.issues) {
        if (issue.line < 0) {
            validation.addWarning("음수 라인 번호: " + issue.line);
        }
    }

    return validation;
}
```

---

## 핵심 정리

1. **JSON 요청** → 프롬프트에서 형식 지정
2. **JSON 파싱** → Gson으로 구조화된 데이터 추출
3. **정규표현식 폴백** → JSON 실패 시 패턴 매칭
4. **검증** → 파싱 결과 유효성 확인

```
LLM 응답 → JSON 시도 → 성공? → 구조화된 데이터
                ↓ 실패
           정규표현식 → 데이터 추출
```

---

## 다음 시간 예고

이제 AI가 알려준 문제를 파싱했어요:

```json
{
  "line": 2,
  "message": "비밀번호가 하드코딩됨",
  "fixedCode": "System.getenv(\"DB_PASSWORD\")"
}
```

근데 이걸 그냥 보여주기만 하면 아쉽잖아요? **직접 코드를 고쳐주면** 어떨까요?

다음 STEP에서는 **AI 제안을 바탕으로 실제 코드를 수정하는 방법**을 알아볼게요!

---

## 실습

```bash
cd code-ai-part3-service
../gradlew :step16-response:run
```

여러 형식의 AI 응답을 파싱해보고, 결과가 제대로 추출되는지 확인해보세요!
