# Mini AI Full-Stack (Java)

**과정 중심 교육 자료** - AI 시스템을 직접 만들면서 배우는 프로젝트

---

## 프로젝트 소개

이 저장소는 **"완성품"이 아니라 "학습 과정"** 입니다.

자바로 "토큰화 → N-gram 학습/서빙 → REST API → CLI"를 직접 구현하면서,
각 단계마다 문서/데모/커밋을 남겨 교육 자료로 활용할 수 있도록 설계되었습니다.

### 핵심 특징

- **Step별 학습**: 한 번에 완성하지 않고, 단계별로 하나씩
- **과정 산출물**: 각 Step마다 문서 + 데모 로그 + Git 태그
- **교체 가능 설계**: 인터페이스 우선, 구현체는 언제든 교체 가능
- **실전 구조**: 실제 LLM 서비스와 유사한 아키텍처 (토큰, Usage, API)

---

## 아키텍처

```
CLI ──HTTP──> Server ──> Model ──> Tokenizer ──> Core
                                                   ↑
                                              (인터페이스만)
```

### 모듈 구성

| 모듈 | 역할 | Step |
|------|------|------|
| `mini-ai-core` | 인터페이스/DTO 정의 | 0 |
| `mini-ai-tokenizer-simple` | Tokenizer 구현 | 1 |
| `mini-ai-model-ngram` | Bigram 학습/생성 | 2, 3 |
| `mini-ai-server` | Spring Boot REST API | 5 |
| `mini-ai-cli` | 명령줄 도구 | 6 |

---

## Step별 실행 흐름

이 프로젝트는 **Git 태그를 따라가면서** 학습할 수 있도록 구성되어 있습니다.

### Step 0: 뼈대 만들기 ✅
**학습 포인트**: 교체 가능한 구조란?

- Gradle 멀티모듈 프로젝트 생성
- mini-ai-core에 인터페이스/DTO 정의
- 외부 의존성 없는 순수 Java 설계

**산출물**:
- [docs/STEP-00.md](docs/STEP-00.md) - 아키텍처 개요
- docs/demo/STEP-00.log - 빌드 성공 로그

**태그**: `step-00`

```bash
git checkout step-00
./gradlew build
```

---

### Step 1: Tokenizer 만들기 ✅
**학습 포인트**: 텍스트가 "조각"으로 바뀌는 순간

- WhitespaceTokenizer 구현 (encode/decode)
- JUnit 테스트 작성
- 토큰화의 한계 이해

**산출물**:
- docs/STEP-01.md - 토큰이 무엇인지
- docs/demo/STEP-01.log - 테스트 실행 로그

**태그**: `step-01` ✅

---

### Step 2: ✅ Bigram 학습 구현 ✅
**학습 포인트**: 학습 = 카운트 테이블 만들기

- BigramTrainer 구현
- Corpus → 카운트 테이블 → JSON artifact
- 학습 데이터에서 패턴 추출

**산출물**:
- docs/STEP-02.md - Bigram이 무엇인지
- docs/demo/STEP-02.log - 학습 실행 로그

**태그**: `step-02` ✅

---

### Step 3: ✅ Bigram 생성 구현 ✅
**학습 포인트**: 다음 토큰 예측 루프

- BigramModel 구현
- Sampler (topK, temperature)
- seed 고정으로 재현성 보장

**산출물**:
- docs/STEP-03.md - 생성 루프 설명
- docs/demo/STEP-03.log - 생성 결과 비교

**태그**: `step-03` ✅

---

### Step 4: ✅ Usage 측정 ✅
**학습 포인트**: 토큰 = 비용 단위

- UsageMeter 구현
- GenerateResponse에 usage 포함
- input/output/total 토큰 추적

**산출물**:
- docs/STEP-04.md - 왜 토큰 카운트가 중요한지
- docs/demo/STEP-04.log - usage 변화 확인

**태그**: `step-04` ✅

---

### Step 5: ✅ Server 만들기 ✅
**학습 포인트**: 모델 서빙

- Spring Boot REST API
- POST /v1/train, /v1/generate
- latency 측정

**산출물**:
- docs/STEP-05.md - API 설계
- docs/demo/STEP-05.log - curl 테스트

