package com.codeai.analyzer.fix;

import com.codeai.analyzer.ai.AICodeReviewer;
import com.codeai.analyzer.ast.ASTAnalyzer;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 코드 자동 수정기
 *
 * 발견된 이슈를 자동으로 수정합니다.
 *
 * 지원하는 자동 수정:
 * - 빈 catch 블록 → 로깅 추가
 * - System.out.println → Logger 변환
 * - 매직 넘버 → 상수 추출
 * - 문자열 연결 → StringBuilder 변환
 * - null 체크 → Optional 변환
 * - 중첩 if → Early Return 패턴
 */
public class AutoFixer {

    private final JavaParser parser;
    private final List<FixResult> fixes = new ArrayList<>();
    private final Set<FixType> enabledFixes;

    public enum FixType {
        EMPTY_CATCH,           // 빈 catch 블록
        SYSTEM_OUT,            // System.out.println
        MAGIC_NUMBER,          // 매직 넘버
        STRING_CONCAT_LOOP,    // 루프 내 문자열 연결
        NULL_CHECK,            // null 체크
        DEEP_NESTING,          // 깊은 중첩
        RAW_TYPE,              // Raw 타입
        UNUSED_IMPORT,         // 미사용 import
        TRAILING_WHITESPACE,   // 끝 공백
        MISSING_BRACES         // 누락된 중괄호
    }

    public AutoFixer() {
        this(EnumSet.allOf(FixType.class));
    }

    public AutoFixer(Set<FixType> enabledFixes) {
        ParserConfiguration config = new ParserConfiguration();
        config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        this.parser = new JavaParser(config);
        this.enabledFixes = enabledFixes;
    }

    /**
     * 코드 분석 및 자동 수정
     */
    public FixReport fix(String code) {
        fixes.clear();

        ParseResult<CompilationUnit> parseResult = parser.parse(code);
        if (!parseResult.isSuccessful()) {
            return new FixReport(code, fixes, false, "파싱 실패");
        }

        CompilationUnit cu = parseResult.getResult().orElseThrow();

        // Lexical Preserving Printer 활성화 (원본 포맷 유지)
        LexicalPreservingPrinter.setup(cu);

        // 각 수정 타입 적용
        if (enabledFixes.contains(FixType.EMPTY_CATCH)) {
            fixEmptyCatch(cu);
        }
        if (enabledFixes.contains(FixType.SYSTEM_OUT)) {
            fixSystemOut(cu);
        }
        if (enabledFixes.contains(FixType.MAGIC_NUMBER)) {
            fixMagicNumbers(cu);
        }
        if (enabledFixes.contains(FixType.MISSING_BRACES)) {
            fixMissingBraces(cu);
        }
        if (enabledFixes.contains(FixType.DEEP_NESTING)) {
            fixDeepNesting(cu);
        }

        String fixedCode = LexicalPreservingPrinter.print(cu);

        // 텍스트 기반 수정
        if (enabledFixes.contains(FixType.TRAILING_WHITESPACE)) {
            fixedCode = fixTrailingWhitespace(fixedCode);
        }

        return new FixReport(fixedCode, new ArrayList<>(fixes), true, null);
    }

    /**
     * 빈 catch 블록 수정 → 로깅 추가
     */
    private void fixEmptyCatch(CompilationUnit cu) {
        cu.findAll(CatchClause.class).forEach(catchClause -> {
            if (catchClause.getBody().getStatements().isEmpty()) {
                int line = catchClause.getBegin().map(p -> p.line).orElse(0);

                // 예외 변수명 가져오기
                String exName = catchClause.getParameter().getNameAsString();
                String exType = catchClause.getParameter().getType().asString();

                // 로깅 코드 추가
                ExpressionStmt logStmt = new ExpressionStmt(
                    new MethodCallExpr(
                        new NameExpr("log"),
                        "error",
                        new NodeList<>(
                            new StringLiteralExpr("예외 발생: {}"),
                            new MethodCallExpr(new NameExpr(exName), "getMessage"),
                            new NameExpr(exName)
                        )
                    )
                );

                catchClause.getBody().addStatement(logStmt);

                fixes.add(new FixResult(
                    FixType.EMPTY_CATCH,
                    line,
                    "빈 catch 블록에 로깅 추가",
                    "catch (" + exType + " " + exName + ") { }",
                    "catch (" + exType + " " + exName + ") { log.error(...); }"
                ));
            }
        });
    }

