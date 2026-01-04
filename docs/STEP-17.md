# STEP 17: AI가 직접 코드를 고쳐준다면? - 액션 실행

> AI가 "2번째 줄에 비밀번호가 하드코딩되어 있어요. 환경변수를 쓰세요"라고 했어요.
> 보여주기만 하면 아쉬워요. **직접 고쳐주면** 어떨까요?

---

## 액션이 뭔데?

AI의 제안을 **실제로 실행**하는 거예요:

```
AI 응답 → 파싱 → 액션 결정 → 실행!

"2번째 줄 수정하세요"  →  파일 열기 → 2번째 줄 교체 → 저장
```

---

## 액션의 종류

| 액션 | 설명 | 위험도 |
|------|------|--------|
| `EDIT_CODE` | 코드 수정 | 중간 |
| `CREATE_FILE` | 새 파일 생성 | 낮음 |
| `DELETE_FILE` | 파일 삭제 | **높음** |
| `RUN_COMMAND` | 명령 실행 (테스트 등) | **높음** |
| `REPORT` | 리포트 생성 | 낮음 |

위험한 액션은 조심해야 해요!

---

## 코드 수정하기

AI가 이렇게 제안했다고 해봐요:

```json
{
  "line": 2,
  "originalCode": "private String password = \"admin123\";",
  "fixedCode": "private String password = System.getenv(\"DB_PASSWORD\");"
}
```

이걸 실제로 적용하는 코드:

```java
public class ActionExecutor {
    private final Path workingDirectory;

    public ActionOutcome executeEditCode(Action action) throws IOException {
        Path filePath = action.getFilePath();

        // 1. 파일 읽기
        String content = Files.readString(filePath);
        String[] lines = content.split("\n");

        // 2. 해당 라인 수정
        int lineNum = action.getLineNumber();
        if (lineNum > 0 && lineNum <= lines.length) {
            String originalLine = lines[lineNum - 1];
            lines[lineNum - 1] = action.getFixedCode();

            // 3. 백업 생성 (안전!)
            Path backupPath = createBackup(filePath);

            // 4. 파일 저장
            Files.writeString(filePath, String.join("\n", lines));

            return ActionOutcome.success(action,
                "Line " + lineNum + " 수정됨. 백업: " + backupPath);
        }

        return ActionOutcome.failed(action, "유효하지 않은 라인 번호");
    }
}
```

---

## 백업은 필수!

코드를 수정하기 전에 **항상 백업**해야 해요:

```java
private Path createBackup(Path originalPath) throws IOException {
    Path backupDir = workingDirectory.resolve(".backups");
    Files.createDirectories(backupDir);

    String timestamp = String.valueOf(System.currentTimeMillis());
    Path backupPath = backupDir.resolve(
        originalPath.getFileName() + "." + timestamp + ".bak"
    );

    Files.copy(originalPath, backupPath);
    return backupPath;
}
```

뭔가 잘못되면 백업에서 복원할 수 있어요:

```
.backups/
├── Example.java.1704123456789.bak
├── Example.java.1704123456790.bak
└── ...
```

---

## 안전 장치

AI가 시키는 대로 다 하면 위험해요! **안전 장치**가 필요해요:

### 1. 안전 모드

```java
public class ActionExecutor {
    private boolean safeMode = true;  // 기본값: 켜짐

    public ActionOutcome executeAction(Action action) {
        // 안전 모드에서 위험한 액션 차단
        if (safeMode) {
            if (action.getType() == ActionType.DELETE_FILE ||
                action.getType() == ActionType.RUN_COMMAND) {
                return ActionOutcome.blocked(action, "안전 모드에서 차단됨");
            }
        }

        // ... 실행
    }
}
```

### 2. 경로 제한

```java
private final Set<Path> allowedPaths = new HashSet<>();

private boolean isActionAllowed(Action action) {
    if (action.getFilePath() != null) {
        Path normalized = action.getFilePath().toAbsolutePath().normalize();

        // 허용된 경로 안에 있는지 확인
        boolean inAllowedPath = allowedPaths.stream()
            .anyMatch(allowed -> normalized.startsWith(allowed));

        if (!inAllowedPath) {
            return false;  // 허용 안 된 경로!
        }
    }
    return true;
}
```

