# Code AI 프로젝트 전체 요약

## 프로젝트 개요

**Code AI**는 교육용 코드 특화 AI 어시스턴트입니다. 직접 구현한 N-gram 언어 모델과 AST 분석을 결합하여 Java 코드를 분석하고 리뷰합니다.

```
┌─────────────────────────────────────────────────────────────┐
│                      Code AI System                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   [Input: Java Code]                                        │
│         │                                                   │
│         ▼                                                   │
│   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    │
│   │  Tokenizer  │───▶│  N-gram     │───▶│    AST      │    │
│   │  (BPE/Code) │    │   Model     │    │  Analyzer   │    │
│   └─────────────┘    └─────────────┘    └─────────────┘    │
│         │                   │                   │           │
│         └───────────────────┴───────────────────┘           │
│                             │                               │
│                             ▼                               │
│                  ┌─────────────────────┐                   │
│                  │   Code Reviewer     │                   │
│                  │   (Score + Issues)  │                   │
│                  └─────────────────────┘                   │
│                             │                               │
│         ┌───────────────────┼───────────────────┐          │
│         ▼                   ▼                   ▼          │
│   ┌──────────┐       ┌──────────┐       ┌──────────┐       │
│   │   CLI    │       │   IDE    │       │   Web    │       │
│   │  v10.0   │       │ Plugins  │       │Dashboard │       │
│   └──────────┘       └──────────┘       └──────────┘       │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 프로젝트 구조

```
code-ai/
├── mini-ai-core/                 # 기초 유틸리티
├── mini-ai-tokenizer-simple/     # 단순 토크나이저
├── code-ai-tokenizer/            # 코드 특화 토크나이저 (BPE)
├── mini-ai-model-ngram/          # N-gram 언어 모델
├── code-ai-analyzer/             # 코드 분석 엔진 (핵심)
│   ├── ast/                      # AST 분석기
│   ├── ai/                       # AI 코드 리뷰어
│   ├── llm/                      # LLM 클라이언트
│   └── fix/                      # 자동 수정기
├── mini-ai-server/               # REST API 서버
├── mini-ai-cli/                  # CLI 도구 (v10.0)
├── code-ai-intellij/             # IntelliJ 플러그인
├── code-ai-vscode/               # VS Code 확장
├── code-ai-web/                  # 웹 대시보드 (Spring Boot)
├── .github/workflows/            # CI/CD 파이프라인
├── docs/                         # 문서 (STEP-00 ~ STEP-18)
└── *.html                        # 정적 대시보드 UI
```

---

## 구현 단계별 요약

### Phase 1: 기초 (STEP 00-04)

| STEP | 주제 | 핵심 구현 |
|------|------|----------|
| 00 | 프로젝트 설계 | 아키텍처 설계, 모듈 구조 정의 |
| 01 | 토크나이저 기초 | 문자열 → 토큰 변환, Vocabulary 구축 |
| 02 | BPE 토크나이저 | Byte Pair Encoding 알고리즘 구현 |
| 03 | 코드 토크나이저 | Java 문법 인식, 키워드/연산자 처리 |
| 04 | N-gram 모델 | Unigram, Bigram, Trigram 확률 계산 |

**핵심 코드:**
```java
// BPE 토크나이저
BPETokenizer tokenizer = new BPETokenizer(vocabSize);
tokenizer.train(corpus);
List<String> tokens = tokenizer.tokenize("public class Example {}");

// N-gram 모델
NGramModel model = new NGramModel(3); // Trigram
model.train(tokenizedCorpus);
double prob = model.probability("public", "class");
```

---

### Phase 2: 언어 모델 (STEP 05-07)

| STEP | 주제 | 핵심 구현 |
|------|------|----------|
| 05 | 확률 계산 | 조건부 확률, 로그 확률 |
| 06 | 스무딩 | Laplace, Kneser-Ney 스무딩 |
| 07 | 평가 | Perplexity 계산, 모델 비교 |

**실험 결과:**
```
┌────────────┬────────────┬───────────┐
│ 모델       │ Perplexity │ 정확도    │
├────────────┼────────────┼───────────┤
│ Trigram    │ 45.2       │ 78.5%     │
│ 5-gram     │ 38.7       │ 82.3%     │
│ Code-aware │ 32.1       │ 86.7%     │
└────────────┴────────────┴───────────┘
```

---

### Phase 3: 코드 분석 (STEP 08-10)

| STEP | 주제 | 핵심 구현 |
|------|------|----------|
| 08 | AST 분석 | JavaParser 기반 구문 트리 분석 |
| 09 | 코드 품질 | 복잡도, 중복, 코드 스멜 탐지 |
| 10 | 점수 계산 | 카테고리별 점수, 등급 산정 |

**AST 분석기:**
```java
ASTAnalyzer analyzer = new ASTAnalyzer();
AnalysisResult result = analyzer.analyze(javaCode);

