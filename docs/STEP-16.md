# STEP-16: LLM 연동

## 목표
Claude, OpenAI, Ollama 등 다양한 LLM을 통합하여 실제 AI 기반 코드 리뷰를 수행합니다.

## 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│                    LLM Code Reviewer                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────┐                                            │
│  │ LLMClient   │ ← 추상화 인터페이스                         │
│  └─────────────┘                                            │
│         ▲                                                   │
│         │                                                   │
│    ┌────┴────┬────────────┬────────────┐                   │
│    │         │            │            │                   │
│  ┌─┴──┐  ┌───┴───┐  ┌────┴────┐  ┌────┴────┐              │
│  │Claude│  │OpenAI │  │ Ollama │  │ Custom │              │
│  │Client│  │Client │  │ Client │  │ Client │              │
│  └─────┘  └───────┘  └─────────┘  └─────────┘              │
│     │         │           │                                 │
│     ▼         ▼           ▼                                 │
│  ┌─────┐  ┌─────┐   ┌──────────┐                           │
│  │Claude│  │ GPT │   │ CodeLlama│                           │
│  │ API │  │ API │   │ Ollama   │                           │
│  └─────┘  └─────┘   └──────────┘                           │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## 구현 내용

### 1. LLMClient 인터페이스
```java
public interface LLMClient {
    // 동기 호출
    LLMResponse chat(LLMRequest request);

    // 비동기 호출
    CompletableFuture<LLMResponse> chatAsync(LLMRequest request);

    // 스트리밍 호출
    void chatStream(LLMRequest request, StreamHandler handler);

    String getName();
    boolean isAvailable();
}
```

### 2. ClaudeClient
```java
ClaudeClient client = ClaudeClient.builder()
    .apiKey("sk-...")                           // 또는 ANTHROPIC_API_KEY 환경변수
    .model("claude-3-5-sonnet-20241022")        // 기본값
    .build();
```

**지원 모델:**
- `claude-3-5-sonnet-20241022` (권장)
- `claude-3-opus-20240229`
- `claude-3-haiku-20240307`

### 3. OpenAIClient
```java
OpenAIClient client = OpenAIClient.builder()
    .apiKey("sk-...")                           // 또는 OPENAI_API_KEY 환경변수
    .model("gpt-4o")                            // 기본값
    .build();
```

**지원 모델:**
- `gpt-4o` (권장)
- `gpt-4-turbo`
- `gpt-3.5-turbo`

### 4. OllamaClient (로컬 LLM)
```java
OllamaClient client = OllamaClient.builder()
    .baseUrl("http://localhost:11434")          // 기본값
    .model("codellama:13b")                     // 기본값
    .build();
```

**권장 모델:**
- `codellama:13b` (코드 특화)
- `deepseek-coder:6.7b` (코드 특화)
- `qwen2.5-coder:7b` (코드 특화)
- `llama3:8b` (범용)

### 5. LLMCodeReviewer
```java
// Claude 사용
LLMCodeReviewer reviewer = LLMCodeReviewer.withClaude();

// OpenAI 사용
LLMCodeReviewer reviewer = LLMCodeReviewer.withOpenAI();

// Ollama 사용
LLMCodeReviewer reviewer = LLMCodeReviewer.withOllama("codellama:13b");

// 리뷰 실행
LLMReviewResult result = reviewer.review(code);
System.out.println(result.formatReport());
```

## CLI 사용법

### 기본 사용
```bash
# Claude로 리뷰 (기본값)
code-ai llm-review src/MyClass.java

# OpenAI로 리뷰
code-ai llm-review src/MyClass.java --provider openai

# Ollama로 로컬 리뷰
code-ai llm-review src/MyClass.java --provider ollama

# 특정 모델 지정
code-ai llm-review src/MyClass.java --provider claude --model claude-3-opus-20240229

# API 키 직접 지정
code-ai llm-review src/MyClass.java --api-key sk-...

# 스트리밍 모드
code-ai llm-review src/MyClass.java --stream
```

### 환경변수 설정
```bash
# Claude
export ANTHROPIC_API_KEY=sk-ant-...

# OpenAI
export OPENAI_API_KEY=sk-...

# Ollama (선택)
export OLLAMA_HOST=http://localhost:11434
```

