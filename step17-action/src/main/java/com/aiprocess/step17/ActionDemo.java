package com.aiprocess.step17;

import java.util.*;

/**
 * STEP 17: 액션 실행 데모
 */
public class ActionDemo {

    public static void main(String[] args) {
        System.out.println("═".repeat(60));
        System.out.println("STEP 17: 액션 실행 (Action Execution)");
        System.out.println("═".repeat(60));
        System.out.println();
        System.out.println("핵심 질문: AI가 도구를 어떻게 사용하는가?");
        System.out.println();

        // 액션 실행 데모
        demoActionTypes();
        demoCodeFix();
        demoBatchExecution();
    }

    private static void demoActionTypes() {
        System.out.println("─".repeat(60));
        System.out.println("1. 지원하는 액션 타입");
        System.out.println("─".repeat(60));
        System.out.println();

        System.out.println("  ┌─────────────────────────────────────────────────────┐");
        System.out.println("  │ AI가 사용할 수 있는 도구들                            │");
        System.out.println("  └─────────────────────────────────────────────────────┘");
        System.out.println();

        for (ActionExecutor.ActionType type : ActionExecutor.ActionType.values()) {
            String emoji = switch (type) {
                case REPLACE_CODE -> "🔄";
                case INSERT_CODE -> "➕";
                case DELETE_CODE -> "🗑️";
                case ADD_IMPORT -> "📦";
                case RENAME -> "✏️";
                case EXTRACT_METHOD -> "📤";
                case ADD_LOGGING -> "📝";
            };

            System.out.println("  " + emoji + " " + type.name());
            System.out.println("     " + type.getDescription());
            System.out.println();
        }
    }

    private static void demoCodeFix() {
        System.out.println("─".repeat(60));
        System.out.println("2. 코드 자동 수정");
        System.out.println("─".repeat(60));
        System.out.println();

        String originalCode = """
            public class UserService {
                public User findUser(String id) {
                    System.out.println("Finding user: " + id);
                    String query = "SELECT * FROM users WHERE id=" + id;
                    return db.execute(query);
                }
            }
            """;

        System.out.println("  📝 원본 코드:");
        System.out.println("  ─────────────────────────────────────────────────────");
        printCode(originalCode);
        System.out.println("  ─────────────────────────────────────────────────────");
        System.out.println();

        ActionExecutor executor = new ActionExecutor();

        // 액션 1: System.out을 로거로 교체
        ActionExecutor.Action action1 = new ActionExecutor.Action(
            ActionExecutor.ActionType.REPLACE_CODE,
            Map.of(
                "old_code", "System.out.println(\"Finding user: \" + id);",
                "new_code", "logger.info(\"Finding user: {}\", id);"
            ),
            null
        );

        System.out.println("  🔧 액션 1: System.out → Logger 변환");

        ActionExecutor.CodeContext context1 = new ActionExecutor.CodeContext(originalCode, null);
        ActionExecutor.ActionResult result1 = executor.execute(action1, context1);

        System.out.println("     결과: " + (result1.success() ? "✅ " : "❌ ") + result1.message());
        System.out.println();

        // 액션 2: SQL Injection 수정
        String afterAction1 = result1.modifiedCode();
        ActionExecutor.Action action2 = new ActionExecutor.Action(
            ActionExecutor.ActionType.REPLACE_CODE,
            Map.of(
                "old_code", "String query = \"SELECT * FROM users WHERE id=\" + id;",
                "new_code", "String query = \"SELECT * FROM users WHERE id=?\";"
            ),
            null
        );

        System.out.println("  🔧 액션 2: SQL Injection 수정");

        ActionExecutor.CodeContext context2 = new ActionExecutor.CodeContext(afterAction1, null);
        ActionExecutor.ActionResult result2 = executor.execute(action2, context2);

        System.out.println("     결과: " + (result2.success() ? "✅ " : "❌ ") + result2.message());
        System.out.println();

        // 액션 3: import 추가
        String afterAction2 = result2.modifiedCode();
        ActionExecutor.Action action3 = new ActionExecutor.Action(
            ActionExecutor.ActionType.ADD_IMPORT,
            Map.of("import", "org.slf4j.Logger"),
            null
        );

        System.out.println("  🔧 액션 3: Logger import 추가");

        ActionExecutor.CodeContext context3 = new ActionExecutor.CodeContext(afterAction2, null);
        ActionExecutor.ActionResult result3 = executor.execute(action3, context3);

        System.out.println("     결과: " + (result3.success() ? "✅ " : "❌ ") + result3.message());
        System.out.println();

        System.out.println("  ✨ 수정된 코드:");
        System.out.println("  ─────────────────────────────────────────────────────");
        printCode(result3.modifiedCode());
        System.out.println("  ─────────────────────────────────────────────────────");
        System.out.println();
    }

