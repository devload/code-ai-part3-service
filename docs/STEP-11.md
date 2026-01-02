# STEP 11: 멀티파일 프로젝트 분석

> **목표**: 단일 파일 분석을 넘어 프로젝트 전체를 분석하여 크로스파일 이슈 감지
> **핵심 기술**: 디렉토리 스캔, 의존성 그래프, 순환 의존성 탐지

---

## 1. 왜 멀티파일 분석인가?

### 단일 파일 vs 프로젝트 분석

| 구분 | 단일 파일 (STEP-10) | 프로젝트 (STEP-11) |
|------|---------------------|-------------------|
| **범위** | 1개 파일 | N개 파일 |
| **순환 의존성** | 감지 불가 | ✅ 감지 가능 |
| **사용되지 않는 public** | 판단 불가 | ✅ 감지 가능 |
| **패키지 구조** | 분석 불가 | ✅ 분석 가능 |
| **프로젝트 메트릭** | 불가 | ✅ 집계 가능 |

### 크로스파일 이슈 예시

```java
// UserService.java
public class UserService {
    private OrderService orderService;  // OrderService 의존
}

// OrderService.java
public class OrderService {
    private UserService userService;    // UserService 의존 → 순환!
}
```

---

## 2. 아키텍처

### 클래스 다이어그램

```
┌─────────────────────────────────────────────────────────────┐
│                     ProjectAnalyzer                         │
├─────────────────────────────────────────────────────────────┤
│ - parser: JavaParser                                        │
│ - astAnalyzer: ASTAnalyzer                                  │
│ - allClasses: Map<String, ClassInfo>                        │
│ - classDependencies: Map<String, Set<String>>               │
│ - classUsages: Map<String, Set<String>>                     │
├─────────────────────────────────────────────────────────────┤
│ + analyze(projectPath: Path): ProjectAnalysisResult         │
│ - collectJavaFiles(path): List<Path>                        │
│ - analyzeFile(file, projectRoot)                            │
│ - collectClassInfo(cu, filePath)                            │
│ - analyzeDependencies(cu, filePath)                         │
│ - analyzeCrossFileIssues()                                  │
│ - detectCircularDependencies()                              │
│ - aggregateMetrics()                                        │
└─────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│                  ProjectAnalysisResult                      │
├─────────────────────────────────────────────────────────────┤
│ + projectPath: Path                                         │
│ + fileAnalyses: List<FileAnalysis>                          │
│ + projectIssues: List<ProjectIssue>                         │
│ + metrics: ProjectMetrics                                   │
│ + formatReport(minSeverity, showDetails): String            │
│ + getTopProblematicFiles(n): String                         │
│ + getTopComplexFiles(n): String                             │
└─────────────────────────────────────────────────────────────┘
```

### 분석 흐름

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  1단계      │     │  2단계      │     │  3단계      │
│  파일 수집  │ --> │  개별 분석  │ --> │ 크로스파일  │
│             │     │             │     │    분석     │
│ *.java 찾기 │     │ AST 파싱    │     │ 의존성 검사 │
└─────────────┘     │ 클래스 수집 │     │ 순환 감지   │
                    │ 의존성 추출 │     │ 미사용 감지 │
                    └─────────────┘     └─────────────┘
                                               │
                                               ▼
                                        ┌─────────────┐
                                        │  4단계      │
                                        │ 메트릭 집계 │
                                        │             │
                                        │ 전체 통계   │
                                        │ Top N 파일  │
                                        └─────────────┘
