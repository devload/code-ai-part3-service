package com.aiprocess.step17;

import java.util.*;
import java.util.regex.*;

/**
 * STEP 17: 액션 실행
 *
 * 핵심 질문: AI가 도구를 어떻게 사용하는가?
 *
 * AI의 분석 결과를 실제 코드 수정으로 변환합니다.
 *
 * ┌─────────────────────────────────────────────────────────┐
 * │ 액션 실행 과정                                           │
 * │                                                          │
 * │ 1. 액션 정의                                             │
 * │    - 지원하는 액션 목록                                  │
 * │    - 각 액션의 파라미터                                  │
 * │                                                          │
 * │ 2. 액션 파싱                                             │
 * │    - AI 응답에서 액션 추출                               │
 * │    - 파라미터 검증                                       │
 * │                                                          │
 * │ 3. 액션 실행                                             │
 * │    - 코드 수정                                           │
 * │    - 파일 생성/삭제                                      │
 * │    - 명령어 실행                                         │
 * │                                                          │
 * │ 4. 결과 확인                                             │
 * │    - 성공/실패 보고                                      │
 * │    - 롤백 지원                                           │
 * └─────────────────────────────────────────────────────────┘
 */
public class ActionExecutor {

    private final Map<ActionType, ActionHandler> handlers;
    private final List<ExecutedAction> history;

    public ActionExecutor() {
        this.handlers = new EnumMap<>(ActionType.class);
        this.history = new ArrayList<>();
        registerDefaultHandlers();
    }

    private void registerDefaultHandlers() {
        handlers.put(ActionType.REPLACE_CODE, this::handleReplaceCode);
        handlers.put(ActionType.INSERT_CODE, this::handleInsertCode);
        handlers.put(ActionType.DELETE_CODE, this::handleDeleteCode);
        handlers.put(ActionType.ADD_IMPORT, this::handleAddImport);
        handlers.put(ActionType.RENAME, this::handleRename);
        handlers.put(ActionType.EXTRACT_METHOD, this::handleExtractMethod);
        handlers.put(ActionType.ADD_LOGGING, this::handleAddLogging);
    }

    /**
     * 액션 실행
     */
    public ActionResult execute(Action action, CodeContext context) {
        ActionHandler handler = handlers.get(action.type());

        if (handler == null) {
            return ActionResult.failure("Unknown action type: " + action.type());
        }

        try {
            ActionResult result = handler.handle(action, context);

            // 성공한 액션 기록
            if (result.success()) {
                history.add(new ExecutedAction(
                    action,
                    context.originalCode(),
                    result.modifiedCode(),
                    System.currentTimeMillis()
                ));
            }

            return result;
        } catch (Exception e) {
            return ActionResult.failure("Action execution failed: " + e.getMessage());
        }
    }

    /**
     * 여러 액션 일괄 실행
     */
    public BatchResult executeBatch(List<Action> actions, String originalCode) {
        List<ActionResult> results = new ArrayList<>();
        String currentCode = originalCode;
        int successCount = 0;
        int failureCount = 0;

        for (Action action : actions) {
            CodeContext context = new CodeContext(currentCode, action.targetFile());
            ActionResult result = execute(action, context);

            results.add(result);

            if (result.success()) {
                currentCode = result.modifiedCode();
                successCount++;
            } else {
                failureCount++;
            }
        }

        return new BatchResult(results, currentCode, successCount, failureCount);
    }

    /**
     * AI 응답에서 액션 추출
     */
    public List<Action> parseActions(String aiResponse) {
        List<Action> actions = new ArrayList<>();

        // JSON 액션 블록 추출
        Pattern jsonPattern = Pattern.compile(
            "\\{\\s*\"action\"\\s*:\\s*\"([^\"]+)\"[^}]*\\}",
            Pattern.DOTALL
        );

        Matcher matcher = jsonPattern.matcher(aiResponse);
        while (matcher.find()) {
            String actionBlock = matcher.group(0);
            Action action = parseActionBlock(actionBlock);
            if (action != null) {
                actions.add(action);
            }
        }

        // 코드 블록에서 수정 제안 추출
        Pattern codePattern = Pattern.compile(
            "```(?:java|python)?\\s*\\n([\\s\\S]*?)```"
        );

        Matcher codeMatcher = codePattern.matcher(aiResponse);
        while (codeMatcher.find()) {
            String code = codeMatcher.group(1).trim();
            if (!code.isEmpty() && !containsAction(actions, code)) {
                actions.add(new Action(
                    ActionType.REPLACE_CODE,
                    Map.of("new_code", code),
                    null
                ));
            }
        }

        return actions;
    }

