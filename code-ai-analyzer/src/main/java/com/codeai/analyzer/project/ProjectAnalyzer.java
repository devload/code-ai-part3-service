package com.codeai.analyzer.project;

import com.codeai.analyzer.ast.ASTAnalyzer;
import com.codeai.analyzer.ast.ASTAnalyzer.ASTAnalysisResult;
import com.codeai.analyzer.ast.ASTAnalyzer.ASTIssue;
import com.codeai.analyzer.ast.ASTAnalyzer.Severity;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 프로젝트 분석기 - 멀티파일 분석
 *
 * 단일 파일 분석을 넘어 프로젝트 전체를 분석하여:
 * - 프로젝트 전체 메트릭 집계
 * - 파일 간 의존성 분석
 * - 사용되지 않는 public 클래스/메서드 감지
 * - 순환 의존성 감지
 * - 패키지 구조 분석
 */
public class ProjectAnalyzer {

    private final JavaParser parser;
    private final ASTAnalyzer astAnalyzer;
    private final List<FileAnalysis> fileAnalyses = new ArrayList<>();
    private final List<ProjectIssue> projectIssues = new ArrayList<>();
    private final ProjectMetrics projectMetrics = new ProjectMetrics();

    // 크로스파일 분석용 데이터
    private final Map<String, ClassInfo> allClasses = new HashMap<>();
    private final Map<String, Set<String>> classDependencies = new HashMap<>();
    private final Map<String, Set<String>> classUsages = new HashMap<>();

    public ProjectAnalyzer() {
        ParserConfiguration config = new ParserConfiguration();
        config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        this.parser = new JavaParser(config);
        this.astAnalyzer = new ASTAnalyzer();
    }

    /**
     * 프로젝트 디렉토리 분석
     */
    public ProjectAnalysisResult analyze(Path projectPath) throws IOException {
        fileAnalyses.clear();
        projectIssues.clear();
        projectMetrics.reset();
        allClasses.clear();
        classDependencies.clear();
        classUsages.clear();

        // 1단계: 모든 Java 파일 수집
        List<Path> javaFiles = collectJavaFiles(projectPath);
        projectMetrics.totalFiles = javaFiles.size();

        // 2단계: 각 파일 분석 및 클래스 정보 수집
        for (Path file : javaFiles) {
            analyzeFile(file, projectPath);
        }

        // 3단계: 크로스파일 분석
        analyzeCrossFileIssues();

        // 4단계: 프로젝트 메트릭 집계
        aggregateMetrics();

        return new ProjectAnalysisResult(
            projectPath,
            fileAnalyses,
            projectIssues,
            projectMetrics
        );
    }