```

---

## 3. 구현 상세

### 3.1 파일 수집

```java
private List<Path> collectJavaFiles(Path projectPath) throws IOException {
    List<Path> javaFiles = new ArrayList<>();

    Files.walkFileTree(projectPath, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            // .java 파일만 (테스트 제외)
            if (file.toString().endsWith(".java") &&
                !file.toString().contains("/test/")) {
                javaFiles.add(file);
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            String dirName = dir.getFileName().toString();
            // 빌드 디렉토리, 숨김 디렉토리 제외
            if (dirName.startsWith(".") || dirName.equals("build") ||
                dirName.equals("target") || dirName.equals("node_modules")) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        }
    });

    return javaFiles;
}
```

### 3.2 클래스 정보 수집

```java
private void collectClassInfo(CompilationUnit cu, String filePath) {
    String packageName = cu.getPackageDeclaration()
        .map(pd -> pd.getNameAsString())
        .orElse("");

    cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
        String className = clazz.getNameAsString();
        String fullName = packageName + "." + className;

        ClassInfo info = new ClassInfo(
            fullName,
            filePath,
            clazz.isPublic(),
            clazz.isInterface(),
            clazz.isAbstract()
        );

        // public 메서드/필드 수집
        clazz.getMethods().stream()
            .filter(m -> m.isPublic())
            .forEach(m -> info.publicMethods.add(m.getNameAsString()));

        allClasses.put(fullName, info);
    });
}
```

### 3.3 의존성 분석

```java
private void analyzeDependencies(CompilationUnit cu, String filePath) {
    // import 분석
    Set<String> imports = cu.getImports().stream()
        .filter(i -> !i.isAsterisk())
        .map(i -> i.getNameAsString())
        .collect(Collectors.toSet());

    // 타입 사용 분석
    Set<String> usedTypes = new HashSet<>();

    // 필드 타입
    cu.findAll(FieldDeclaration.class).forEach(field ->
        field.getVariables().forEach(var ->
            usedTypes.add(var.getType().asString())
        )
    );

    // 메서드 파라미터/리턴 타입
    cu.findAll(MethodDeclaration.class).forEach(method -> {
        usedTypes.add(method.getType().asString());
        method.getParameters().forEach(p ->
            usedTypes.add(p.getType().asString())
        );
    });

    // 객체 생성
    cu.findAll(ObjectCreationExpr.class).forEach(creation ->
        usedTypes.add(creation.getType().asString())
    );

    // 의존성 매핑
    for (String currentClass : currentClasses) {
        Set<String> deps = new HashSet<>();
        for (String imp : imports) {
            if (allClasses.containsKey(imp)) {
                deps.add(imp);
                classUsages.computeIfAbsent(imp, k -> new HashSet<>())
                    .add(currentClass);
            }
        }
        classDependencies.put(currentClass, deps);
    }
}
```

### 3.4 순환 의존성 감지 (DFS)

```java
private void detectCircularDependencies() {
    Set<String> visited = new HashSet<>();
    Set<String> recursionStack = new HashSet<>();
    List<String> path = new ArrayList<>();

    for (String className : classDependencies.keySet()) {
        if (detectCycleDFS(className, visited, recursionStack, path)) {
            // 순환 발견
            int cycleStart = path.lastIndexOf(className);
            List<String> cycle = path.subList(cycleStart, path.size());
            cycle.add(className);

            String cycleStr = cycle.stream()
                .map(this::getSimpleName)
                .collect(Collectors.joining(" → "));

            projectIssues.add(new ProjectIssue(
                Severity.WARNING,
                "CIRCULAR_DEPENDENCY",
                "순환 의존성 감지: " + cycleStr,
                "의존성 방향을 정리하거나 인터페이스를 도입하세요.",
                cycle.get(0)
            ));
        }
    }
}

