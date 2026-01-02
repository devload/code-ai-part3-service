# STEP 09: 코드 분석 기능 - 리뷰 & 리팩토링

> **목표**: N-gram 기반 코드 생성을 넘어, 정적 분석 기반 코드 리뷰 및 리팩토링 제안 기능 구현
> **핵심 개념**: 패턴 매칭, 정규표현식, 코드 스멜, 보안 취약점

---

## 1. 개요

### 왜 코드 분석인가?

N-gram 모델은 **코드 생성**에 집중했다면, 이번 단계는 **코드 품질 개선**에 초점을 맞춥니다.

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   코드 생성     │     │   코드 리뷰     │     │  리팩토링 제안  │
│   (N-gram)      │ --> │  (CodeAnalyzer) │ --> │(RefactoringSugg)│
│                 │     │                 │     │                 │
│ "public class"  │     │ 문제점 감지     │     │ 개선 방법 제시  │
│ → 자동완성      │     │ → 42/100점      │     │ → Before/After  │
└─────────────────┘     └─────────────────┘     └─────────────────┘
```

### 실제 IDE의 코드 분석

| IDE/도구 | 분석 방식 | 특징 |
|----------|-----------|------|
| IntelliJ IDEA | PSI 트리 분석 | 의미론적 분석, 리팩토링 자동화 |
| SonarQube | 정적 분석 | 품질 게이트, CI/CD 통합 |
| ESLint | AST 분석 | 규칙 기반, 자동 수정 |
| **우리 구현** | **정규표현식** | 교육용, 패턴 매칭 이해 |

---

## 2. 아키텍처

### 모듈 구조

```
code-ai/
├── code-ai-analyzer/                    # 🆕 새 모듈
│   ├── build.gradle
│   └── src/main/java/com/codeai/analyzer/
│       ├── CodeAnalyzer.java            # 코드 리뷰 엔진
│       └── RefactoringSuggester.java    # 리팩토링 제안기
│
└── mini-ai-cli/
    └── MiniAiCli.java                   # review, refactor 명령어
```

### 클래스 다이어그램

```
┌────────────────────────────────────────────────────────────┐
│                      CodeAnalyzer                          │
├────────────────────────────────────────────────────────────┤
│ - issues: List<CodeIssue>                                  │
│ - metrics: CodeMetrics                                     │
├────────────────────────────────────────────────────────────┤
│ + analyze(code: String): AnalysisResult                    │
│ - detectLongMethods(code)                                  │
│ - detectTooManyParameters(code)                            │
│ - detectDeepNesting(code)                                  │
│ - detectHardcodedSecrets(code)      // 🚨 보안             │
│ - detectSqlInjection(code)          // 🚨 보안             │
│ - checkNamingConventions(code)                             │
│ - calculateMetrics(code)                                   │
└────────────────────────────────────────────────────────────┘
         │
         ▼
┌────────────────────────────────────────────────────────────┐
│                     AnalysisResult                         │
├────────────────────────────────────────────────────────────┤
│ + issues: List<CodeIssue>                                  │
│ + metrics: CodeMetrics                                     │
│ + getSummary(): String                                     │
│ + formatReport(minSeverity): String                        │
└────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────┐
│                  RefactoringSuggester                      │
├────────────────────────────────────────────────────────────┤
│ + suggest(code: String): List<Refactoring>                 │
│ - suggestOptionalUsage(code)        // null → Optional     │
│ - suggestStreamAPI(code)            // for → Stream        │
│ - suggestBuilderPattern(code)       // setter → Builder    │
│ - suggestSimplifyConditionals(code) // if-else 단순화      │
│ - suggestTryWithResources(code)     // try-finally 개선    │
│ - suggestStringFormatting(code)     // + → format()        │
└────────────────────────────────────────────────────────────┘
```

---

## 3. CodeAnalyzer - 코드 리뷰 엔진

### 3.1 감지 항목

#### 🚨 Critical (보안)

```java
// 1. 하드코딩된 비밀정보
private String password = "admin123";      // ❌ 감지됨
private String apiKey = "sk-1234abcd";     // ❌ 감지됨