    /**
     * System.out.println → Logger 변환
     */
    private void fixSystemOut(CompilationUnit cu) {
        List<MethodCallExpr> systemOuts = new ArrayList<>();

        cu.findAll(MethodCallExpr.class).forEach(call -> {
            if (isSystemOut(call)) {
                systemOuts.add(call);
            }
        });

        for (MethodCallExpr call : systemOuts) {
            int line = call.getBegin().map(p -> p.line).orElse(0);
            String original = call.toString();

            // System.out.println(x) → log.info(x)
            Expression arg = call.getArguments().isEmpty() ?
                new StringLiteralExpr("") :
                call.getArgument(0).clone();

            // 문자열이 아니면 String.valueOf로 감싸기
            Expression logArg;
            if (arg instanceof StringLiteralExpr) {
                logArg = arg;
            } else {
                logArg = new MethodCallExpr(
                    new NameExpr("String"),
                    "valueOf",
                    new NodeList<>(arg)
                );
            }

            call.setScope(new NameExpr("log"));
            call.setName("info");
            call.setArguments(new NodeList<>(logArg));

            fixes.add(new FixResult(
                FixType.SYSTEM_OUT,
                line,
                "System.out.println을 Logger로 변환",
                original,
                call.toString()
            ));
        }

        // Logger import 및 필드 추가 (System.out이 있었다면)
        if (!systemOuts.isEmpty()) {
            ensureLoggerImport(cu);
        }
    }

    private boolean isSystemOut(MethodCallExpr call) {
        if (!call.getNameAsString().equals("println") &&
            !call.getNameAsString().equals("print")) {
            return false;
        }
        return call.getScope()
            .map(s -> s.toString().contains("System.out") || s.toString().contains("System.err"))
            .orElse(false);
    }

    private void ensureLoggerImport(CompilationUnit cu) {
        // import 추가
        boolean hasLoggerImport = cu.getImports().stream()
            .anyMatch(i -> i.getNameAsString().contains("Logger"));

        if (!hasLoggerImport) {
            cu.addImport("org.slf4j.Logger");
            cu.addImport("org.slf4j.LoggerFactory");
        }

        // Logger 필드 추가
        cu.findFirst(ClassOrInterfaceDeclaration.class).ifPresent(clazz -> {
            boolean hasLoggerField = clazz.getFields().stream()
                .anyMatch(f -> f.getVariables().stream()
                    .anyMatch(v -> v.getNameAsString().equals("log")));

            if (!hasLoggerField) {
                FieldDeclaration loggerField = clazz.addFieldWithInitializer(
                    "Logger",
                    "log",
                    new MethodCallExpr(
                        new NameExpr("LoggerFactory"),
                        "getLogger",
                        new NodeList<>(new ClassExpr(
                            new com.github.javaparser.ast.type.ClassOrInterfaceType(null, clazz.getNameAsString())
                        ))
                    ),
                    com.github.javaparser.ast.Modifier.Keyword.PRIVATE,
                    com.github.javaparser.ast.Modifier.Keyword.STATIC,
                    com.github.javaparser.ast.Modifier.Keyword.FINAL
                );

                // 첫 번째 필드로 이동
                clazz.getMembers().remove(loggerField);
                clazz.getMembers().add(0, loggerField);

                fixes.add(new FixResult(
                    FixType.SYSTEM_OUT,
                    clazz.getBegin().map(p -> p.line).orElse(0),
                    "Logger 필드 추가",
                    "",
                    "private static final Logger log = LoggerFactory.getLogger(" +
                        clazz.getNameAsString() + ".class);"
                ));
            }
        });
    }

