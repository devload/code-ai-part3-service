package com.codeai.analyzer;

import java.util.*;
import java.util.regex.*;

/**
 * 코드 분석기 - Code Smell 감지 및 품질 측정
 *
 * 기능:
 * 1. Code Smell 감지 (긴 메서드, 매개변수 과다 등)
 * 2. 코드 메트릭 계산 (복잡도, LOC 등)
 * 3. 보안 취약점 감지 (SQL Injection, 하드코딩된 비밀번호 등)
 * 4. 네이밍 규칙 검사
 */
public class CodeAnalyzer {

    private final List<CodeIssue> issues = new ArrayList<>();
    private final CodeMetrics metrics = new CodeMetrics();

    /**
     * 코드 분석 실행
     */
    public AnalysisResult analyze(String code) {
        issues.clear();

        // 1. Code Smell 감지
        detectLongMethods(code);
        detectTooManyParameters(code);
        detectDeepNesting(code);
        detectDuplicateCode(code);
        detectMagicNumbers(code);
        detectEmptyCatchBlocks(code);

        // 2. 보안 취약점 감지
        detectHardcodedSecrets(code);
        detectSqlInjection(code);
        detectNullPointerRisks(code);

        // 3. 네이밍 규칙 검사
        checkNamingConventions(code);

        // 4. 코드 메트릭 계산
        calculateMetrics(code);

        return new AnalysisResult(new ArrayList<>(issues), metrics);
    }

    // =========== Code Smell 감지 ===========

    private void detectLongMethods(String code) {
        Pattern methodPattern = Pattern.compile(
            "(public|private|protected)?\\s*(static)?\\s*\\w+\\s+(\\w+)\\s*\\([^)]*\\)\\s*\\{",
            Pattern.MULTILINE
        );
        Matcher matcher = methodPattern.matcher(code);

        while (matcher.find()) {
            String methodName = matcher.group(3);
            int startPos = matcher.end();
            int braceCount = 1;
            int lineCount = 0;

            for (int i = startPos; i < code.length() && braceCount > 0; i++) {
                char c = code.charAt(i);
                if (c == '{') braceCount++;
                else if (c == '}') braceCount--;
                else if (c == '\n') lineCount++;
            }

            if (lineCount > 30) {
                issues.add(new CodeIssue(
                    Severity.WARNING,
                    "LONG_METHOD",
                    "메서드 '" + methodName + "'이 너무 깁니다 (" + lineCount + "줄)",
                    "20줄 이하로 분리하세요. Extract Method 리팩토링을 고려하세요.",
                    findLineNumber(code, matcher.start())
                ));
            }
        }
    }

    private void detectTooManyParameters(String code) {
        Pattern methodPattern = Pattern.compile(
            "(\\w+)\\s*\\(([^)]*)\\)\\s*\\{",
            Pattern.MULTILINE
        );
        Matcher matcher = methodPattern.matcher(code);

        while (matcher.find()) {
            String methodName = matcher.group(1);
            String params = matcher.group(2).trim();

            if (!params.isEmpty()) {
                int paramCount = params.split(",").length;
                if (paramCount > 4) {
                    issues.add(new CodeIssue(
                        Severity.WARNING,
                        "TOO_MANY_PARAMS",
                        "메서드 '" + methodName + "'의 매개변수가 너무 많습니다 (" + paramCount + "개)",
                        "Parameter Object 패턴 또는 Builder 패턴을 사용하세요.",
                        findLineNumber(code, matcher.start())
                    ));
                }
            }
        }
    }

    private void detectDeepNesting(String code) {
        String[] lines = code.split("\n");
        int maxNesting = 0;
        int currentNesting = 0;
        int deepestLine = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            currentNesting += countChar(line, '{') - countChar(line, '}');

            if (currentNesting > maxNesting) {
                maxNesting = currentNesting;
                deepestLine = i + 1;
            }
        }

