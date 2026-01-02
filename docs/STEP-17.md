# STEP-17: 코드 자동 수정 (Auto-fix)

## 목표
발견된 코드 이슈를 자동으로 수정하는 기능을 구현합니다. 규칙 기반 수정과 LLM 기반 수정을 조합한 하이브리드 방식을 사용합니다.

## 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│                     Auto-fix System                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                   AutoFixer                          │   │
│  │              (규칙 기반 수정)                         │   │
│  │                                                     │   │
│  │  • EMPTY_CATCH → 로깅 추가                          │   │
│  │  • SYSTEM_OUT → Logger 변환                         │   │
│  │  • MAGIC_NUMBER → 상수 추출                         │   │
│  │  • MISSING_BRACES → 중괄호 추가                     │   │
│  │  • DEEP_NESTING → Early return 적용                 │   │
│  │  • TRAILING_WHITESPACE → 공백 제거                  │   │
│  └─────────────────────────────────────────────────────┘   │
│                          │                                  │
│                          ▼                                  │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                 LLMAutoFixer                         │   │
│  │               (LLM 기반 수정)                         │   │
│  │                                                     │   │
│  │  • 복잡한 리팩토링                                   │   │
│  │  • 알고리즘 최적화                                   │   │
│  │  • 보안 취약점 수정                                  │   │
│  │  • 아키텍처 개선                                     │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## 구현 내용

### 1. AutoFixer (규칙 기반)

```java
AutoFixer fixer = new AutoFixer();
AutoFixer.FixReport report = fixer.fix(code);

System.out.println(report.formatReport());
System.out.println(report.fixedCode());
```

**지원하는 수정 유형:**

| 유형 | 설명 | 예시 |
|------|------|------|
| EMPTY_CATCH | 빈 catch 블록에 로깅 추가 | `catch(e) {}` → `catch(e) { logger.error(...); }` |
| SYSTEM_OUT | System.out을 Logger로 변환 | `System.out.println()` → `logger.info()` |
| MAGIC_NUMBER | 매직 넘버를 상수로 추출 | `if (x > 100)` → `if (x > MAX_VALUE)` |
| MISSING_BRACES | 누락된 중괄호 추가 | `if (x) return;` → `if (x) { return; }` |
| DEEP_NESTING | Early return 패턴 적용 | 중첩 if → Guard clause |
| TRAILING_WHITESPACE | 후행 공백 제거 | `code   \n` → `code\n` |
| NULL_CHECK | null 체크 추가 | Objects.requireNonNull() 사용 |
| RAW_TYPE | Raw type에 제네릭 추가 | `List` → `List<Object>` |

### 2. LLMAutoFixer (LLM 기반)

```java
// Claude 사용
LLMAutoFixer fixer = LLMAutoFixer.withClaude();

// OpenAI 사용
LLMAutoFixer fixer = LLMAutoFixer.withOpenAI();

// Ollama 사용
LLMAutoFixer fixer = LLMAutoFixer.withOllama("codellama:13b");

// 수정 실행
LLMAutoFixer.LLMFixResult result = fixer.fix(code, issues);
System.out.println(result.formatReport());
```

**LLM 수정 기능:**

```java
// 이슈 목록 기반 수정
LLMFixResult result = fixer.fix(code, List.of(
    "Line 45: SQL Injection 취약점",
    "Line 78: 메서드가 너무 깁니다"
));

// 단일 이슈 수정
LLMFixResult result = fixer.fixIssue(code, "SQL Injection 취약점 수정");

// 전체 코드 개선
LLMFixResult result = fixer.improve(code);

// 특정 라인 범위만 수정
LLMFixResult result = fixer.fixLines(code, 45, 60, "보안 취약점 수정");
```

### 3. 하이브리드 수정 프로세스