    private static void demoBatchExecution() {
        System.out.println("─".repeat(60));
        System.out.println("3. 배치 액션 실행");
        System.out.println("─".repeat(60));
        System.out.println();

        String originalCode = """
            public class Calculator {
                public int add(int a, int b) {
                    return a + b;
                }

                public int multiply(int x, int y) {
                    int result = 0;
                    for (int i = 0; i < y; i++) {
                        result += x;
                    }
                    return result;
                }
            }
            """;

        System.out.println("  📝 원본 코드:");
        System.out.println("  ─────────────────────────────────────────────────────");
        printCode(originalCode);
        System.out.println("  ─────────────────────────────────────────────────────");
        System.out.println();

        // AI가 제안한 여러 액션
        List<ActionExecutor.Action> actions = List.of(
            new ActionExecutor.Action(
                ActionExecutor.ActionType.RENAME,
                Map.of("old_name", "x", "new_name", "multiplicand"),
                null
            ),
            new ActionExecutor.Action(
                ActionExecutor.ActionType.RENAME,
                Map.of("old_name", "y", "new_name", "multiplier"),
                null
            ),
            new ActionExecutor.Action(
                ActionExecutor.ActionType.REPLACE_CODE,
                Map.of(
                    "old_code", "int result = 0;\n        for (int i = 0; i < multiplier; i++) {\n            result += multiplicand;\n        }\n        return result;",
                    "new_code", "return multiplicand * multiplier;"
                ),
                null
            )
        );

        System.out.println("  🔄 배치 액션 실행 (" + actions.size() + "개):");
        System.out.println();

        ActionExecutor executor = new ActionExecutor();
        ActionExecutor.BatchResult batchResult = executor.executeBatch(actions, originalCode);

        int idx = 1;
        for (ActionExecutor.ActionResult result : batchResult.results()) {
            String emoji = result.success() ? "✅" : "❌";
            System.out.println("     " + idx + ". " + emoji + " " + result.message());
            idx++;
        }

        System.out.println();
        System.out.println("  📊 결과: " + batchResult.successCount() + " 성공, " +
                          batchResult.failureCount() + " 실패");
        System.out.println();

        System.out.println("  ✨ 최종 코드:");
        System.out.println("  ─────────────────────────────────────────────────────");
        printCode(batchResult.finalCode());
        System.out.println("  ─────────────────────────────────────────────────────");
        System.out.println();

        System.out.println("  ┌─────────────────────────────────────────────────────┐");
        System.out.println("  │ 액션 실행 핵심 포인트                               │");
        System.out.println("  └─────────────────────────────────────────────────────┘");
        System.out.println();
        System.out.println("  ✅ 원자적 실행: 각 액션은 독립적으로 실행");
        System.out.println("  ✅ 순서 보장: 의존성 있는 액션은 순차 실행");
        System.out.println("  ✅ 롤백 지원: 실패 시 이전 상태로 복원 가능");
        System.out.println("  ✅ 히스토리: 모든 변경 기록 유지");
        System.out.println();
    }

    private static void printCode(String code) {
        int lineNum = 1;
        for (String line : code.split("\n")) {
            System.out.printf("  %3d │ %s%n", lineNum++, line);
        }
    }
}