**태그**: `step-05` ✅

---

### Step 6: ✅ CLI 만들기 ✅
**학습 포인트**: AI 사용 경험

- picocli 기반 CLI
- train, run, chat, tokenize 명령
- HTTP로 서버 호출

**산출물**:
- docs/STEP-06.md - CLI UX 설계
- docs/demo/STEP-06.log - 전체 시나리오

**태그**: `step-06` ✅

---

### Step 7: ✅ 확장 설계 ✅
**학습 포인트**: 모델 교체 가능성

- Trigram 확장 포인트 확보
- backoff, interpolation 자리 표시
- 다음 단계 로드맵

**산출물**:
- docs/STEP-07.md - 확장 로드맵
- 코드에 "Trigram 자리" 존재

**태그**: `step-07` ✅

---

## 빠른 시작

### 1. 저장소 클론
```bash
git clone <repository-url>
cd mini-ai
```

### 2. 특정 Step 체크아웃
```bash
# Step 0부터 시작
git checkout step-00

# 또는 최신 상태
git checkout main
```

### 3. 빌드 및 실행
```bash
# 빌드
./gradlew build

# 테스트 (Step 1 이후)
./gradlew test

# 서버 실행 (Step 5 이후)
./gradlew :mini-ai-server:bootRun

# CLI 실행 (Step 6 이후)
./gradlew :mini-ai-cli:run --args="run -p 'Hello world'"
```

---

## 학습 가이드

### 이 저장소를 활용하는 방법

1. **순차적 학습** (권장)
   ```bash
   git checkout step-00
   # docs/STEP-00.md 읽기
   # 코드 살펴보기
   ./gradlew build

   git checkout step-01
   # docs/STEP-01.md 읽기
   # 변경된 코드 확인 (git diff step-00 step-01)
   ./gradlew test
   ```

2. **특정 개념 학습**
   - 토큰화만? → `git checkout step-01`
   - 생성 알고리즘만? → `git checkout step-03`
   - 전체 서빙 구조? → `git checkout step-05`

3. **코드 비교**
   ```bash
   # Step 간 변경 사항 확인
   git diff step-00 step-01
   git diff step-02 step-03
   ```

---

## 기술 스택

- **언어**: Java 17
- **빌드**: Gradle 8.5
- **테스트**: JUnit 5
- **서버**: Spring Boot 3.2.0 (Step 5)
- **CLI**: picocli 4.7.5 (Step 6)
- **JSON**: Gson 2.10.1
- **HTTP 클라이언트**: OkHttp 4.12.0 (Step 6)

---

## 디렉토리 구조

```
mini-ai/
├── mission.md              # 전체 미션 정의
├── ANALYSIS.md             # 미션 분석
├── README.md               # 이 문서
├── docs/
│   ├── STEP-00.md         # Step별 문서
│   ├── STEP-01.md
│   └── demo/              # 실행 로그
│       ├── STEP-00.log
│       └── STEP-01.log
├── mini-ai-core/          # 인터페이스/DTO
├── mini-ai-tokenizer-simple/
├── mini-ai-model-ngram/
├── mini-ai-server/
└── mini-ai-cli/
```

---

## 핵심 원칙

### 1. 과정 중심
- 완성품이 아니라 **"어떻게 만들어지는가"**
- 각 Step마다 **"왜 이렇게 했는지"** 기록

### 2. 점진적 구현
- 한 번에 완성하지 않음
- 하나의 개념씩 추가

### 3. 재현 가능
- 모든 명령어는 문서에 기록
- seed 옵션으로 결과 재현 가능
- Git 태그로 언제든 특정 시점으로 이동

### 4. 실전 구조
- 실제 LLM 서비스와 유사한 설계
- Usage, latency, API 구조 등

---

## FAQ

### Q: Bigram만 구현하나요?
A: 네. MVP는 Bigram만 완전히 구현합니다. Trigram은 Step 7에서 "확장 포인트"만 확보합니다.

### Q: 왜 처음부터 완성하지 않나요?
A: 이 프로젝트는 **"학습 과정"** 이 목적입니다. 각 Step에서 하나의 개념만 집중하여 이해도를 높입니다.

