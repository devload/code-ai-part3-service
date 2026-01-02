# STEP 10: AST 기반 코드 분석 (JavaParser)

> **목표**: 정규표현식의 한계를 넘어 AST(Abstract Syntax Tree) 기반 정확한 코드 분석
> **핵심 기술**: JavaParser, Visitor 패턴, 순환 복잡도 계산

---

## 1. 왜 AST인가?

### 정규표현식 vs AST

| 구분 | 정규표현식 (STEP-09) | AST (STEP-10) |
|------|---------------------|---------------|
| **정확도** | 오탐 가능 | 정확한 구문 분석 |
| **주석 처리** | 주석 내 코드도 감지 | 주석 제외 |
| **중첩 분석** | 정확한 깊이 파악 어려움 | 정확한 트리 구조 |
| **타입 정보** | 불가능 | 가능 (Symbol Solver) |
| **성능** | 빠름 | 상대적으로 느림 |
| **구현 복잡도** | 단순 | 복잡 |

### 실제 예시

```java
// 정규표현식은 이것도 감지 (오탐)
// if (password != null) { return password; }

// AST는 주석을 무시하고 실제 코드만 분석
String password = "secret";  // ← 이것만 감지
```

---

## 2. AST 구조 이해

### Java 코드의 AST 변환

```java
public class User {
    private String name;

    public String getName() {
        return name;
    }
}
```

**AST 트리:**
```
CompilationUnit
└── ClassOrInterfaceDeclaration [User]
    ├── FieldDeclaration
    │   └── VariableDeclarator [name: String]
    └── MethodDeclaration [getName]
        └── BlockStmt
            └── ReturnStmt
                └── NameExpr [name]
```

### 주요 AST 노드 타입

| 노드 타입 | 설명 | 예시 |
|-----------|------|------|
| `CompilationUnit` | 파일 전체 | `.java` 파일 |
| `ClassOrInterfaceDeclaration` | 클래스/인터페이스 | `class User` |
| `MethodDeclaration` | 메서드 선언 | `public void save()` |
| `FieldDeclaration` | 필드 선언 | `private int count` |
| `IfStmt` | if 문 | `if (x > 0)` |
| `ForStmt` / `ForEachStmt` | for 루프 | `for (int i...)` |
| `MethodCallExpr` | 메서드 호출 | `list.add(item)` |
| `BinaryExpr` | 이항 연산 | `a + b`, `x && y` |

---

## 3. 아키텍처

### 모듈 구조

```
code-ai-analyzer/
└── src/main/java/com/codeai/analyzer/
    ├── CodeAnalyzer.java           # 정규식 기반 (STEP-09)
    ├── RefactoringSuggester.java   # 리팩토링 제안
    └── ast/
        └── ASTAnalyzer.java        # 🆕 AST 기반 분석
```

### 클래스 다이어그램

```
┌─────────────────────────────────────────────────────────────┐
│                        ASTAnalyzer                          │
├─────────────────────────────────────────────────────────────┤
│ - parser: JavaParser                                        │
│ - issues: List<ASTIssue>                                    │
│ - metrics: ASTMetrics                                       │
├─────────────────────────────────────────────────────────────┤
│ + analyze(code: String): ASTAnalysisResult                  │
│ - collectMetrics(cu: CompilationUnit)                       │
│ - detectCodeSmells(cu)                                      │
│ - detectSecurityIssues(cu)                                  │
│ - checkBestPractices(cu)                                    │
│ - calculateCyclomaticComplexity(method): int                │
│ - calculateMaxNestingDepth(method): int                     │
└─────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│                     ASTAnalysisResult                       │
├─────────────────────────────────────────────────────────────┤
│ + issues: List<ASTIssue>                                    │
│ + metrics: ASTMetrics                                       │
│ + parseSuccess: boolean                                     │
│ + formatReport(minSeverity): String                         │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                       ASTMetrics                            │
├─────────────────────────────────────────────────────────────┤
│ + totalLines: int                                           │
│ + classCount: int                                           │
│ + methodCount: int                                          │
│ + fieldCount: int                                           │
│ + totalComplexity: int                                      │
│ + avgMethodLength: int                                      │
│ + methodComplexities: Map<String, Integer>                  │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. 순환 복잡도 (Cyclomatic Complexity)

### 개념

**McCabe's Cyclomatic Complexity**는 프로그램의 복잡도를 측정하는 지표입니다.

```
CC = E - N + 2P

E = 엣지 수 (실행 경로)
N = 노드 수 (코드 블록)
P = 연결된 컴포넌트 수 (보통 1)
```

### 간단한 계산법

```
CC = 1 + (분기점 개수)

