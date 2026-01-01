# Code AI - 코드 생성 특화 AI 어시스턴트

> **기반 프로젝트**: [Mini AI](../aimaker) - 토큰/Bigram 교육용 프로젝트
> **목적**: 실제 코드 자동완성 및 생성에 특화된 AI 시스템 구축

---

## 프로젝트 소개

이 프로젝트는 **Mini AI**의 교육용 N-gram 엔진을 기반으로,
**코드 자동완성과 코드 생성**에 특화된 버전을 개발합니다.

### Mini AI vs Code AI

| 특징 | Mini AI (기반) | Code AI (이 프로젝트) |
|------|---------------|---------------------|
| **목적** | 교육 (토큰 이해) | 실용 (코드 생성) |
| **토크나이저** | WhitespaceTokenizer | CodeTokenizer (예정) |
| **코퍼스** | 일반 문장 | 코드 패턴 |
| **출력** | 텍스트 | 실행 가능한 코드 |
| **통합** | CLI/REST API | VSCode Extension (예정) |

---

## 핵심 개선 포인트

### 1. CodeTokenizer (핵심!)

기존 WhitespaceTokenizer의 한계:
```java
// 기존: 공백으로만 분리
"public void getName() {"
→ ["public", "void", "getName()", "{"]  // 괄호가 붙어버림!

// 개선: 코드 문법 인식
"public void getName() {"
→ ["public", "void", "getName", "(", ")", "{"]  // 문법 단위로 분리!
```

### 2. 들여쓰기 압축

GPT-2 vs GPT-4 연구 결과 적용:
```python
# GPT-2: 147 토큰 (공백 1개 = 토큰 1개)
# GPT-4: 70 토큰 (공백 묶음 = 토큰 1개)

# 우리 목표: GPT-4 방식
"    if (true) {"
→ ["INDENT_1", "if", "(", "true", ")", "{"]
```

### 3. 코드 코퍼스

| 언어 | 패턴 | 용도 |
|------|------|------|
| Java | Spring Boot, POJO | 주력 |
| Python | FastAPI, Django | 확장 |
| JavaScript | React, Node.js | 확장 |

---

## 빠른 시작

### 1. 빌드
```bash
./gradlew build
```

### 2. 서버 실행
```bash
./gradlew :mini-ai-server:bootRun
```

### 3. 코드 패턴 학습
```bash
./gradlew :mini-ai-cli:run --args="train --corpus data/code-corpus/java/starter-patterns.txt"
```

### 4. 코드 자동완성
```bash
./gradlew :mini-ai-cli:run --args="run -p 'public class User {' --max-tokens 10"
```

---

## 로드맵

### Phase 0: Fork from Mini AI
- [x] 저장소 Fork
- [x] Git 초기화
- [x] README 업데이트
- [x] 초기 코드 코퍼스 추가

### Phase 1: CodeTokenizer 개발
- [ ] 기본 CodeTokenizer 인터페이스
- [ ] 들여쓰기 압축 구현
- [ ] 괄호/세미콜론 분리
- [ ] Java 키워드 인식
- [ ] 테스트 작성

### Phase 2: 코드 코퍼스 확장
- [ ] Java 패턴 100+ 개
- [ ] Spring Boot 패턴
- [ ] 디자인 패턴

### Phase 3: Context 확장
- [ ] Trigram 구현
- [ ] Smoothing/Backoff
- [ ] 5-gram 이상 실험

### Phase 4: IDE 통합
- [ ] REST API 확장
- [ ] VSCode Extension 개발
- [ ] 실시간 자동완성

---

## 아키텍처

```
CLI ──HTTP──> Server ──> Model ──> Tokenizer ──> Core
                                      │
                              CodeTokenizer (NEW!)
                              ├── 들여쓰기 압축
                              ├── 심볼 분리
                              └── 키워드 인식
```

### 모듈 구성

| 모듈 | 역할 | 상태 |
|------|------|------|
| `mini-ai-core` | 인터페이스/DTO | 기존 유지 |
| `mini-ai-tokenizer-simple` | 기존 Tokenizer | 기존 유지 |
| `code-ai-tokenizer` | CodeTokenizer | **신규 개발** |
| `mini-ai-model-ngram` | N-gram 모델 | 기존 유지 |
| `mini-ai-server` | REST API | 기존 유지 |
| `mini-ai-cli` | CLI 도구 | 확장 예정 |

---

## 기술 스택

- **언어**: Java 17
- **빌드**: Gradle 8.5
- **테스트**: JUnit 5
- **서버**: Spring Boot 3.2.0
- **CLI**: picocli 4.7.5
- **JSON**: Gson 2.10.1
- **HTTP 클라이언트**: OkHttp 4.12.0

---

## 디렉토리 구조

```
code-ai/
├── README.md               # 이 문서
├── FORK-PLAN-코딩특화.md    # 상세 개발 계획
├── FORK-시작가이드.md       # Fork 시작 가이드
├── 연구자료-코드LLM-핵심인사이트.md  # GPT 연구 분석
├── data/
│   ├── code-corpus/        # 코드 학습 데이터
│   │   └── java/
│   │       └── starter-patterns.txt
│   └── research/           # 참고 자료
├── mini-ai-core/
├── mini-ai-tokenizer-simple/
├── code-ai-tokenizer/      # 신규 (예정)
├── mini-ai-model-ngram/
├── mini-ai-server/
└── mini-ai-cli/
```

---

## 참고 문서

### 개발 계획
- [FORK-PLAN-코딩특화.md](FORK-PLAN-코딩특화.md) - 5단계 개발 로드맵
- [FORK-시작가이드.md](FORK-시작가이드.md) - Fork 시작 가이드

### 연구 자료
- [연구자료-코드LLM-핵심인사이트.md](연구자료-코드LLM-핵심인사이트.md) - GPT 연구 분석
- `data/research/` - 코드 특화 LLM 관련 PDF

### 기반 프로젝트
- [Mini AI README](README-ORIGINAL.md) - 원본 프로젝트 문서

---

## 라이선스

MIT License

---

**목표: 코드 자동완성에 특화된 실용적인 AI 어시스턴트 구축**