private boolean detectCycleDFS(String current, Set<String> visited,
                               Set<String> recursionStack, List<String> path) {
    if (recursionStack.contains(current)) {
        return true;  // 순환 발견!
    }
    if (visited.contains(current)) {
        return false;
    }

    visited.add(current);
    recursionStack.add(current);
    path.add(current);

    Set<String> deps = classDependencies.getOrDefault(current, Set.of());
    for (String dep : deps) {
        if (allClasses.containsKey(dep)) {
            if (detectCycleDFS(dep, visited, recursionStack, path)) {
                return true;
            }
        }
    }

    recursionStack.remove(current);
    path.remove(path.size() - 1);
    return false;
}
```

---

## 4. 감지 항목

### 프로젝트 레벨 이슈

| 코드 | 설명 | 심각도 |
|------|------|--------|
| `CIRCULAR_DEPENDENCY` | 클래스 간 순환 의존성 | ⚠️ WARNING |
| `UNUSED_PUBLIC_CLASS` | 사용되지 않는 public 클래스 | 💡 INFO |
| `GOD_PACKAGE` | 15개 이상 클래스가 있는 패키지 | ⚠️ WARNING |
| `DEEP_PACKAGE` | 6레벨 이상 깊은 패키지 | 💡 INFO |

### 파일 레벨 이슈 (STEP-10에서 상속)

- 긴 메서드, 높은 복잡도
- 하드코딩된 비밀정보
- 명명 규칙 위반
- 등등...

---

## 5. CLI 사용법

### 기본 사용

```bash
code-ai project-review ./src/main/java
```

### 옵션

```bash
# 심각도 필터링
code-ai project-review ./src --severity WARNING

# 파일별 상세 이슈 출력
code-ai project-review ./src --details

# Top N 파일 수 조정
code-ai project-review ./src --top 10
```

### 출력 예시

```
📁 프로젝트 분석 시작...
  경로: /Users/devload/code-ai
  분석기: ProjectAnalyzer (멀티파일 AST)

======================================================================
📁 프로젝트 분석 결과: code-ai
======================================================================

📊 프로젝트 메트릭:
   파일: 35개 (성공: 35, 실패: 0)
   패키지: 16개 | 클래스: 64개 | 메서드: 353개
   총 순환 복잡도: 743 (평균: 2.1)
   클래스당 평균 메서드: 5.5개

🔍 발견된 이슈: 233개
   🚨 Critical: 7 | ❌ Error: 0 | ⚠️ Warning: 63 | 💡 Info: 163

----------------------------------------------------------------------
🌐 프로젝트 레벨 이슈:
----------------------------------------------------------------------
⚠️ [CIRCULAR_DEPENDENCY] 순환 의존성 감지: A → B → C → A
   → 의존성 방향을 정리하거나 인터페이스를 도입하세요.
   📁 com.example.A

⚠️ [GOD_PACKAGE] 패키지 'com.example.core'에 클래스가 너무 많습니다 (18개)
   → 관련 클래스들을 하위 패키지로 분리하세요.
   📁 com.example.core

======================================================================
📈 프로젝트 품질 점수: 45/100 ❌ 심각한 문제
======================================================================

🔥 문제가 많은 파일 Top 5:
   CodeAnalyzer.java: 34개 (🚨0 ⚠️8)
   RefactoringSuggester.java: 26개 (🚨0 ⚠️4)
   ASTAnalyzer.java: 24개 (🚨0 ⚠️9)

🧩 복잡도가 높은 파일 Top 5:
   ProjectAnalyzer.java: CC=90 (평균: 4.5, 메서드: 20개)
   ASTAnalyzer.java: CC=83 (평균: 4.2, 메서드: 20개)
   CodeAnalyzer.java: CC=79 (평균: 3.8, 메서드: 21개)
```

---

## 6. 프로젝트 메트릭

### ProjectMetrics 클래스

```java
public static class ProjectMetrics {
    public int totalFiles = 0;        // 전체 파일 수
    public int successfullyParsed = 0; // 파싱 성공
    public int parseFailures = 0;      // 파싱 실패
    public int totalClasses = 0;       // 전체 클래스 수
    public int totalMethods = 0;       // 전체 메서드 수
    public int totalComplexity = 0;    // 전체 순환 복잡도
    public int totalPackages = 0;      // 전체 패키지 수
    public int criticalIssues = 0;     // Critical 이슈 수
    public int errorIssues = 0;        // Error 이슈 수
    public int warningIssues = 0;      // Warning 이슈 수
    public int infoIssues = 0;         // Info 이슈 수
}
```

### 파생 메트릭

```java
// 평균 복잡도
double avgComplexity = totalMethods > 0 ?
    (double) totalComplexity / totalMethods : 0;

