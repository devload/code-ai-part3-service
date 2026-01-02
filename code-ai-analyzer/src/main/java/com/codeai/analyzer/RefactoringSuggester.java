package com.codeai.analyzer;

import java.util.*;
import java.util.regex.*;

/**
 * 리팩토링 제안기 - 코드 개선 패턴 감지 및 변환
 *
 * 지원 리팩토링:
 * 1. Extract Method - 긴 코드 블록 메서드로 추출
 * 2. Introduce Variable - 복잡한 표현식 변수로 추출
 * 3. Replace Magic Number - 상수로 변환
 * 4. Simplify Conditionals - 조건문 단순화
 * 5. Use Optional - null 체크를 Optional로 변환
 * 6. Use Stream API - for 루프를 Stream으로 변환
 * 7. Builder Pattern - 다중 setter를 Builder로 변환
 */
public class RefactoringSuggester {

    private final List<Refactoring> suggestions = new ArrayList<>();

    public List<Refactoring> suggest(String code) {
        suggestions.clear();

        suggestOptionalUsage(code);
        suggestStreamAPI(code);
        suggestBuilderPattern(code);
        suggestExtractMethod(code);
        suggestSimplifyConditionals(code);
        suggestStringFormatting(code);
        suggestTryWithResources(code);

        return new ArrayList<>(suggestions);
    }

    /**
     * null 체크를 Optional로 변환
     */
    private void suggestOptionalUsage(String code) {
        // if (x != null) { return x.something(); } else { return default; }
        Pattern pattern = Pattern.compile(
            "if\\s*\\(\\s*(\\w+)\\s*!=\\s*null\\s*\\)\\s*\\{\\s*return\\s+\\1\\.([^;]+);\\s*\\}\\s*else\\s*\\{\\s*return\\s+([^;]+);\\s*\\}",
            Pattern.MULTILINE
        );
        Matcher matcher = pattern.matcher(code);

        while (matcher.find()) {
            String varName = matcher.group(1);
            String method = matcher.group(2);
            String defaultValue = matcher.group(3);

            String before = matcher.group();
            String after = String.format("return Optional.ofNullable(%s).map(v -> v.%s).orElse(%s);",
                varName, method, defaultValue);

            suggestions.add(new Refactoring(
                RefactoringType.USE_OPTIONAL,
                "null 체크를 Optional로 변환",
                before,
                after,
                findLineNumber(code, matcher.start())
            ));
        }

        // if (x != null) { x.doSomething(); }
        Pattern simplePattern = Pattern.compile(
            "if\\s*\\(\\s*(\\w+)\\s*!=\\s*null\\s*\\)\\s*\\{\\s*(\\1\\.[^}]+)\\s*\\}",
            Pattern.MULTILINE
        );
        Matcher simpleMatcher = simplePattern.matcher(code);

        while (simpleMatcher.find()) {
            String varName = simpleMatcher.group(1);
            String action = simpleMatcher.group(2).trim();

            String before = simpleMatcher.group();
            String after = String.format("Optional.ofNullable(%s).ifPresent(v -> %s);",
                varName, action.replace(varName + ".", "v."));

            suggestions.add(new Refactoring(
                RefactoringType.USE_OPTIONAL,
                "null 체크를 Optional.ifPresent로 변환",
                before,
                after,
                findLineNumber(code, simpleMatcher.start())
            ));
        }
    }

    /**
     * for 루프를 Stream API로 변환
     */
    private void suggestStreamAPI(String code) {
        // for loop with filter and action
        Pattern forFilterPattern = Pattern.compile(
            "for\\s*\\(\\s*(\\w+)\\s+(\\w+)\\s*:\\s*(\\w+)\\s*\\)\\s*\\{\\s*if\\s*\\(([^)]+)\\)\\s*\\{\\s*([^}]+)\\s*\\}\\s*\\}",
            Pattern.MULTILINE
        );
        Matcher filterMatcher = forFilterPattern.matcher(code);

        while (filterMatcher.find()) {
            String type = filterMatcher.group(1);
            String varName = filterMatcher.group(2);
            String collection = filterMatcher.group(3);
            String condition = filterMatcher.group(4);
            String action = filterMatcher.group(5).trim();

            String before = filterMatcher.group();
            String after = String.format(
                "%s.stream()\n    .filter(%s -> %s)\n    .forEach(%s -> %s);",
                collection, varName, condition, varName, action.replace(";", "")
            );

            suggestions.add(new Refactoring(
                RefactoringType.USE_STREAM,
                "for-if 루프를 Stream.filter().forEach()로 변환",
                before,
                after,
                findLineNumber(code, filterMatcher.start())
            ));
        }

        // for loop collecting to list
        Pattern forCollectPattern = Pattern.compile(
            "List<(\\w+)>\\s+(\\w+)\\s*=\\s*new\\s+ArrayList<>\\(\\);\\s*for\\s*\\(\\s*\\w+\\s+(\\w+)\\s*:\\s*(\\w+)\\s*\\)\\s*\\{\\s*if\\s*\\(([^)]+)\\)\\s*\\{\\s*\\2\\.add\\(\\3\\);\\s*\\}\\s*\\}",
            Pattern.MULTILINE
        );
        Matcher collectMatcher = forCollectPattern.matcher(code);

        while (collectMatcher.find()) {
            String elementType = collectMatcher.group(1);
            String resultVar = collectMatcher.group(2);
            String loopVar = collectMatcher.group(3);
            String collection = collectMatcher.group(4);
            String condition = collectMatcher.group(5);

            String before = collectMatcher.group();
            String after = String.format(
                "List<%s> %s = %s.stream()\n    .filter(%s -> %s)\n    .collect(Collectors.toList());",
                elementType, resultVar, collection, loopVar, condition
            );

            suggestions.add(new Refactoring(
                RefactoringType.USE_STREAM,
                "for-if-add 패턴을 Stream.filter().collect()로 변환",
                before,
                after,
                findLineNumber(code, collectMatcher.start())
            ));
        }
    }