    private Action parseActionBlock(String block) {
        try {
            // 간단한 JSON 파싱 (실제로는 Gson 사용)
            ActionType type = null;
            Map<String, String> params = new HashMap<>();

            Pattern typePattern = Pattern.compile("\"action\"\\s*:\\s*\"([^\"]+)\"");
            Matcher typeMatcher = typePattern.matcher(block);
            if (typeMatcher.find()) {
                String actionName = typeMatcher.group(1).toUpperCase();
                try {
                    type = ActionType.valueOf(actionName);
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }

            // 파라미터 추출
            Pattern paramPattern = Pattern.compile("\"(\\w+)\"\\s*:\\s*\"([^\"]+)\"");
            Matcher paramMatcher = paramPattern.matcher(block);
            while (paramMatcher.find()) {
                String key = paramMatcher.group(1);
                String value = paramMatcher.group(2);
                if (!key.equals("action")) {
                    params.put(key, value);
                }
            }

            return type != null ? new Action(type, params, null) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean containsAction(List<Action> actions, String code) {
        return actions.stream()
            .anyMatch(a -> code.equals(a.params().get("new_code")));
    }

    // Action handlers

    private ActionResult handleReplaceCode(Action action, CodeContext context) {
        String oldCode = action.params().get("old_code");
        String newCode = action.params().get("new_code");

        if (newCode == null) {
            return ActionResult.failure("Missing new_code parameter");
        }

        String result;
        if (oldCode != null) {
            result = context.originalCode().replace(oldCode, newCode);
        } else {
            result = newCode;
        }

        return ActionResult.success(result, "Code replaced successfully");
    }

    private ActionResult handleInsertCode(Action action, CodeContext context) {
        String code = action.params().get("code");
        String position = action.params().getOrDefault("position", "end");
        int line = Integer.parseInt(action.params().getOrDefault("line", "0"));

        if (code == null) {
            return ActionResult.failure("Missing code parameter");
        }

        String[] lines = context.originalCode().split("\n");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            if (i == line - 1 && position.equals("before")) {
                result.append(code).append("\n");
            }
            result.append(lines[i]).append("\n");
            if (i == line - 1 && position.equals("after")) {
                result.append(code).append("\n");
            }
        }

        if (position.equals("end")) {
            result.append(code).append("\n");
        }

        return ActionResult.success(result.toString(), "Code inserted at line " + line);
    }

    private ActionResult handleDeleteCode(Action action, CodeContext context) {
        String target = action.params().get("target");

        if (target == null) {
            return ActionResult.failure("Missing target parameter");
        }

        String result = context.originalCode().replace(target, "");
        return ActionResult.success(result, "Code deleted");
    }

    private ActionResult handleAddImport(Action action, CodeContext context) {
        String importStatement = action.params().get("import");

        if (importStatement == null) {
            return ActionResult.failure("Missing import parameter");
        }

        String importLine = "import " + importStatement + ";\n";
        String code = context.originalCode();

        // package 문 다음에 import 추가
        Pattern packagePattern = Pattern.compile("(package [^;]+;\\n)");
        Matcher matcher = packagePattern.matcher(code);

        String result;
        if (matcher.find()) {
            result = code.substring(0, matcher.end()) + "\n" + importLine + code.substring(matcher.end());
        } else {
            result = importLine + code;
        }

        return ActionResult.success(result, "Import added: " + importStatement);
    }

    private ActionResult handleRename(Action action, CodeContext context) {
        String oldName = action.params().get("old_name");
        String newName = action.params().get("new_name");

        if (oldName == null || newName == null) {
            return ActionResult.failure("Missing old_name or new_name parameter");
        }

        // 단어 경계를 고려한 치환
        String pattern = "\\b" + Pattern.quote(oldName) + "\\b";
        String result = context.originalCode().replaceAll(pattern, newName);

        return ActionResult.success(result, "Renamed: " + oldName + " → " + newName);
    }

    private ActionResult handleExtractMethod(Action action, CodeContext context) {
        String code = action.params().get("code");
        String methodName = action.params().get("method_name");
        String parameters = action.params().getOrDefault("parameters", "");

        if (code == null || methodName == null) {
            return ActionResult.failure("Missing code or method_name parameter");
        }

        String methodCall = methodName + "(" + parameters + ");";
        String methodDef = String.format(
            "\n    private void %s(%s) {\n        %s\n    }\n",
            methodName, parameters, code.replace("\n", "\n        ")
        );

        String result = context.originalCode().replace(code, methodCall);

        // 클래스 끝 전에 메서드 추가
        int lastBrace = result.lastIndexOf("}");
        if (lastBrace > 0) {
            result = result.substring(0, lastBrace) + methodDef + result.substring(lastBrace);
        }

        return ActionResult.success(result, "Extracted method: " + methodName);
    }

    private ActionResult handleAddLogging(Action action, CodeContext context) {
        String target = action.params().get("target");
        String level = action.params().getOrDefault("level", "info");
        String message = action.params().getOrDefault("message", "");

        if (target == null) {
            return ActionResult.failure("Missing target parameter");
        }

        String logStatement = String.format(
            "logger.%s(\"%s\");",
            level, message.isEmpty() ? target : message
        );

        String result = context.originalCode().replace(
            target,
            logStatement + "\n        " + target
        );

        return ActionResult.success(result, "Added logging before: " + target);
    }

    /**
     * 액션 타입
     */
    public enum ActionType {
        REPLACE_CODE("코드 교체"),
        INSERT_CODE("코드 삽입"),
        DELETE_CODE("코드 삭제"),
        ADD_IMPORT("import 추가"),
        RENAME("이름 변경"),
        EXTRACT_METHOD("메서드 추출"),
        ADD_LOGGING("로깅 추가");

        private final String description;

        ActionType(String description) {
            this.description = description;
        }

        public String getDescription() { return description; }
    }

    /**
     * 액션 정의
     */
    public record Action(
        ActionType type,
        Map<String, String> params,
        String targetFile
    ) {}

    /**
     * 코드 컨텍스트
     */
    public record CodeContext(
        String originalCode,
        String filePath
    ) {}

    /**
     * 액션 결과
     */
    public record ActionResult(
        boolean success,
        String modifiedCode,
        String message
    ) {
        public static ActionResult success(String code, String message) {
            return new ActionResult(true, code, message);
        }

        public static ActionResult failure(String message) {
            return new ActionResult(false, null, message);
        }
    }

    /**
     * 배치 실행 결과
     */
    public record BatchResult(
        List<ActionResult> results,
        String finalCode,
        int successCount,
        int failureCount
    ) {}

    /**
     * 실행된 액션 기록
     */
    public record ExecutedAction(
        Action action,
        String beforeCode,
        String afterCode,
        long timestamp
    ) {}

    @FunctionalInterface
    private interface ActionHandler {
        ActionResult handle(Action action, CodeContext context);
    }
}