    /**
     * Java 파일 수집 (재귀적)
     */
    private List<Path> collectJavaFiles(Path projectPath) throws IOException {
        List<Path> javaFiles = new ArrayList<>();

        Files.walkFileTree(projectPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".java") &&
                    !file.toString().contains("/test/") &&
                    !file.toString().contains("\\test\\")) {
                    javaFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String dirName = dir.getFileName().toString();
                // 빌드 디렉토리, 숨김 디렉토리 제외
                if (dirName.startsWith(".") || dirName.equals("build") ||
                    dirName.equals("target") || dirName.equals("node_modules")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return javaFiles;
    }

    /**
     * 단일 파일 분석
     */
    private void analyzeFile(Path file, Path projectRoot) {
        try {
            String code = Files.readString(file);
            String relativePath = projectRoot.relativize(file).toString();

            // AST 파싱
            ParseResult<CompilationUnit> parseResult = parser.parse(code);

            if (!parseResult.isSuccessful()) {
                fileAnalyses.add(new FileAnalysis(
                    relativePath,
                    false,
                    new ArrayList<>(),
                    0, 0, 0
                ));
                return;
            }

            CompilationUnit cu = parseResult.getResult().orElseThrow();

            // AST 분석 실행
            ASTAnalysisResult astResult = astAnalyzer.analyze(code);

            // 클래스 정보 수집
            collectClassInfo(cu, relativePath);

            // 의존성 분석
            analyzeDependencies(cu, relativePath);

            // 파일 분석 결과 저장
            fileAnalyses.add(new FileAnalysis(
                relativePath,
                true,
                astResult.issues,
                astResult.metrics.classCount,
                astResult.metrics.methodCount,
                astResult.metrics.totalComplexity
            ));

        } catch (IOException e) {
            fileAnalyses.add(new FileAnalysis(
                file.toString(),
                false,
                new ArrayList<>(),
                0, 0, 0
            ));
        }
    }

    /**
     * 클래스 정보 수집
     */
    private void collectClassInfo(CompilationUnit cu, String filePath) {
        String packageName = cu.getPackageDeclaration()
            .map(pd -> pd.getNameAsString())
            .orElse("");

        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
            String className = clazz.getNameAsString();
            String fullName = packageName.isEmpty() ? className : packageName + "." + className;

            ClassInfo info = new ClassInfo(
                fullName,
                filePath,
                clazz.isPublic(),
                clazz.isInterface(),
                clazz.isAbstract()
            );

            // public 메서드 수집
            clazz.getMethods().stream()
                .filter(m -> m.isPublic())
                .forEach(m -> info.publicMethods.add(m.getNameAsString()));

            // public 필드 수집
            clazz.getFields().stream()
                .filter(f -> f.isPublic())
                .forEach(f -> f.getVariables().forEach(v ->
                    info.publicFields.add(v.getNameAsString())
                ));

            allClasses.put(fullName, info);
        });
    }

    /**
     * 의존성 분석
     */
    private void analyzeDependencies(CompilationUnit cu, String filePath) {
        String packageName = cu.getPackageDeclaration()
            .map(pd -> pd.getNameAsString())
            .orElse("");

        // 현재 파일의 클래스들
        Set<String> currentClasses = cu.findAll(ClassOrInterfaceDeclaration.class).stream()
            .map(c -> packageName.isEmpty() ? c.getNameAsString() : packageName + "." + c.getNameAsString())
            .collect(Collectors.toSet());

        // import 분석
        Set<String> imports = cu.getImports().stream()
            .filter(i -> !i.isAsterisk())
            .map(i -> i.getNameAsString())
            .collect(Collectors.toSet());

        // 타입 사용 분석
        Set<String> usedTypes = new HashSet<>();

        // 필드 타입
        cu.findAll(FieldDeclaration.class).forEach(field ->
            field.getVariables().forEach(var ->
                usedTypes.add(var.getType().asString())
            )
        );

        // 메서드 파라미터/리턴 타입
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            usedTypes.add(method.getType().asString());
            method.getParameters().forEach(p -> usedTypes.add(p.getType().asString()));
        });

        // 객체 생성
        cu.findAll(ObjectCreationExpr.class).forEach(creation ->
            usedTypes.add(creation.getType().asString())
        );

        // 메서드 호출의 스코프
        cu.findAll(MethodCallExpr.class).forEach(call ->
            call.getScope().ifPresent(scope -> {
                if (scope instanceof NameExpr) {
                    usedTypes.add(((NameExpr) scope).getNameAsString());
                }
            })
        );

        // 의존성 매핑
        for (String currentClass : currentClasses) {
            Set<String> deps = new HashSet<>();

            for (String imp : imports) {
                // 같은 프로젝트 내 클래스만
                if (allClasses.containsKey(imp) || usedTypes.contains(getSimpleName(imp))) {
                    deps.add(imp);
                    // 사용 기록
                    classUsages.computeIfAbsent(imp, k -> new HashSet<>()).add(currentClass);
                }
            }

            classDependencies.put(currentClass, deps);
        }
    }

    private String getSimpleName(String fullName) {
        int lastDot = fullName.lastIndexOf('.');
        return lastDot >= 0 ? fullName.substring(lastDot + 1) : fullName;
    }

    /**
     * 크로스파일 이슈 분석
     */
    private void analyzeCrossFileIssues() {
        // 1. 사용되지 않는 public 클래스
        for (Map.Entry<String, ClassInfo> entry : allClasses.entrySet()) {
            ClassInfo info = entry.getValue();
            String className = entry.getKey();

            if (info.isPublic && !info.isInterface) {
                Set<String> usages = classUsages.getOrDefault(className, Collections.emptySet());
                // 자기 자신을 제외한 사용처가 없으면
                usages.remove(className);

                if (usages.isEmpty() && !className.endsWith("Application") &&
                    !className.endsWith("Main") && !className.endsWith("Test")) {
                    projectIssues.add(new ProjectIssue(
                        Severity.INFO,
                        "UNUSED_PUBLIC_CLASS",
                        "public 클래스 '" + getSimpleName(className) + "'가 다른 곳에서 사용되지 않습니다",
                        "접근 제어자를 package-private으로 변경하거나 필요 없으면 삭제하세요.",
                        info.filePath
                    ));
                }
            }
        }

        // 2. 순환 의존성 감지
        detectCircularDependencies();

        // 3. God Package 감지 (한 패키지에 너무 많은 클래스)
        Map<String, List<String>> packageClasses = new HashMap<>();
        for (String className : allClasses.keySet()) {
            int lastDot = className.lastIndexOf('.');
            String pkg = lastDot >= 0 ? className.substring(0, lastDot) : "(default)";
            packageClasses.computeIfAbsent(pkg, k -> new ArrayList<>()).add(className);
        }

        for (Map.Entry<String, List<String>> entry : packageClasses.entrySet()) {
            if (entry.getValue().size() > 15) {
                projectIssues.add(new ProjectIssue(
                    Severity.WARNING,
                    "GOD_PACKAGE",
                    "패키지 '" + entry.getKey() + "'에 클래스가 너무 많습니다 (" + entry.getValue().size() + "개)",
                    "관련 클래스들을 하위 패키지로 분리하세요.",
                    entry.getKey()
                ));
            }
        }

        // 4. 깊은 패키지 구조
        for (String pkg : packageClasses.keySet()) {
            int depth = pkg.split("\\.").length;
            if (depth > 6) {
                projectIssues.add(new ProjectIssue(
                    Severity.INFO,
                    "DEEP_PACKAGE",
                    "패키지 '" + pkg + "'의 깊이가 너무 깊습니다 (깊이: " + depth + ")",
                    "패키지 구조를 단순화하세요.",
                    pkg
                ));
            }
        }
    }

    /**
     * 순환 의존성 감지 (DFS)
     */
    private void detectCircularDependencies() {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        List<String> path = new ArrayList<>();

        for (String className : classDependencies.keySet()) {
            if (detectCycleDFS(className, visited, recursionStack, path)) {
                // 순환 발견
                int cycleStart = path.lastIndexOf(className);
                if (cycleStart >= 0) {
                    List<String> cycle = new ArrayList<>(path.subList(cycleStart, path.size()));
                    cycle.add(className);

                    String cycleStr = cycle.stream()
                        .map(this::getSimpleName)
                        .collect(Collectors.joining(" → "));

                    projectIssues.add(new ProjectIssue(
                        Severity.WARNING,
                        "CIRCULAR_DEPENDENCY",
                        "순환 의존성 감지: " + cycleStr,
                        "의존성 방향을 정리하거나 인터페이스를 도입하세요.",
                        cycle.get(0)
                    ));
                }
            }
            path.clear();
            recursionStack.clear();
        }
    }

    private boolean detectCycleDFS(String current, Set<String> visited,
                                   Set<String> recursionStack, List<String> path) {
        if (recursionStack.contains(current)) {
            return true;
        }
        if (visited.contains(current)) {
            return false;
        }

        visited.add(current);
        recursionStack.add(current);
        path.add(current);

        Set<String> deps = classDependencies.getOrDefault(current, Collections.emptySet());
        for (String dep : deps) {
            if (allClasses.containsKey(dep)) {
                if (detectCycleDFS(dep, visited, recursionStack, path)) {
                    return true;
                }
            }
        }

        recursionStack.remove(current);
        path.remove(path.size() - 1);
        return false;
    }

    /**
     * 프로젝트 메트릭 집계
     */
    private void aggregateMetrics() {
        for (FileAnalysis fa : fileAnalyses) {
            if (fa.parseSuccess) {
                projectMetrics.successfullyParsed++;
                projectMetrics.totalClasses += fa.classCount;
                projectMetrics.totalMethods += fa.methodCount;
                projectMetrics.totalComplexity += fa.complexity;

                for (ASTIssue issue : fa.issues) {
                    switch (issue.severity) {
                        case CRITICAL -> projectMetrics.criticalIssues++;
                        case ERROR -> projectMetrics.errorIssues++;
                        case WARNING -> projectMetrics.warningIssues++;
                        case INFO -> projectMetrics.infoIssues++;
                    }
                }
            } else {
                projectMetrics.parseFailures++;
            }
        }

        // 프로젝트 레벨 이슈 카운트
        for (ProjectIssue issue : projectIssues) {
            switch (issue.severity) {
                case CRITICAL -> projectMetrics.criticalIssues++;
                case ERROR -> projectMetrics.errorIssues++;
                case WARNING -> projectMetrics.warningIssues++;
                case INFO -> projectMetrics.infoIssues++;
            }
        }

        projectMetrics.totalPackages = (int) allClasses.keySet().stream()
            .map(c -> {
                int lastDot = c.lastIndexOf('.');
                return lastDot >= 0 ? c.substring(0, lastDot) : "(default)";
            })
            .distinct()
            .count();
    }

    // =========== 내부 클래스 ===========

    public static class ClassInfo {
        public final String fullName;
        public final String filePath;
        public final boolean isPublic;
        public final boolean isInterface;
        public final boolean isAbstract;
        public final Set<String> publicMethods = new HashSet<>();
        public final Set<String> publicFields = new HashSet<>();

        public ClassInfo(String fullName, String filePath, boolean isPublic,
                        boolean isInterface, boolean isAbstract) {
            this.fullName = fullName;
            this.filePath = filePath;
            this.isPublic = isPublic;
            this.isInterface = isInterface;
            this.isAbstract = isAbstract;
        }
    }

    public static class FileAnalysis {
        public final String filePath;
        public final boolean parseSuccess;
        public final List<ASTIssue> issues;
        public final int classCount;
        public final int methodCount;
        public final int complexity;

        public FileAnalysis(String filePath, boolean parseSuccess, List<ASTIssue> issues,
                           int classCount, int methodCount, int complexity) {
            this.filePath = filePath;
            this.parseSuccess = parseSuccess;
            this.issues = new ArrayList<>(issues);
            this.classCount = classCount;
            this.methodCount = methodCount;
            this.complexity = complexity;
        }
    }

    public static class ProjectIssue {
        public final Severity severity;
        public final String code;
        public final String message;
        public final String suggestion;
        public final String location;

        public ProjectIssue(Severity severity, String code, String message,
                           String suggestion, String location) {
            this.severity = severity;
            this.code = code;
            this.message = message;
            this.suggestion = suggestion;
            this.location = location;
        }

        @Override
        public String toString() {
            return String.format("%s [%s] %s\n   → %s\n   📁 %s",
                severity.icon, code, message, suggestion, location);
        }
    }

    public static class ProjectMetrics {
        public int totalFiles = 0;
        public int successfullyParsed = 0;
        public int parseFailures = 0;
        public int totalClasses = 0;
        public int totalMethods = 0;
        public int totalComplexity = 0;
        public int totalPackages = 0;
        public int criticalIssues = 0;
        public int errorIssues = 0;
        public int warningIssues = 0;
        public int infoIssues = 0;

        public void reset() {
            totalFiles = 0;
            successfullyParsed = 0;
            parseFailures = 0;
            totalClasses = 0;
            totalMethods = 0;
            totalComplexity = 0;
            totalPackages = 0;
            criticalIssues = 0;
            errorIssues = 0;
            warningIssues = 0;
            infoIssues = 0;
        }

        public int getTotalIssues() {
            return criticalIssues + errorIssues + warningIssues + infoIssues;
        }

        @Override
        public String toString() {
            double avgComplexity = totalMethods > 0 ? (double) totalComplexity / totalMethods : 0;
            double avgMethodsPerClass = totalClasses > 0 ? (double) totalMethods / totalClasses : 0;

            return String.format("""
                📊 프로젝트 메트릭:
                   파일: %d개 (성공: %d, 실패: %d)
                   패키지: %d개 | 클래스: %d개 | 메서드: %d개
                   총 순환 복잡도: %d (평균: %.1f)
                   클래스당 평균 메서드: %.1f개""",
                totalFiles, successfullyParsed, parseFailures,
                totalPackages, totalClasses, totalMethods,
                totalComplexity, avgComplexity, avgMethodsPerClass);
        }
    }

    public static class ProjectAnalysisResult {
        public final Path projectPath;
        public final List<FileAnalysis> fileAnalyses;
        public final List<ProjectIssue> projectIssues;
        public final ProjectMetrics metrics;

        public ProjectAnalysisResult(Path projectPath, List<FileAnalysis> fileAnalyses,
                                    List<ProjectIssue> projectIssues, ProjectMetrics metrics) {
            this.projectPath = projectPath;
            this.fileAnalyses = new ArrayList<>(fileAnalyses);
            this.projectIssues = new ArrayList<>(projectIssues);
            this.metrics = metrics;
        }

        public String formatReport() {
            return formatReport(Severity.INFO, false);
        }

        public String formatReport(Severity minSeverity, boolean showFileDetails) {
            StringBuilder sb = new StringBuilder();

            sb.append("\n" + "=".repeat(70) + "\n");
            sb.append("📁 프로젝트 분석 결과: " + projectPath.getFileName() + "\n");
            sb.append("=".repeat(70) + "\n\n");

            // 메트릭
            sb.append(metrics.toString()).append("\n\n");

            // 이슈 요약
            sb.append(String.format("🔍 발견된 이슈: %d개\n", metrics.getTotalIssues()));
            sb.append(String.format("   🚨 Critical: %d | ❌ Error: %d | ⚠️ Warning: %d | 💡 Info: %d\n\n",
                metrics.criticalIssues, metrics.errorIssues,
                metrics.warningIssues, metrics.infoIssues));

            // 프로젝트 레벨 이슈
            List<ProjectIssue> filteredProjectIssues = projectIssues.stream()
                .filter(i -> i.severity.ordinal() >= minSeverity.ordinal())
                .toList();

            if (!filteredProjectIssues.isEmpty()) {
                sb.append("-".repeat(70) + "\n");
                sb.append("🌐 프로젝트 레벨 이슈:\n");
                sb.append("-".repeat(70) + "\n");
                for (ProjectIssue issue : filteredProjectIssues) {
                    sb.append(issue.toString()).append("\n\n");
                }
            }

            // 파일별 이슈 (옵션)
            if (showFileDetails) {
                sb.append("-".repeat(70) + "\n");
                sb.append("📄 파일별 이슈:\n");
                sb.append("-".repeat(70) + "\n");

                for (FileAnalysis fa : fileAnalyses) {
                    List<ASTIssue> filteredIssues = fa.issues.stream()
                        .filter(i -> i.severity.ordinal() >= minSeverity.ordinal())
                        .toList();

                    if (!filteredIssues.isEmpty()) {
                        sb.append("\n📄 " + fa.filePath + "\n");
                        for (ASTIssue issue : filteredIssues) {
                            sb.append("   " + issue.toString().replace("\n", "\n   ")).append("\n");
                        }
                    }
                }
            }

            // 점수 계산
            int score = 100
                - (metrics.criticalIssues * 20)
                - (metrics.errorIssues * 10)
                - (metrics.warningIssues * 3)
                - (metrics.infoIssues * 1);
            score = Math.max(0, Math.min(100, score));

            sb.append("\n" + "=".repeat(70) + "\n");
            sb.append(String.format("📈 프로젝트 품질 점수: %d/100 %s\n",
                score,
                score >= 80 ? "✅ 좋음" : score >= 60 ? "⚠️ 개선 필요" : "❌ 심각한 문제"));
            sb.append("=".repeat(70) + "\n");

            return sb.toString();
        }

        /**
         * 가장 문제가 많은 파일 Top N
         */
        public String getTopProblematicFiles(int n) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n🔥 문제가 많은 파일 Top " + n + ":\n");

            fileAnalyses.stream()
                .filter(fa -> fa.parseSuccess)
                .sorted((a, b) -> b.issues.size() - a.issues.size())
                .limit(n)
                .forEach(fa -> {
                    long critical = fa.issues.stream().filter(i -> i.severity == Severity.CRITICAL).count();
                    long warnings = fa.issues.stream().filter(i -> i.severity == Severity.WARNING).count();
                    sb.append(String.format("   %s: %d개 (🚨%d ⚠️%d)\n",
                        fa.filePath, fa.issues.size(), critical, warnings));
                });

            return sb.toString();
        }

        /**
         * 복잡도가 높은 파일 Top N
         */
        public String getTopComplexFiles(int n) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n🧩 복잡도가 높은 파일 Top " + n + ":\n");

            fileAnalyses.stream()
                .filter(fa -> fa.parseSuccess)
                .sorted((a, b) -> b.complexity - a.complexity)
                .limit(n)
                .forEach(fa -> {
                    double avgComplexity = fa.methodCount > 0 ?
                        (double) fa.complexity / fa.methodCount : 0;
                    sb.append(String.format("   %s: CC=%d (평균: %.1f, 메서드: %d개)\n",
                        fa.filePath, fa.complexity, avgComplexity, fa.methodCount));
                });

            return sb.toString();
        }
    }
}
