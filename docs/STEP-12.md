# STEP 12: Symbol Solver - 완전한 타입 해석

> **목표**: JavaParser Symbol Solver를 사용하여 변수/메서드의 실제 타입을 해석하고 타입 안전성 검사
> **핵심 기술**: Symbol Solver, Type Resolution, 상속 분석, 메서드 호출 추적

---

## 1. Symbol Solver란?

### AST vs Symbol Solver

| 구분 | AST만 사용 (STEP-10) | Symbol Solver (STEP-12) |
|------|---------------------|------------------------|
| **타입 정보** | 문자열만 (예: "String") | 완전한 타입 (java.lang.String) |
| **상속 관계** | 알 수 없음 | 전체 상속 체인 |
| **메서드 해석** | 이름만 | 선언 위치, 반환 타입 |
| **제네릭** | 알 수 없음 | 구체적 타입 해석 |

### 예시: 타입 해석

```java
// AST만 사용
List users = getUsers();
users.add(item);  // add(?)가 뭘까?

// Symbol Solver 사용
List users = getUsers();  // List<User> 해석
users.add(item);  // java.util.List.add(User) 확인
```

---

## 2. 아키텍처

### 타입 해석 흐름

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│   소스 코드  │     │  JavaParser  │     │    AST       │
│   (*.java)   │ --> │   + Symbol   │ --> │  + 타입 정보 │
│              │     │    Solver    │     │              │
└──────────────┘     └──────────────┘     └──────────────┘
                            │
                            ▼
                    ┌──────────────┐
                    │ TypeSolver   │
                    │ (복합 해석기)│
                    ├──────────────┤
                    │ Reflection   │ ← JRE 표준 라이브러리
                    │ JavaParser   │ ← 프로젝트 소스
                    │ Jar          │ ← 외부 라이브러리
                    └──────────────┘
```

### 클래스 다이어그램

```
┌─────────────────────────────────────────────────────────────┐
│                      TypeResolver                           │
├─────────────────────────────────────────────────────────────┤
│ - parser: JavaParser                                        │
│ - typeSolver: CombinedTypeSolver                            │
│ - issues: List<TypeIssue>                                   │
│ - metrics: TypeMetrics                                      │
├─────────────────────────────────────────────────────────────┤
│ + TypeResolver()                                            │
│ + TypeResolver(projectRoot: Path)                           │
│ + addSourcePath(sourcePath: Path)                           │
│ + addJarPath(jarPath: Path)                                 │
│ + analyze(code: String): TypeAnalysisResult                 │
│ - analyzeTypes(cu: CompilationUnit)                         │
│ - analyzeMethodCalls(cu)                                    │
│ - analyzeInheritance(cu)                                    │
│ - checkTypeIssues(cu)                                       │
└─────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│                    TypeMetrics                              │
├─────────────────────────────────────────────────────────────┤
│ + resolvedTypes: int                                        │
│ + unresolvedTypes: int                                      │
│ + resolvedMethodCalls: int                                  │
│ + unresolvedMethodCalls: int                                │
│ + typeInfos: List<TypeInfo>                                 │
│ + methodCallInfos: List<MethodCallInfo>                     │
│ + inheritanceInfos: List<InheritanceInfo>                   │
│ + getResolutionRate(): double                               │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. 구현 상세

### 3.1 Symbol Solver 설정

```java
public TypeResolver(Path projectRoot) {
    this.typeSolver = new CombinedTypeSolver();

    // 1. JRE 표준 라이브러리 (String, List 등)
    typeSolver.add(new ReflectionTypeSolver());

    // 2. 프로젝트 소스 (있으면)
    if (projectRoot != null) {
        Path srcMain = projectRoot.resolve("src/main/java");
        if (Files.isDirectory(srcMain)) {
            typeSolver.add(new JavaParserTypeSolver(srcMain));
        }
    }

    // Symbol Solver 설정
    JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
    ParserConfiguration config = new ParserConfiguration();
    config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
    config.setSymbolResolver(symbolSolver);

    this.parser = new JavaParser(config);
}
```

### 3.2 타입 해석

```java
private void analyzeTypes(CompilationUnit cu) {
    cu.findAll(FieldDeclaration.class).forEach(field -> {
        field.getVariables().forEach(var -> {
            try {
                // 타입 해석 시도
                ResolvedType resolvedType = var.getType().resolve();
                metrics.resolvedTypes++;

                TypeInfo typeInfo = new TypeInfo(
                    var.getNameAsString(),
                    resolvedType.describe(),  // "java.util.List<String>"
                    getTypeCategory(resolvedType),
                    var.getBegin().map(p -> p.line).orElse(0)
                );
                metrics.typeInfos.add(typeInfo);

            } catch (UnsolvedSymbolException e) {
                metrics.unresolvedTypes++;
                metrics.unresolvedTypeNames.add(var.getType().asString());
            }
        });
    });
}
```

### 3.3 메서드 호출 분석