`/etc/passwd` 같은 시스템 파일을 수정하려고 하면 차단!

### 3. 명령 허용 목록

```java
private boolean isCommandAllowed(String command) {
    List<String> allowedCommands = List.of(
        "gradle", "mvn", "npm", "pytest", "go test"
    );

    return allowedCommands.stream()
        .anyMatch(cmd -> command.startsWith(cmd));
}
```

`rm -rf /`는 절대 실행 안 돼요!

---

## 실제 적용 예시

AI가 이런 문제를 찾았어요:

```java
public class Example {
    private String password = "admin123";

    public void process(String input) {
        String sql = "SELECT * FROM users WHERE id = '" + input + "'";
    }
}
```

AI 응답:

```json
{
  "issues": [
    {
      "line": 2,
      "fixedCode": "private String password = System.getenv(\"DB_PASSWORD\");"
    },
    {
      "line": 5,
      "fixedCode": "String sql = \"SELECT * FROM users WHERE id = ?\";"
    }
  ]
}
```

적용 결과:

```
=== 자동 수정 적용 ===

[SUCCESS] Line 2 수정됨. 백업: .backups/Example.java.1704123456789.bak
[SUCCESS] Line 5 수정됨. 백업: .backups/Example.java.1704123456790.bak

=== 수정된 코드 ===
public class Example {
    private String password = System.getenv("DB_PASSWORD");

    public void process(String input) {
        String sql = "SELECT * FROM users WHERE id = ?";
    }
}
```

자동으로 고쳐졌어요! 🎉

---

## 실행 취소 (Undo)

실수로 잘못 수정했으면?

```java
public ActionOutcome undoLastAction() {
    if (executedActions.isEmpty()) {
        return ActionOutcome.failed(null, "취소할 액션 없음");
    }

    ExecutedAction last = executedActions.remove(executedActions.size() - 1);

    // 백업에서 복원
    Path backupPath = last.backupPath;
    Path originalPath = last.action.getFilePath();

    Files.copy(backupPath, originalPath, StandardCopyOption.REPLACE_EXISTING);

    return ActionOutcome.success(last.action, "복원됨");
}
```

백업이 있으니까 언제든 되돌릴 수 있어요.

---

## 액션 실행 결과

```java
public class ActionOutcome {
    public final Action action;
    public final Status status;
    public final String message;

    public enum Status {
        SUCCESS,    // 성공
        FAILED,     // 실패
        BLOCKED     // 안전 장치에 의해 차단
    }

    public static ActionOutcome success(Action action, String message) {
        return new ActionOutcome(action, Status.SUCCESS, message);
    }

    public static ActionOutcome blocked(Action action, String message) {
        return new ActionOutcome(action, Status.BLOCKED, message);
    }
}
```

---

## 안전 요약

```
┌────────────────────────────────────────────────────────┐
│                    안전 장치                            │
├────────────────────────────────────────────────────────┤
│ 1. 안전 모드      → 위험 액션(삭제, 명령) 차단         │
│ 2. 경로 제한      → 허용된 폴더만 접근                 │
│ 3. 명령 허용목록  → gradle, npm 등만 실행              │
│ 4. 자동 백업      → 모든 수정 전 백업 생성             │
│ 5. 실행 취소      → 언제든 되돌리기 가능               │
└────────────────────────────────────────────────────────┘
```

---

## 핵심 정리

1. **액션 = AI 제안 실행** → 코드 수정, 파일 생성 등
2. **안전 장치 필수** → 삭제/명령 차단, 경로 제한
3. **백업은 생명** → 수정 전 항상 백업
4. **Undo 가능** → 실수해도 복원 가능

---

## 다음 시간 예고

수정은 했어요. 근데 **제대로 된 건지** 어떻게 알까요?

- 수정 후에 테스트가 통과하나?
- 새로운 문제가 생기진 않았나?
- 점수가 올랐나?

다음 STEP에서는 **수정 결과를 검증하고 개선하는 피드백 루프**를 알아볼게요!

---

## 실습

```bash
cd code-ai-part3-service
../gradlew :step17-action:run
```

AI의 제안을 직접 적용해보고, 코드가 어떻게 바뀌는지 확인해보세요!
