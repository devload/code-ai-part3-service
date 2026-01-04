# Part 3: AI 서비스 통합

Part 1에서 배운 AI 원리와 Part 2에서 배운 데이터 분석을 조합해 실제 서비스를 만듭니다. 코드 리뷰를 예시로 사용하지만, 같은 패턴으로 문서 분석, 번역, 챗봇 등 다양한 AI 서비스를 구축할 수 있습니다.

---

## 학습 목차

| STEP | 제목 | 핵심 개념 |
|------|------|----------|
| [13](STEP-13.md) | API 호출 | Claude, OpenAI, Ollama |
| [14](STEP-14.md) | 프롬프트 구성 | Few-shot, Zero-shot, CoT |
| [15](STEP-15.md) | 모델 라우팅 | 모델 선택, 비용 관리, Context Window |
| [16](STEP-16.md) | 응답 파싱 | JSON, 정규표현식, Streaming |
| [17](STEP-17.md) | 액션 실행 | 자동 실행, 안전장치, Function Calling |
| [18](STEP-18.md) | 피드백 루프 | 검증, 반복 개선, Hallucination 대응 |

---

## 전체 흐름

```
API → 프롬프트 → LLM → 파싱 → 액션 → 피드백
```

```
[분석 결과 + 데이터]
     ↓
[STEP 13] API 호출: AI에게 요청 (Streaming 포함)
     ↓
[STEP 14] 프롬프트: Few-shot, CoT로 효과적으로 질문
     ↓
[STEP 15] 모델 라우팅: 적합한 모델 선택, Context Window 관리
     ↓
[STEP 16] 응답 파싱: 답변에서 정보 추출, Caching
     ↓
[STEP 17] 액션 실행: Function Calling, 안전하게 실행
     ↓
[STEP 18] 피드백: Hallucination 대응, 검증 및 반복
     ↓
[완성된 AI 서비스]
```

---

## 핵심 키워드

`API` `프롬프트` `Few-shot` `Chain-of-Thought` `모델 라우팅` `Context Window` `Streaming` `Function Calling` `Hallucination` `피드백 루프`

---

## 학습 후 할 수 있는 것

- Claude/OpenAI/Ollama API 호출 (Streaming 포함)
- Few-shot, Chain-of-Thought 프롬프트 설계
- 작업에 맞는 모델 선택과 비용 최적화
- AI 응답 파싱 및 Caching 적용
- Function Calling으로 AI가 도구 사용하게 하기
- Hallucination 대응 및 피드백 루프로 품질 보장

---

## 선수 학습

- [Part 1: AI 모델의 원리](https://github.com/devload/code-ai-part1-basics)
- [Part 2: 도메인 데이터 분석](https://github.com/devload/code-ai-part2-analyzer)

## 다음 단계

- [Part 4: 고급 주제](https://github.com/devload/code-ai-part4-advanced)

---

## 완성!

Part 1, 2, 3을 모두 완료하면 **AI 기반 서비스**를 직접 만들 수 있습니다.

코드 리뷰는 예시일 뿐, 같은 패턴으로 여러분만의 AI 서비스를 구축해보세요.