    /**
     * 매직 넘버 → 상수 추출
     */
    private void fixMagicNumbers(CompilationUnit cu) {
        Map<Integer, String> magicNumbers = new LinkedHashMap<>();
        List<IntegerLiteralExpr> literals = new ArrayList<>();

        cu.findAll(IntegerLiteralExpr.class).forEach(literal -> {
            int value = Integer.parseInt(literal.getValue());

            // 0, 1, -1, 10 이하는 무시
            if (value == 0 || value == 1 || value == -1 || value <= 10) {
                return;
            }

            // 상수 선언 내부가 아닌 경우만
            boolean inConstant = literal.findAncestor(FieldDeclaration.class)
                .map(f -> f.isStatic() && f.isFinal())
                .orElse(false);

            if (!inConstant) {
                literals.add(literal);
                magicNumbers.putIfAbsent(value, suggestConstantName(value, literal));
            }
        });

        if (magicNumbers.isEmpty()) return;

        // 상수 필드 추가
        cu.findFirst(ClassOrInterfaceDeclaration.class).ifPresent(clazz -> {
            int insertIndex = 0;

            // Logger 필드 다음에 삽입
            for (int i = 0; i < clazz.getMembers().size(); i++) {
                if (clazz.getMembers().get(i) instanceof FieldDeclaration fd) {
                    if (fd.getVariables().stream().anyMatch(v -> v.getNameAsString().equals("log"))) {
                        insertIndex = i + 1;
                        break;
                    }
                }
            }

            for (Map.Entry<Integer, String> entry : magicNumbers.entrySet()) {
                int value = entry.getKey();
                String name = entry.getValue();

                VariableDeclarator var = new VariableDeclarator(
                    new com.github.javaparser.ast.type.PrimitiveType(
                        com.github.javaparser.ast.type.PrimitiveType.Primitive.INT),
                    name,
                    new IntegerLiteralExpr(value)
                );

                FieldDeclaration field = new FieldDeclaration(
                    new NodeList<>(
                        com.github.javaparser.ast.Modifier.privateModifier(),
                        com.github.javaparser.ast.Modifier.staticModifier(),
                        com.github.javaparser.ast.Modifier.finalModifier()
                    ),
                    var
                );

                clazz.getMembers().add(insertIndex++, field);

                fixes.add(new FixResult(
                    FixType.MAGIC_NUMBER,
                    clazz.getBegin().map(p -> p.line).orElse(0),
                    "매직 넘버 " + value + "를 상수로 추출",
                    String.valueOf(value),
                    "private static final int " + name + " = " + value + ";"
                ));
            }
        });

        // 리터럴을 상수 참조로 변경
        for (IntegerLiteralExpr literal : literals) {
            int value = Integer.parseInt(literal.getValue());
            String name = magicNumbers.get(value);
            if (name != null) {
                literal.replace(new NameExpr(name));
            }
        }
    }

    private String suggestConstantName(int value, IntegerLiteralExpr literal) {
        // 컨텍스트에서 이름 추론 시도
        Optional<MethodCallExpr> methodCall = literal.findAncestor(MethodCallExpr.class);
        if (methodCall.isPresent()) {
            String methodName = methodCall.get().getNameAsString();
            if (methodName.contains("timeout") || methodName.contains("Timeout")) {
                return "TIMEOUT_" + value;
            }
            if (methodName.contains("size") || methodName.contains("Size")) {
                return "SIZE_" + value;
            }
            if (methodName.contains("max") || methodName.contains("Max")) {
                return "MAX_" + value;
            }
            if (methodName.contains("min") || methodName.contains("Min")) {
                return "MIN_" + value;
            }
        }

        // 기본 이름
        return "VALUE_" + value;
    }

