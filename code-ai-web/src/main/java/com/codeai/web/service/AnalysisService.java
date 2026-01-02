package com.codeai.web.service;

import com.codeai.analyzer.AICodeReviewer;
import com.codeai.analyzer.ASTAnalyzer;
import com.codeai.analyzer.CodeScorer;
import com.codeai.analyzer.fix.AutoFixer;
import com.codeai.analyzer.fix.LLMAutoFixer;
import com.codeai.analyzer.llm.LLMCodeReviewer;
import com.codeai.web.model.AnalysisRequest;
import com.codeai.web.model.AnalysisResponse;
import com.codeai.web.model.AnalysisResponse.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 코드 분석 서비스
 */
@Service
public class AnalysisService {

    private final Map<String, AnalysisResponse> analysisCache = new ConcurrentHashMap<>();
    private final AICodeReviewer reviewer = new AICodeReviewer();
    private final ASTAnalyzer astAnalyzer = new ASTAnalyzer();
    private final CodeScorer scorer = new CodeScorer();
    private final AutoFixer autoFixer = new AutoFixer();

    /**
     * 코드 분석 수행
     */
    public AnalysisResponse analyze(AnalysisRequest request) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        long startTime = System.currentTimeMillis();

        try {
            String code = request.code();
            AnalysisRequest.AnalysisOptions options = request.optionsOrDefault();

            // 1. 기본 분석
            List<Issue> issues = new ArrayList<>();
            List<String> positives = new ArrayList<>();

            // AST 분석
            if (options.includeAST()) {
                ASTAnalyzer.AnalysisResult astResult = astAnalyzer.analyze(code);
                for (ASTAnalyzer.ASTIssue issue : astResult.issues()) {
                    issues.add(new Issue(
                        issue.code(),
                        mapSeverity(issue.severity()),
                        issue.line(),
                        issue.column(),
                        issue.message(),
                        issue.suggestion(),
                        issue.category()
                    ));
                }
            }

            // AI 리뷰
            AICodeReviewer.ReviewResult reviewResult = reviewer.review(code);
            for (AICodeReviewer.ReviewIssue issue : reviewResult.issues()) {
                issues.add(new Issue(
                    issue.type().name(),
                    mapSeverity(issue.severity()),
                    issue.line(),
                    0,
                    issue.message(),
                    issue.suggestion(),
                    issue.category()
                ));
            }
            positives.addAll(reviewResult.positives());

            // 2. 점수 계산
            CodeScorer.ScoreResult scoreResult = scorer.score(code);
            Map<String, Integer> categories = new LinkedHashMap<>();
            for (CodeScorer.CategoryScore cat : scoreResult.categories()) {
                categories.put(cat.name(), cat.score());
            }

            ScoreSummary scores = new ScoreSummary(
                scoreResult.totalScore(),
                scoreResult.grade(),
                categories
            );

            // 3. 통계
            Statistics statistics = calculateStatistics(code, reviewResult);

            // 4. LLM 분석 (옵션)
            LLMInfo llmInfo = null;
            if (options.includeLLM()) {
                long llmStart = System.currentTimeMillis();
                LLMCodeReviewer llmReviewer = createLLMReviewer(options.llmProvider());
                LLMCodeReviewer.LLMReviewResult llmResult = llmReviewer.review(code);

                if (llmResult.success()) {
                    for (LLMCodeReviewer.LLMIssue issue : llmResult.issues()) {
                        issues.add(new Issue(
                            "LLM_" + issue.severity(),
                            issue.severity(),
                            issue.line(),
                            0,
                            issue.message(),
                            issue.suggestion(),
                            "llm"
                        ));
                    }
                    positives.addAll(llmResult.positives());
                }

                llmInfo = new LLMInfo(
                    options.llmProvider(),
                    llmResult.metadata() != null ? llmResult.metadata().model() : "unknown",
                    llmResult.metadata() != null ? llmResult.metadata().tokens() : 0,
                    System.currentTimeMillis() - llmStart
                );
            }

            // 5. 자동 수정 (옵션)
            String fixedCode = null;
            if (options.autoFix()) {
                AutoFixer.FixReport fixReport = autoFixer.fix(code);
                if (!fixReport.fixes().isEmpty()) {
                    fixedCode = fixReport.fixedCode();
                }
            }

            // 6. 결과 생성
            AnalysisResponse response = new AnalysisResponse(
                id,
                request.filename(),
                LocalDateTime.now(),
                scores,
                issues,
                positives,
                statistics,
                llmInfo,
                fixedCode
            );

            // 캐시 저장
            analysisCache.put(id, response);

            return response;

        } catch (Exception e) {
            return AnalysisResponse.error("분석 중 오류 발생: " + e.getMessage());
        }
    }

    /**
     * 캐시된 분석 결과 조회
     */
    public Optional<AnalysisResponse> getAnalysis(String id) {
        return Optional.ofNullable(analysisCache.get(id));
    }

    /**
     * 최근 분석 목록
     */
    public List<AnalysisResponse> getRecentAnalyses(int limit) {
        return analysisCache.values().stream()
            .sorted((a, b) -> b.timestamp().compareTo(a.timestamp()))
            .limit(limit)
            .toList();
    }

    /**
     * 빠른 점수 계산
     */
    public ScoreSummary quickScore(String code) {
        CodeScorer.ScoreResult result = scorer.score(code);
        Map<String, Integer> categories = new LinkedHashMap<>();
        for (CodeScorer.CategoryScore cat : result.categories()) {
            categories.put(cat.name(), cat.score());
        }
        return new ScoreSummary(result.totalScore(), result.grade(), categories);
    }

    private String mapSeverity(String severity) {
        return switch (severity.toUpperCase()) {
            case "ERROR", "CRITICAL" -> "CRITICAL";
            case "WARNING", "MAJOR" -> "WARNING";
            case "INFO", "MINOR" -> "INFO";
            default -> "INFO";
        };
    }

    private Statistics calculateStatistics(String code, AICodeReviewer.ReviewResult result) {
        String[] lines = code.split("\n");
        int totalLines = lines.length;
        int codeLines = 0;
        int commentLines = 0;

        boolean inBlockComment = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("/*")) inBlockComment = true;
            if (inBlockComment || trimmed.startsWith("//")) {
                commentLines++;
            } else if (!trimmed.isEmpty()) {
                codeLines++;
            }
            if (trimmed.endsWith("*/")) inBlockComment = false;
        }

        return new Statistics(
            totalLines,
            codeLines,
            commentLines,
            result.metadata().methodCount(),
            result.metadata().classCount(),
            result.metadata().cyclomaticComplexity()
        );
    }

    private LLMCodeReviewer createLLMReviewer(String provider) {
        return switch (provider.toLowerCase()) {
            case "openai" -> LLMCodeReviewer.withOpenAI();
            case "ollama" -> LLMCodeReviewer.withOllama("codellama:13b");
            default -> LLMCodeReviewer.withClaude();
        };
    }
}