// 2. SQL Injection
String query = "SELECT * FROM users WHERE id = '" + userId + "'";  // ❌ 감지됨
```

**감지 패턴:**
```java
// 비밀정보 패턴
Pattern.compile("(password|secret|apiKey|token)\\s*=\\s*\"[^\"]+\"", CASE_INSENSITIVE);

// SQL Injection 패턴
Pattern.compile("\"\\s*\\+\\s*\\w+\\s*\\+\\s*\"", MULTILINE);  // 문자열 연결 감지
```

#### ⚠️ Warning (코드 품질)

```java
// 1. 긴 메서드 (>20줄)
public void processData() {
    // 38줄의 코드...  // ⚠️ 감지됨
}

// 2. 매개변수 과다 (>4개)
public void save(String a, int b, double c, String d, boolean e) {}  // ⚠️ 5개

// 3. 깊은 중첩 (>3레벨)
if (a) {
    if (b) {
        if (c) {
            if (d) {  // ⚠️ 중첩 깊이 4
            }
        }
    }
}
```

#### 💡 Info (스타일)

```java
// 1. 매직 넘버
int timeout = 86400;  // 💡 상수로 정의 권장: SECONDS_PER_DAY

// 2. 클래스명 명명 규칙
class badClassName {}  // 💡 PascalCase 권장: BadClassName

// 3. 상수명 명명 규칙
static final int maxSize = 100;  // 💡 UPPER_SNAKE_CASE 권장: MAX_SIZE
```

### 3.2 코드 메트릭

```java
public class CodeMetrics {
    public int totalLines;           // 전체 라인 수
    public int codeLines;            // 코드 라인 수
    public int commentLines;         // 주석 라인 수
    public int blankLines;           // 빈 라인 수
    public int cyclomaticComplexity; // 순환 복잡도
    public int methodCount;          // 메서드 수
    public int classCount;           // 클래스 수
}
```

**순환 복잡도 계산:**
```
복잡도 = 1 + (if 개수) + (for 개수) + (while 개수) + (case 개수)
          + (catch 개수) + (&& 개수) + (|| 개수) + (? 개수)
```

### 3.3 품질 점수 계산

```java
int score = 100
    - (critical * 20)   // Critical은 -20점
    - (errors * 10)     // Error는 -10점
    - (warnings * 5)    // Warning은 -5점
    - (info * 1);       // Info는 -1점

// 결과 해석
score >= 80  → ✅ 좋음
score >= 60  → ⚠️ 개선 필요
score < 60   → ❌ 심각한 문제
```

---

## 4. RefactoringSuggester - 리팩토링 제안

### 4.1 지원 패턴

#### 1️⃣ null 체크 → Optional

```java
// Before
if (user != null) {
    return user.getName();
} else {
    return "Unknown";
}

// After
return Optional.ofNullable(user)
    .map(u -> u.getName())
    .orElse("Unknown");
```

#### 2️⃣ for-if → Stream API

```java
// Before
for (String item : items) {
    if (item.startsWith("A")) {
        System.out.println(item);
    }
}

// After
items.stream()
    .filter(item -> item.startsWith("A"))
    .forEach(item -> System.out.println(item));
```

#### 3️⃣ 연속 setter → Builder 패턴

```java
// Before
User user = new User();
user.setName("Kim");
user.setEmail("kim@example.com");
user.setAge(30);

// After
User user = User.builder()
    .name("Kim")
    .email("kim@example.com")
    .age(30)
    .build();
```

#### 4️⃣ 조건문 단순화

```java
// Before
if (isValid) {
    return true;
} else {
    return false;
}

// After
return isValid;
```

#### 5️⃣ try-finally → try-with-resources

```java
// Before
FileReader reader = new FileReader(file);
try {
    // 사용
} finally {
    reader.close();
}

// After
try (FileReader reader = new FileReader(file)) {
    // 사용 (자동 close)
}
```

#### 6️⃣ 문자열 연결 → String.format

```java
// Before
String msg = "User " + name + " logged in at " + time;

