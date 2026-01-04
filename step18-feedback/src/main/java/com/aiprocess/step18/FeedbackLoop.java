package com.aiprocess.step18;

import java.util.*;

/**
 * STEP 18: 피드백 루프
 *
 * 핵심 질문: 결과를 어떻게 개선하는가?
 *
 * AI의 결과를 평가하고 지속적으로 개선하는 피드백 시스템입니다.
 *
 * ┌─────────────────────────────────────────────────────────┐
 * │ 피드백 루프 과정                                         │
 * │                                                          │
 * │ 1. 결과 평가                                             │
 * │    - 자동 검증 (빌드, 테스트)                            │
 * │    - 품질 메트릭 측정                                    │
 * │    - 사용자 피드백 수집                                  │
 * │                                                          │
 * │ 2. 개선 방향 결정                                        │
 * │    - 성공/실패 분석                                      │
 * │    - 패턴 학습                                           │
 * │    - 프롬프트 조정                                       │
 * │                                                          │
 * │ 3. 반복 실행                                             │
 * │    - 재시도 로직                                         │
 * │    - 대안 전략 적용                                      │
 * │    - 점진적 개선                                         │
 * └─────────────────────────────────────────────────────────┘
 */
public class FeedbackLoop {

    private final List<FeedbackEntry> history;
    private final Map<String, Integer> successPatterns;
    private final Map<String, Integer> failurePatterns;
    private int totalAttempts;
    private int successfulAttempts;

    public FeedbackLoop() {
        this.history = new ArrayList<>();
        this.successPatterns = new HashMap<>();
        this.failurePatterns = new HashMap<>();
        this.totalAttempts = 0;
        this.successfulAttempts = 0;
    }

    /**
     * 결과 평가 및 피드백 수집
     */
    public FeedbackResult evaluate(EvaluationInput input) {
        List<ValidationResult> validations = new ArrayList<>();
        double overallScore = 0;

        // 1. 자동 검증
        if (input.modifiedCode() != null) {
            // 구문 검증
            ValidationResult syntaxCheck = checkSyntax(input.modifiedCode());
            validations.add(syntaxCheck);

            // 이슈 해결 여부
            ValidationResult issueCheck = checkIssueResolved(input);
            validations.add(issueCheck);

            // 새로운 이슈 유발 여부
            ValidationResult regressionCheck = checkNoNewIssues(input);
            validations.add(regressionCheck);
        }

        // 2. 점수 계산
        long passedCount = validations.stream().filter(ValidationResult::passed).count();
        overallScore = validations.isEmpty() ? 0 : (double) passedCount / validations.size() * 100;

        boolean success = overallScore >= 80;

        // 3. 패턴 기록
        recordPatterns(input, success);

        // 4. 피드백 기록
        FeedbackEntry entry = new FeedbackEntry(
            input.taskType(),
            input.originalIssue(),
            success,
            overallScore,
            validations,
            System.currentTimeMillis()
        );
        history.add(entry);

        totalAttempts++;
        if (success) successfulAttempts++;

        // 5. 개선 제안 생성
        List<String> improvements = generateImprovements(validations, input);

        return new FeedbackResult(
            success,
            overallScore,
            validations,
            improvements,
            shouldRetry(validations),
            getRetryStrategy(validations)
        );
    }

    /**
     * 구문 검증
     */
    private ValidationResult checkSyntax(String code) {
        // 간단한 구문 체크 (실제로는 컴파일러 사용)
        boolean hasMatchingBraces = countChar(code, '{') == countChar(code, '}');
        boolean hasMatchingParens = countChar(code, '(') == countChar(code, ')');
        boolean hasMatchingBrackets = countChar(code, '[') == countChar(code, ']');

        boolean passed = hasMatchingBraces && hasMatchingParens && hasMatchingBrackets;

        return new ValidationResult(
            "SYNTAX_CHECK",
            passed,
            passed ? "구문이 올바릅니다" : "괄호 짝이 맞지 않습니다"
        );
    }

    /**
     * 이슈 해결 여부 검증
     */
    private ValidationResult checkIssueResolved(EvaluationInput input) {
        String issue = input.originalIssue();
        String newCode = input.modifiedCode();

        boolean resolved = switch (issue.toUpperCase()) {
            case "SYSTEM_OUT" -> !newCode.contains("System.out.println");
            case "SQL_INJECTION" -> !newCode.contains("+ id") && !newCode.contains("+ userId");
            case "EMPTY_CATCH" -> !newCode.matches("(?s).*catch\\s*\\([^)]+\\)\\s*\\{\\s*\\}.*");
            case "HARDCODED_SECRET" -> !newCode.matches("(?s).*password\\s*=\\s*\"[^\"]+\".*");
            default -> true;
        };

        return new ValidationResult(
            "ISSUE_RESOLVED",
            resolved,
            resolved ? "이슈가 해결되었습니다" : "이슈가 여전히 존재합니다"
        );
    }

