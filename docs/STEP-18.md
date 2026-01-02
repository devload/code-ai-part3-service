# STEP-18: 웹 대시보드

## 목표
Spring Boot 기반 웹 대시보드를 구현하여 코드 분석 결과를 시각화하고 실시간 리뷰 기능을 제공합니다.

## 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│                    Code AI Web Dashboard                     │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                   Frontend (SPA)                     │   │
│  │  • Dashboard - 메인 대시보드                         │   │
│  │  • Analyze - 코드 분석 페이지                        │   │
│  │  • History - 분석 기록                               │   │
│  │  • Settings - 설정                                   │   │
│  └─────────────────────────────────────────────────────┘   │
│                          │                                  │
│                    REST API / WebSocket                     │
│                          │                                  │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              Spring Boot Backend                      │   │
│  │                                                       │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  │   │
│  │  │AnalysisAPI │  │ WebSocket  │  │ Dashboard   │  │   │
│  │  │ Controller │  │ Controller │  │ Controller  │  │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  │   │
│  │                          │                           │   │
│  │  ┌─────────────────────────────────────────────┐    │   │
│  │  │            AnalysisService                   │    │   │
│  │  │  • AICodeReviewer                            │    │   │
│  │  │  • ASTAnalyzer                               │    │   │
│  │  │  • CodeScorer                                │    │   │
│  │  │  • LLMCodeReviewer                           │    │   │
│  │  │  • AutoFixer                                 │    │   │
│  │  └─────────────────────────────────────────────┘    │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## 구현 내용

### 1. 프로젝트 구조

```
code-ai-web/
├── build.gradle
├── src/main/java/com/codeai/web/
│   ├── CodeAIWebApplication.java    # 메인 클래스
│   ├── config/
│   │   └── WebSocketConfig.java     # WebSocket 설정
│   ├── controller/
│   │   ├── AnalysisController.java  # REST API
│   │   ├── DashboardController.java # 페이지 라우팅
│   │   └── WebSocketController.java # 실시간 통신
│   ├── service/
│   │   └── AnalysisService.java     # 분석 서비스
│   └── model/
│       ├── AnalysisRequest.java     # 요청 모델
│       └── AnalysisResponse.java    # 응답 모델
└── src/main/resources/
    ├── application.yml              # 설정
    ├── static/
    │   ├── css/style.css            # 스타일
    │   └── js/app.js                # JavaScript
    └── templates/
        ├── dashboard.html           # 대시보드
        ├── analyze.html             # 분석 페이지
        ├── history.html             # 기록 페이지
        └── settings.html            # 설정 페이지
```

### 2. REST API

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/v1/analyze` | 코드 분석 (JSON) |
| POST | `/api/v1/analyze/file` | 파일 업로드 분석 |
| POST | `/api/v1/score` | 빠른 점수 계산 |
| GET | `/api/v1/analysis/{id}` | 분석 결과 조회 |
| GET | `/api/v1/analyses` | 최근 분석 목록 |
| GET | `/api/v1/health` | 헬스 체크 |
| GET | `/api/v1/info` | API 정보 |

### 3. WebSocket 엔드포인트

| 목적지 | 구독 토픽 | 설명 |
|--------|-----------|------|
| `/app/analyze` | `/topic/analysis` | 실시간 코드 분석 |
| `/app/score` | `/topic/score` | 실시간 점수 계산 |
| `/app/analyze-llm` | `/topic/llm-result` | 비동기 LLM 분석 |
| - | `/topic/status` | 상태 업데이트 |

## 사용법

### 서버 실행

```bash
# Gradle로 실행
cd code-ai-web
../gradlew bootRun

# JAR 빌드 후 실행
../gradlew bootJar
java -jar build/libs/code-ai-web.jar
```

### 접속
- 대시보드: http://localhost:8080/dashboard
- 분석 페이지: http://localhost:8080/analyze
- API 정보: http://localhost:8080/api/v1/info

## API 사용 예시

### 코드 분석

```bash
curl -X POST http://localhost:8080/api/v1/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "code": "public class Example { public void test() { System.out.println(\"Hello\"); } }",
    "filename": "Example.java",
    "options": {
      "includeAST": true,
      "includeLLM": false,
      "autoFix": false
    }
  }'
```

### 파일 업로드 분석

```bash
curl -X POST http://localhost:8080/api/v1/analyze/file \
  -F "file=@src/MyClass.java" \
  -F "includeAST=true" \
  -F "includeLLM=true" \
  -F "provider=claude"
```

### 빠른 점수 계산

```bash
curl -X POST http://localhost:8080/api/v1/score \
  -H "Content-Type: application/json" \
  -d '{"code": "public class Test { }"}'
