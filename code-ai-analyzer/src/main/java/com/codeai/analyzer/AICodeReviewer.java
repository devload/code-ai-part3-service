package com.codeai.analyzer;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.comments.Comment;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI 기반 코드 리뷰어
 *
 * 실제 LLM/CodeBERT 대신 규칙 기반 + 자연어 피드백 생성을 통해
 * AI 코드 리뷰의 개념을 시연합니다.
 *
 * 특징:
 * - 자연어 형태의 리뷰 코멘트 생성
 * - 코드 품질 점수 계산 (A-F 등급)
 * - 개선 제안 및 예시 코드 제공
 * - 코드 스타일 일관성 분석
 *
 * 실제 프로덕션에서는:
 * - CodeBERT, GraphCodeBERT 등 사용
 * - OpenAI Codex, Claude API 연동
 * - 로컬 LLM (Ollama + CodeLlama) 연동
 */
public class AICodeReviewer {

    private final JavaParser parser;
    private final List<ReviewComment> comments = new ArrayList<>();
    private final CodeQualityScore score = new CodeQualityScore();
    private final Random random = new Random();

    // 리뷰 템플릿
    private static final Map<String, List<String>> REVIEW_TEMPLATES = new HashMap<>();

    static {
        // 긴 메서드에 대한 코멘트
        REVIEW_TEMPLATES.put("LONG_METHOD", List.of(
            "이 메서드가 %d줄로 꽤 길어요. 읽기 쉽게 작은 메서드로 나눠보는 건 어떨까요?",
            "메서드 '%s'가 너무 많은 일을 하고 있는 것 같아요. Single Responsibility 원칙을 적용해볼까요?",
            "'%s' 메서드를 보니 여러 단계의 로직이 섞여 있네요. Extract Method로 정리하면 테스트하기도 쉬워질 거예요."
        ));

        // 복잡한 조건문
        REVIEW_TEMPLATES.put("COMPLEX_CONDITION", List.of(
            "이 조건문이 좀 복잡해 보여요. 의미 있는 이름의 변수로 추출하면 이해하기 쉬워질 거예요.",
            "조건이 여러 개 중첩되어 있네요. Early Return 패턴을 사용하면 가독성이 좋아질 것 같아요.",
            "복잡한 조건은 버그의 온상이에요. 각 조건을 설명하는 메서드로 분리해보세요."
        ));

        // 하드코딩된 값
        REVIEW_TEMPLATES.put("HARDCODED_VALUE", List.of(
            "여기 하드코딩된 값이 있네요. 상수나 설정으로 빼면 나중에 변경하기 쉬워요.",
            "이 매직 넘버가 무슨 의미인지 모르겠어요. 의미 있는 이름의 상수로 정의해주세요.",
            "'%s' 같은 값은 나중에 바뀔 수 있어요. 설정 파일이나 환경 변수로 관리하는 게 좋아요."
        ));

        // 명명 규칙
        REVIEW_TEMPLATES.put("NAMING", List.of(
            "'%s'라는 이름이 좀 모호해요. 더 설명적인 이름을 사용하면 코드 리뷰할 때 이해가 빨라요.",
            "변수명이 너무 짧아요. 축약어보다는 명확한 이름을 선호해요.",
            "이 메서드 이름이 하는 일을 잘 설명하지 못하는 것 같아요. 동사로 시작하는 이름은 어떨까요?"
        ));

        // 에러 처리
        REVIEW_TEMPLATES.put("ERROR_HANDLING", List.of(
            "예외를 무시하고 있네요. 최소한 로그라도 남기는 게 좋아요.",
            "catch 블록이 비어 있어요. 나중에 디버깅할 때 어려움을 겪을 수 있어요.",
            "Exception을 그냥 잡기보다 구체적인 예외 타입을 사용하면 어떨까요?"
        ));

        // 주석
        REVIEW_TEMPLATES.put("COMMENTS", List.of(
            "이 복잡한 로직에 주석이 없네요. 왜 이렇게 구현했는지 설명을 추가해주세요.",
            "주석이 코드와 맞지 않는 것 같아요. 주석을 업데이트하거나 삭제해주세요.",
            "좋은 코드는 자체로 설명이 되어야 해요. 주석 대신 메서드 이름으로 의도를 표현해보세요."
        ));

        // 보안
        REVIEW_TEMPLATES.put("SECURITY", List.of(
            "🚨 보안 이슈가 보여요! 사용자 입력을 검증 없이 사용하면 안 돼요.",
            "비밀번호나 API 키가 코드에 하드코딩되어 있어요. 절대 안 됩니다!",
            "SQL 쿼리를 문자열 연결로 만들고 있네요. SQL Injection 위험이 있어요."
        ));

        // 성능
        REVIEW_TEMPLATES.put("PERFORMANCE", List.of(
            "루프 안에서 무거운 작업을 하고 있네요. 루프 밖으로 빼면 성능이 좋아질 거예요.",
            "매번 새 객체를 생성하고 있어요. 재사용할 수 있는지 확인해보세요.",
            "이 연산이 O(n²)으로 보여요. 더 효율적인 알고리즘이 있을 것 같아요."
        ));

        // 칭찬
        REVIEW_TEMPLATES.put("PRAISE", List.of(
            "👍 깔끔한 코드네요! 읽기 쉽게 잘 작성되어 있어요.",
            "메서드 분리가 잘 되어 있어서 이해하기 쉬워요.",
            "명명 규칙을 일관성 있게 따르고 있네요. 좋아요!",
            "예외 처리가 잘 되어 있어요. 프로덕션 품질이에요.",
            "단위 테스트하기 좋은 구조네요. 잘 설계되어 있어요."
        ));
    }