    /**
     * 누락된 중괄호 추가
     */
    private void fixMissingBraces(CompilationUnit cu) {
        // if 문
        cu.findAll(IfStmt.class).forEach(ifStmt -> {
            if (!(ifStmt.getThenStmt() instanceof BlockStmt)) {
                int line = ifStmt.getBegin().map(p -> p.line).orElse(0);
                Statement thenStmt = ifStmt.getThenStmt();

                BlockStmt block = new BlockStmt(new NodeList<>(thenStmt.clone()));
                ifStmt.setThenStmt(block);

                fixes.add(new FixResult(
                    FixType.MISSING_BRACES,
                    line,
                    "if 문에 중괄호 추가",
                    "if (...) stmt;",
                    "if (...) { stmt; }"
                ));
            }

            ifStmt.getElseStmt().ifPresent(elseStmt -> {
                if (!(elseStmt instanceof BlockStmt) && !(elseStmt instanceof IfStmt)) {
                    BlockStmt block = new BlockStmt(new NodeList<>(elseStmt.clone()));
                    ifStmt.setElseStmt(block);
                }
            });
        });

        // for 문
        cu.findAll(ForStmt.class).forEach(forStmt -> {
            if (!(forStmt.getBody() instanceof BlockStmt)) {
                int line = forStmt.getBegin().map(p -> p.line).orElse(0);
                Statement body = forStmt.getBody();

                BlockStmt block = new BlockStmt(new NodeList<>(body.clone()));
                forStmt.setBody(block);

                fixes.add(new FixResult(
                    FixType.MISSING_BRACES,
                    line,
                    "for 문에 중괄호 추가",
                    "for (...) stmt;",
                    "for (...) { stmt; }"
                ));
            }
        });

        // while 문
        cu.findAll(WhileStmt.class).forEach(whileStmt -> {
            if (!(whileStmt.getBody() instanceof BlockStmt)) {
                int line = whileStmt.getBegin().map(p -> p.line).orElse(0);
                Statement body = whileStmt.getBody();

                BlockStmt block = new BlockStmt(new NodeList<>(body.clone()));
                whileStmt.setBody(block);

                fixes.add(new FixResult(
                    FixType.MISSING_BRACES,
                    line,
                    "while 문에 중괄호 추가",
                    "while (...) stmt;",
                    "while (...) { stmt; }"
                ));
            }
        });
    }