```
┌──────────────────────────────────────────────────────────┐
│                    수정 프로세스                          │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  1. 규칙 기반 수정 (AutoFixer)                           │
│     • 빠른 패턴 매칭                                     │
│     • 결정적 변환                                        │
│     • 비용 없음                                          │
│                        ↓                                 │
│  2. 잔여 이슈 필터링                                     │
│     • 규칙으로 수정된 이슈 제외                          │
│     • LLM 수정이 필요한 이슈 선별                        │
│                        ↓                                 │
│  3. LLM 기반 수정 (LLMAutoFixer)                         │
│     • 복잡한 리팩토링                                    │
│     • 컨텍스트 이해 필요한 수정                          │
│     • 창의적 해결책                                      │
│                        ↓                                 │
│  4. 결과 병합                                            │
│     • 모든 변경 사항 통합                                │
│     • 변경 이력 추적                                     │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

## CLI 사용법

### 기본 사용

```bash
# 규칙 기반 수정 (미리보기)
code-ai auto-fix src/MyClass.java

# 파일에 직접 저장
code-ai auto-fix src/MyClass.java --write

# LLM 기반 수정 포함
code-ai auto-fix src/MyClass.java --llm

# 특정 LLM 제공자 지정
code-ai auto-fix src/MyClass.java --llm --provider openai

# diff 형식으로 출력
code-ai auto-fix src/MyClass.java --diff

# 백업 생성 후 수정
code-ai auto-fix src/MyClass.java --write --backup

# 디렉토리 전체 수정
code-ai auto-fix src/ --write
```

### 옵션

| 옵션 | 단축 | 설명 |
|------|------|------|
| `--write` | `-w` | 수정된 코드를 파일에 직접 저장 |
| `--llm` | | LLM 기반 수정 활성화 |
| `--provider` | `-p` | LLM 제공자 (claude, openai, ollama) |
| `--diff` | | diff 형식으로 변경사항 출력 |
| `--backup` | | 수정 전 백업 파일 생성 (.bak) |

## 출력 예시

### 규칙 기반 수정

```
============================================================
🔧 자동 수정 결과
============================================================

📊 통계:
   총 수정: 5개
   파일: src/MyClass.java

📝 적용된 수정:

1. [EMPTY_CATCH] Line 45
   - catch (Exception e) { }
   + catch (Exception e) { logger.error("Exception occurred", e); }

2. [SYSTEM_OUT] Line 67
   - System.out.println("Debug: " + value);
   + logger.info("Debug: {}", value);

