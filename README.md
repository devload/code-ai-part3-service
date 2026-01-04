# AI Process - Part 3: AI 서비스 프로세스

> **AI가 실제 서비스로 동작하는 과정**을 프로세스 관점에서 학습하는 교육용 프로젝트
>
> 이 프로젝트는 [Part 2: 코드 이해 프로세스](https://github.com/devload/code-ai-part2-analyzer)를 기반으로 합니다.

---

## 학습 목표

**AI가 API 호출부터 자동 수정까지 동작하는 서비스 과정을 이해합니다**

```
입력: "이 코드를 분석해줘"
        │
        ▼
┌─────────────────────────────────────────────────────────┐
│ STEP 13: API 호출 (API Calling)                         │
│ 요청 구성 → HTTP 전송 → 응답 수신                        │
└─────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────┐
│ STEP 14: 프롬프트 구성 (Prompt Engineering)             │
│ System Prompt + Context + Task + Output Format          │
└─────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────┐
│ STEP 15: LLM 처리 (LLM Processing)                      │
│ 모델 선택 → 비용/성능 최적화 → 라우팅                    │
└─────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────┐
│ STEP 16: 응답 파싱 (Response Parsing)                   │
│ JSON/Markdown → 구조화된 데이터 → 이슈 목록              │
└─────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────┐
│ STEP 17: 액션 실행 (Action Execution)                   │
│ 코드 수정 → 리팩토링 → 파일 변경                         │
└─────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────┐
│ STEP 18: 피드백 루프 (Feedback Loop)                    │
│ 검증 → 학습 → 재시도 전략 → 지속적 개선                  │
└─────────────────────────────────────────────────────────┘
        │
        ▼
출력: ✅ 수정된 코드 + 📊 분석 리포트
```

---

## 프로젝트 구조

```
ai-process-part3/
├── step13-api/                # STEP 13: API 호출
│   ├── APIClient.java
│   └── APIDemo.java
│
├── step14-prompt/             # STEP 14: 프롬프트 구성
│   ├── PromptBuilder.java
│   └── PromptDemo.java
│
├── step15-llm/                # STEP 15: LLM 처리
│   ├── LLMRouter.java
│   └── LLMDemo.java
│
├── step16-response/           # STEP 16: 응답 파싱
│   ├── ResponseParser.java
│   └── ResponseDemo.java
│
├── step17-action/             # STEP 17: 액션 실행
│   ├── ActionExecutor.java
│   └── ActionDemo.java
│
├── step18-feedback/           # STEP 18: 피드백 루프
│   ├── FeedbackLoop.java
│   └── FeedbackDemo.java
│
├── service-pipeline/          # AI 서비스 파이프라인 통합
│   └── ServicePipelineDemo.java
│
├── code-ai-analyzer/          # 기존 코드 분석 엔진
├── code-ai-web/               # 웹 대시보드
├── code-ai-intellij/          # IntelliJ 플러그인
├── code-ai-vscode/            # VS Code 확장
│
└── docs/                      # 문서
```

---

## 학습 단계

| STEP | 제목 | 핵심 질문 | 파일 |
|------|------|----------|------|
| 13 | API 호출 | LLM API는 어떻게 사용하는가? | `step13-api/` |
| 14 | 프롬프트 구성 | 좋은 프롬프트는 어떻게 만드는가? | `step14-prompt/` |
| 15 | LLM 처리 | 어떤 모델을 언제 쓰는가? | `step15-llm/` |
| 16 | 응답 파싱 | AI 응답을 어떻게 처리하는가? | `step16-response/` |
| 17 | 액션 실행 | AI가 도구를 어떻게 사용하는가? | `step17-action/` |
| 18 | 피드백 루프 | 결과를 어떻게 개선하는가? | `step18-feedback/` |

---

## 빠른 시작

### 빌드
```bash
./gradlew build
```

### 각 단계 데모 실행

```bash
# STEP 13: API 호출 데모
./gradlew :step13-api:run

# STEP 14: 프롬프트 구성 데모
./gradlew :step14-prompt:run

# STEP 15: LLM 처리 데모
./gradlew :step15-llm:run

# STEP 16: 응답 파싱 데모
./gradlew :step16-response:run

# STEP 17: 액션 실행 데모
./gradlew :step17-action:run

# STEP 18: 피드백 루프 데모
./gradlew :step18-feedback:run

# 전체 서비스 파이프라인
./gradlew :service-pipeline:run
```

---

## 지원하는 LLM 프로바이더

| 프로바이더 | 특징 | 환경변수 |
|-----------|------|---------|
| Claude | 긴 컨텍스트, 안전성, 코드 분석 강점 | `CLAUDE_API_KEY` |
| OpenAI | 다양한 모델, Function Calling | `OPENAI_API_KEY` |
| Ollama | 로컬 실행, 무료, 프라이버시 | (불필요) |

---

## 모델 선택 가이드

```
┌───────────────────────────────────────────────────────────┐
│ 작업 유형별 권장 모델                                      │
├─────────────────────────────┬─────────────────────────────┤
│ 🚀 복잡한 분석              │ Claude Opus, GPT-4          │
│ ⚖️  일반 코드 리뷰           │ Claude Sonnet, GPT-4-Turbo  │
│ ⚡ 빠른 응답                │ Claude Haiku, GPT-3.5       │
│ 🏠 민감 데이터              │ Ollama (로컬)               │
└─────────────────────────────┴─────────────────────────────┘
```

---

## 프롬프트 엔지니어링 기법

```
┌─────────────────────────────────────────────────────────┐
│ 효과적인 프롬프트 구조                                   │
│                                                          │
│ 1. System Prompt: "You are an expert code reviewer..."   │
│                                                          │
│ 2. Context: 코드, 분석 결과, 이전 대화                   │
│                                                          │
│ 3. Task: "Analyze this code for security issues..."      │
│                                                          │
│ 4. Output Format: "Respond in JSON format with..."       │
└─────────────────────────────────────────────────────────┘
```

---

## 자동 액션 타입

| 액션 | 설명 |
|------|------|
| REPLACE_CODE | 코드 교체 |
| INSERT_CODE | 코드 삽입 |
| DELETE_CODE | 코드 삭제 |
| ADD_IMPORT | import 추가 |
| RENAME | 이름 변경 |
| EXTRACT_METHOD | 메서드 추출 |
| ADD_LOGGING | 로깅 추가 |

---

## 피드백 루프

```
       ┌──────────────┐
       │   AI 실행    │
       └──────┬───────┘
              │
              ▼
       ┌──────────────┐
       │   결과 평가  │◀────────────┐
       └──────┬───────┘            │
              │                    │
        ┌─────┴─────┐              │
        ▼           ▼              │
    ┌───────┐  ┌───────┐           │
    │ 성공  │  │ 실패  │───────────┤
    └───────┘  └───┬───┘           │
                   │               │
                   ▼               │
            ┌──────────────┐       │
            │  전략 조정   │───────┘
            └──────────────┘
```

---

## 시리즈 구성

```
Part 1: AI 기초 프로세스
├── 토큰화 → 컨텍스트 → 확률계산 → 샘플링 → 생성루프 → 후처리
│
Part 2: 코드 이해 프로세스
├── 파싱 → AST → 의미분석 → 패턴매칭 → 이슈탐지 → 점수화
│
Part 3: AI 서비스 프로세스 (현재)
└── API호출 → 프롬프트구성 → LLM처리 → 응답파싱 → 액션실행 → 피드백
```

---

## 기술 스택

| 기술 | 버전 | 용도 |
|------|------|------|
| OkHttp | 4.12.0 | HTTP 클라이언트 |
| Gson | 2.10.1 | JSON 파싱 |
| Spring Boot | 3.2.1 | 웹 서버 |
| WebSocket | STOMP | 실시간 통신 |
| IntelliJ SDK | Latest | IDE 플러그인 |
| TypeScript | 5.0 | VS Code 확장 |

---

## 이전 / 다음 단계

👈 [Part 2: 코드 이해 프로세스](https://github.com/devload/code-ai-part2-analyzer)

👈 [Part 1: 기초 프로세스](https://github.com/devload/code-ai-part1-basics)

---

## 라이선스

MIT License

---

**Version**: 3.0.0 | **Focus**: AI Service Process Education
