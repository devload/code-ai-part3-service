# STEP-14: IDE 플러그인

## 목표
IntelliJ IDEA와 VS Code용 플러그인을 개발하여 IDE에서 직접 AI 코드 리뷰를 실행합니다.

## IntelliJ IDEA 플러그인

### 프로젝트 구조
```
code-ai-intellij/
├── build.gradle                    # IntelliJ Plugin Gradle 설정
└── src/main/
    ├── java/com/codeai/intellij/
    │   ├── action/
    │   │   ├── AIReviewAction.java     # AI 리뷰 액션
    │   │   ├── ASTReviewAction.java    # AST 리뷰 액션
    │   │   └── QuickScoreAction.java   # 빠른 점수 확인
    │   ├── service/
    │   │   └── CodeAIService.java      # 결과 관리 서비스
    │   └── toolwindow/
    │       ├── CodeAIToolWindowFactory.java
    │       └── CodeAIToolWindowPanel.java
    └── resources/
        ├── META-INF/
        │   └── plugin.xml              # 플러그인 설정
        └── icons/
            ├── codeai.svg
            └── review.svg
```

### build.gradle
```gradle
plugins {
    id 'java'
    id 'org.jetbrains.intellij' version '1.17.2'
}

dependencies {
    implementation project(':code-ai-analyzer')
}

intellij {
    version = '2024.1'
    type = 'IC'  // Community Edition
    plugins = ['java']
}
```

### plugin.xml
```xml
<idea-plugin>
    <id>com.codeai.intellij</id>
    <name>Code AI Review</name>

    <depends>com.intellij.modules.platform</depends>
    <depends>com.intellij.modules.java</depends>

    <extensions defaultExtensionNs="com.intellij">
        <!-- 서비스 등록 -->
        <projectService
            serviceImplementation="com.codeai.intellij.service.CodeAIService"/>

        <!-- Tool Window -->
        <toolWindow id="Code AI Review"
                    anchor="bottom"
                    factoryClass="com.codeai.intellij.toolwindow.CodeAIToolWindowFactory"/>

        <!-- 알림 그룹 -->
        <notificationGroup id="Code AI Notifications"
                          displayType="BALLOON"/>
    </extensions>

    <actions>
        <group id="CodeAI.ActionGroup" text="Code AI" popup="true">
            <add-to-group group-id="ToolsMenu"/>
            <add-to-group group-id="EditorPopupMenu"/>

            <action id="CodeAI.AIReview"
                    class="com.codeai.intellij.action.AIReviewAction"
                    text="AI Review">
                <keyboard-shortcut keymap="$default" first-keystroke="ctrl alt R"/>
            </action>
        </group>
    </actions>
</idea-plugin>
```

### 주요 기능

#### 1. AI Review Action
```java
public class AIReviewAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        // 백그라운드에서 분석 실행
        ProgressManager.getInstance().run(
            new Task.Backgroundable(project, "AI 코드 리뷰 중...", true) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    AICodeReviewer reviewer = new AICodeReviewer();
                    result = reviewer.review(code);
                }

                @Override
                public void onSuccess() {
                    // Tool Window에 결과 표시
                    showResultInToolWindow(project, fileName, result);
                }
            });
    }
}
```

#### 2. Quick Score (팝업)
```java
// 에디터 상단에 점수 팝업 표시
JBPopupFactory.getInstance()
    .createHtmlTextBalloonBuilder(html, null, bgColor, null)
    .setFadeoutTime(5000)
    .createBalloon()
    .show(RelativePoint.getNorthEastOf(editor.getComponent()),
          Balloon.Position.above);
```

#### 3. Tool Window
```java
public class CodeAIToolWindowPanel extends JPanel {
    // 점수 바 표시
    private JPanel createScoreBar(String label, int score) {
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue(score);
        bar.setForeground(getScoreColor(score));
        return panel;
    }

    // 코멘트 패널
    private JPanel createCommentPanel(ReviewComment comment) {
        // 타입별 색상 구분
        panel.setBorder(BorderFactory.createMatteBorder(
            0, 3, 0, 0, getTypeColor(comment.type)));
        return panel;
    }
}
```

### 단축키
| 단축키 | 기능 |
|--------|------|
| `Ctrl+Alt+R` | AI Review 실행 |
| `Ctrl+Alt+S` | Quick Score 표시 |

### 빌드 및 실행
```bash
# 플러그인 빌드
./gradlew :code-ai-intellij:buildPlugin

# IDE에서 테스트 실행
./gradlew :code-ai-intellij:runIde
```

## VS Code 확장

### 프로젝트 구조
```
code-ai-vscode/
├── package.json        # 확장 설정
├── tsconfig.json       # TypeScript 설정
└── src/
    └── extension.ts    # 메인 확장 코드
```

