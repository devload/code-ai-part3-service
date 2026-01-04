package com.codeai.analyzer;

import java.util.ArrayList;
import java.util.List;

/**
 * 코드 품질 점수 계산기
 */
public class CodeScorer {

    public ScoreResult score(String code) {
        List<CategoryScore> categories = new ArrayList<>();
        int totalScore = 0;

        // 기본 점수 계산
        int structureScore = calculateStructureScore(code);
        int readabilityScore = calculateReadabilityScore(code);
        int maintainabilityScore = calculateMaintainabilityScore(code);
        int reliabilityScore = calculateReliabilityScore(code);
        int securityScore = calculateSecurityScore(code);
        int performanceScore = calculatePerformanceScore(code);

        categories.add(new CategoryScore("구조", structureScore, 20));
        categories.add(new CategoryScore("가독성", readabilityScore, 20));
        categories.add(new CategoryScore("유지보수성", maintainabilityScore, 20));
        categories.add(new CategoryScore("신뢰성", reliabilityScore, 15));
        categories.add(new CategoryScore("보안", securityScore, 15));
        categories.add(new CategoryScore("성능", performanceScore, 10));

        totalScore = structureScore + readabilityScore + maintainabilityScore +
                     reliabilityScore + securityScore + performanceScore;

        String grade = calculateGrade(totalScore);

        return new ScoreResult(totalScore, grade, categories);
    }

    private int calculateStructureScore(String code) {
        int score = 20;
        if (code.split("\n").length > 500) score -= 5;
        return Math.max(0, score);
    }

    private int calculateReadabilityScore(String code) {
        int score = 20;
        if (code.contains("System.out.println")) score -= 3;
        if (code.contains("System.err.println")) score -= 2;
        return Math.max(0, score);
    }

    private int calculateMaintainabilityScore(String code) {
        int score = 20;
        // 매직 넘버 체크
        if (code.matches(".*\\b\\d{4,}\\b.*")) score -= 3;
        return Math.max(0, score);
    }

    private int calculateReliabilityScore(String code) {
        int score = 15;
        if (code.contains("catch") && code.contains("{ }")) score -= 5;
        if (code.contains("catch (Exception e) {}")) score -= 5;
        return Math.max(0, score);
    }

    private int calculateSecurityScore(String code) {
        int score = 15;
        if (code.toLowerCase().contains("password") && code.contains("\"")) score -= 5;
        if (code.contains("+ userId") || code.contains("+ id")) score -= 5;
        return Math.max(0, score);
    }

    private int calculatePerformanceScore(String code) {
        int score = 10;
        if (code.contains("Thread.sleep")) score -= 2;
        return Math.max(0, score);
    }

    private String calculateGrade(int totalScore) {
        if (totalScore >= 90) return "A";
        if (totalScore >= 80) return "B";
        if (totalScore >= 70) return "C";
        if (totalScore >= 60) return "D";
        return "F";
    }

    public record ScoreResult(int totalScore, String grade, List<CategoryScore> categories) {}

    public record CategoryScore(String name, int score, int maxScore) {}
}