```java
private void analyzeMethodCalls(CompilationUnit cu) {
    cu.findAll(MethodCallExpr.class).forEach(call -> {
        try {
            ResolvedMethodDeclaration resolved = call.resolve();
            metrics.resolvedMethodCalls++;

            MethodCallInfo callInfo = new MethodCallInfo(
                call.getNameAsString(),
                resolved.getQualifiedName(),  // "java.util.List.add"
                resolved.getReturnType().describe(),  // "boolean"
                resolved.getNumberOfParams(),
                call.getBegin().map(p -> p.line).orElse(0)
            );

            // void 메서드의 반환값 사용 감지
            if (resolved.getReturnType().isVoid() && isReturnValueUsed(call)) {
                issues.add(new TypeIssue(
                    Severity.ERROR,
                    "VOID_RETURN_USED",
                    "void 메서드의 반환값을 사용하려고 합니다",
                    "void 메서드는 반환값이 없습니다.",
                    call.getBegin().map(p -> p.line).orElse(0)
                ));
            }

        } catch (UnsolvedSymbolException e) {
            metrics.unresolvedMethodCalls++;
        }
    });
}
```

### 3.4 상속 관계 분석

```java
private void analyzeInheritance(CompilationUnit cu) {
    cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
        try {
            ResolvedReferenceTypeDeclaration resolved = clazz.resolve();

            // 전체 상속 체인
            List<String> ancestors = new ArrayList<>();
            resolved.getAllAncestors().forEach(ancestor ->
                ancestors.add(ancestor.getQualifiedName())
            );

            InheritanceInfo inheritInfo = new InheritanceInfo(
                clazz.getNameAsString(),
                resolved.getQualifiedName(),
                ancestors,
                clazz.isInterface(),
                clazz.isAbstract()
            );

            // 너무 깊은 상속 경고
            if (ancestors.size() > 5) {
                issues.add(new TypeIssue(
                    Severity.WARNING,
                    "DEEP_INHERITANCE",
                    "상속 깊이가 깊습니다 (" + ancestors.size() + ")",
                    "상속보다 조합(Composition)을 고려하세요.",
                    clazz.getBegin().map(p -> p.line).orElse(0)
                ));
            }

        } catch (UnsolvedSymbolException e) {
            // 해석 실패
        }
    });
}
```

---

## 4. 타입 이슈 감지

### 감지 항목

| 코드 | 설명 | 심각도 |
|------|------|--------|
| `VOID_RETURN_USED` | void 메서드 반환값 사용 | ❌ ERROR |
| `TYPE_MISMATCH` | 타입 불일치 대입 | ❌ ERROR |
| `DEEP_INHERITANCE` | 5레벨 초과 상속 | ⚠️ WARNING |
| `RAW_TYPE` | 제네릭 없이 사용 | ⚠️ WARNING |
| `OPTIONAL_GET` | Optional.get() 직접 호출 | ⚠️ WARNING |
| `CAST_TO_OBJECT` | Object로 불필요한 캐스팅 | 💡 INFO |
| `INSTANCEOF_PATTERN` | instanceof 후 캐스팅 | 💡 INFO |

### 예시

```java
// RAW_TYPE - 제네릭 없이 사용
List items = new ArrayList();  // ⚠️ List<?>를 사용하세요

// OPTIONAL_GET - 위험한 사용
Optional<User> user = findUser();
user.get();  // ⚠️ orElse() 또는 ifPresent() 권장

// INSTANCEOF_PATTERN - 패턴 매칭 권장
if (obj instanceof String) {
    String s = (String) obj;  // 💡 Java 16+: if (obj instanceof String s)
}

// DEEP_INHERITANCE
class A extends B { }
class B extends C { }
class C extends D { }
class D extends E { }
class E extends F { }  // ⚠️ 상속 깊이 5 초과
```

---

## 5. CLI 사용법

### 기본 사용

```bash
code-ai type-check src/main/java/MyClass.java
```

### 프로젝트 경로 지정 (더 정확한 해석)

```bash
code-ai type-check src/MyClass.java --project ./src/main/java
```

### 메서드 호출 추적

```bash
code-ai type-check src/MyClass.java --trace add
```

### 출력 예시

```
🔍 타입 분석 시작...
  파일: ASTAnalyzer.java
  분석기: Symbol Solver (타입 해석)
  프로젝트: ./src/main/java

============================================================
🔍 타입 분석 결과 (Symbol Solver)
============================================================

📊 타입 분석 메트릭:
   타입 해석: 70개 성공, 1개 실패 (98.6% 성공)
   메서드 호출: 123개 성공, 227개 실패 (35.1% 성공)
   클래스 분석: 5개
   미해석 타입: JavaParser

🔍 발견된 타입 이슈: 6개
   ❌ Error: 0 | ⚠️ Warning: 0 | 💡 Info: 6

------------------------------------------------------------
💡 [INSTANCEOF_PATTERN] Line 331: instanceof 체크 후 캐스팅 대신 패턴 매칭 권장
   → Java 16+: if (obj instanceof String s) { s.length(); }

------------------------------------------------------------
🌳 상속 관계:
   class ASTAnalyzer
      ↳ Object
   class NestingDepthVisitor
   class ASTIssue
      ↳ Object
```