분기점:
- if, else if
- for, while, do-while
- switch case
- catch
- && (AND)
- || (OR)
- ? (삼항 연산자)
```

### 예시

```java
public void process(int x) {     // +1 (기본)
    if (x > 0) {                  // +1
        for (int i = 0; i < x; i++) {  // +1
            if (i % 2 == 0) {     // +1
                // ...
            }
        }
    } else if (x < 0) {           // +1
        // ...
    }
}
// 총 CC = 5
```

### 복잡도 기준

| CC 값 | 위험도 | 설명 |
|-------|--------|------|
| 1-5 | ✅ 낮음 | 단순하고 테스트하기 쉬움 |
| 6-10 | ⚠️ 중간 | 약간 복잡, 주의 필요 |
| 11-20 | ❌ 높음 | 복잡, 리팩토링 권장 |
| 21+ | 🚨 매우 높음 | 테스트 어려움, 반드시 분리 |

---

## 5. 구현 상세

### 5.1 JavaParser 설정

```java
public ASTAnalyzer() {
    // Java 17 지원 (Text Block, Record, Sealed Class 등)
    ParserConfiguration config = new ParserConfiguration();
    config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
    this.parser = new JavaParser(config);
}
```

### 5.2 순환 복잡도 계산

```java
private int calculateCyclomaticComplexity(MethodDeclaration method) {
    int complexity = 1; // 기본값

    // if 문
    complexity += method.findAll(IfStmt.class).size();

    // for 루프
    complexity += method.findAll(ForStmt.class).size();
    complexity += method.findAll(ForEachStmt.class).size();

    // while 루프
    complexity += method.findAll(WhileStmt.class).size();
    complexity += method.findAll(DoStmt.class).size();

    // switch case
    complexity += method.findAll(SwitchEntry.class).stream()
        .filter(se -> !se.getLabels().isEmpty())
        .count();

    // catch 블록
    complexity += method.findAll(CatchClause.class).size();

    // 논리 연산자 (&&, ||)
    complexity += method.findAll(BinaryExpr.class).stream()
        .filter(be -> be.getOperator() == BinaryExpr.Operator.AND ||
                     be.getOperator() == BinaryExpr.Operator.OR)
        .count();

    // 삼항 연산자
    complexity += method.findAll(ConditionalExpr.class).size();

    return complexity;
}
```

### 5.3 중첩 깊이 계산 (Visitor 패턴)

```java
private static class NestingDepthVisitor extends VoidVisitorAdapter<int[]> {
    private int currentDepth = 0;

    @Override
    public void visit(IfStmt n, int[] maxDepth) {
        currentDepth++;
        maxDepth[0] = Math.max(maxDepth[0], currentDepth);
        super.visit(n, maxDepth);  // 자식 노드 방문
        currentDepth--;
    }