    /**
     * 연속된 setter를 Builder 패턴으로 변환
     */
    private void suggestBuilderPattern(String code) {
        // 같은 객체에 대한 연속 setter 호출
        Pattern pattern = Pattern.compile(
            "(\\w+)\\s+(\\w+)\\s*=\\s*new\\s+\\1\\(\\);\\s*((?:\\2\\.set\\w+\\([^)]+\\);\\s*){3,})",
            Pattern.MULTILINE
        );
        Matcher matcher = pattern.matcher(code);

        while (matcher.find()) {
            String className = matcher.group(1);
            String varName = matcher.group(2);
            String setterCalls = matcher.group(3);

            // setter 추출
            Pattern setterPattern = Pattern.compile("\\.set(\\w+)\\(([^)]+)\\)");
            Matcher setterMatcher = setterPattern.matcher(setterCalls);
            StringBuilder builderChain = new StringBuilder();
            builderChain.append(className).append(".builder()");

            while (setterMatcher.find()) {
                String property = setterMatcher.group(1);
                String value = setterMatcher.group(2);
                property = Character.toLowerCase(property.charAt(0)) + property.substring(1);
                builderChain.append("\n    .").append(property).append("(").append(value).append(")");
            }
            builderChain.append("\n    .build();");

            String before = matcher.group();
            String after = className + " " + varName + " = " + builderChain;

            suggestions.add(new Refactoring(
                RefactoringType.USE_BUILDER,
                "연속된 setter 호출을 Builder 패턴으로 변환",
                truncate(before, 100),
                after,
                findLineNumber(code, matcher.start())
            ));
        }
    }

    /**
     * 긴 메서드에서 Extract Method 제안
     */
    private void suggestExtractMethod(String code) {
        // 중복 코드 블록 찾기
        Pattern blockPattern = Pattern.compile(
            "(\\{[^{}]{100,}?\\})",
            Pattern.MULTILINE | Pattern.DOTALL
        );
        Matcher matcher = blockPattern.matcher(code);

        while (matcher.find()) {
            String block = matcher.group(1);
            int lineCount = block.split("\n").length;

            if (lineCount > 15) {
                suggestions.add(new Refactoring(
                    RefactoringType.EXTRACT_METHOD,
                    "긴 코드 블록을 별도 메서드로 추출",
                    truncate(block, 80),
                    "// TODO: 이 블록을 의미 있는 이름의 private 메서드로 추출\n" +
                    "private void extractedMethod() {\n    // 추출된 로직\n}",
                    findLineNumber(code, matcher.start())
                ));
            }
        }
    }

    /**
     * 조건문 단순화
     */
    private void suggestSimplifyConditionals(String code) {
        // if (condition) return true; else return false;
        Pattern boolReturnPattern = Pattern.compile(
            "if\\s*\\(([^)]+)\\)\\s*\\{?\\s*return\\s+true;\\s*\\}?\\s*else\\s*\\{?\\s*return\\s+false;\\s*\\}?",
            Pattern.MULTILINE
        );
        Matcher boolMatcher = boolReturnPattern.matcher(code);

        while (boolMatcher.find()) {
            String condition = boolMatcher.group(1);
            String before = boolMatcher.group();
            String after = "return " + condition + ";";

            suggestions.add(new Refactoring(
                RefactoringType.SIMPLIFY_CONDITIONAL,
                "불필요한 if-else 제거",
                before,
                after,
                findLineNumber(code, boolMatcher.start())
            ));
        }

        // if (condition) { x = true; } else { x = false; }
        Pattern boolAssignPattern = Pattern.compile(
            "if\\s*\\(([^)]+)\\)\\s*\\{\\s*(\\w+)\\s*=\\s*true;\\s*\\}\\s*else\\s*\\{\\s*\\2\\s*=\\s*false;\\s*\\}",
            Pattern.MULTILINE
        );
        Matcher assignMatcher = boolAssignPattern.matcher(code);

        while (assignMatcher.find()) {
            String condition = assignMatcher.group(1);
            String varName = assignMatcher.group(2);

            String before = assignMatcher.group();
            String after = varName + " = " + condition + ";";

            suggestions.add(new Refactoring(
                RefactoringType.SIMPLIFY_CONDITIONAL,
                "boolean 할당 단순화",
                before,
                after,
                findLineNumber(code, assignMatcher.start())
            ));
        }

        // 삼항 연산자 단순화: condition ? true : false
        Pattern ternaryPattern = Pattern.compile("(\\w+)\\s*\\?\\s*true\\s*:\\s*false");
        Matcher ternaryMatcher = ternaryPattern.matcher(code);

        while (ternaryMatcher.find()) {
            String before = ternaryMatcher.group();
            String after = ternaryMatcher.group(1);

            suggestions.add(new Refactoring(
                RefactoringType.SIMPLIFY_CONDITIONAL,
                "불필요한 삼항 연산자 제거",
                before,
                after,
                findLineNumber(code, ternaryMatcher.start())
            ));
        }
    }

