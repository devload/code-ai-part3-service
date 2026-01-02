package com.codeai.analyzer.type;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Symbol Solver 기반 타입 분석기
 *
 * JavaParser Symbol Solver를 사용하여:
 * - 변수/표현식의 실제 타입 해석
 * - 메서드 호출 체인 추적
 * - 상속 관계 분석
 * - 타입 안전성 검사
 */
public class TypeResolver {

    private final JavaParser parser;
    private final CombinedTypeSolver typeSolver;
    private final List<TypeIssue> issues = new ArrayList<>();
    private final TypeMetrics metrics = new TypeMetrics();

    public TypeResolver() {
        this(null);
    }

    public TypeResolver(Path projectRoot) {
        this.typeSolver = new CombinedTypeSolver();

        // 1. JRE 표준 라이브러리
        typeSolver.add(new ReflectionTypeSolver());

        // 2. 프로젝트 소스 (있으면)
        if (projectRoot != null && Files.isDirectory(projectRoot)) {
            try {
                // src/main/java 찾기
                Path srcMain = projectRoot.resolve("src/main/java");
                if (Files.isDirectory(srcMain)) {
                    typeSolver.add(new JavaParserTypeSolver(srcMain));
                } else {
                    typeSolver.add(new JavaParserTypeSolver(projectRoot));
                }
            } catch (Exception e) {
                // 무시
            }
        }

        // Symbol Solver 설정
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        ParserConfiguration config = new ParserConfiguration();
        config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        config.setSymbolResolver(symbolSolver);

        this.parser = new JavaParser(config);
    }

    /**
     * 프로젝트 경로 추가 (추가 소스 디렉토리)
     */
    public void addSourcePath(Path sourcePath) {
        if (Files.isDirectory(sourcePath)) {
            typeSolver.add(new JavaParserTypeSolver(sourcePath));
        }
    }

    /**
     * JAR 파일 추가
     */
    public void addJarPath(Path jarPath) throws IOException {
        if (Files.isRegularFile(jarPath) && jarPath.toString().endsWith(".jar")) {
            typeSolver.add(new JarTypeSolver(jarPath));
        }
    }

    /**
     * 코드 분석 (타입 해석 포함)
     */
    public TypeAnalysisResult analyze(String code) {
        issues.clear();
        metrics.reset();

        ParseResult<CompilationUnit> parseResult = parser.parse(code);

        if (!parseResult.isSuccessful()) {
            return new TypeAnalysisResult(issues, metrics, null, false);
        }

        CompilationUnit cu = parseResult.getResult().orElseThrow();

        // 타입 분석 수행
        analyzeTypes(cu);
        analyzeMethodCalls(cu);
        analyzeInheritance(cu);
        checkTypeIssues(cu);

        return new TypeAnalysisResult(issues, metrics, cu, true);
    }

    /**
     * 변수/필드 타입 분석
     */
    private void analyzeTypes(CompilationUnit cu) {
        // 필드 타입 수집
        cu.findAll(FieldDeclaration.class).forEach(field -> {
            field.getVariables().forEach(var -> {
                try {
                    ResolvedType resolvedType = var.getType().resolve();
                    metrics.resolvedTypes++;

                    TypeInfo typeInfo = new TypeInfo(
                        var.getNameAsString(),
                        resolvedType.describe(),
                        getTypeCategory(resolvedType),
                        var.getBegin().map(p -> p.line).orElse(0)
                    );
                    metrics.typeInfos.add(typeInfo);

                } catch (UnsolvedSymbolException e) {
                    metrics.unresolvedTypes++;
                    metrics.unresolvedTypeNames.add(var.getType().asString());
                } catch (Exception e) {
                    metrics.unresolvedTypes++;
                }
            });
        });

        // 지역 변수 타입 수집
        cu.findAll(VariableDeclarator.class).forEach(var -> {
            try {
                ResolvedType resolvedType = var.getType().resolve();
                metrics.resolvedTypes++;
            } catch (Exception e) {
                // 지역 변수는 해석 실패해도 무시
            }
        });
    }

    /**
     * 메서드 호출 분석
     */
    private void analyzeMethodCalls(CompilationUnit cu) {
        cu.findAll(MethodCallExpr.class).forEach(call -> {
            try {
                ResolvedMethodDeclaration resolved = call.resolve();
                metrics.resolvedMethodCalls++;

                MethodCallInfo callInfo = new MethodCallInfo(
                    call.getNameAsString(),
                    resolved.getQualifiedName(),
                    resolved.getReturnType().describe(),
                    resolved.getNumberOfParams(),
                    call.getBegin().map(p -> p.line).orElse(0)
                );
                metrics.methodCallInfos.add(callInfo);

                // 반환 타입이 void인데 값을 사용하는 경우
                if (resolved.getReturnType().isVoid()) {
                    if (isReturnValueUsed(call)) {
                        issues.add(new TypeIssue(
                            Severity.ERROR,
                            "VOID_RETURN_USED",
                            "void 메서드 '" + call.getNameAsString() + "'의 반환값을 사용하려고 합니다",
                            "void 메서드는 반환값이 없습니다.",
                            call.getBegin().map(p -> p.line).orElse(0)
                        ));
                    }
                }

            } catch (UnsolvedSymbolException e) {
                metrics.unresolvedMethodCalls++;
            } catch (Exception e) {
                metrics.unresolvedMethodCalls++;
            }
        });
    }