// 클래스당 평균 메서드
double avgMethodsPerClass = totalClasses > 0 ?
    (double) totalMethods / totalClasses : 0;
```

---

## 7. 순환 의존성 해결 전략

### 문제

```
A → B → C → A (순환!)
```

### 해결책 1: 인터페이스 도입

```java
// Before
class A { private B b; }
class B { private C c; }
class C { private A a; }  // 순환!

// After
interface AInterface { ... }
class A implements AInterface { private B b; }
class B { private C c; }
class C { private AInterface a; }  // 인터페이스에 의존
```

### 해결책 2: 의존성 역전 (DIP)

```java
// Before: 상위 모듈이 하위 모듈에 의존
class UserService { private MySqlRepository repo; }

// After: 둘 다 추상화에 의존
interface UserRepository { ... }
class UserService { private UserRepository repo; }
class MySqlRepository implements UserRepository { ... }
```

### 해결책 3: 이벤트 기반

```java
// Before: 직접 의존
class OrderService {
    private PaymentService payment;
    void complete() { payment.process(); }
}

// After: 이벤트 발행
class OrderService {
    private EventBus eventBus;
    void complete() { eventBus.publish(new OrderCompleted()); }
}
```

---

## 8. 테스트 결과

### code-ai 프로젝트 분석

```
📊 프로젝트 메트릭:
   파일: 35개 (성공: 35, 실패: 0)
   패키지: 16개 | 클래스: 64개 | 메서드: 353개
   총 순환 복잡도: 743 (평균: 2.1)
   클래스당 평균 메서드: 5.5개

🧩 복잡도가 높은 파일 Top 3:
   ProjectAnalyzer.java: CC=90
   ASTAnalyzer.java: CC=83
   CodeAnalyzer.java: CC=79
```

---

## 9. 한계점 및 다음 단계

### 현재 한계

| 한계 | 설명 |
|------|------|
| 타입 해석 미완성 | 제네릭, 상속 관계 미분석 |
| import * 미지원 | 와일드카드 import 무시 |
| 리플렉션 미분석 | 동적 의존성 감지 불가 |

### 다음 단계

| STEP | 제목 | 내용 |
|------|------|------|
| 12 | Symbol Solver | 완전한 타입 해석 |
| 13 | AI 코드 리뷰 | CodeBERT/Transformer |
| 14 | IDE 플러그인 | IntelliJ/VSCode 통합 |

---

## 10. 실습 과제

### 과제 1: 의존성 그래프 시각화

```java
// DOT 형식으로 출력
// digraph dependencies {
//     "UserService" -> "UserRepository"
//     "UserService" -> "OrderService"
//     "OrderService" -> "UserService"  // 순환!
// }
```

### 과제 2: 사용되지 않는 public 메서드 감지

```java
// 프로젝트 전체에서 호출되지 않는 public 메서드 찾기
public void unusedMethod() { }  // ← 경고
```

### 과제 3: 패키지 응집도 분석

```java
// 같은 패키지 내 클래스끼리 얼마나 협력하는가?
// 높은 응집도 = 좋은 패키지 구조
```

---

## 11. 정리

### 학습 포인트

1. **디렉토리 순회**
   - `Files.walkFileTree` API
   - `FileVisitor` 패턴

2. **의존성 그래프**
   - 클래스 간 import/사용 관계
   - 인접 리스트 표현

3. **순환 의존성 탐지**
   - DFS (깊이 우선 탐색)
   - 재귀 스택으로 사이클 감지

4. **메트릭 집계**
   - 프로젝트 전체 통계
   - Top N 문제 파일 식별

### CLI v6.0 명령어

```bash
code-ai train          # 모델 학습
code-ai complete       # 코드 자동완성
code-ai review         # 정규식 기반 리뷰
code-ai refactor       # 리팩토링 제안
code-ai ast-review     # AST 기반 리뷰
code-ai project-review # 🆕 프로젝트 분석
```