### package.json
```json
{
  "name": "code-ai-review",
  "displayName": "Code AI Review",
  "activationEvents": ["onLanguage:java"],
  "contributes": {
    "commands": [
      {
        "command": "codeai.aiReview",
        "title": "AI Review",
        "category": "Code AI"
      }
    ],
    "keybindings": [
      {
        "command": "codeai.aiReview",
        "key": "ctrl+alt+r",
        "when": "editorTextFocus && editorLangId == java"
      }
    ]
  }
}
```

### 주요 기능

#### 1. AI Review 명령
```typescript
let aiReviewCommand = vscode.commands.registerCommand(
    'codeai.aiReview',
    async () => {
        await vscode.window.withProgress({
            location: vscode.ProgressLocation.Notification,
            title: 'AI 코드 리뷰 중...'
        }, async (progress) => {
            const result = await callAIReviewAPI(code);
            displayReviewResult(document.uri, fileName, result);
        });
    }
);
```

#### 2. 진단 정보 표시
```typescript
// 에디터에 문제 표시 (밑줄)
function updateDiagnostics(uri: vscode.Uri, comments: ReviewComment[]) {
    const diagnostics = comments.map(comment => {
        const range = new vscode.Range(
            new vscode.Position(comment.line - 1, 0),
            new vscode.Position(comment.line - 1, 1000)
        );
        return new vscode.Diagnostic(range, comment.message, severity);
    });
    diagnosticCollection.set(uri, diagnostics);
}
```

#### 3. 상태 바 점수 표시
```typescript
const statusBarItem = vscode.window.createStatusBarItem(
    vscode.StatusBarAlignment.Right, 100
);
statusBarItem.text = `🌟 A (95/100)`;
statusBarItem.tooltip = `구조: 90\n가독성: 95\n...`;
statusBarItem.show();
```

### 빌드 및 실행
```bash
cd code-ai-vscode

# 의존성 설치
npm install

# 컴파일
npm run compile

# VS Code에서 테스트 (F5로 디버그)
```

## UI 미리보기

### IntelliJ Tool Window
```
┌─────────────────────────────────────────────────────────┐
│ AI Review: UserService.java                          B │
├─────────────────────────────────────────────────────────┤
│ 📊 코드 품질 점수                                       │
│   구조        [████████░░] 85/100                      │
│   가독성      [█████████░] 90/100                      │
│   유지보수성  [███████░░░] 75/100                      │
│   신뢰성      [████████░░] 80/100                      │
│   보안        [███████░░░] 70/100                      │
│   성능        [████████░░] 85/100                      │
│   ────────────────────────────                         │
│   종합 점수: 80/100                                    │
├─────────────────────────────────────────────────────────┤
│ 📝 리뷰 코멘트: 5개                                     │
│ ─────────────────────────────────────────────────────  │
│ │ 👍 Line 1: 메서드들이 짧고 집중되어 있어요.           │
│ ─────────────────────────────────────────────────────  │
│ │ 💡 Line 45: 이 메서드가 35줄로 꽤 길어요...          │
│ │                                                      │
│ │   // After:                                          │
│ │   public void processUser() {                        │
│ │       validateInput();                               │
│ │       processData();                                 │
│ │   }                                                  │
│ ─────────────────────────────────────────────────────  │
└─────────────────────────────────────────────────────────┘
```

### VS Code 출력 채널
```
============================================================
🤖 AI 코드 리뷰 결과: UserService.java
============================================================

📊 코드 품질 점수:
   구조:        85/100
   가독성:      90/100
   유지보수성:  75/100
   신뢰성:      80/100
   보안:        70/100
   성능:        85/100
   ──────────────────────
   종합:        80/100  등급: B

📝 리뷰 코멘트: 3개
------------------------------------------------------------
💡 Line 25: 프로덕션 코드에서 System.out.println 대신 로깅 프레임워크를 사용하는 게 좋아요.

⚠️ Line 42: catch 블록이 비어 있어요. 최소한 로그라도 남기는 게 좋아요.

💡 Line 78: TODO 주석이 있네요. 기술 부채 관리가 필요해요.
```

## 파일 구조
```
code-ai/
├── code-ai-intellij/           # IntelliJ 플러그인
│   ├── build.gradle
│   └── src/main/
│       ├── java/com/codeai/intellij/
│       │   ├── action/
│       │   ├── service/
│       │   └── toolwindow/
│       └── resources/
│           ├── META-INF/plugin.xml
│           └── icons/
│
└── code-ai-vscode/             # VS Code 확장
    ├── package.json
    ├── tsconfig.json
    └── src/extension.ts
```

## CLI 버전: v8.0
```bash
# 사용 가능한 명령어
code-ai train          # 모델 학습
code-ai complete       # 코드 자동완성
code-ai review         # 정규식 기반 리뷰
code-ai ast-review     # AST 기반 리뷰
code-ai project-review # 프로젝트 분석
code-ai type-check     # 타입 분석
code-ai ai-review      # AI 코드 리뷰
```

## 다음 단계
- STEP-15: CI/CD 통합 (GitHub Actions)
- STEP-16: LLM 연동 (Claude/GPT API)
- STEP-17: 코드 자동 수정 (Auto-fix)