        if (maxNesting > 4) {
            issues.add(new CodeIssue(
                Severity.WARNING,
                "DEEP_NESTING",
                "중첩이 너무 깊습니다 (깊이: " + maxNesting + ")",
                "Early Return 패턴이나 메서드 추출로 중첩을 줄이세요.",
                deepestLine
            ));
        }
    }

    private void detectDuplicateCode(String code) {
        String[] lines = code.split("\n");
        Map<String, List<Integer>> lineOccurrences = new HashMap<>();

        for (int i = 0; i < lines.length; i++) {
            String normalized = lines[i].trim();
            if (normalized.length() > 20 && !normalized.startsWith("//") && !normalized.startsWith("*")) {
                lineOccurrences.computeIfAbsent(normalized, k -> new ArrayList<>()).add(i + 1);
            }
        }

        for (Map.Entry<String, List<Integer>> entry : lineOccurrences.entrySet()) {
            if (entry.getValue().size() >= 3) {
                issues.add(new CodeIssue(
                    Severity.INFO,
                    "DUPLICATE_CODE",
                    "중복 코드 감지: \"" + truncate(entry.getKey(), 50) + "\"",
                    "공통 메서드로 추출하세요. 라인: " + entry.getValue(),
                    entry.getValue().get(0)
                ));
            }
        }
    }

    private void detectMagicNumbers(String code) {
        Pattern pattern = Pattern.compile("(?<![\\w\"])\\b(\\d{2,})\\b(?![\\w\"])");
        Matcher matcher = pattern.matcher(code);

        Set<String> reported = new HashSet<>();
        while (matcher.find()) {
            String number = matcher.group(1);
            // 흔한 숫자 제외 (0, 1, 10, 100 등)
            if (!number.equals("10") && !number.equals("100") && !number.equals("1000")) {
                if (!reported.contains(number)) {
                    reported.add(number);
                    issues.add(new CodeIssue(
                        Severity.INFO,
                        "MAGIC_NUMBER",
                        "매직 넘버 감지: " + number,
                        "의미 있는 상수로 정의하세요. 예: private static final int MAX_SIZE = " + number + ";",
                        findLineNumber(code, matcher.start())
                    ));
                }
            }
        }
    }

    private void detectEmptyCatchBlocks(String code) {
        Pattern pattern = Pattern.compile("catch\\s*\\([^)]+\\)\\s*\\{\\s*\\}");
        Matcher matcher = pattern.matcher(code);

        while (matcher.find()) {
            issues.add(new CodeIssue(
                Severity.ERROR,
                "EMPTY_CATCH",
                "빈 catch 블록이 있습니다",
                "예외를 로깅하거나 적절히 처리하세요. 최소한 로그를 남기세요.",
                findLineNumber(code, matcher.start())
            ));
        }
    }

    // =========== 보안 취약점 감지 ===========

    private void detectHardcodedSecrets(String code) {
        String[] secretPatterns = {
            "password\\s*=\\s*\"[^\"]+\"",
            "apiKey\\s*=\\s*\"[^\"]+\"",
            "secret\\s*=\\s*\"[^\"]+\"",
            "token\\s*=\\s*\"[^\"]+\"",
            "private_key\\s*=\\s*\"[^\"]+\""
        };

        for (String patternStr : secretPatterns) {
            Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(code);

            while (matcher.find()) {
                issues.add(new CodeIssue(
                    Severity.CRITICAL,
                    "HARDCODED_SECRET",
                    "하드코딩된 비밀 정보: " + truncate(matcher.group(), 30),
                    "환경 변수나 설정 파일에서 읽어오세요. 절대 코드에 비밀번호를 넣지 마세요!",
                    findLineNumber(code, matcher.start())
                ));
            }
        }
    }

    private void detectSqlInjection(String code) {
        Pattern pattern = Pattern.compile(
            "(executeQuery|executeUpdate|prepareStatement)\\s*\\(\\s*\"[^\"]*\"\\s*\\+",
            Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(code);

        while (matcher.find()) {
            issues.add(new CodeIssue(
                Severity.CRITICAL,
                "SQL_INJECTION",
                "SQL Injection 취약점 가능성",
                "문자열 연결 대신 PreparedStatement와 파라미터 바인딩을 사용하세요.",
                findLineNumber(code, matcher.start())
            ));
        }
    }

    private void detectNullPointerRisks(String code) {
        // 널 체크 없이 메서드 호출하는 패턴
        Pattern pattern = Pattern.compile(
            "(\\w+)\\s*=\\s*\\w+\\.\\w+\\([^)]*\\);[^}]*\\1\\.",
            Pattern.MULTILINE
        );
        Matcher matcher = pattern.matcher(code);

        while (matcher.find()) {
            String varName = matcher.group(1);
            // null 체크가 있는지 확인
            String context = code.substring(Math.max(0, matcher.start() - 100),
                                           Math.min(code.length(), matcher.end() + 100));
            if (!context.contains(varName + " != null") && !context.contains(varName + " == null")) {
                issues.add(new CodeIssue(
                    Severity.WARNING,
                    "NULL_POINTER_RISK",
                    "'" + varName + "' 변수의 null 체크가 없습니다",
                    "Optional을 사용하거나 null 체크를 추가하세요.",
                    findLineNumber(code, matcher.start())
                ));
            }
        }
    }

    // =========== 네이밍 규칙 검사 ===========

    private void checkNamingConventions(String code) {
        // 클래스명: PascalCase
        Pattern classPattern = Pattern.compile("class\\s+(\\w+)");
        Matcher classMatcher = classPattern.matcher(code);
        while (classMatcher.find()) {
            String className = classMatcher.group(1);
            if (!Character.isUpperCase(className.charAt(0))) {
                issues.add(new CodeIssue(
                    Severity.INFO,
                    "NAMING_CLASS",
                    "클래스명 '" + className + "'은 대문자로 시작해야 합니다",
                    "PascalCase를 사용하세요: " + capitalize(className),
                    findLineNumber(code, classMatcher.start())
                ));
            }
        }

        // 상수: UPPER_SNAKE_CASE
        Pattern constantPattern = Pattern.compile("(static\\s+final|final\\s+static)\\s+\\w+\\s+(\\w+)\\s*=");
        Matcher constantMatcher = constantPattern.matcher(code);
        while (constantMatcher.find()) {
            String constName = constantMatcher.group(2);
            if (!constName.equals(constName.toUpperCase())) {
                issues.add(new CodeIssue(
                    Severity.INFO,
                    "NAMING_CONSTANT",
                    "상수 '" + constName + "'은 대문자와 언더스코어를 사용해야 합니다",
                    "UPPER_SNAKE_CASE를 사용하세요: " + toUpperSnakeCase(constName),
                    findLineNumber(code, constantMatcher.start())
                ));
            }
        }
    }

    // =========== 메트릭 계산 ===========

    private void calculateMetrics(String code) {
        String[] lines = code.split("\n");

        metrics.totalLines = lines.length;
        metrics.codeLines = 0;
        metrics.commentLines = 0;
        metrics.blankLines = 0;

        boolean inBlockComment = false;
        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                metrics.blankLines++;
            } else if (trimmed.startsWith("//") || inBlockComment) {
                metrics.commentLines++;
            } else if (trimmed.startsWith("/*")) {
                metrics.commentLines++;
                inBlockComment = !trimmed.contains("*/");
            } else if (trimmed.contains("*/")) {
                metrics.commentLines++;
                inBlockComment = false;
            } else {
                metrics.codeLines++;
            }
        }

        // 복잡도 계산 (분기문 개수)
        metrics.cyclomaticComplexity = 1;
        String[] complexityKeywords = {"if", "else", "for", "while", "case", "catch", "&&", "\\|\\|", "\\?"};
        for (String keyword : complexityKeywords) {
            Pattern p = Pattern.compile("\\b" + keyword + "\\b");
            Matcher m = p.matcher(code);
            while (m.find()) {
                metrics.cyclomaticComplexity++;
            }
        }

        // 메서드 수
        Pattern methodPattern = Pattern.compile("(public|private|protected)\\s+\\w+\\s+\\w+\\s*\\(");
        Matcher methodMatcher = methodPattern.matcher(code);
        while (methodMatcher.find()) {
            metrics.methodCount++;
        }

        // 클래스 수
        Pattern classPattern = Pattern.compile("\\bclass\\s+\\w+");
        Matcher classMatcher = classPattern.matcher(code);
        while (classMatcher.find()) {
            metrics.classCount++;
        }
    }

    // =========== 유틸리티 ===========

    private int findLineNumber(String code, int position) {
        return code.substring(0, position).split("\n").length;
    }

    private int countChar(String s, char c) {
        int count = 0;
        for (char ch : s.toCharArray()) {
            if (ch == c) count++;
        }
        return count;
    }

    private String truncate(String s, int maxLength) {
        if (s.length() <= maxLength) return s;
        return s.substring(0, maxLength) + "...";
    }

    private String capitalize(String s) {
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String toUpperSnakeCase(String s) {
        return s.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
    }

    // =========== 내부 클래스 ===========

    public enum Severity {
        INFO("💡"), WARNING("⚠️"), ERROR("❌"), CRITICAL("🚨");

        public final String icon;
        Severity(String icon) { this.icon = icon; }
    }

    public static class CodeIssue {
        public final Severity severity;
        public final String code;
        public final String message;
        public final String suggestion;
        public final int line;

        public CodeIssue(Severity severity, String code, String message, String suggestion, int line) {
            this.severity = severity;
            this.code = code;
            this.message = message;
            this.suggestion = suggestion;
            this.line = line;
        }

        @Override
        public String toString() {
            return String.format("%s [%s] Line %d: %s\n   → %s",
                severity.icon, code, line, message, suggestion);
        }
    }

    public static class CodeMetrics {
        public int totalLines;
        public int codeLines;
        public int commentLines;
        public int blankLines;
        public int cyclomaticComplexity;
        public int methodCount;
        public int classCount;

        @Override
        public String toString() {
            return String.format("""
                📊 코드 메트릭:
                   총 라인: %d (코드: %d, 주석: %d, 빈줄: %d)
                   순환 복잡도: %d
                   메서드 수: %d
                   클래스 수: %d""",
                totalLines, codeLines, commentLines, blankLines,
                cyclomaticComplexity, methodCount, classCount);
        }
    }

    public static class AnalysisResult {
        public final List<CodeIssue> issues;
        public final CodeMetrics metrics;

        public AnalysisResult(List<CodeIssue> issues, CodeMetrics metrics) {
            this.issues = issues;
            this.metrics = metrics;
        }

        public String getSummary() {
            long critical = issues.stream().filter(i -> i.severity == Severity.CRITICAL).count();
            long errors = issues.stream().filter(i -> i.severity == Severity.ERROR).count();
            long warnings = issues.stream().filter(i -> i.severity == Severity.WARNING).count();
            long info = issues.stream().filter(i -> i.severity == Severity.INFO).count();

            StringBuilder sb = new StringBuilder();
            sb.append("\n" + "=".repeat(60) + "\n");
            sb.append("📋 코드 리뷰 결과\n");
            sb.append("=".repeat(60) + "\n\n");

            sb.append(metrics.toString()).append("\n\n");

            sb.append(String.format("🔍 발견된 이슈: %d개\n", issues.size()));
            sb.append(String.format("   🚨 Critical: %d | ❌ Error: %d | ⚠️ Warning: %d | 💡 Info: %d\n\n",
                critical, errors, warnings, info));

            if (!issues.isEmpty()) {
                sb.append("-".repeat(60) + "\n");
                for (CodeIssue issue : issues) {
                    sb.append(issue.toString()).append("\n\n");
                }
            }

            // 점수 계산
            int score = 100 - (int)(critical * 20 + errors * 10 + warnings * 5 + info * 1);
            score = Math.max(0, Math.min(100, score));

            sb.append("-".repeat(60) + "\n");
            sb.append(String.format("📈 코드 품질 점수: %d/100 %s\n",
                score,
                score >= 80 ? "✅ 좋음" : score >= 60 ? "⚠️ 개선 필요" : "❌ 심각한 문제"));

            return sb.toString();
        }

        /**
         * 심각도 필터링을 적용한 리포트 생성
         */
        public String formatReport(Severity minSeverity) {
            List<CodeIssue> filtered = issues.stream()
                .filter(i -> i.severity.ordinal() >= minSeverity.ordinal())
                .toList();

            long critical = filtered.stream().filter(i -> i.severity == Severity.CRITICAL).count();
            long errors = filtered.stream().filter(i -> i.severity == Severity.ERROR).count();
            long warnings = filtered.stream().filter(i -> i.severity == Severity.WARNING).count();
            long info = filtered.stream().filter(i -> i.severity == Severity.INFO).count();

            StringBuilder sb = new StringBuilder();
            sb.append("\n" + "=".repeat(60) + "\n");
            sb.append("📋 코드 리뷰 결과");
            if (minSeverity != Severity.INFO) {
                sb.append(" (최소 심각도: ").append(minSeverity).append(")");
            }
            sb.append("\n");
            sb.append("=".repeat(60) + "\n\n");

            sb.append(metrics.toString()).append("\n\n");

            sb.append(String.format("🔍 발견된 이슈: %d개\n", filtered.size()));
            sb.append(String.format("   🚨 Critical: %d | ❌ Error: %d | ⚠️ Warning: %d | 💡 Info: %d\n\n",
                critical, errors, warnings, info));

            if (!filtered.isEmpty()) {
                sb.append("-".repeat(60) + "\n");
                for (CodeIssue issue : filtered) {
                    sb.append(issue.toString()).append("\n\n");
                }
            }

            // 점수 계산 (전체 이슈 기준)
            long allCritical = issues.stream().filter(i -> i.severity == Severity.CRITICAL).count();
            long allErrors = issues.stream().filter(i -> i.severity == Severity.ERROR).count();
            long allWarnings = issues.stream().filter(i -> i.severity == Severity.WARNING).count();
            long allInfo = issues.stream().filter(i -> i.severity == Severity.INFO).count();

            int score = 100 - (int)(allCritical * 20 + allErrors * 10 + allWarnings * 5 + allInfo * 1);
            score = Math.max(0, Math.min(100, score));

            sb.append("-".repeat(60) + "\n");
            sb.append(String.format("📈 코드 품질 점수: %d/100 %s\n",
                score,
                score >= 80 ? "✅ 좋음" : score >= 60 ? "⚠️ 개선 필요" : "❌ 심각한 문제"));

            return sb.toString();
        }
    }
}