---

## 6. 타입 해석 성공률

### 해석 성공률에 영향을 주는 요소

| 요소 | 영향 |
|------|------|
| JRE 표준 클래스 | 항상 성공 (ReflectionTypeSolver) |
| 프로젝트 내부 클래스 | --project 옵션 필요 |
| 외부 라이브러리 | JAR 추가 필요 |
| 제네릭 와일드카드 | 일부 실패 가능 |

### 해석률 높이기

```java
TypeResolver resolver = new TypeResolver(Path.of("./src/main/java"));

// 추가 소스 경로
resolver.addSourcePath(Path.of("./other-module/src/main/java"));

// JAR 라이브러리
resolver.addJarPath(Path.of("./libs/guava-31.1.jar"));
```

---

## 7. 내부 데이터 구조

### TypeInfo

```java
public static class TypeInfo {
    public final String name;           // "userList"
    public final String resolvedType;   // "java.util.List<User>"
    public final String category;       // "JAVA_UTIL"
    public final int line;
}
```

### MethodCallInfo

```java
public static class MethodCallInfo {
    public final String methodName;     // "add"
    public final String qualifiedName;  // "java.util.List.add"
    public final String returnType;     // "boolean"
    public final int paramCount;        // 1
    public final int line;
}
```

### InheritanceInfo

```java
public static class InheritanceInfo {
    public final String className;      // "UserService"
    public final String qualifiedName;  // "com.example.UserService"
    public final List<String> ancestors; // ["BaseService", "Object"]
    public final boolean isInterface;
    public final boolean isAbstract;
}
```

---

## 8. 테스트 결과

### code-ai 프로젝트 분석

```
📊 타입 분석 메트릭:
   타입 해석: 70개 성공, 1개 실패 (98.6% 성공)
   메서드 호출: 123개 성공, 227개 실패 (35.1% 성공)
   클래스 분석: 5개

🌳 상속 관계:
   class ASTAnalyzer ↳ Object
   class NestingDepthVisitor
   class ASTIssue ↳ Object
   class ASTMetrics ↳ Object
   class ASTAnalysisResult ↳ Object
```

### 메서드 호출 실패 원인

- 외부 라이브러리 (JavaParser) 클래스 미해석
- 람다 표현식 내부 호출
- 체인 메서드 호출

---

## 9. 한계점 및 다음 단계

### 현재 한계

| 한계 | 설명 |
|------|------|
| 외부 라이브러리 | JAR 수동 추가 필요 |
| Gradle/Maven 통합 없음 | 의존성 자동 해석 불가 |
| 람다 타입 추론 | 일부 실패 |

### 다음 단계

| STEP | 제목 | 내용 |
|------|------|------|
| 13 | AI 코드 리뷰 | CodeBERT/Transformer 통합 |
| 14 | IDE 플러그인 | IntelliJ/VSCode 통합 |
| 15 | Gradle 통합 | 의존성 자동 해석 |

---

## 10. 실습 과제

### 과제 1: 미사용 import 감지

```java
import java.util.List;  // 사용됨 ✅
import java.util.Map;   // 사용 안 됨 ⚠️

public class Example {
    private List<String> items;  // List 사용
}
```

### 과제 2: 타입 호환성 검사 강화

```java
// 컴파일은 되지만 런타임 에러
List<String> strings = new ArrayList<>();
List raw = strings;
raw.add(123);  // ⚠️ 경고 필요
```

### 과제 3: 메서드 오버로딩 분석

```java
void process(String s) { }
void process(int n) { }

process("hello");  // String 버전 호출 확인
process(123);      // int 버전 호출 확인
```

---

## 11. 정리

### 학습 포인트

1. **Symbol Solver**
   - 심볼 해석기 구성
   - 다중 타입 소스 통합

2. **타입 해석**
   - ResolvedType API
   - 완전한 타입 이름 (Qualified Name)

3. **상속 분석**
   - 조상 클래스 체인
   - 인터페이스 구현 관계

4. **타입 안전성**
   - 타입 불일치 감지
   - 위험한 패턴 경고

### CLI v7.0 명령어

```bash
code-ai train          # 모델 학습
code-ai complete       # 코드 자동완성
code-ai review         # 정규식 기반 리뷰
code-ai refactor       # 리팩토링 제안
code-ai ast-review     # AST 기반 리뷰
code-ai project-review # 프로젝트 분석
code-ai type-check     # 🆕 타입 분석
```

### 의존성

```gradle
dependencies {
    implementation 'com.github.javaparser:javaparser-core:3.25.8'
    implementation 'com.github.javaparser:javaparser-symbol-solver-core:3.25.8'
}
```