### Q: 실제 사용 가능한 모델인가요?
A: Bigram은 성능이 낮지만, **"AI 시스템의 구조"** 를 이해하기에는 충분합니다. 중요한 건 "완성도"가 아니라 "학습 경험"입니다.

### Q: Step을 건너뛰어도 되나요?
A: 가능하지만, 순차적 학습을 권장합니다. 각 Step은 이전 Step의 개념 위에 쌓입니다.

---

## 🎓 교육 자료: 토큰 개념 실습

이 프로젝트는 `../doc/` 폴더의 교육 문서들과 연계되어 있습니다.

### 핵심 문서

- **`../doc/토큰을 모르면 AI는 늘 신기한 상자로 남아요.md`**
  - 토큰, n-gram, 다음 토큰 예측의 원리를 쉽게 설명
  - 왜 비용이 토큰 기준인지, 왜 대화가 길어지면 잊는지 등 실전 질문에 답변

### 실습 가이드

이 프로젝트는 문서의 개념들을 **직접 체험**할 수 있도록 설계되었습니다:

#### 🚀 빠른 시작 (5분)

```bash
# 토큰 개념을 빠르게 체험하는 스크립트
./examples/빠른시작-토큰체험.sh
```

이 스크립트는 다음을 자동으로 시연합니다:
- ✅ 토큰화 (텍스트를 조각으로 나누기)
- ✅ 토큰 수와 비용의 관계
- ✅ Bigram 학습 (자주 붙는 쌍을 세기)
- ✅ 다음 토큰 예측 (문장 생성)
- ✅ Seed와 재현성
- ✅ Usage 측정
- ✅ Temperature 효과

#### 📖 상세 실습 가이드

```bash
# 단계별 실습 가이드 (권장)
cat examples/토큰-개념-실습.md
```

다음 개념들을 실습합니다:
1. **토큰화**: 왜 텍스트를 자르는가
2. **Bigram 학습**: "세기"의 의미
3. **다음 토큰 예측**: 문장이 만들어지는 과정
4. **Usage (비용)**: 왜 토큰 수가 중요한가
5. **확률적 선택**: 같은 질문에 다른 답이 나오는 이유
6. **Bigram의 한계**: 짧은 문맥의 문제
7. **희소성 문제**: "처음 보는 문맥"을 어떻게 다룰까

### 교육용 데이터셋

프로젝트는 개념 이해를 돕는 특별한 데이터셋을 포함합니다:

- **`data/교육용-토큰개념.txt`**
  - Bigram의 확률적 선택을 보여주는 구조
  - "오늘은" → "날씨가" / "기분이" (둘 다 가능)
  - 토큰 카운팅과 확률 계산 체험

- **`data/교육용-문맥차이.txt`**
  - 한국어의 조사/어미 패턴
  - Bigram vs Trigram 차이 체험
  - "할 수 있다", "하고 싶다" 등 복합 표현

### 학습 흐름 권장

```
1. ../doc/ 문서 읽기
   ↓
2. ./examples/빠른시작-토큰체험.sh 실행
   ↓
3. examples/토큰-개념-실습.md 따라하기
   ↓
4. Step별 코드 이해 (docs/STEP-XX.md)
   ↓
5. 직접 커스텀 데이터로 실험
```

### 문서와 코드의 연결

| 문서 개념 | 이 프로젝트에서 체험 |
|----------|-------------------|
| 토큰화 | `mini-ai-tokenizer-simple` |
| Bigram 학습 (세기) | `BigramTrainer.java` |
| 다음 토큰 예측 | `BigramModel.java` |
| 확률적 선택 | `Sampler.java` (topK, temperature) |
| Usage (비용) | `GenerateResponse.usage` |
| 희소성 문제 | 교육용 데이터로 직접 확인 |

---

## 라이선스

MIT License (또는 원하시는 라이선스)

---

## 다음 단계

**현재 위치**: Step 0 완료 ✅

**다음**: Step 1 - Tokenizer 만들기

```bash
# Step 1 태그로 이동 (준비되면)
git checkout step-01

# 또는 직접 구현 시작
# docs/STEP-01.md 참고
```

---

**이 저장소는 과정 중심이며, Step을 따라가면 됩니다.**
