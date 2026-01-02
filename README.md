# Code AI

코드 특화 AI 어시스턴트 - N-gram 언어 모델 기반 코드 분석 시스템

```
┌─────────────────────────────────────────────────────────┐
│  [Java Code] → [Tokenizer] → [N-gram] → [AST] → [Score] │
└─────────────────────────────────────────────────────────┘
```

## 특징

- 🎓 **교육용**: 토크나이저부터 언어 모델까지 직접 구현
- 🔍 **코드 분석**: AST 기반 정적 분석 + N-gram 확률 모델
- 🛠 **자동 수정**: 발견된 이슈 자동 수정 (Auto-fix)
- 🌐 **웹 대시보드**: 실시간 코드 분석 UI
- 🔌 **확장 가능**: Claude, OpenAI, Ollama 연동 지원

---

## 빠른 시작

### CLI 사용 (v10.0)
```bash
# 빌드
./gradlew build

# 코드 분석
./gradlew :mini-ai-cli:run --args="analyze src/Example.java"

# 점수 확인
./gradlew :mini-ai-cli:run --args="score src/Example.java"

# 자동 수정
./gradlew :mini-ai-cli:run --args="auto-fix src/Example.java --write"

# 코드 자동완성
./gradlew :mini-ai-cli:run --args='complete "public class User {"'
```

### 웹 대시보드
```bash
# 정적 HTML 버전
open dashboard.html

# Spring Boot 버전
cd code-ai-web && ../gradlew bootRun
# http://localhost:8080
```

---

## 프로젝트 구조

```
code-ai/
├── mini-ai-core/             # 기초 유틸리티
├── code-ai-tokenizer/        # 코드 토크나이저 (BPE)
├── mini-ai-model-ngram/      # N-gram 언어 모델 (Trigram)
├── code-ai-analyzer/         # 코드 분석 엔진
│   ├── ast/                  # AST 분석 (JavaParser)
│   ├── ai/                   # AI 코드 리뷰어
│   ├── llm/                  # LLM 클라이언트
│   └── fix/                  # 자동 수정
├── mini-ai-server/           # REST API 서버
├── mini-ai-cli/              # CLI (v10.0)
├── code-ai-intellij/         # IntelliJ 플러그인
├── code-ai-vscode/           # VS Code 확장
├── code-ai-web/              # 웹 대시보드 (Spring Boot)
├── .github/workflows/        # CI/CD (GitHub Actions)
├── docs/                     # 문서 (STEP-00 ~ STEP-18)
└── dashboard.html            # 정적 대시보드 UI
```

---

## 핵심 기능

### 1. 코드 분석
```java
AICodeReviewer reviewer = new AICodeReviewer();
ReviewResult result = reviewer.review(javaCode);

System.out.println("등급: " + result.grade());   // A, B, C, D, F
System.out.println("점수: " + result.score());   // 0-100
```

### 2. 이슈 탐지

| 이슈 | 설명 | 심각도 |
|------|------|--------|
| SQL_INJECTION | SQL 인젝션 취약점 | CRITICAL |
| EMPTY_CATCH | 빈 catch 블록 | CRITICAL |
| SYSTEM_OUT | System.out 사용 | WARNING |
| MAGIC_NUMBER | 매직 넘버 | WARNING |
| LONG_METHOD | 긴 메서드 (>50줄) | WARNING |
| DEEP_NESTING | 깊은 중첩 (>4단계) | INFO |
| MISSING_BRACES | 중괄호 누락 | INFO |

### 3. 자동 수정
```java
AutoFixer fixer = new AutoFixer();
FixReport report = fixer.fix(code);

// 적용되는 수정:
// - System.out → Logger 변환
// - 빈 catch → 로깅 추가
// - 매직 넘버 → 상수 추출
// - 중괄호 누락 → 추가
```

### 4. 모델 선택
```
1. Code AI (Our N-gram Model)  ← 기본값
2. Claude API
3. OpenAI API
4. Ollama (Local)
```

---

## CLI 명령어

```bash
# 기본 분석 (우리 모델)
code-ai analyze <file>

# 점수만 확인
code-ai score <file>

# AST 분석
code-ai ast <file>

# 자동 수정
code-ai auto-fix <file> --write

# LLM 리뷰 (외부 모델)
code-ai llm-review <file> --provider claude

# 코드 자동완성
code-ai complete "public class"

# 모델 학습
code-ai train --corpus data/code.txt --model trigram
```

---

## 웹 대시보드

| 페이지 | 기능 |
|--------|------|
| **Dashboard** | 코드 입력, 실시간 분석, 점수/등급 표시 |
| **Analyze** | 파일 업로드, LLM 옵션, Auto-fix |
| **History** | 분석 기록, 필터, CSV 내보내기 |
| **Settings** | 모델 선택, API 키, UI 설정 |

```bash
# 정적 버전 (바로 사용 가능)
open dashboard.html

# Spring Boot 버전
cd code-ai-web && ../gradlew bootRun
```

---

## 개발 단계 (STEP 00-18)

| Phase | 단계 | 내용 |
|-------|------|------|
| 1 | STEP 00-04 | 토크나이저, N-gram 기초 |
| 2 | STEP 05-07 | 확률 계산, 스무딩, 평가 |
| 3 | STEP 08-10 | AST 분석, 코드 품질 |
| 4 | STEP 11-13 | AI 리뷰, 리팩토링 제안 |
| 5 | STEP 14-18 | IDE 플러그인, CI/CD, 웹 UI |

### 완료된 기능

- ✅ CodeTokenizer (코드 문법 인식)
- ✅ Trigram 모델 + Backoff
- ✅ AST 분석 (JavaParser)
- ✅ 코드 품질 점수 (A~F 등급)
- ✅ 자동 수정 (규칙 기반 + LLM)
- ✅ IntelliJ / VS Code 플러그인
- ✅ GitHub Actions CI/CD
- ✅ 웹 대시보드

---

## 기술 스택

| 영역 | 기술 |
|------|------|
| Language | Java 17 |
| Build | Gradle 8.x |
| Parser | JavaParser 3.25 |
| Web | Spring Boot 3.2 |
| HTTP | OkHttp 4.12 |
| JSON | Gson 2.10 |
| CLI | picocli 4.7 |
| IDE | IntelliJ Platform SDK |

---

## 문서

| 문서 | 설명 |
|------|------|
| [PROJECT-SUMMARY.md](docs/PROJECT-SUMMARY.md) | 전체 프로젝트 요약 |
| [STEP-00.md](docs/STEP-00.md) ~ [STEP-18.md](docs/STEP-18.md) | 단계별 구현 문서 |

---

## 라이선스

Educational Purpose

---

**Version**: 1.0.0 | **CLI**: v10.0