3. [MAGIC_NUMBER] Line 89
   - if (count > 100) {
   + private static final int MAX_COUNT = 100;
   + if (count > MAX_COUNT) {

4. [MISSING_BRACES] Line 102
   - if (valid) return true;
   + if (valid) { return true; }

5. [TRAILING_WHITESPACE] Line 115
   - (공백 제거됨)
   +

============================================================
```

### LLM 기반 수정

```
============================================================
🤖 LLM 자동 수정 결과
============================================================

📝 총 2개 변경

• Line 45: SQL Injection 취약점 수정
  - String query = "SELECT * FROM users WHERE id = " + userId;
  + PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE id = ?");
  + ps.setInt(1, userId);

• Line 78: 메서드 추출 리팩토링
  - (35줄의 긴 메서드)
  + validateInput();
  + processData();
  + saveResult();

💡 설명:
   SQL Injection 취약점을 PreparedStatement로 수정하고,
   긴 메서드를 3개의 작은 메서드로 분리했습니다.

📌 분석 정보:
   모델: Claude (claude-3-5-sonnet-20241022)
   토큰: 1,847
```

## 코드 구조

```
code-ai-analyzer/src/main/java/com/codeai/analyzer/fix/
├── AutoFixer.java        # 규칙 기반 자동 수정
└── LLMAutoFixer.java     # LLM 기반 자동 수정
```

### AutoFixer.java 주요 구조

```java
public class AutoFixer {

    public enum FixType {
        EMPTY_CATCH, SYSTEM_OUT, MAGIC_NUMBER, STRING_CONCAT_LOOP,
        NULL_CHECK, DEEP_NESTING, RAW_TYPE, UNUSED_IMPORT,
        TRAILING_WHITESPACE, MISSING_BRACES
    }

    // 수정 실행
    public FixReport fix(String code) { ... }

    // 특정 유형만 수정
    public FixReport fix(String code, Set<FixType> types) { ... }

    // 개별 수정 메서드
    private void fixEmptyCatch(CompilationUnit cu, List<FixResult> fixes) { ... }
    private void fixSystemOut(CompilationUnit cu, List<FixResult> fixes) { ... }
    private void fixMagicNumbers(CompilationUnit cu, List<FixResult> fixes) { ... }
    // ...

    // 결과 레코드
    public record FixResult(FixType type, int line, String description,
                            String before, String after) {}
    public record FixReport(String originalCode, String fixedCode,
                           List<FixResult> fixes) { ... }
}
```

### LLMAutoFixer.java 주요 구조

```java
public class LLMAutoFixer {

    private final LLMClient client;
    private final AutoFixer ruleFixer;

    // 이슈 기반 수정
    public LLMFixResult fix(String code, List<String> issues) { ... }

    // 단일 이슈 수정
    public LLMFixResult fixIssue(String code, String issue) { ... }

    // 전체 개선
    public LLMFixResult improve(String code) { ... }

    // 특정 라인 수정
    public LLMFixResult fixLines(String code, int start, int end, String issue) { ... }

    // 팩토리 메서드
    public static LLMAutoFixer withClaude(String apiKey) { ... }
    public static LLMAutoFixer withOpenAI(String apiKey) { ... }
    public static LLMAutoFixer withOllama(String model) { ... }

    // 결과 레코드
    public record LLMFixResult(String fixedCode, List<FixChange> changes,
                               String explanation, boolean success,
                               LLMMetadata metadata) { ... }
    public record FixChange(int line, String description,
                           String before, String after) {}
    public record LLMMetadata(String model, int tokens) {}
}
```

## LLM 프롬프트

```
You are an expert code refactoring assistant. Your task is to fix code issues.

Rules:
1. Only modify the specific issues mentioned
2. Preserve the original code structure and style as much as possible
3. Keep variable and method names consistent
4. Add necessary imports if needed
5. Ensure the fixed code compiles

Respond in the following JSON format:
{
  "success": true,
  "fixedCode": "// the complete fixed code",
  "changes": [
    {
      "line": 10,
      "description": "수정 설명 (한국어)",
      "before": "original code snippet",
      "after": "fixed code snippet"
    }
  ],
  "explanation": "Overall explanation of changes in Korean"
}

IMPORTANT:
- Return the COMPLETE fixed code, not just the changed parts
- Ensure proper indentation and formatting
- Keep all original comments
```

## 안전 장치

### 1. 백업

```bash
# --backup 옵션 사용 시 .bak 파일 생성
code-ai auto-fix src/MyClass.java --write --backup
# → src/MyClass.java.bak 생성
```

### 2. 미리보기 모드

```bash
# --write 없이 실행하면 미리보기만
code-ai auto-fix src/MyClass.java
# → 변경사항만 표시, 파일 수정 없음
```

### 3. Diff 출력

```bash
# diff 형식으로 변경사항 검토
code-ai auto-fix src/MyClass.java --diff
```

### 4. 선택적 수정

```java
// 특정 유형만 수정
AutoFixer fixer = new AutoFixer();
FixReport report = fixer.fix(code, EnumSet.of(
    FixType.EMPTY_CATCH,
    FixType.SYSTEM_OUT
));
```

## CLI 버전: v10.0

```bash
code-ai auto-fix src/MyClass.java
code-ai auto-fix src/MyClass.java --write
code-ai auto-fix src/MyClass.java --llm --provider claude
```

## 비용 고려사항

| 수정 방식 | 비용 | 속도 | 정확도 | 복잡성 처리 |
|----------|------|------|--------|------------|
| 규칙 기반 | 무료 | 매우 빠름 | 높음 | 단순 패턴만 |
| LLM (Claude) | $0.01~0.05/파일 | 보통 | 높음 | 복잡한 리팩토링 |
| LLM (Ollama) | 무료 | 느림 | 중간 | 로컬 실행 |

## 다음 단계

- STEP-18: 웹 대시보드
- STEP-19: 팀 협업 기능
- STEP-20: 지속적 학습 시스템