    public AICodeReviewer() {
        ParserConfiguration config = new ParserConfiguration();
        config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        this.parser = new JavaParser(config);
    }

    /**
     * AI 코드 리뷰 수행
     */
    public AIReviewResult review(String code) {
        comments.clear();
        score.reset();

        ParseResult<CompilationUnit> parseResult = parser.parse(code);

        if (!parseResult.isSuccessful()) {
            comments.add(new ReviewComment(
                ReviewType.ERROR,
                0,
                "코드를 파싱할 수 없어요. 문법 오류가 있는지 확인해주세요.",
                null
            ));
            return new AIReviewResult(comments, score, false);
        }

        CompilationUnit cu = parseResult.getResult().orElseThrow();

        // 다양한 관점에서 리뷰
        reviewCodeStructure(cu);
        reviewNaming(cu);
        reviewComplexity(cu);
        reviewErrorHandling(cu);
        reviewSecurity(cu);
        reviewPerformance(cu);
        reviewComments(cu, code);
        reviewBestPractices(cu);

        // 점수 계산
        calculateScore(cu);

        // 긍정적 피드백 추가 (코드가 괜찮으면)
        addPositiveFeedback(cu);

        return new AIReviewResult(comments, score, true);
    }

    /**
     * 코드 구조 리뷰
     */
    private void reviewCodeStructure(CompilationUnit cu) {
        // 긴 메서드 검사
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            method.getRange().ifPresent(range -> {
                int lines = range.end.line - range.begin.line + 1;
                if (lines > 30) {
                    addComment(ReviewType.SUGGESTION, range.begin.line,
                        pickTemplate("LONG_METHOD", lines, method.getNameAsString()),
                        generateExtractMethodExample(method.getNameAsString()));
                    score.structureScore -= 10;
                } else if (lines > 20) {
                    score.structureScore -= 5;
                }
            });
        });

        // 너무 많은 메서드
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
            int methodCount = clazz.getMethods().size();
            if (methodCount > 15) {
                addComment(ReviewType.SUGGESTION, clazz.getBegin().map(p -> p.line).orElse(0),
                    String.format("클래스 '%s'에 메서드가 %d개나 있어요. 책임이 너무 많은 것 같아요. 분리를 고려해보세요.",
                        clazz.getNameAsString(), methodCount),
                    null);
                score.structureScore -= 10;
            }
        });

        // 깊은 중첩
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            int maxDepth = calculateMaxNestingDepth(method);
            if (maxDepth > 4) {
                addComment(ReviewType.SUGGESTION, method.getBegin().map(p -> p.line).orElse(0),
                    String.format("'%s' 메서드의 중첩이 %d단계나 되네요. 읽기 어려워요. Early Return이나 메서드 추출을 고려해보세요.",
                        method.getNameAsString(), maxDepth),
                    generateEarlyReturnExample());
                score.structureScore -= 15;
            }
        });
    }

    /**
     * 명명 규칙 리뷰
     */
    private void reviewNaming(CompilationUnit cu) {
        // 짧은 변수명
        cu.findAll(VariableDeclarator.class).forEach(var -> {
            String name = var.getNameAsString();
            if (name.length() <= 2 && !name.equals("id") && !name.equals("i") && !name.equals("j")) {
                addComment(ReviewType.SUGGESTION, var.getBegin().map(p -> p.line).orElse(0),
                    pickTemplate("NAMING", name),
                    null);
                score.readabilityScore -= 5;
            }
        });

        // 클래스명 규칙
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
            String name = clazz.getNameAsString();
            if (!Character.isUpperCase(name.charAt(0))) {
                addComment(ReviewType.ISSUE, clazz.getBegin().map(p -> p.line).orElse(0),
                    String.format("클래스명 '%s'은 대문자로 시작해야 해요. Java 컨벤션이에요.", name),
                    "// 수정: " + Character.toUpperCase(name.charAt(0)) + name.substring(1));
                score.readabilityScore -= 10;
            }
        });

        // 메서드명 규칙
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            String name = method.getNameAsString();
            if (Character.isUpperCase(name.charAt(0)) && !name.equals(method.getParentNode()
                    .filter(p -> p instanceof ClassOrInterfaceDeclaration)
                    .map(p -> ((ClassOrInterfaceDeclaration) p).getNameAsString())
                    .orElse(""))) {
                addComment(ReviewType.ISSUE, method.getBegin().map(p -> p.line).orElse(0),
                    String.format("메서드명 '%s'은 소문자로 시작해야 해요.", name),
                    null);
                score.readabilityScore -= 5;
            }
        });
    }

    /**
     * 복잡도 리뷰
     */
    private void reviewComplexity(CompilationUnit cu) {
        // 복잡한 조건문
        cu.findAll(IfStmt.class).forEach(ifStmt -> {
            String condition = ifStmt.getCondition().toString();
            int operators = countLogicalOperators(condition);
            if (operators >= 3) {
                addComment(ReviewType.SUGGESTION, ifStmt.getBegin().map(p -> p.line).orElse(0),
                    pickTemplate("COMPLEX_CONDITION"),
                    generateExtractConditionExample(condition));
                score.maintainabilityScore -= 10;
            }
        });

        // 긴 메서드 체인
        cu.findAll(MethodCallExpr.class).forEach(call -> {
            int chainLength = countMethodChain(call);
            if (chainLength > 5) {
                addComment(ReviewType.SUGGESTION, call.getBegin().map(p -> p.line).orElse(0),
                    String.format("메서드 체인이 %d개나 연결되어 있어요. 중간 변수를 사용하면 디버깅하기 쉬워져요.", chainLength),
                    null);
                score.maintainabilityScore -= 5;
            }
        });
    }

    /**
     * 에러 처리 리뷰
     */
    private void reviewErrorHandling(CompilationUnit cu) {
        // 빈 catch 블록
        cu.findAll(CatchClause.class).forEach(catchClause -> {
            if (catchClause.getBody().getStatements().isEmpty()) {
                addComment(ReviewType.ISSUE, catchClause.getBegin().map(p -> p.line).orElse(0),
                    pickTemplate("ERROR_HANDLING"),
                    generateLoggingExample());
                score.reliabilityScore -= 15;
            }
        });

        // 너무 광범위한 예외 처리
        cu.findAll(CatchClause.class).forEach(catchClause -> {
            String exceptionType = catchClause.getParameter().getType().asString();
            if (exceptionType.equals("Exception") || exceptionType.equals("Throwable")) {
                addComment(ReviewType.SUGGESTION, catchClause.getBegin().map(p -> p.line).orElse(0),
                    "Exception/Throwable을 직접 잡는 건 좋지 않아요. 구체적인 예외 타입을 사용해주세요.",
                    null);
                score.reliabilityScore -= 5;
            }
        });
    }

    /**
     * 보안 리뷰
     */
    private void reviewSecurity(CompilationUnit cu) {
        // 하드코딩된 비밀정보
        cu.findAll(FieldDeclaration.class).forEach(field -> {
            field.getVariables().forEach(var -> {
                String name = var.getNameAsString().toLowerCase();
                if ((name.contains("password") || name.contains("secret") ||
                     name.contains("apikey") || name.contains("token"))) {
                    var.getInitializer().ifPresent(init -> {
                        if (init instanceof StringLiteralExpr) {
                            addComment(ReviewType.CRITICAL, var.getBegin().map(p -> p.line).orElse(0),
                                pickTemplate("SECURITY"),
                                "// 환경 변수 사용 예시:\n// String password = System.getenv(\"DB_PASSWORD\");");
                            score.securityScore -= 30;
                        }
                    });
                }
            });
        });

        // SQL Injection 가능성
        cu.findAll(BinaryExpr.class).forEach(expr -> {
            if (expr.getOperator() == BinaryExpr.Operator.PLUS) {
                String exprStr = expr.toString().toLowerCase();
                if (exprStr.contains("select") || exprStr.contains("insert") ||
                    exprStr.contains("update") || exprStr.contains("delete")) {
                    addComment(ReviewType.CRITICAL, expr.getBegin().map(p -> p.line).orElse(0),
                        "🚨 SQL Injection 위험! 문자열 연결로 SQL을 만들면 안 돼요.",
                        "// PreparedStatement 사용:\n// PreparedStatement ps = conn.prepareStatement(\"SELECT * FROM users WHERE id = ?\");\n// ps.setInt(1, userId);");
                    score.securityScore -= 30;
                }
            }
        });
    }

    /**
     * 성능 리뷰
     */
    private void reviewPerformance(CompilationUnit cu) {
        // 루프 내 객체 생성
        cu.findAll(ForStmt.class).forEach(forStmt -> {
            forStmt.getBody().findAll(ObjectCreationExpr.class).forEach(creation -> {
                String typeName = creation.getType().asString();
                if (typeName.contains("SimpleDateFormat") || typeName.contains("Pattern") ||
                    typeName.contains("DecimalFormat")) {
                    addComment(ReviewType.SUGGESTION, creation.getBegin().map(p -> p.line).orElse(0),
                        String.format("루프 안에서 %s를 생성하고 있어요. 루프 밖에서 한 번만 생성하면 성능이 좋아져요.", typeName),
                        null);
                    score.performanceScore -= 10;
                }
            });
        });

        // 문자열 연결 루프
        cu.findAll(ForStmt.class).forEach(forStmt -> {
            forStmt.getBody().findAll(AssignExpr.class).forEach(assign -> {
                if (assign.getOperator() == AssignExpr.Operator.PLUS) {
                    String target = assign.getTarget().toString();
                    if (assign.getValue().toString().contains(target)) {
                        addComment(ReviewType.SUGGESTION, assign.getBegin().map(p -> p.line).orElse(0),
                            "루프에서 문자열을 += 로 연결하고 있어요. StringBuilder를 사용하면 훨씬 빨라요.",
                            "StringBuilder sb = new StringBuilder();\nfor (...) { sb.append(...); }\nString result = sb.toString();");
                        score.performanceScore -= 10;
                    }
                }
            });
        });
    }

    /**
     * 주석 리뷰
     */
    private void reviewComments(CompilationUnit cu, String code) {
        // TODO 주석 확인
        Pattern todoPattern = Pattern.compile("//\\s*TODO[:\\s](.+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = todoPattern.matcher(code);
        int todoCount = 0;
        while (matcher.find()) {
            todoCount++;
        }
        if (todoCount > 5) {
            addComment(ReviewType.SUGGESTION, 1,
                String.format("TODO 주석이 %d개나 있어요. 기술 부채가 쌓이고 있네요. 정리할 시간을 갖는 게 좋겠어요.", todoCount),
                null);
        }

        // 오래된 주석 (년도가 있는 주석)
        Pattern datePattern = Pattern.compile("//.*20[0-1][0-9]");
        if (datePattern.matcher(code).find()) {
            addComment(ReviewType.SUGGESTION, 1,
                "오래된 날짜가 포함된 주석이 있어요. 아직 유효한 내용인지 확인해주세요.",
                null);
        }
    }

    /**
     * 베스트 프랙티스 리뷰
     */
    private void reviewBestPractices(CompilationUnit cu) {
        // System.out.println 사용
        cu.findAll(MethodCallExpr.class).forEach(call -> {
            if (call.getNameAsString().equals("println") &&
                call.getScope().map(s -> s.toString().contains("System.out")).orElse(false)) {
                addComment(ReviewType.SUGGESTION, call.getBegin().map(p -> p.line).orElse(0),
                    "프로덕션 코드에서 System.out.println 대신 로깅 프레임워크를 사용하는 게 좋아요.",
                    "// Logger 사용 예시:\n// private static final Logger log = LoggerFactory.getLogger(MyClass.class);\n// log.info(\"메시지\");");
                score.maintainabilityScore -= 3;
            }
        });

        // 하드코딩된 매직 넘버
        cu.findAll(IntegerLiteralExpr.class).forEach(literal -> {
            int value = Integer.parseInt(literal.getValue());
            if (value != 0 && value != 1 && value != -1 && value > 10) {
                // 상수 선언 내부가 아닌 경우만
                boolean inConstant = literal.findAncestor(FieldDeclaration.class)
                    .map(f -> f.isStatic() && f.isFinal())
                    .orElse(false);

                if (!inConstant) {
                    addComment(ReviewType.SUGGESTION, literal.getBegin().map(p -> p.line).orElse(0),
                        pickTemplate("HARDCODED_VALUE", value),
                        String.format("private static final int MEANINGFUL_NAME = %d;", value));
                    score.maintainabilityScore -= 3;
                }
            }
        });
    }

    /**
     * 긍정적 피드백 추가
     */
    private void addPositiveFeedback(CompilationUnit cu) {
        // 점수가 높으면 칭찬
        if (score.getOverallScore() >= 80) {
            addComment(ReviewType.PRAISE, 1, pickTemplate("PRAISE"), null);
        }

        // 잘 작성된 Javadoc이 있으면
        long javadocCount = cu.findAll(MethodDeclaration.class).stream()
            .filter(m -> m.getJavadoc().isPresent())
            .count();
        if (javadocCount > 3) {
            addComment(ReviewType.PRAISE, 1,
                "Javadoc이 잘 작성되어 있네요! 다른 개발자들이 이해하기 쉬울 거예요.",
                null);
        }

        // 짧은 메서드가 많으면
        long shortMethods = cu.findAll(MethodDeclaration.class).stream()
            .filter(m -> m.getRange().map(r -> r.end.line - r.begin.line < 10).orElse(false))
            .count();
        if (shortMethods > 5) {
            addComment(ReviewType.PRAISE, 1,
                "메서드들이 짧고 집중되어 있어요. 좋은 구조예요!",
                null);
        }
    }

    /**
     * 점수 계산
     */
    private void calculateScore(CompilationUnit cu) {
        // 기본 점수 100에서 시작
        score.structureScore = Math.max(0, Math.min(100, 100 + score.structureScore));
        score.readabilityScore = Math.max(0, Math.min(100, 100 + score.readabilityScore));
        score.maintainabilityScore = Math.max(0, Math.min(100, 100 + score.maintainabilityScore));
        score.reliabilityScore = Math.max(0, Math.min(100, 100 + score.reliabilityScore));
        score.securityScore = Math.max(0, Math.min(100, 100 + score.securityScore));
        score.performanceScore = Math.max(0, Math.min(100, 100 + score.performanceScore));
    }

    // =========== 유틸리티 ===========

    private String pickTemplate(String category, Object... args) {
        List<String> templates = REVIEW_TEMPLATES.get(category);
        if (templates == null || templates.isEmpty()) {
            return "코드를 검토해주세요.";
        }
        String template = templates.get(random.nextInt(templates.size()));
        return args.length > 0 ? String.format(template, args) : template;
    }

    private void addComment(ReviewType type, int line, String message, String suggestion) {
        comments.add(new ReviewComment(type, line, message, suggestion));
    }

    private int calculateMaxNestingDepth(MethodDeclaration method) {
        int[] maxDepth = {0};
        int[] currentDepth = {0};

        method.walk(node -> {
            if (node instanceof IfStmt || node instanceof ForStmt ||
                node instanceof WhileStmt || node instanceof TryStmt) {
                currentDepth[0]++;
                maxDepth[0] = Math.max(maxDepth[0], currentDepth[0]);
            }
        });

        return maxDepth[0];
    }

    private int countLogicalOperators(String condition) {
        int count = 0;
        count += condition.split("&&").length - 1;
        count += condition.split("\\|\\|").length - 1;
        return count;
    }

    private int countMethodChain(MethodCallExpr call) {
        int count = 1;
        Expression current = call;
        while (current instanceof MethodCallExpr) {
            MethodCallExpr mce = (MethodCallExpr) current;
            if (mce.getScope().isPresent() && mce.getScope().get() instanceof MethodCallExpr) {
                count++;
                current = mce.getScope().get();
            } else {
                break;
            }
        }
        return count;
    }

    private String generateExtractMethodExample(String methodName) {
        return String.format("""
            // Before:
            // public void %s() {
            //     // 50줄의 코드...
            // }

            // After:
            public void %s() {
                validateInput();
                processData();
                saveResult();
            }

            private void validateInput() { /* ... */ }
            private void processData() { /* ... */ }
            private void saveResult() { /* ... */ }""", methodName, methodName);
    }

    private String generateEarlyReturnExample() {
        return """
            // Before (깊은 중첩):
            if (user != null) {
                if (user.isActive()) {
                    if (user.hasPermission()) {
                        // 실제 로직
                    }
                }
            }

            // After (Early Return):
            if (user == null) return;
            if (!user.isActive()) return;
            if (!user.hasPermission()) return;
            // 실제 로직""";
    }

    private String generateExtractConditionExample(String condition) {
        return """
            // Before:
            // if (user != null && user.isActive() && user.getAge() >= 18 && hasPermission)

            // After:
            boolean isValidUser = user != null && user.isActive();
            boolean isAdult = user.getAge() >= 18;
            boolean canProceed = isValidUser && isAdult && hasPermission;

            if (canProceed) {
                // ...
            }""";
    }

    private String generateLoggingExample() {
        return """
            // 최소한의 로깅:
            try {
                // ...
            } catch (IOException e) {
                log.error("파일 처리 실패: {}", e.getMessage(), e);
                throw new ProcessingException("처리 실패", e);
            }""";
    }

    // =========== 내부 클래스 ===========

    public enum ReviewType {
        PRAISE("👍"),      // 칭찬
        SUGGESTION("💡"),  // 제안
        ISSUE("⚠️"),       // 이슈
        CRITICAL("🚨"),    // 심각
        ERROR("❌");       // 에러

        public final String icon;
        ReviewType(String icon) { this.icon = icon; }
    }

    public static class ReviewComment {
        public final ReviewType type;
        public final int line;
        public final String message;
        public final String suggestion;

        public ReviewComment(ReviewType type, int line, String message, String suggestion) {
            this.type = type;
            this.line = line;
            this.message = message;
            this.suggestion = suggestion;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%s Line %d: %s", type.icon, line, message));
            if (suggestion != null && !suggestion.isEmpty()) {
                sb.append("\n\n").append(suggestion);
            }
            return sb.toString();
        }
    }

    public static class CodeQualityScore {
        public int structureScore = 0;      // 구조
        public int readabilityScore = 0;    // 가독성
        public int maintainabilityScore = 0; // 유지보수성
        public int reliabilityScore = 0;    // 신뢰성
        public int securityScore = 0;       // 보안
        public int performanceScore = 0;    // 성능

        public void reset() {
            structureScore = 0;
            readabilityScore = 0;
            maintainabilityScore = 0;
            reliabilityScore = 0;
            securityScore = 0;
            performanceScore = 0;
        }

        public int getOverallScore() {
            return (structureScore + readabilityScore + maintainabilityScore +
                    reliabilityScore + securityScore + performanceScore) / 6;
        }

        public String getGrade() {
            int score = getOverallScore();
            if (score >= 90) return "A";
            if (score >= 80) return "B";
            if (score >= 70) return "C";
            if (score >= 60) return "D";
            return "F";
        }

        @Override
        public String toString() {
            return String.format("""
                📊 코드 품질 점수:
                   ┌────────────────────────────────────┐
                   │ 구조        %3d/100  %s│
                   │ 가독성      %3d/100  %s│
                   │ 유지보수성  %3d/100  %s│
                   │ 신뢰성      %3d/100  %s│
                   │ 보안        %3d/100  %s│
                   │ 성능        %3d/100  %s│
                   ├────────────────────────────────────┤
                   │ 종합        %3d/100  등급: %s      │
                   └────────────────────────────────────┘""",
                structureScore, getBar(structureScore),
                readabilityScore, getBar(readabilityScore),
                maintainabilityScore, getBar(maintainabilityScore),
                reliabilityScore, getBar(reliabilityScore),
                securityScore, getBar(securityScore),
                performanceScore, getBar(performanceScore),
                getOverallScore(), getGrade());
        }

        private String getBar(int score) {
            int filled = score / 10;
            return "█".repeat(filled) + "░".repeat(10 - filled);
        }
    }

    public static class AIReviewResult {
        public final List<ReviewComment> comments;
        public final CodeQualityScore score;
        public final boolean parseSuccess;

        public AIReviewResult(List<ReviewComment> comments, CodeQualityScore score, boolean parseSuccess) {
            this.comments = new ArrayList<>(comments);
            this.score = score;
            this.parseSuccess = parseSuccess;
        }

        public String formatReport() {
            StringBuilder sb = new StringBuilder();

            sb.append("\n" + "=".repeat(60) + "\n");
            sb.append("🤖 AI 코드 리뷰 결과\n");
            sb.append("=".repeat(60) + "\n\n");

            if (!parseSuccess) {
                sb.append("❌ 코드 파싱 실패\n");
                return sb.toString();
            }

            // 점수
            sb.append(score.toString()).append("\n\n");

            // 통계
            long critical = comments.stream().filter(c -> c.type == ReviewType.CRITICAL).count();
            long issues = comments.stream().filter(c -> c.type == ReviewType.ISSUE).count();
            long suggestions = comments.stream().filter(c -> c.type == ReviewType.SUGGESTION).count();
            long praises = comments.stream().filter(c -> c.type == ReviewType.PRAISE).count();

            sb.append(String.format("📝 리뷰 코멘트: %d개\n", comments.size()));
            sb.append(String.format("   🚨 Critical: %d | ⚠️ Issue: %d | 💡 Suggestion: %d | 👍 Praise: %d\n\n",
                critical, issues, suggestions, praises));

            // Critical 먼저 출력
            List<ReviewComment> sortedComments = comments.stream()
                .sorted((a, b) -> a.type.ordinal() - b.type.ordinal())
                .toList();

            if (!sortedComments.isEmpty()) {
                sb.append("-".repeat(60) + "\n");
                for (ReviewComment comment : sortedComments) {
                    sb.append(comment.toString()).append("\n");
                    sb.append("-".repeat(60) + "\n");
                }
            }

            // 요약
            sb.append("\n📋 요약:\n");
            if (critical > 0) {
                sb.append("   🚨 심각한 이슈가 있어요! 반드시 수정해주세요.\n");
            }
            if (score.getGrade().equals("A") || score.getGrade().equals("B")) {
                sb.append("   ✅ 전반적으로 잘 작성된 코드예요!\n");
            } else if (score.getGrade().equals("C")) {
                sb.append("   ⚠️ 몇 가지 개선하면 더 좋아질 거예요.\n");
            } else {
                sb.append("   ❌ 리팩토링이 필요해 보여요.\n");
            }

            return sb.toString();
        }
    }
}