    /**
     * 새로운 이슈 유발 여부 검증
     */
    private ValidationResult checkNoNewIssues(EvaluationInput input) {
        String newCode = input.modifiedCode();
        List<String> newIssues = new ArrayList<>();

        // 새로운 문제 체크
        if (newCode.contains("TODO") || newCode.contains("FIXME")) {
            newIssues.add("미완성 코드 마커 발견");
        }
        if (newCode.contains("null") && !input.originalCode().contains("null")) {
            newIssues.add("새로운 null 참조 추가됨");
        }

        boolean passed = newIssues.isEmpty();

        return new ValidationResult(
            "NO_REGRESSION",
            passed,
            passed ? "새로운 이슈 없음" : "새로운 이슈 발견: " + String.join(", ", newIssues)
        );
    }

    /**
     * 패턴 기록
     */
    private void recordPatterns(EvaluationInput input, boolean success) {
        String pattern = input.taskType() + ":" + input.originalIssue();

        if (success) {
            successPatterns.merge(pattern, 1, Integer::sum);
        } else {
            failurePatterns.merge(pattern, 1, Integer::sum);
        }
    }

    /**
     * 개선 제안 생성
     */
    private List<String> generateImprovements(List<ValidationResult> validations,
                                               EvaluationInput input) {
        List<String> improvements = new ArrayList<>();

        for (ValidationResult v : validations) {
            if (!v.passed()) {
                switch (v.checkType()) {
                    case "SYNTAX_CHECK" ->
                        improvements.add("코드 구문 오류 수정 필요 - 괄호 균형 확인");
                    case "ISSUE_RESOLVED" ->
                        improvements.add("원래 이슈(" + input.originalIssue() + ")가 해결되지 않음 - 다른 접근 방식 시도");
                    case "NO_REGRESSION" ->
                        improvements.add("새로운 문제 발생 - 변경 사항 최소화 필요");
                }
            }
        }

        // 패턴 기반 제안
        String pattern = input.taskType() + ":" + input.originalIssue();
        int failures = failurePatterns.getOrDefault(pattern, 0);
        if (failures >= 2) {
            improvements.add("이 유형의 작업이 반복 실패 - 프롬프트 수정 권장");
        }

        return improvements;
    }

    /**
     * 재시도 여부 결정
     */
    private boolean shouldRetry(List<ValidationResult> validations) {
        long failedCount = validations.stream().filter(v -> !v.passed()).count();
        return failedCount > 0 && failedCount < validations.size();
    }

    /**
     * 재시도 전략 결정
     */
    private RetryStrategy getRetryStrategy(List<ValidationResult> validations) {
        for (ValidationResult v : validations) {
            if (!v.passed()) {
                return switch (v.checkType()) {
                    case "SYNTAX_CHECK" -> RetryStrategy.DIFFERENT_MODEL;
                    case "ISSUE_RESOLVED" -> RetryStrategy.REFINED_PROMPT;
                    case "NO_REGRESSION" -> RetryStrategy.MINIMAL_CHANGE;
                    default -> RetryStrategy.SAME_WITH_CONTEXT;
                };
            }
        }
        return RetryStrategy.NONE;
    }

    /**
     * 통계 조회
     */
    public Statistics getStatistics() {
        double successRate = totalAttempts > 0
            ? (double) successfulAttempts / totalAttempts * 100
            : 0;

        Map<String, Double> patternSuccessRates = new HashMap<>();
        for (String pattern : successPatterns.keySet()) {
            int successes = successPatterns.getOrDefault(pattern, 0);
            int failures = failurePatterns.getOrDefault(pattern, 0);
            int total = successes + failures;
            if (total > 0) {
                patternSuccessRates.put(pattern, (double) successes / total * 100);
            }
        }

        return new Statistics(
            totalAttempts,
            successfulAttempts,
            successRate,
            patternSuccessRates
        );
    }

    private int countChar(String s, char c) {
        return (int) s.chars().filter(ch -> ch == c).count();
    }

    /**
     * 재시도 전략
     */
    public enum RetryStrategy {
        NONE("재시도 불필요"),
        SAME_WITH_CONTEXT("동일 모델, 추가 컨텍스트"),
        REFINED_PROMPT("프롬프트 수정 후 재시도"),
        DIFFERENT_MODEL("다른 모델로 시도"),
        MINIMAL_CHANGE("최소 변경 요청");

        private final String description;

        RetryStrategy(String description) {
            this.description = description;
        }

        public String getDescription() { return description; }
    }

    /**
     * 평가 입력
     */
    public record EvaluationInput(
        String taskType,
        String originalIssue,
        String originalCode,
        String modifiedCode
    ) {}

    /**
     * 검증 결과
     */
    public record ValidationResult(
        String checkType,
        boolean passed,
        String message
    ) {}

    /**
     * 피드백 결과
     */
    public record FeedbackResult(
        boolean success,
        double score,
        List<ValidationResult> validations,
        List<String> improvements,
        boolean shouldRetry,
        RetryStrategy retryStrategy
    ) {}

    /**
     * 피드백 기록
     */
    public record FeedbackEntry(
        String taskType,
        String issue,
        boolean success,
        double score,
        List<ValidationResult> validations,
        long timestamp
    ) {}

    /**
     * 통계
     */
    public record Statistics(
        int totalAttempts,
        int successfulAttempts,
        double successRate,
        Map<String, Double> patternSuccessRates
    ) {}
}