    @Override
    public void visit(ForStmt n, int[] maxDepth) {
        currentDepth++;
        maxDepth[0] = Math.max(maxDepth[0], currentDepth);
        super.visit(n, maxDepth);
        currentDepth--;
    }
    // WhileStmt, TryStmt 등도 동일하게 처리
}
```

### 5.4 코드 스멜 감지

```java
private void detectCodeSmells(CompilationUnit cu) {
    // 1. 긴 메서드 (>30줄)
    cu.findAll(MethodDeclaration.class).forEach(method -> {
        int lines = method.getRange()
            .map(r -> r.end.line - r.begin.line + 1)
            .orElse(0);
        if (lines > 30) {
            issues.add(new ASTIssue(
                Severity.WARNING,
                "LONG_METHOD",
                "메서드 '" + method.getNameAsString() + "'이 너무 깁니다",
                "Extract Method 리팩토링을 고려하세요.",
                method.getBegin().map(p -> p.line).orElse(0)
            ));
        }
    });

    // 2. 빈 catch 블록
    cu.findAll(CatchClause.class).forEach(catchClause -> {
        if (catchClause.getBody().getStatements().isEmpty()) {
            issues.add(new ASTIssue(
                Severity.WARNING,
                "EMPTY_CATCH",
                "빈 catch 블록이 있습니다",
                "최소한 로깅을 추가하세요.",
                catchClause.getBegin().map(p -> p.line).orElse(0)
            ));
        }
    });

    // 3. 사용되지 않는 private 메서드
    Set<String> calledMethods = new HashSet<>();
    cu.findAll(MethodCallExpr.class).forEach(call ->
        calledMethods.add(call.getNameAsString())
    );

    cu.findAll(MethodDeclaration.class).stream()
        .filter(m -> m.isPrivate())
        .filter(m -> !calledMethods.contains(m.getNameAsString()))
        .forEach(method -> {
            issues.add(new ASTIssue(
                Severity.INFO,
                "UNUSED_METHOD",
                "private 메서드 '" + method.getNameAsString() + "'가 사용되지 않습니다",
                "불필요한 코드는 삭제하세요.",
                method.getBegin().map(p -> p.line).orElse(0)
            ));
        });
}
```

### 5.5 보안 취약점 감지

```java
private void detectSecurityIssues(CompilationUnit cu) {
    // 하드코딩된 비밀정보
    cu.findAll(FieldDeclaration.class).forEach(field -> {
        field.getVariables().forEach(var -> {
            String name = var.getNameAsString().toLowerCase();
            if (name.contains("password") || name.contains("secret") ||
                name.contains("apikey") || name.contains("token")) {

                var.getInitializer().ifPresent(init -> {
                    if (init instanceof StringLiteralExpr) {
                        issues.add(new ASTIssue(
                            Severity.CRITICAL,
                            "HARDCODED_SECRET",
                            "하드코딩된 비밀 정보: " + var.getNameAsString(),
                            "환경 변수나 설정 파일에서 읽어오세요.",
                            var.getBegin().map(p -> p.line).orElse(0)
                        ));
                    }
                });
            }
        });
    });
}
```

---

## 6. CLI 사용법

### 기본 사용

```bash
code-ai ast-review src/main/java/MyClass.java
```

### 심각도 필터링

```bash
# WARNING 이상만 표시
code-ai ast-review src/MyClass.java --severity WARNING

# CRITICAL만 표시 (보안 이슈)
code-ai ast-review src/MyClass.java --severity CRITICAL
```

### 메서드별 복잡도 상세

```bash
code-ai ast-review src/MyClass.java --metrics
```

### 출력 예시

```
🌳 AST 기반 코드 리뷰 시작...
  파일: src/main/java/MyClass.java
  분석기: JavaParser (AST)

============================================================
📋 AST 기반 코드 리뷰 결과
============================================================

📊 AST 메트릭:
   총 라인: 419
   클래스: 2개 | 메서드: 13개 | 필드: 7개
   총 순환 복잡도: 29 (평균: 2.2)
   평균 메서드 길이: 25줄
   메서드별 복잡도:
     - suggestSimplifyConditionals: 4
     - suggestOptionalUsage: 3
     - suggestBuilderPattern: 3

🔍 발견된 이슈: 4개
   🚨 Critical: 0 | ❌ Error: 0 | ⚠️ Warning: 4 | 💡 Info: 0

------------------------------------------------------------
⚠️ [LONG_METHOD] Line 39: 메서드 'suggestOptionalUsage'이 너무 깁니다 (50줄)
   → 20줄 이하로 분리하세요. Extract Method 리팩토링을 고려하세요.

------------------------------------------------------------
📈 코드 품질 점수: 80/100 ✅ 좋음

📊 메서드별 순환 복잡도:
   ✅ suggestSimplifyConditionals: 4
   ✅ suggestOptionalUsage: 3
   ⚠️ complexMethod: 8
   ❌ veryComplexMethod: 15
```

---

## 7. 정규식 vs AST 비교 실험

### 테스트 코드

```java
public class BadCode {
    // 주석: password = "test123"
    private String password = "admin123";