// After
String msg = String.format("User %s logged in at %s", name, time);
```

### 4.2 패턴 매칭 구현

```java
// null 체크 패턴 감지
Pattern pattern = Pattern.compile(
    "if\\s*\\(\\s*(\\w+)\\s*!=\\s*null\\s*\\)\\s*\\{" +
    "\\s*return\\s+\\1\\.([^;]+);\\s*\\}" +
    "\\s*else\\s*\\{\\s*return\\s+([^;]+);\\s*\\}",
    Pattern.MULTILINE
);

Matcher matcher = pattern.matcher(code);
while (matcher.find()) {
    String varName = matcher.group(1);      // user
    String method = matcher.group(2);       // getName()
    String defaultValue = matcher.group(3); // "Unknown"

    // 변환 제안 생성
    String after = String.format(
        "return Optional.ofNullable(%s).map(v -> v.%s).orElse(%s);",
        varName, method, defaultValue
    );
}
```

---

## 5. CLI 사용법

### 5.1 코드 리뷰

```bash
# 기본 사용
code-ai review src/main/java/MyClass.java

# 심각도 필터링 (WARNING 이상만)
code-ai review src/main/java/MyClass.java --severity WARNING

# CRITICAL만 보기 (보안 이슈)
code-ai review src/main/java/MyClass.java --severity CRITICAL
```

**출력 예시:**
```
============================================================
📋 코드 리뷰 결과
============================================================

📊 코드 메트릭:
   총 라인: 61 (코드: 45, 주석: 6, 빈줄: 10)
   순환 복잡도: 15
   메서드 수: 5
   클래스 수: 2

🔍 발견된 이슈: 8개
   🚨 Critical: 2 | ❌ Error: 0 | ⚠️ Warning: 3 | 💡 Info: 3

------------------------------------------------------------
🚨 [HARDCODED_SECRET] Line 9: 하드코딩된 비밀 정보: password = "admin123"
   → 환경 변수나 설정 파일에서 읽어오세요.

⚠️ [LONG_METHOD] Line 16: 메서드 'processData'이 너무 깁니다 (38줄)
   → 20줄 이하로 분리하세요. Extract Method 리팩토링을 고려하세요.

💡 [NAMING_CLASS] Line 58: 클래스명 'badInnerClass'은 대문자로 시작해야 합니다
   → PascalCase를 사용하세요: BadInnerClass

------------------------------------------------------------
📈 코드 품질 점수: 42/100 ❌ 심각한 문제
```

### 5.2 리팩토링 제안

```bash
# 기본 사용 (모든 제안)
code-ai refactor src/main/java/MyClass.java

# 특정 유형만 필터링
code-ai refactor src/main/java/MyClass.java --type optional
code-ai refactor src/main/java/MyClass.java --type stream
code-ai refactor src/main/java/MyClass.java --type builder
code-ai refactor src/main/java/MyClass.java --type conditional
```

**출력 예시:**
```
============================================================
🔧 리팩토링 제안 (4개)
============================================================

🔧 [Optional 사용] null 체크를 Optional.ifPresent로 변환 (Line 32)

Before:
    if (a != null) {
        a.toLowerCase();
    }

After:
    Optional.ofNullable(a).ifPresent(v -> v.toLowerCase());
------------------------------------------------------------

🔧 [조건문 단순화] 불필요한 if-else 제거 (Line 37)

Before:
    if (b > 10) { return true; } else { return false; }

After:
    return b > 10;
------------------------------------------------------------
```

---

## 6. 구현 핵심 코드

### 6.1 CodeAnalyzer 핵심

```java
public class CodeAnalyzer {
    private final List<CodeIssue> issues = new ArrayList<>();
    private final CodeMetrics metrics = new CodeMetrics();