```

## 응답 형식

### AnalysisResponse

```json
{
  "id": "abc12345",
  "filename": "Example.java",
  "timestamp": "2024-01-15T10:30:00",
  "scores": {
    "overall": 85,
    "grade": "B",
    "categories": {
      "가독성": 90,
      "유지보수성": 85,
      "보안": 80,
      "성능": 85
    }
  },
  "issues": [
    {
      "code": "SYSTEM_OUT",
      "severity": "WARNING",
      "line": 5,
      "column": 9,
      "message": "System.out 대신 Logger를 사용하세요",
      "suggestion": "private static final Logger logger = LoggerFactory.getLogger(Example.class);",
      "category": "best-practice"
    }
  ],
  "positives": [
    "메서드가 단일 책임을 잘 따르고 있습니다",
    "클래스 구조가 명확합니다"
  ],
  "statistics": {
    "totalLines": 25,
    "codeLines": 20,
    "commentLines": 3,
    "methods": 5,
    "classes": 1,
    "complexity": 8
  },
  "llmInfo": {
    "provider": "claude",
    "model": "claude-3-5-sonnet-20241022",
    "tokens": 1247,
    "latencyMs": 2500
  },
  "fixedCode": null
}
```

## 대시보드 기능

### 1. 메인 대시보드
- 코드 품질 점수 원형 표시
- 카테고리별 점수 막대 그래프
- 발견된 이슈 목록
- 긍정적 피드백 표시
- 코드 통계 (라인 수, 메서드 수, 복잡도)

### 2. 코드 분석 페이지
- 코드 에디터 (구문 강조)
- 파일 드래그 앤 드롭 업로드
- LLM 분석 옵션 선택
- 자동 수정 기능
- 수정된 코드 미리보기

### 3. 분석 기록 페이지
- 최근 분석 목록
- 파일명, 등급, 점수, 이슈 수, 시간 표시
- 상세 결과 조회

### 4. 설정 페이지
- LLM 제공자 선택 (Claude, OpenAI, Ollama)
- API 키 설정
- 분석 옵션 설정
- UI 테마 설정

## WebSocket 실시간 기능

### JavaScript 클라이언트

```javascript
// WebSocket 연결
const socket = new SockJS('/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, () => {
    // 분석 결과 구독
    stompClient.subscribe('/topic/analysis', (message) => {
        const result = JSON.parse(message.body);
        updateUI(result);
    });

    // 실시간 점수 구독
    stompClient.subscribe('/topic/score', (message) => {
        const score = JSON.parse(message.body);
        updateScore(score);
    });

    // 상태 업데이트 구독
    stompClient.subscribe('/topic/status', (message) => {
        const status = JSON.parse(message.body);
        showStatus(status.message);
    });
});

// 코드 분석 요청
function analyzeCode(code) {
    stompClient.send('/app/analyze', {}, JSON.stringify({
        code: code,
        options: { includeAST: true }
    }));
}

// 실시간 점수 계산
function quickScore(code) {
    stompClient.send('/app/score', {}, JSON.stringify({ code }));
}
```

## 설정

### application.yml

```yaml
server:
  port: 8080

spring:
  application:
    name: code-ai-web
  thymeleaf:
    cache: false
  servlet:
    multipart:
      max-file-size: 10MB

codeai:
  llm:
    default-provider: claude
    timeout: 60000
  analysis:
    max-file-size: 1048576
    supported-extensions:
      - .java
      - .kt
      - .scala
```

## 스크린샷

### 대시보드
```
┌────────────────────────────────────────────────────────────┐
│  Code AI                                       준비됨       │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐                      │
│  │  25  │ │  20  │ │   5  │ │   8  │                      │
│  │ 라인 │ │ 코드 │ │메서드│ │복잡도│                      │
│  └──────┘ └──────┘ └──────┘ └──────┘                      │
│                                                            │
│  ┌─────────────────────┐  ┌─────────────────────┐         │
│  │   코드 입력          │  │      점수            │         │
│  │  ┌───────────────┐  │  │    ┌───────┐        │         │
│  │  │               │  │  │    │  85   │        │         │
│  │  │ public class  │  │  │    │   B   │        │         │
│  │  │ Example {...} │  │  │    └───────┘        │         │
│  │  │               │  │  │                     │         │
│  │  └───────────────┘  │  │ 가독성      ████ 90 │         │
│  │  [분석] [LLM 분석]   │  │ 유지보수    ███░ 85 │         │
│  └─────────────────────┘  │ 보안        ███░ 80 │         │
│                           └─────────────────────┘         │
│  ┌─────────────────────┐  ┌─────────────────────┐         │
│  │   발견된 이슈       │  │   긍정적 피드백      │         │
│  │ ⚠ System.out 사용   │  │ ✓ 단일 책임 준수     │         │
│  │ ⚠ 매직 넘버 발견    │  │ ✓ 명확한 구조        │         │
│  └─────────────────────┘  └─────────────────────┘         │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

## 의존성

```gradle
dependencies {
    // Spring Boot
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
    implementation 'org.springframework.boot:spring-boot-starter-websocket'
    implementation 'org.springframework.boot:spring-boot-starter-validation'

    // Code AI Analyzer
    implementation project(':code-ai-analyzer')

    // JSON
    implementation 'com.google.code.gson:gson:2.10.1'
}
```

## 다음 단계

- STEP-19: 팀 협업 기능
- STEP-20: 지속적 학습 시스템
- STEP-21: 성능 최적화
