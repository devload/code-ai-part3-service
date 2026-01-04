package com.codeai.analyzer;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.*;

/**
 * AST 기반 코드 분석기
 *
 * JavaParser를 사용하여 Java 코드를 AST(Abstract Syntax Tree)로 파싱하고
 * 정확한 구문 분석 기반의 코드 리뷰를 수행합니다.
 *
 * 정규표현식 기반 분석과의 차이:
 * - 정확한 구문 인식 (주석 내 코드 무시)
 * - 중첩 구조 정확히 파악
 * - 변수 타입, 스코프 분석 가능
 * - 메서드 호출 체인 추적
 */
public class ASTAnalyzer {

    private final List<ASTIssue> issues = new ArrayList<>();
    private final ASTMetrics metrics = new ASTMetrics();
    private final JavaParser parser;

    public ASTAnalyzer() {
        // Java 17 지원 (Text Block, Record, Sealed Class 등)
        ParserConfiguration config = new ParserConfiguration();
        config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        this.parser = new JavaParser(config);
    }

    /**
     * 코드 분석 실행
     */
    public ASTAnalysisResult analyze(String code) {
        issues.clear();
        metrics.reset();

        ParseResult<CompilationUnit> parseResult = parser.parse(code);

        if (!parseResult.isSuccessful()) {
            issues.add(new ASTIssue(
                Severity.ERROR,
                "PARSE_ERROR",
                "코드 파싱 실패: " + parseResult.getProblems(),
                "문법 오류를 수정하세요",
                1
            ));
            return new ASTAnalysisResult(issues, metrics, false);
        }

        CompilationUnit cu = parseResult.getResult().orElseThrow();

        // 메트릭 수집
        collectMetrics(cu);

        // 코드 스멜 감지
        detectCodeSmells(cu);

        // 보안 취약점 감지
        detectSecurityIssues(cu);

        // 베스트 프랙티스 검사
        checkBestPractices(cu);

        return new ASTAnalysisResult(issues, metrics, true);
    }

    // =========== 메트릭 수집 ===========