    /**
     * 상속 관계 분석
     */
    private void analyzeInheritance(CompilationUnit cu) {
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
            try {
                ResolvedReferenceTypeDeclaration resolved = clazz.resolve();
                metrics.classesAnalyzed++;

                // 상속 체인
                List<String> ancestors = new ArrayList<>();
                try {
                    resolved.getAllAncestors().forEach(ancestor ->
                        ancestors.add(ancestor.getQualifiedName())
                    );
                } catch (Exception e) {
                    // 일부 조상 해석 실패 무시
                }

                InheritanceInfo inheritInfo = new InheritanceInfo(
                    clazz.getNameAsString(),
                    resolved.getQualifiedName(),
                    ancestors,
                    clazz.isInterface(),
                    clazz.isAbstract()
                );
                metrics.inheritanceInfos.add(inheritInfo);

                // 너무 깊은 상속 체인 경고
                if (ancestors.size() > 5) {
                    issues.add(new TypeIssue(
                        Severity.WARNING,
                        "DEEP_INHERITANCE",
                        "클래스 '" + clazz.getNameAsString() + "'의 상속 깊이가 깊습니다 (" + ancestors.size() + ")",
                        "상속보다 조합(Composition)을 고려하세요.",
                        clazz.getBegin().map(p -> p.line).orElse(0)
                    ));
                }

            } catch (UnsolvedSymbolException e) {
                // 해석 실패
            } catch (Exception e) {
                // 무시
            }
        });
    }

    /**
     * 타입 관련 이슈 검사
     */
    private void checkTypeIssues(CompilationUnit cu) {
        // 1. Raw Type 사용 검사 (제네릭 없이 사용)
        cu.findAll(VariableDeclarator.class).forEach(var -> {
            Type type = var.getType();
            String typeStr = type.asString();

            if (isRawType(typeStr)) {
                issues.add(new TypeIssue(
                    Severity.WARNING,
                    "RAW_TYPE",
                    "Raw 타입 사용: " + typeStr,
                    "제네릭 타입 파라미터를 명시하세요. 예: List<String>",
                    var.getBegin().map(p -> p.line).orElse(0)
                ));
            }
        });

        // 2. Object로의 불필요한 캐스팅
        cu.findAll(CastExpr.class).forEach(cast -> {
            String targetType = cast.getType().asString();
            if (targetType.equals("Object")) {
                issues.add(new TypeIssue(
                    Severity.INFO,
                    "CAST_TO_OBJECT",
                    "Object로의 캐스팅은 불필요합니다",
                    "모든 객체는 이미 Object입니다.",
                    cast.getBegin().map(p -> p.line).orElse(0)
                ));
            }
        });

        // 3. instanceof 후 캐스팅
        cu.findAll(IfStmt.class).forEach(ifStmt -> {
            ifStmt.getCondition().ifInstanceOfExpr(instanceOf -> {
                // Java 16+ 패턴 매칭 권장
                String typeCheck = instanceOf.getType().asString();
                issues.add(new TypeIssue(
                    Severity.INFO,
                    "INSTANCEOF_PATTERN",
                    "instanceof 체크 후 캐스팅 대신 패턴 매칭 권장",
                    "Java 16+: if (obj instanceof String s) { s.length(); }",
                    ifStmt.getBegin().map(p -> p.line).orElse(0)
                ));
            });
        });

        // 4. 타입 일치 검사 (대입문)
        cu.findAll(AssignExpr.class).forEach(assign -> {
            try {
                Expression target = assign.getTarget();
                Expression value = assign.getValue();

                if (target instanceof NameExpr && value != null) {
                    ResolvedType targetType = target.calculateResolvedType();
                    ResolvedType valueType = value.calculateResolvedType();

                    if (!isAssignable(targetType, valueType)) {
                        issues.add(new TypeIssue(
                            Severity.ERROR,
                            "TYPE_MISMATCH",
                            "타입 불일치: " + valueType.describe() + " → " + targetType.describe(),
                            "호환되는 타입으로 변환하세요.",
                            assign.getBegin().map(p -> p.line).orElse(0)
                        ));
                    }
                }
            } catch (Exception e) {
                // 타입 해석 실패 시 무시
            }
        });

        // 5. Null 가능성 분석 (간단한 버전)
        cu.findAll(MethodCallExpr.class).forEach(call -> {
            call.getScope().ifPresent(scope -> {
                // .get() 호출 전 isPresent() 체크 여부
                if (call.getNameAsString().equals("get")) {
                    try {
                        ResolvedType scopeType = scope.calculateResolvedType();
                        if (scopeType.describe().contains("Optional")) {
                            issues.add(new TypeIssue(
                                Severity.WARNING,
                                "OPTIONAL_GET",
                                "Optional.get() 직접 호출은 위험합니다",
                                "orElse(), orElseThrow(), ifPresent() 사용을 권장합니다.",
                                call.getBegin().map(p -> p.line).orElse(0)
                            ));
                        }
                    } catch (Exception e) {
                        // 무시
                    }
                }
            });
        });
    }

    // =========== 유틸리티 ===========

    private String getTypeCategory(ResolvedType type) {
        if (type.isPrimitive()) return "PRIMITIVE";
        if (type.isArray()) return "ARRAY";
        if (type.isReferenceType()) {
            String name = type.describe();
            if (name.startsWith("java.lang.")) return "JAVA_LANG";
            if (name.startsWith("java.util.")) return "JAVA_UTIL";
            if (name.startsWith("java.io.")) return "JAVA_IO";
            return "REFERENCE";
        }
        return "OTHER";
    }

    private boolean isRawType(String typeStr) {
        Set<String> genericTypes = Set.of(
            "List", "Set", "Map", "Collection", "Iterator",
            "Optional", "Stream", "Comparable", "Iterable"
        );
        return genericTypes.contains(typeStr);
    }

    private boolean isReturnValueUsed(MethodCallExpr call) {
        return call.getParentNode()
            .filter(p -> p instanceof AssignExpr ||
                        p instanceof VariableDeclarator ||
                        p instanceof ReturnStmt ||
                        p instanceof MethodCallExpr)
            .isPresent();
    }

    private boolean isAssignable(ResolvedType target, ResolvedType value) {
        try {
            return target.isAssignableBy(value);
        } catch (Exception e) {
            return true; // 확인 불가 시 통과
        }
    }

    // =========== 내부 클래스 ===========

    public enum Severity {
        INFO("💡"), WARNING("⚠️"), ERROR("❌"), CRITICAL("🚨");

        public final String icon;
        Severity(String icon) { this.icon = icon; }
    }

    public static class TypeIssue {
        public final Severity severity;
        public final String code;
        public final String message;
        public final String suggestion;
        public final int line;

        public TypeIssue(Severity severity, String code, String message,
                        String suggestion, int line) {
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

    public static class TypeInfo {
        public final String name;
        public final String resolvedType;
        public final String category;
        public final int line;

        public TypeInfo(String name, String resolvedType, String category, int line) {
            this.name = name;
            this.resolvedType = resolvedType;
            this.category = category;
            this.line = line;
        }
    }

    public static class MethodCallInfo {
        public final String methodName;
        public final String qualifiedName;
        public final String returnType;
        public final int paramCount;
        public final int line;

        public MethodCallInfo(String methodName, String qualifiedName,
                             String returnType, int paramCount, int line) {
            this.methodName = methodName;
            this.qualifiedName = qualifiedName;
            this.returnType = returnType;
            this.paramCount = paramCount;
            this.line = line;
        }
    }

    public static class InheritanceInfo {
        public final String className;
        public final String qualifiedName;
        public final List<String> ancestors;
        public final boolean isInterface;
        public final boolean isAbstract;

        public InheritanceInfo(String className, String qualifiedName,
                              List<String> ancestors, boolean isInterface, boolean isAbstract) {
            this.className = className;
            this.qualifiedName = qualifiedName;
            this.ancestors = new ArrayList<>(ancestors);
            this.isInterface = isInterface;
            this.isAbstract = isAbstract;
        }
    }

    public static class TypeMetrics {
        public int resolvedTypes = 0;
        public int unresolvedTypes = 0;
        public int resolvedMethodCalls = 0;
        public int unresolvedMethodCalls = 0;
        public int classesAnalyzed = 0;
        public Set<String> unresolvedTypeNames = new HashSet<>();
        public List<TypeInfo> typeInfos = new ArrayList<>();
        public List<MethodCallInfo> methodCallInfos = new ArrayList<>();
        public List<InheritanceInfo> inheritanceInfos = new ArrayList<>();

        public void reset() {
            resolvedTypes = 0;
            unresolvedTypes = 0;
            resolvedMethodCalls = 0;
            unresolvedMethodCalls = 0;
            classesAnalyzed = 0;
            unresolvedTypeNames.clear();
            typeInfos.clear();
            methodCallInfos.clear();
            inheritanceInfos.clear();
        }

        public double getResolutionRate() {
            int total = resolvedTypes + unresolvedTypes;
            return total > 0 ? (double) resolvedTypes / total * 100 : 100;
        }

        public double getMethodResolutionRate() {
            int total = resolvedMethodCalls + unresolvedMethodCalls;
            return total > 0 ? (double) resolvedMethodCalls / total * 100 : 100;
        }

        @Override
        public String toString() {
            return String.format("""
                📊 타입 분석 메트릭:
                   타입 해석: %d개 성공, %d개 실패 (%.1f%% 성공)
                   메서드 호출: %d개 성공, %d개 실패 (%.1f%% 성공)
                   클래스 분석: %d개
                   미해석 타입: %s""",
                resolvedTypes, unresolvedTypes, getResolutionRate(),
                resolvedMethodCalls, unresolvedMethodCalls, getMethodResolutionRate(),
                classesAnalyzed,
                unresolvedTypeNames.isEmpty() ? "없음" :
                    unresolvedTypeNames.stream().limit(5).collect(Collectors.joining(", ")));
        }
    }

    public static class TypeAnalysisResult {
        public final List<TypeIssue> issues;
        public final TypeMetrics metrics;
        public final CompilationUnit cu;
        public final boolean parseSuccess;

        public TypeAnalysisResult(List<TypeIssue> issues, TypeMetrics metrics,
                                 CompilationUnit cu, boolean parseSuccess) {
            this.issues = new ArrayList<>(issues);
            this.metrics = metrics;
            this.cu = cu;
            this.parseSuccess = parseSuccess;
        }

        public String formatReport() {
            return formatReport(Severity.INFO);
        }

        public String formatReport(Severity minSeverity) {
            List<TypeIssue> filtered = issues.stream()
                .filter(i -> i.severity.ordinal() >= minSeverity.ordinal())
                .toList();

            long errors = filtered.stream().filter(i -> i.severity == Severity.ERROR).count();
            long warnings = filtered.stream().filter(i -> i.severity == Severity.WARNING).count();
            long info = filtered.stream().filter(i -> i.severity == Severity.INFO).count();

            StringBuilder sb = new StringBuilder();
            sb.append("\n" + "=".repeat(60) + "\n");
            sb.append("🔍 타입 분석 결과 (Symbol Solver)\n");
            sb.append("=".repeat(60) + "\n\n");

            if (!parseSuccess) {
                sb.append("❌ 코드 파싱 실패\n\n");
                return sb.toString();
            }

            sb.append(metrics.toString()).append("\n\n");

            sb.append(String.format("🔍 발견된 타입 이슈: %d개\n", filtered.size()));
            sb.append(String.format("   ❌ Error: %d | ⚠️ Warning: %d | 💡 Info: %d\n\n",
                errors, warnings, info));

            if (!filtered.isEmpty()) {
                sb.append("-".repeat(60) + "\n");
                for (TypeIssue issue : filtered) {
                    sb.append(issue.toString()).append("\n\n");
                }
            }

            // 상속 관계 출력
            if (!metrics.inheritanceInfos.isEmpty()) {
                sb.append("-".repeat(60) + "\n");
                sb.append("🌳 상속 관계:\n");
                for (InheritanceInfo info2 : metrics.inheritanceInfos) {
                    String type = info2.isInterface ? "interface" :
                                 info2.isAbstract ? "abstract class" : "class";
                    sb.append(String.format("   %s %s\n", type, info2.className));
                    if (!info2.ancestors.isEmpty()) {
                        sb.append("      ↳ " + String.join(" → ",
                            info2.ancestors.stream().limit(3)
                                .map(a -> a.substring(a.lastIndexOf('.') + 1))
                                .toList()));
                        if (info2.ancestors.size() > 3) {
                            sb.append(" → ...");
                        }
                        sb.append("\n");
                    }
                }
            }

            return sb.toString();
        }

        /**
         * 특정 메서드 호출 체인 추적
         */
        public String traceMethodCalls(String methodName) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n🔗 메서드 호출 추적: " + methodName + "\n");

            List<MethodCallInfo> calls = metrics.methodCallInfos.stream()
                .filter(c -> c.methodName.equals(methodName))
                .toList();

            if (calls.isEmpty()) {
                sb.append("   (호출 없음)\n");
            } else {
                for (MethodCallInfo call : calls) {
                    sb.append(String.format("   Line %d: %s → %s\n",
                        call.line, call.qualifiedName, call.returnType));
                }
            }

            return sb.toString();
        }
    }
}