    /**
     * 깊은 중첩 → Early Return 패턴
     */
    private void fixDeepNesting(CompilationUnit cu) {
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            method.getBody().ifPresent(body -> {
                List<Statement> statements = body.getStatements();

                if (statements.size() == 1 && statements.get(0) instanceof IfStmt ifStmt) {
                    // 단일 if 문으로 감싸진 경우 → Early Return 적용
                    if (canApplyEarlyReturn(ifStmt, method)) {
                        applyEarlyReturn(ifStmt, body, method);
                    }
                }
            });
        });
    }

    private boolean canApplyEarlyReturn(IfStmt ifStmt, MethodDeclaration method) {
        // else가 없고, then 블록이 있는 경우
        if (ifStmt.getElseStmt().isPresent()) return false;
        if (!(ifStmt.getThenStmt() instanceof BlockStmt)) return false;

        // 반환 타입이 void인 경우에만
        return method.getType().isVoidType();
    }

    private void applyEarlyReturn(IfStmt ifStmt, BlockStmt body, MethodDeclaration method) {
        int line = ifStmt.getBegin().map(p -> p.line).orElse(0);

        // 조건 반전
        Expression condition = ifStmt.getCondition();
        Expression negated = negateCondition(condition);

        // Early return 문
        IfStmt earlyReturn = new IfStmt(negated, new ReturnStmt(), null);

        // 원래 then 블록의 문장들
        BlockStmt thenBlock = (BlockStmt) ifStmt.getThenStmt();
        List<Statement> thenStatements = new ArrayList<>(thenBlock.getStatements());

        // body 재구성
        body.getStatements().clear();
        body.addStatement(earlyReturn);
        thenStatements.forEach(body::addStatement);

        fixes.add(new FixResult(
            FixType.DEEP_NESTING,
            line,
            "Early Return 패턴 적용",
            "if (condition) { ... }",
            "if (!condition) return; ..."
        ));
    }

    private Expression negateCondition(Expression condition) {
        if (condition instanceof UnaryExpr unary &&
            unary.getOperator() == UnaryExpr.Operator.LOGICAL_COMPLEMENT) {
            // !!x → x
            return unary.getExpression().clone();
        }

        if (condition instanceof BinaryExpr binary) {
            BinaryExpr.Operator negated = switch (binary.getOperator()) {
                case EQUALS -> BinaryExpr.Operator.NOT_EQUALS;
                case NOT_EQUALS -> BinaryExpr.Operator.EQUALS;
                case LESS -> BinaryExpr.Operator.GREATER_EQUALS;
                case GREATER -> BinaryExpr.Operator.LESS_EQUALS;
                case LESS_EQUALS -> BinaryExpr.Operator.GREATER;
                case GREATER_EQUALS -> BinaryExpr.Operator.LESS;
                default -> null;
            };

            if (negated != null) {
                return new BinaryExpr(
                    binary.getLeft().clone(),
                    binary.getRight().clone(),
                    negated
                );
            }
        }

        // 기본: !condition
        return new UnaryExpr(
            new EnclosedExpr(condition.clone()),
            UnaryExpr.Operator.LOGICAL_COMPLEMENT
        );
    }

    /**
     * 끝 공백 제거
     */
    private String fixTrailingWhitespace(String code) {
        String[] lines = code.split("\n", -1);
        int fixCount = 0;

        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].replaceAll("\\s+$", "");
            if (!trimmed.equals(lines[i])) {
                lines[i] = trimmed;
                fixCount++;
            }
        }

        if (fixCount > 0) {
            fixes.add(new FixResult(
                FixType.TRAILING_WHITESPACE,
                0,
                fixCount + "줄에서 끝 공백 제거",
                "",
                ""
            ));
        }

        return String.join("\n", lines);
    }

    // ============ Inner Classes ============

    public record FixResult(
        FixType type,
        int line,
        String description,
        String before,
        String after
    ) {}

    public record FixReport(
        String fixedCode,
        List<FixResult> fixes,
        boolean success,
        String error
    ) {
        public int getFixCount() {
            return fixes.size();
        }

        public String formatReport() {
            StringBuilder sb = new StringBuilder();

            sb.append("\n").append("=".repeat(60)).append("\n");
            sb.append("🔧 자동 수정 결과\n");
            sb.append("=".repeat(60)).append("\n\n");

            if (!success) {
                sb.append("❌ 수정 실패: ").append(error).append("\n");
                return sb.toString();
            }

            if (fixes.isEmpty()) {
                sb.append("✅ 수정할 이슈가 없습니다.\n");
                return sb.toString();
            }

            sb.append(String.format("📝 총 %d개 수정\n\n", fixes.size()));

            // 타입별 그룹화
            Map<FixType, List<FixResult>> byType = new LinkedHashMap<>();
            for (FixResult fix : fixes) {
                byType.computeIfAbsent(fix.type, k -> new ArrayList<>()).add(fix);
            }

            for (Map.Entry<FixType, List<FixResult>> entry : byType.entrySet()) {
                FixType type = entry.getKey();
                List<FixResult> typeFixes = entry.getValue();

                String icon = switch (type) {
                    case EMPTY_CATCH -> "🔇";
                    case SYSTEM_OUT -> "📝";
                    case MAGIC_NUMBER -> "🔢";
                    case STRING_CONCAT_LOOP -> "🔗";
                    case NULL_CHECK -> "❓";
                    case DEEP_NESTING -> "📐";
                    case MISSING_BRACES -> "{ }";
                    case TRAILING_WHITESPACE -> "␣";
                    default -> "🔧";
                };

                sb.append(String.format("%s %s (%d개)\n", icon, type, typeFixes.size()));

                for (FixResult fix : typeFixes) {
                    sb.append(String.format("   Line %d: %s\n", fix.line, fix.description));
                    if (!fix.before.isEmpty() || !fix.after.isEmpty()) {
                        sb.append(String.format("      - %s\n", fix.before));
                        sb.append(String.format("      + %s\n", fix.after));
                    }
                }
                sb.append("\n");
            }

            return sb.toString();
        }
    }

    /**
     * Builder 패턴
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Set<FixType> enabledFixes = EnumSet.allOf(FixType.class);

        public Builder enable(FixType... types) {
            this.enabledFixes = EnumSet.copyOf(Arrays.asList(types));
            return this;
        }

        public Builder disable(FixType... types) {
            for (FixType type : types) {
                this.enabledFixes.remove(type);
            }
            return this;
        }

        public Builder all() {
            this.enabledFixes = EnumSet.allOf(FixType.class);
            return this;
        }

        public AutoFixer build() {
            return new AutoFixer(enabledFixes);
        }
    }
}