    public void process() {
        // if (x != null) 이건 주석
        if (data != null) {
            data.doSomething();
        }
    }
}
```

### 결과 비교

| 항목 | 정규식 (review) | AST (ast-review) |
|------|-----------------|------------------|
| 주석 내 password | ⚠️ 감지됨 (오탐) | ✅ 무시됨 |
| 실제 password | ✅ 감지됨 | ✅ 감지됨 |
| 주석 내 if 문 | ⚠️ 감지됨 (오탐) | ✅ 무시됨 |
| 실제 if 문 | ✅ 감지됨 | ✅ 감지됨 |
| 중첩 깊이 | 부정확할 수 있음 | ✅ 정확함 |
| 순환 복잡도 | ❌ 계산 불가 | ✅ 정확한 계산 |

---

## 8. 감지 항목 정리

### 코드 스멜

| 코드 | 설명 | 심각도 |
|------|------|--------|
| `LONG_METHOD` | 30줄 초과 메서드 | ⚠️ WARNING |
| `TOO_MANY_PARAMS` | 4개 초과 매개변수 | ⚠️ WARNING |
| `DEEP_NESTING` | 3레벨 초과 중첩 | ⚠️ WARNING |
| `EMPTY_CATCH` | 빈 catch 블록 | ⚠️ WARNING |
| `GOD_CLASS` | 20+ 메서드 또는 15+ 필드 | ⚠️ WARNING |
| `HIGH_COMPLEXITY` | CC > 10 | ⚠️ WARNING |
| `UNUSED_METHOD` | 사용 안 되는 private 메서드 | 💡 INFO |

### 보안 취약점

| 코드 | 설명 | 심각도 |
|------|------|--------|
| `HARDCODED_SECRET` | 하드코딩된 비밀정보 | 🚨 CRITICAL |
| `SQL_INJECTION` | 문자열 연결 SQL | 🚨 CRITICAL |
| `INSECURE_RANDOM` | java.util.Random 사용 | 💡 INFO |
| `SYSTEM_EXIT` | System.exit() 호출 | ⚠️ WARNING |

### 베스트 프랙티스

| 코드 | 설명 | 심각도 |
|------|------|--------|
| `STRING_COMPARE` | 문자열 == 비교 | ⚠️ WARNING |
| `NAMING_CLASS` | 클래스명 규칙 위반 | 💡 INFO |
| `NAMING_METHOD` | 메서드명 규칙 위반 | 💡 INFO |
| `NAMING_CONSTANT` | 상수명 규칙 위반 | 💡 INFO |
| `MAGIC_NUMBER` | 매직 넘버 | 💡 INFO |

---

## 9. 의존성

### build.gradle

```gradle
dependencies {
    // JavaParser - AST 기반 코드 분석
    implementation 'com.github.javaparser:javaparser-core:3.25.8'
    implementation 'com.github.javaparser:javaparser-symbol-solver-core:3.25.8'
}
```

### Symbol Solver (선택적)

Symbol Solver를 사용하면 타입 해석이 가능합니다:

```java
// 타입 정보 없이
MethodCallExpr call = ...;
call.getNameAsString();  // "getName" (메서드명만)

// Symbol Solver 사용 시
call.resolve().getReturnType();  // "String" (반환 타입)
call.resolve().getDeclaringType();  // "User" (선언 클래스)
```

---

## 10. 한계점 및 다음 단계

### 현재 한계

| 한계 | 설명 |
|------|------|
| 단일 파일 분석 | 프로젝트 전체 분석 불가 |
| 타입 해석 미흡 | Symbol Solver 미사용 시 타입 정보 제한 |
| 실시간 분석 불가 | IDE 통합 필요 |

### 다음 단계

| STEP | 제목 | 내용 |
|------|------|------|
| 11 | 멀티파일 분석 | 프로젝트 전체 스캔, 의존성 분석 |
| 12 | Symbol Solver | 타입 해석, 메서드 호출 추적 |
| 13 | AI 코드 리뷰 | CodeBERT/Transformer 통합 |

---

## 11. 실습 과제

### 과제 1: 새로운 코드 스멜 추가

```java
// 감지: 메서드 내 return 문이 5개 이상
public String getValue(int type) {
    if (type == 1) return "A";
    if (type == 2) return "B";
    if (type == 3) return "C";
    if (type == 4) return "D";
    return "E";  // 5개 return → 경고
}
```

### 과제 2: 중복 코드 감지

```java
// 감지: 동일한 코드 블록이 2회 이상 등장
void methodA() {
    validate(input);
    process(input);
    save(input);
}

void methodB() {
    validate(data);   // 동일 패턴!
    process(data);
    save(data);
}
```

### 과제 3: 메서드 호출 그래프 생성

```java
// A.call() → B.process() → C.save()
// 호출 관계를 그래프로 시각화
```

---

## 12. 정리

### 학습 포인트

1. **AST (Abstract Syntax Tree)**
   - 코드를 트리 구조로 표현
   - 정확한 구문 분석 가능

2. **Visitor 패턴**
   - AST 순회의 표준 패턴
   - 노드 타입별 처리 로직 분리

3. **순환 복잡도**
   - 코드 복잡도의 정량적 측정
   - 테스트 용이성 지표

4. **JavaParser**
   - Java 코드 파싱 라이브러리
   - Java 17+ 문법 지원

### CLI v5.0 명령어

```bash
code-ai train       # 모델 학습
code-ai complete    # 코드 자동완성
code-ai review      # 정규식 기반 리뷰
code-ai refactor    # 리팩토링 제안
code-ai ast-review  # 🆕 AST 기반 리뷰
```