// 탐지 항목
- 빈 catch 블록
- System.out 사용
- 매직 넘버
- 긴 메서드 (>50줄)
- 깊은 중첩 (>4단계)
- 미사용 변수
- SQL Injection 취약점
```

---

### Phase 4: AI 리뷰 (STEP 11-13)

| STEP | 주제 | 핵심 구현 |
|------|------|----------|
| 11 | 규칙 기반 리뷰 | 패턴 매칭, 이슈 분류 |
| 12 | 리팩토링 제안 | Extract Method, 디자인 패턴 |
| 13 | 종합 리뷰 | N-gram + AST + 규칙 통합 |

**AI 코드 리뷰어:**
```java
AICodeReviewer reviewer = new AICodeReviewer();
ReviewResult result = reviewer.review(code);

System.out.println("등급: " + result.grade());      // A, B, C, D, F
System.out.println("점수: " + result.score());      // 0-100
System.out.println("이슈: " + result.issues());     // 발견된 문제
System.out.println("장점: " + result.positives());  // 잘한 점
```

**점수 체계:**
```
┌─────────────────────────────────────────────────────┐
│                   Code Quality Score                 │
├─────────────┬───────────────────────────────────────┤
│ 카테고리     │ 평가 항목                             │
├─────────────┼───────────────────────────────────────┤
│ 가독성      │ 명명규칙, 주석, 포맷팅                 │
│ 유지보수성  │ 복잡도, 결합도, 응집도                 │
│ 보안        │ SQL Injection, XSS, 하드코딩 비밀     │
│ 성능        │ 알고리즘 효율, 리소스 관리            │
└─────────────┴───────────────────────────────────────┘

등급: A(90+), B(80+), C(70+), D(60+), F(<60)
```

---

### Phase 5: 통합 (STEP 14-18)

| STEP | 주제 | 핵심 구현 |
|------|------|----------|
| 14 | IDE 플러그인 | IntelliJ, VS Code 통합 |
| 15 | CI/CD | GitHub Actions 자동화 |
| 16 | LLM 연동 | Claude, OpenAI, Ollama 클라이언트 |
| 17 | 자동 수정 | 규칙 기반 + LLM 기반 Auto-fix |
| 18 | 웹 대시보드 | Spring Boot, WebSocket, REST API |

---

## 핵심 컴포넌트

### 1. 코드 토크나이저

```java
// 일반 텍스트
"public class" → ["public", "class"]

// 코드 인식
"System.out.println" → ["System", ".", "out", ".", "println"]
"i++" → ["i", "++"]
"List<String>" → ["List", "<", "String", ">"]
```

### 2. N-gram 모델

```java
// 다음 토큰 예측
P("void" | "public", "static") = 0.85
P("{" | "class", "Example") = 0.92

// 코드 자연스러움 평가
score("public class Example {}") = 0.95  // 자연스러움
score("class public {} Example") = 0.12  // 부자연스러움
```

### 3. AST 분석기

```java
ASTAnalyzer analyzer = new ASTAnalyzer();

// 분석 결과
AnalysisResult {
    issues: [
        {code: "EMPTY_CATCH", line: 45, severity: "CRITICAL"},
        {code: "SYSTEM_OUT", line: 23, severity: "WARNING"},
        {code: "MAGIC_NUMBER", line: 67, severity: "INFO"}
    ],
    metrics: {
        lines: 156,
        methods: 12,
        complexity: 24,
        maxNesting: 4
    }
}
```

### 4. 자동 수정기

```java
AutoFixer fixer = new AutoFixer();
FixReport report = fixer.fix(code);

// 지원하는 자동 수정
- EMPTY_CATCH → 로깅 코드 추가
- SYSTEM_OUT → Logger 변환
- MAGIC_NUMBER → 상수 추출
- MISSING_BRACES → 중괄호 추가
- RAW_TYPE → 제네릭 추가
```

### 5. LLM 클라이언트

```java
// 우리 모델 (기본)
CodeAIModel model = new CodeAIModel();
ReviewResult result = model.review(code);

// 외부 LLM (옵션)
LLMClient claude = ClaudeClient.builder().build();
LLMClient openai = OpenAIClient.builder().build();
LLMClient ollama = OllamaClient.builder().model("codellama:13b").build();
```

---

## CLI 사용법 (v10.0)

```bash
# 기본 분석 (우리 모델)
code-ai analyze src/MyClass.java

# 점수만 확인
code-ai score src/MyClass.java

# AST 분석
code-ai ast src/MyClass.java

# 자동 수정
code-ai auto-fix src/MyClass.java --write

# LLM 리뷰 (외부 모델 사용 시)
code-ai llm-review src/MyClass.java --provider claude

# 도움말
code-ai --help
```

**출력 예시:**
```
============================================================
🤖 Code AI 분석 결과
============================================================

📊 점수: 78/100 (등급: B)

📈 카테고리별 점수:
   가독성      ████████░░ 82
   유지보수성  ███████░░░ 75
   보안        ██████░░░░ 68
   성능        ████████░░ 85

🔍 발견된 이슈 (4개):
   🚨 [CRITICAL] Line 45: SQL Injection 취약점
   ⚠️  [WARNING] Line 23: System.out 사용
   ⚠️  [WARNING] Line 67: 매직 넘버 사용
   💡 [INFO] Line 89: if문 중괄호 누락