### Ollama 설정
```bash
# 설치
brew install ollama

# 모델 다운로드
ollama pull codellama:13b
ollama pull deepseek-coder:6.7b

# 서버 실행
ollama serve
```

## 출력 예시

```
============================================================
🤖 LLM 코드 리뷰 결과
============================================================

📊 등급: 👍 B (82/100)

📝 요약:
   전반적으로 잘 구조화된 코드입니다. 몇 가지 개선점을 제안드립니다.

✨ 좋은 점:
   • 메서드가 단일 책임을 잘 따르고 있습니다
   • 변수명이 명확하고 의미가 잘 전달됩니다
   • 예외 처리가 적절하게 되어 있습니다

🔍 발견된 이슈:
------------------------------------------------------------
🚨 [CRITICAL] Line 45:
   SQL 쿼리를 문자열 연결로 만들고 있습니다. SQL Injection 위험이 있습니다.

   💡 제안:
      PreparedStatement를 사용하세요:
      PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE id = ?");
      ps.setInt(1, userId);
------------------------------------------------------------
💡 [SUGGESTION] Line 78:
   이 메서드가 35줄로 길어요. 여러 단계의 로직이 섞여 있습니다.

   💡 제안:
      Extract Method 리팩토링을 적용하세요:
      - validateInput()
      - processData()
      - saveResult()
------------------------------------------------------------

📌 분석 정보:
   모델: Claude (claude-3-5-sonnet-20241022)
   토큰: 1,247
```

## 시스템 프롬프트

LLM에 전달되는 시스템 프롬프트:

```
You are an expert code reviewer. Analyze the provided code and give constructive feedback.

Your review should include:
1. Code quality issues (bugs, anti-patterns, code smells)
2. Security vulnerabilities
3. Performance issues
4. Best practice violations
5. Suggestions for improvement
6. Positive aspects of the code

For each issue, provide:
- Line number (if applicable)
- Severity: CRITICAL, ISSUE, SUGGESTION, or PRAISE
- Clear explanation in Korean
- Suggested fix (if applicable)

Respond in the following JSON format:
{
  "summary": "Brief overall assessment in Korean",
  "grade": "A/B/C/D/F",
  "score": 0-100,
  "issues": [...],
  "positives": [...]
}
```

## 하이브리드 분석

LLMCodeReviewer는 규칙 기반 분석과 LLM 분석을 결합합니다:

```
┌─────────────────────────────────────────────────────────────┐
│                   Hybrid Analysis                            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. 규칙 기반 분석 (AICodeReviewer)                          │
│     ↓                                                       │
│  2. LLM에 코드 + 규칙 기반 결과 전달                         │
│     ↓                                                       │
│  3. LLM 분석 수행                                           │
│     ↓                                                       │
│  4. 결과 병합                                               │
│     - LLM이 놓친 Critical 이슈 추가                         │
│     - 중복 제거                                             │
│     ↓                                                       │
│  5. 최종 결과 반환                                          │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## 파일 구조
```
code-ai-analyzer/src/main/java/com/codeai/analyzer/llm/
├── LLMClient.java         # 추상화 인터페이스
├── ClaudeClient.java      # Claude API 클라이언트
├── OpenAIClient.java      # OpenAI API 클라이언트
├── OllamaClient.java      # Ollama 로컬 LLM 클라이언트
└── LLMCodeReviewer.java   # LLM 기반 코드 리뷰어
```

## CLI 버전: v9.0
```bash
code-ai llm-review src/MyClass.java --provider claude
code-ai llm-review src/MyClass.java --provider openai
code-ai llm-review src/MyClass.java --provider ollama --model codellama:13b
```

## 비용 고려사항

| 제공자 | 모델 | 입력 비용 | 출력 비용 | 비고 |
|-------|------|----------|----------|------|
| Claude | claude-3-5-sonnet | $3/1M | $15/1M | 권장 |
| Claude | claude-3-haiku | $0.25/1M | $1.25/1M | 저렴 |
| OpenAI | gpt-4o | $2.5/1M | $10/1M | |
| OpenAI | gpt-3.5-turbo | $0.5/1M | $1.5/1M | 저렴 |
| Ollama | codellama | 무료 | 무료 | 로컬 |

## 다음 단계
- STEP-17: 코드 자동 수정 (Auto-fix)
- STEP-18: 웹 대시보드
- STEP-19: 팀 협업 기능