    private void collectMetrics(CompilationUnit cu) {
        // 클래스 수
        metrics.classCount = cu.findAll(ClassOrInterfaceDeclaration.class).size();

        // 메서드 수
        metrics.methodCount = cu.findAll(MethodDeclaration.class).size();

        // 필드 수
        metrics.fieldCount = cu.findAll(FieldDeclaration.class).size();

        // 라인 수
        cu.getRange().ifPresent(range -> {
            metrics.totalLines = range.end.line;
        });

        // 메서드별 복잡도
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            int complexity = calculateCyclomaticComplexity(method);
            metrics.methodComplexities.put(
                method.getNameAsString(),
                complexity
            );
            metrics.totalComplexity += complexity;
        });

        // 평균 메서드 길이
        List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
        if (!methods.isEmpty()) {
            int totalLines = methods.stream()
                .mapToInt(m -> m.getRange().map(r -> r.end.line - r.begin.line + 1).orElse(0))
                .sum();
            metrics.avgMethodLength = totalLines / methods.size();
        }
    }

    /**
     * 순환 복잡도 계산 (McCabe's Cyclomatic Complexity)
     * CC = E - N + 2P (간단히: 분기점 + 1)
     */
    private int calculateCyclomaticComplexity(MethodDeclaration method) {
        int complexity = 1; // 기본값

        // if 문
        complexity += method.findAll(IfStmt.class).size();

        // for 루프
        complexity += method.findAll(ForStmt.class).size();
        complexity += method.findAll(ForEachStmt.class).size();

        // while 루프
        complexity += method.findAll(WhileStmt.class).size();
        complexity += method.findAll(DoStmt.class).size();

        // switch case
        complexity += method.findAll(SwitchEntry.class).stream()
            .filter(se -> !se.getLabels().isEmpty())
            .count();

        // catch 블록
        complexity += method.findAll(CatchClause.class).size();

        // 논리 연산자 (&&, ||)
        complexity += method.findAll(BinaryExpr.class).stream()
            .filter(be -> be.getOperator() == BinaryExpr.Operator.AND ||
                         be.getOperator() == BinaryExpr.Operator.OR)
            .count();

        // 삼항 연산자
        complexity += method.findAll(ConditionalExpr.class).size();

        return complexity;
    }

    // =========== 코드 스멜 감지 ===========

    private void detectCodeSmells(CompilationUnit cu) {
        // 1. 긴 메서드
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            method.getRange().ifPresent(range -> {
                int lines = range.end.line - range.begin.line + 1;
                if (lines > 30) {
                    issues.add(new ASTIssue(
                        Severity.WARNING,
                        "LONG_METHOD",
                        String.format("메서드 '%s'이 너무 깁니다 (%d줄)", method.getNameAsString(), lines),
                        "20줄 이하로 분리하세요. Extract Method 리팩토링을 고려하세요.",
                        range.begin.line
                    ));
                }
            });
        });

        // 2. 너무 많은 매개변수
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            int paramCount = method.getParameters().size();
            if (paramCount > 4) {
                issues.add(new ASTIssue(
                    Severity.WARNING,
                    "TOO_MANY_PARAMS",
                    String.format("메서드 '%s'의 매개변수가 너무 많습니다 (%d개)",
                        method.getNameAsString(), paramCount),
                    "Parameter Object 패턴 또는 Builder 패턴을 사용하세요.",
                    method.getBegin().map(p -> p.line).orElse(0)
                ));
            }
        });

        // 3. 깊은 중첩
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            int maxDepth = calculateMaxNestingDepth(method);
            if (maxDepth > 3) {
                issues.add(new ASTIssue(
                    Severity.WARNING,
                    "DEEP_NESTING",
                    String.format("메서드 '%s'의 중첩이 너무 깊습니다 (깊이: %d)",
                        method.getNameAsString(), maxDepth),
                    "Early Return 패턴이나 메서드 추출로 중첩을 줄이세요.",
                    method.getBegin().map(p -> p.line).orElse(0)
                ));
            }
        });

        // 4. 빈 catch 블록
        cu.findAll(CatchClause.class).forEach(catchClause -> {
            if (catchClause.getBody().getStatements().isEmpty()) {
                issues.add(new ASTIssue(
                    Severity.WARNING,
                    "EMPTY_CATCH",
                    "빈 catch 블록이 있습니다",
                    "최소한 로깅을 추가하거나, 예외를 다시 throw 하세요.",
                    catchClause.getBegin().map(p -> p.line).orElse(0)
                ));
            }
        });

        // 5. God Class (너무 큰 클래스)
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
            int methodCount = clazz.getMethods().size();
            int fieldCount = clazz.getFields().size();

            if (methodCount > 20 || fieldCount > 15) {
                issues.add(new ASTIssue(
                    Severity.WARNING,
                    "GOD_CLASS",
                    String.format("클래스 '%s'가 너무 큽니다 (메서드: %d, 필드: %d)",
                        clazz.getNameAsString(), methodCount, fieldCount),
                    "Single Responsibility Principle을 적용하여 클래스를 분리하세요.",
                    clazz.getBegin().map(p -> p.line).orElse(0)
                ));
            }
        });

        // 6. 높은 순환 복잡도
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            int complexity = calculateCyclomaticComplexity(method);
            if (complexity > 10) {
                issues.add(new ASTIssue(
                    Severity.WARNING,
                    "HIGH_COMPLEXITY",
                    String.format("메서드 '%s'의 순환 복잡도가 높습니다 (CC: %d)",
                        method.getNameAsString(), complexity),
                    "복잡도 10 이하로 유지하세요. 조건문을 메서드로 추출하세요.",
                    method.getBegin().map(p -> p.line).orElse(0)
                ));
            }
        });

        // 7. 사용되지 않는 private 메서드
        Set<String> calledMethods = new HashSet<>();
        cu.findAll(MethodCallExpr.class).forEach(call -> {
            calledMethods.add(call.getNameAsString());
        });

        cu.findAll(MethodDeclaration.class).stream()
            .filter(m -> m.isPrivate())
            .filter(m -> !calledMethods.contains(m.getNameAsString()))
            .forEach(method -> {
                issues.add(new ASTIssue(
                    Severity.INFO,
                    "UNUSED_METHOD",
                    String.format("private 메서드 '%s'가 사용되지 않습니다",
                        method.getNameAsString()),
                    "불필요한 코드는 삭제하세요.",
                    method.getBegin().map(p -> p.line).orElse(0)
                ));
            });
    }

    private int calculateMaxNestingDepth(MethodDeclaration method) {
        int[] maxDepth = {0};
        method.accept(new NestingDepthVisitor(), maxDepth);
        return maxDepth[0];
    }

    /**
     * 중첩 깊이 계산 Visitor
     */
    private static class NestingDepthVisitor extends VoidVisitorAdapter<int[]> {
        private int currentDepth = 0;

        @Override
        public void visit(IfStmt n, int[] maxDepth) {
            currentDepth++;
            maxDepth[0] = Math.max(maxDepth[0], currentDepth);
            super.visit(n, maxDepth);
            currentDepth--;
        }

        @Override
        public void visit(ForStmt n, int[] maxDepth) {
            currentDepth++;
            maxDepth[0] = Math.max(maxDepth[0], currentDepth);
            super.visit(n, maxDepth);
            currentDepth--;
        }

        @Override
        public void visit(ForEachStmt n, int[] maxDepth) {
            currentDepth++;
            maxDepth[0] = Math.max(maxDepth[0], currentDepth);
            super.visit(n, maxDepth);
            currentDepth--;
        }

        @Override
        public void visit(WhileStmt n, int[] maxDepth) {
            currentDepth++;
            maxDepth[0] = Math.max(maxDepth[0], currentDepth);
            super.visit(n, maxDepth);
            currentDepth--;
        }

        @Override
        public void visit(TryStmt n, int[] maxDepth) {
            currentDepth++;
            maxDepth[0] = Math.max(maxDepth[0], currentDepth);
            super.visit(n, maxDepth);
            currentDepth--;
        }
    }

    // =========== 보안 취약점 감지 ===========

    private void detectSecurityIssues(CompilationUnit cu) {
        // 1. 하드코딩된 비밀정보
        cu.findAll(FieldDeclaration.class).forEach(field -> {
            field.getVariables().forEach(var -> {
                String name = var.getNameAsString().toLowerCase();
                if ((name.contains("password") || name.contains("secret") ||
                     name.contains("apikey") || name.contains("token")) &&
                    var.getInitializer().isPresent()) {

                    Expression init = var.getInitializer().get();
                    if (init instanceof StringLiteralExpr) {
                        issues.add(new ASTIssue(
                            Severity.CRITICAL,
                            "HARDCODED_SECRET",
                            String.format("하드코딩된 비밀 정보: %s", var.getNameAsString()),
                            "환경 변수나 설정 파일에서 읽어오세요.",
                            var.getBegin().map(p -> p.line).orElse(0)
                        ));
                    }
                }
            });
        });

        // 2. SQL Injection (문자열 연결로 쿼리 생성)
        cu.findAll(VariableDeclarator.class).forEach(var -> {
            var.getInitializer().ifPresent(init -> {
                if (init instanceof BinaryExpr) {
                    String initStr = init.toString().toLowerCase();
                    if (initStr.contains("select") || initStr.contains("insert") ||
                        initStr.contains("update") || initStr.contains("delete")) {

                        if (containsStringConcatenation((BinaryExpr) init)) {
                            issues.add(new ASTIssue(
                                Severity.CRITICAL,
                                "SQL_INJECTION",
                                "SQL Injection 취약점: 문자열 연결로 쿼리 생성",
                                "PreparedStatement와 파라미터 바인딩을 사용하세요.",
                                var.getBegin().map(p -> p.line).orElse(0)
                            ));
                        }
                    }
                }
            });
        });

        // 3. 안전하지 않은 난수 생성
        cu.findAll(ObjectCreationExpr.class).forEach(creation -> {
            if (creation.getType().getNameAsString().equals("Random")) {
                // 보안 관련 컨텍스트인지 확인
                issues.add(new ASTIssue(
                    Severity.INFO,
                    "INSECURE_RANDOM",
                    "java.util.Random 사용 감지",
                    "보안이 필요한 경우 SecureRandom을 사용하세요.",
                    creation.getBegin().map(p -> p.line).orElse(0)
                ));
            }
        });

        // 4. System.exit() 호출
        cu.findAll(MethodCallExpr.class).forEach(call -> {
            if (call.getNameAsString().equals("exit") &&
                call.getScope().map(s -> s.toString().equals("System")).orElse(false)) {
                issues.add(new ASTIssue(
                    Severity.WARNING,
                    "SYSTEM_EXIT",
                    "System.exit() 호출 감지",
                    "라이브러리/프레임워크에서는 예외를 사용하세요.",
                    call.getBegin().map(p -> p.line).orElse(0)
                ));
            }
        });
    }

    private boolean containsStringConcatenation(BinaryExpr expr) {
        if (expr.getOperator() == BinaryExpr.Operator.PLUS) {
            // 문자열과 변수의 연결 감지
            Expression left = expr.getLeft();
            Expression right = expr.getRight();

            boolean leftIsString = left instanceof StringLiteralExpr;
            boolean rightIsString = right instanceof StringLiteralExpr;
            boolean leftIsName = left instanceof NameExpr;
            boolean rightIsName = right instanceof NameExpr;

            if ((leftIsString && rightIsName) || (leftIsName && rightIsString)) {
                return true;
            }

            // 재귀적으로 확인
            if (left instanceof BinaryExpr) {
                if (containsStringConcatenation((BinaryExpr) left)) return true;
            }
            if (right instanceof BinaryExpr) {
                if (containsStringConcatenation((BinaryExpr) right)) return true;
            }
        }
        return false;
    }

    // =========== 베스트 프랙티스 ===========

    private void checkBestPractices(CompilationUnit cu) {
        // 1. equals() 없이 == 로 문자열 비교
        cu.findAll(BinaryExpr.class).forEach(expr -> {
            if (expr.getOperator() == BinaryExpr.Operator.EQUALS ||
                expr.getOperator() == BinaryExpr.Operator.NOT_EQUALS) {

                Expression left = expr.getLeft();
                Expression right = expr.getRight();

                if (isStringType(left) || isStringType(right)) {
                    issues.add(new ASTIssue(
                        Severity.WARNING,
                        "STRING_COMPARE",
                        "문자열을 == 연산자로 비교하고 있습니다",
                        "String.equals() 또는 Objects.equals()를 사용하세요.",
                        expr.getBegin().map(p -> p.line).orElse(0)
                    ));
                }
            }
        });

        // 2. 명명 규칙 검사
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
            String name = clazz.getNameAsString();
            if (!Character.isUpperCase(name.charAt(0))) {
                issues.add(new ASTIssue(
                    Severity.INFO,
                    "NAMING_CLASS",
                    String.format("클래스명 '%s'은 대문자로 시작해야 합니다", name),
                    "PascalCase를 사용하세요.",
                    clazz.getBegin().map(p -> p.line).orElse(0)
                ));
            }
        });

        cu.findAll(MethodDeclaration.class).forEach(method -> {
            String name = method.getNameAsString();
            if (Character.isUpperCase(name.charAt(0))) {
                issues.add(new ASTIssue(
                    Severity.INFO,
                    "NAMING_METHOD",
                    String.format("메서드명 '%s'은 소문자로 시작해야 합니다", name),
                    "camelCase를 사용하세요.",
                    method.getBegin().map(p -> p.line).orElse(0)
                ));
            }
        });

        // 3. 상수 명명 규칙 (static final)
        cu.findAll(FieldDeclaration.class).forEach(field -> {
            if (field.isStatic() && field.isFinal()) {
                field.getVariables().forEach(var -> {
                    String name = var.getNameAsString();
                    if (!name.equals(name.toUpperCase())) {
                        issues.add(new ASTIssue(
                            Severity.INFO,
                            "NAMING_CONSTANT",
                            String.format("상수 '%s'은 UPPER_SNAKE_CASE를 사용해야 합니다", name),
                            "예: " + toUpperSnakeCase(name),
                            var.getBegin().map(p -> p.line).orElse(0)
                        ));
                    }
                });
            }
        });

        // 4. 불필요한 this 사용 검사 (옵션)
        // 5. 매직 넘버 검사
        cu.findAll(IntegerLiteralExpr.class).forEach(literal -> {
            int value = Integer.parseInt(literal.getValue());
            if (value != 0 && value != 1 && value != -1 &&
                value != 10 && value != 100) {

                // 상수 선언 내부인지 확인
                boolean inConstant = literal.findAncestor(FieldDeclaration.class)
                    .map(f -> f.isStatic() && f.isFinal())
                    .orElse(false);

                if (!inConstant) {
                    issues.add(new ASTIssue(
                        Severity.INFO,
                        "MAGIC_NUMBER",
                        String.format("매직 넘버 감지: %d", value),
                        "의미 있는 상수로 정의하세요.",
                        literal.getBegin().map(p -> p.line).orElse(0)
                    ));
                }
            }
        });
    }

    private boolean isStringType(Expression expr) {
        if (expr instanceof StringLiteralExpr) return true;
        if (expr instanceof MethodCallExpr) {
            String methodName = ((MethodCallExpr) expr).getNameAsString();
            return methodName.equals("toString") || methodName.equals("getName") ||
                   methodName.equals("getString") || methodName.equals("trim");
        }
        return false;
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

    public static class ASTIssue {
        public final Severity severity;
        public final String code;
        public final String message;
        public final String suggestion;
        public final int line;

        public ASTIssue(Severity severity, String code, String message, String suggestion, int line) {
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

    public static class ASTMetrics {
        public int totalLines = 0;
        public int classCount = 0;
        public int methodCount = 0;
        public int fieldCount = 0;
        public int totalComplexity = 0;
        public int avgMethodLength = 0;
        public Map<String, Integer> methodComplexities = new HashMap<>();

        public void reset() {
            totalLines = 0;
            classCount = 0;
            methodCount = 0;
            fieldCount = 0;
            totalComplexity = 0;
            avgMethodLength = 0;
            methodComplexities.clear();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("📊 AST 메트릭:\n");
            sb.append(String.format("   총 라인: %d\n", totalLines));
            sb.append(String.format("   클래스: %d개 | 메서드: %d개 | 필드: %d개\n",
                classCount, methodCount, fieldCount));
            sb.append(String.format("   총 순환 복잡도: %d (평균: %.1f)\n",
                totalComplexity, methodCount > 0 ? (double) totalComplexity / methodCount : 0));
            sb.append(String.format("   평균 메서드 길이: %d줄\n", avgMethodLength));

            if (!methodComplexities.isEmpty()) {
                sb.append("   메서드별 복잡도:\n");
                methodComplexities.entrySet().stream()
                    .sorted((a, b) -> b.getValue() - a.getValue())
                    .limit(5)
                    .forEach(e -> sb.append(String.format("     - %s: %d\n", e.getKey(), e.getValue())));
            }
            return sb.toString();
        }
    }

    public static class ASTAnalysisResult {
        public final List<ASTIssue> issues;
        public final ASTMetrics metrics;
        public final boolean parseSuccess;

        public ASTAnalysisResult(List<ASTIssue> issues, ASTMetrics metrics, boolean parseSuccess) {
            this.issues = new ArrayList<>(issues);
            this.metrics = metrics;
            this.parseSuccess = parseSuccess;
        }

        public String formatReport() {
            return formatReport(Severity.INFO);
        }

        public String formatReport(Severity minSeverity) {
            List<ASTIssue> filtered = issues.stream()
                .filter(i -> i.severity.ordinal() >= minSeverity.ordinal())
                .toList();

            long critical = filtered.stream().filter(i -> i.severity == Severity.CRITICAL).count();
            long errors = filtered.stream().filter(i -> i.severity == Severity.ERROR).count();
            long warnings = filtered.stream().filter(i -> i.severity == Severity.WARNING).count();
            long info = filtered.stream().filter(i -> i.severity == Severity.INFO).count();

            StringBuilder sb = new StringBuilder();
            sb.append("\n" + "=".repeat(60) + "\n");
            sb.append("📋 AST 기반 코드 리뷰 결과");
            if (minSeverity != Severity.INFO) {
                sb.append(" (최소 심각도: ").append(minSeverity).append(")");
            }
            sb.append("\n");
            sb.append("=".repeat(60) + "\n\n");

            if (!parseSuccess) {
                sb.append("❌ 코드 파싱 실패\n\n");
            }

            sb.append(metrics.toString()).append("\n");

            sb.append(String.format("🔍 발견된 이슈: %d개\n", filtered.size()));
            sb.append(String.format("   🚨 Critical: %d | ❌ Error: %d | ⚠️ Warning: %d | 💡 Info: %d\n\n",
                critical, errors, warnings, info));

            if (!filtered.isEmpty()) {
                sb.append("-".repeat(60) + "\n");
                for (ASTIssue issue : filtered) {
                    sb.append(issue.toString()).append("\n\n");
                }
            }

            // 점수 계산
            int score = 100 - (int)(critical * 25 + errors * 15 + warnings * 5 + info * 1);
            score = Math.max(0, Math.min(100, score));

            sb.append("-".repeat(60) + "\n");
            sb.append(String.format("📈 코드 품질 점수: %d/100 %s\n",
                score,
                score >= 80 ? "✅ 좋음" : score >= 60 ? "⚠️ 개선 필요" : "❌ 심각한 문제"));

            return sb.toString();
        }
    }
}