✨ 잘한 점:
   • 메서드가 단일 책임을 잘 따름
   • 변수명이 명확함
   • 예외 처리가 적절함
============================================================
```

---

## 웹 대시보드

### 접속
```bash
# 정적 HTML 버전
open dashboard.html

# Spring Boot 버전
cd code-ai-web && ../gradlew bootRun
# http://localhost:8080
```

### 페이지 구성
| 페이지 | 기능 |
|--------|------|
| Dashboard | 코드 입력, 실시간 분석, 점수 표시 |
| Analyze | 파일 업로드, LLM 옵션, Auto-fix |
| History | 분석 기록, 필터, CSV 내보내기 |
| Settings | 모델 선택, API 키, UI 설정 |

### 모델 선택
```
1. Code AI (Our N-gram Model) ← 기본값
2. Claude API
3. OpenAI API
4. Ollama (Local)
```

---

## REST API

### 엔드포인트
| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/v1/analyze` | 코드 분석 |
| POST | `/api/v1/score` | 점수 계산 |
| POST | `/api/v1/auto-fix` | 자동 수정 |
| GET | `/api/v1/analyses` | 분석 기록 |

### 요청 예시
```bash
curl -X POST http://localhost:8080/api/v1/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "code": "public class Example { ... }",
    "options": {
      "provider": "codeai",
      "includeAST": true,
      "autoFix": false
    }
  }'
```

---

## 파일 목록

### 핵심 Java 클래스
```
code-ai-tokenizer/
└── CodeTokenizer.java         # 코드 토크나이저

mini-ai-model-ngram/
└── NGramModel.java            # N-gram 언어 모델

code-ai-analyzer/
├── ast/ASTAnalyzer.java       # AST 분석
├── ai/AICodeReviewer.java     # AI 리뷰어
├── llm/
│   ├── LLMClient.java         # LLM 추상화
│   ├── ClaudeClient.java
│   ├── OpenAIClient.java
│   └── OllamaClient.java
├── fix/
│   ├── AutoFixer.java         # 규칙 기반 수정
│   └── LLMAutoFixer.java      # LLM 기반 수정
└── CodeScorer.java            # 점수 계산

mini-ai-cli/
└── MiniAiCli.java             # CLI (v10.0)
```

### 문서
```
docs/
├── STEP-00.md ~ STEP-18.md    # 단계별 구현 문서
├── PROJECT-SUMMARY.md         # 이 문서
└── 실험결과-Trigram-vs-5gram.md
```

### 대시보드
```
dashboard.html    # 메인 대시보드
analyze.html      # 분석 페이지
history.html      # 기록 페이지
settings.html     # 설정 페이지
```

---

## 기술 스택

| 영역 | 기술 |
|------|------|
| Language | Java 17 |
| Build | Gradle 8.x |
| AST Parser | JavaParser 3.25 |
| HTTP Client | OkHttp 4.12 |
| JSON | Gson 2.10 |
| Web | Spring Boot 3.2 |
| WebSocket | STOMP/SockJS |
| Template | Thymeleaf |
| IDE Plugin | IntelliJ Platform SDK |
| VS Code | TypeScript |
| CI/CD | GitHub Actions |

---

## 교육 포인트

### 1. 토큰화 (Tokenization)
- 왜 필요한가: LLM의 입력 단위
- BPE vs WordPiece 비교
- 코드 특화 토큰화의 중요성

### 2. N-gram 모델
- 조건부 확률의 이해
- Markov 가정
- 스무딩 기법의 필요성
- Perplexity로 모델 평가

### 3. 정적 분석
- AST(Abstract Syntax Tree) 개념
- 코드 품질 메트릭
- 보안 취약점 탐지

### 4. 하이브리드 접근
- 규칙 기반 vs 학습 기반
- 각각의 장단점
- 실제 시스템에서의 조합

### 5. LLM 통합
- API 추상화 패턴
- 프롬프트 엔지니어링
- 비용/성능 트레이드오프

---

## 향후 발전 방향

1. **STEP-19**: 팀 협업 기능
2. **STEP-20**: 지속적 학습 시스템
3. **STEP-21**: 성능 최적화
4. **STEP-22**: 다국어 지원 (Python, JavaScript)
5. **STEP-23**: 코드 생성 기능

---

## 결론

Code AI는 직접 구현한 N-gram 모델을 기반으로 코드를 이해하고 분석하는 시스템입니다.
토크나이저부터 웹 대시보드까지 전체 파이프라인을 구현하여 AI 기반 코드 분석의
핵심 개념을 학습할 수 있는 교육용 프로젝트입니다.

**핵심 가치:**
- 🎓 **교육**: LLM의 핵심 개념을 직접 구현하며 학습
- 🔧 **실용**: 실제 코드 리뷰에 활용 가능
- 🔌 **확장**: 외부 LLM과 연동 가능한 구조
- 📊 **시각화**: 웹 대시보드로 결과 확인

---

*Last Updated: 2024-01-15*
*Version: 1.0.0*
*CLI Version: v10.0*