    /**
     * 문자열 연결을 String.format으로 변환
     */
    private void suggestStringFormatting(String code) {
        // "text" + var + "text" 패턴
        Pattern pattern = Pattern.compile(
            "\"[^\"]+\"\\s*\\+\\s*\\w+\\s*\\+\\s*\"[^\"]+\"",
            Pattern.MULTILINE
        );
        Matcher matcher = pattern.matcher(code);

        while (matcher.find()) {
            String before = matcher.group();

            suggestions.add(new Refactoring(
                RefactoringType.USE_STRING_FORMAT,
                "문자열 연결을 String.format 또는 MessageFormat으로 변환",
                before,
                "// String.format(\"...%s...\", variable) 또는\n// \"...\" + variable + \"...\" → String.format() 권장",
                findLineNumber(code, matcher.start())
            ));
        }
    }

    /**
     * try-finally를 try-with-resources로 변환
     */
    private void suggestTryWithResources(String code) {
        Pattern pattern = Pattern.compile(
            "(\\w+)\\s+(\\w+)\\s*=\\s*new\\s+\\1\\([^)]*\\);\\s*try\\s*\\{[^}]+\\}\\s*finally\\s*\\{\\s*\\2\\.close\\(\\);\\s*\\}",
            Pattern.MULTILINE | Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(code);

        while (matcher.find()) {
            String resourceType = matcher.group(1);
            String varName = matcher.group(2);

            suggestions.add(new Refactoring(
                RefactoringType.TRY_WITH_RESOURCES,
                "try-finally를 try-with-resources로 변환",
                truncate(matcher.group(), 80),
                String.format("try (%s %s = new %s(...)) {\n    // 리소스 자동 해제\n}",
                    resourceType, varName, resourceType),
                findLineNumber(code, matcher.start())
            ));
        }
    }

    // =========== 유틸리티 ===========

    private int findLineNumber(String code, int position) {
        return code.substring(0, Math.min(position, code.length())).split("\n").length;
    }

    private String truncate(String s, int maxLength) {
        s = s.replaceAll("\\s+", " ").trim();
        if (s.length() <= maxLength) return s;
        return s.substring(0, maxLength) + "...";
    }

    // =========== 내부 클래스 ===========

    public enum RefactoringType {
        EXTRACT_METHOD("메서드 추출"),
        INTRODUCE_VARIABLE("변수 도입"),
        USE_OPTIONAL("Optional 사용"),
        USE_STREAM("Stream API 사용"),
        USE_BUILDER("Builder 패턴"),
        SIMPLIFY_CONDITIONAL("조건문 단순화"),
        USE_STRING_FORMAT("문자열 포맷팅"),
        TRY_WITH_RESOURCES("Try-with-resources");

        public final String description;
        RefactoringType(String description) { this.description = description; }
    }

    public static class Refactoring {
        public final RefactoringType type;
        public final String description;
        public final String before;
        public final String after;
        public final int line;

        public Refactoring(RefactoringType type, String description, String before, String after, int line) {
            this.type = type;
            this.description = description;
            this.before = before;
            this.after = after;
            this.line = line;
        }

        @Override
        public String toString() {
            return String.format("""
                🔧 [%s] %s (Line %d)

                Before:
                %s

                After:
                %s
                """,
                type.description, description, line,
                indent(before), indent(after));
        }

        private String indent(String s) {
            return "    " + s.replace("\n", "\n    ");
        }
    }

    public static String formatSuggestions(List<Refactoring> suggestions) {
        if (suggestions.isEmpty()) {
            return "✅ 리팩토링 제안이 없습니다. 코드가 깔끔합니다!";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n" + "=".repeat(60) + "\n");
        sb.append("🔧 리팩토링 제안 (" + suggestions.size() + "개)\n");
        sb.append("=".repeat(60) + "\n");

        for (Refactoring r : suggestions) {
            sb.append("\n").append(r.toString());
            sb.append("-".repeat(60) + "\n");
        }

        return sb.toString();
    }
}