    public AnalysisResult analyze(String code) {
        issues.clear();

        // 품질 검사
        detectLongMethods(code);
        detectTooManyParameters(code);
        detectDeepNesting(code);

        // 보안 검사
        detectHardcodedSecrets(code);
        detectSqlInjection(code);

        // 스타일 검사
        checkNamingConventions(code);

        // 메트릭 계산
        calculateMetrics(code);

        return new AnalysisResult(new ArrayList<>(issues), metrics);
    }
}
```

### 6.2 RefactoringSuggester 핵심

```java
public class RefactoringSuggester {
    private final List<Refactoring> suggestions = new ArrayList<>();

    public List<Refactoring> suggest(String code) {
        suggestions.clear();

        suggestOptionalUsage(code);      // if(x!=null) → Optional
        suggestStreamAPI(code);           // for-if → Stream
        suggestBuilderPattern(code);      // setters → Builder
        suggestSimplifyConditionals(code);// if-else 단순화
        suggestTryWithResources(code);    // try-finally → try-with
        suggestStringFormatting(code);    // + → format()

        return new ArrayList<>(suggestions);
    }
}
```

---

## 7. 한계점 및 개선 방향

### 현재 한계

| 한계 | 설명 | 개선 방향 |
|------|------|-----------|
| 정규표현식 기반 | 복잡한 패턴 감지 어려움 | AST 파서 도입 |
| 단일 파일 분석 | 프로젝트 전체 분석 불가 | 멀티파일 스캔 |
| 오탐/미탐 | Matcher는 null 반환 안함 | 의미론적 분석 |
| Java 전용 | 다른 언어 미지원 | 언어별 분석기 |

### 프로덕션 수준 도구

```
┌─────────────────────────────────────────────────────────────┐
│                    프로덕션 코드 분석                        │
├─────────────────────────────────────────────────────────────┤
│  1. AST 기반 분석                                           │
│     - JavaParser, Eclipse JDT                               │
│     - 정확한 구문 분석                                       │
│                                                             │
│  2. 의미론적 분석                                           │
│     - 타입 추론, 데이터 흐름 분석                            │
│     - 변수 범위, 참조 해결                                   │
│                                                             │
│  3. AI 기반 분석                                            │
│     - CodeBERT, GraphCodeBERT                               │
│     - 코드 임베딩, 유사도 분석                               │
└─────────────────────────────────────────────────────────────┘
```

---

## 8. 실습 과제

### 과제 1: 새로운 코드 스멜 감지 추가
```java
// 빈 catch 블록 감지
try {
    // ...
} catch (Exception e) {
    // 빈 블록! → 경고
}
```

### 과제 2: 새로운 리팩토링 패턴 추가
```java
// Before: 중복 null 체크
if (a != null && a.getB() != null && a.getB().getC() != null) {
    return a.getB().getC().getValue();
}

// After: Optional 체이닝
return Optional.ofNullable(a)
    .map(A::getB)
    .map(B::getC)
    .map(C::getValue)
    .orElse(null);
```

### 과제 3: HTML 리포트 생성
- 분석 결과를 HTML 파일로 출력
- 라인별 하이라이팅
- 차트로 메트릭 시각화

---

## 9. 정리

### 학습 포인트

1. **정규표현식 활용**
   - 코드 패턴 매칭의 기초
   - 그룹 캡처와 백레퍼런스

2. **코드 품질 지표**
   - 순환 복잡도
   - 메서드 길이, 매개변수 수
   - 명명 규칙

3. **보안 취약점 감지**
   - 하드코딩된 비밀정보
   - SQL Injection 패턴

4. **리팩토링 패턴**
   - Java 8+ 모던 문법 활용
   - Optional, Stream, Builder

### CLI v4.0 명령어 요약

```bash
code-ai train     # 모델 학습
code-ai complete  # 코드 자동완성
code-ai review    # 🆕 코드 리뷰
code-ai refactor  # 🆕 리팩토링 제안
```

---

## 다음 단계

- **STEP 10**: AST 기반 분석 (JavaParser 도입)
- **STEP 11**: 멀티파일 프로젝트 분석
- **STEP 12**: AI 기반 코드 리뷰 (CodeBERT)
