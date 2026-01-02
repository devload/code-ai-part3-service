package com.codeai.intellij.toolwindow;

import com.codeai.analyzer.ai.AICodeReviewer;
import com.codeai.analyzer.ast.ASTAnalyzer;
import com.codeai.intellij.service.CodeAIService;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Code AI Review Tool Window Panel
 */
public class CodeAIToolWindowPanel extends JPanel implements Disposable, CodeAIService.ResultListener {

    private final Project project;
    private final JPanel contentPanel;
    private final JBLabel titleLabel;
    private final JBLabel gradeLabel;
    private final JPanel scorePanel;
    private final JPanel commentsPanel;

    public CodeAIToolWindowPanel(Project project) {
        this.project = project;
        setLayout(new BorderLayout());

        // 상단 헤더
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(JBUI.Borders.empty(10));
        headerPanel.setBackground(JBColor.background());

        titleLabel = new JBLabel("Code AI Review");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));

        gradeLabel = new JBLabel("");
        gradeLabel.setFont(gradeLabel.getFont().deriveFont(Font.BOLD, 24f));
        gradeLabel.setHorizontalAlignment(SwingConstants.CENTER);

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(gradeLabel, BorderLayout.EAST);

        // 점수 패널
        scorePanel = new JPanel();
        scorePanel.setLayout(new BoxLayout(scorePanel, BoxLayout.Y_AXIS));
        scorePanel.setBorder(JBUI.Borders.empty(10));
        scorePanel.setBackground(JBColor.background());

        // 코멘트 패널
        commentsPanel = new JPanel();
        commentsPanel.setLayout(new BoxLayout(commentsPanel, BoxLayout.Y_AXIS));
        commentsPanel.setBorder(JBUI.Borders.empty(10));
        commentsPanel.setBackground(JBColor.background());

        // 메인 콘텐츠
        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.add(scorePanel);
        contentPanel.add(new JSeparator());
        contentPanel.add(commentsPanel);

        JBScrollPane scrollPane = new JBScrollPane(contentPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(headerPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        // 초기 메시지
        showWelcomeMessage();

        // 서비스 리스너 등록
        CodeAIService service = project.getService(CodeAIService.class);
        if (service != null) {
            service.addListener(this);
            Disposer.register(this, () -> service.removeListener(this));
        }
    }

    private void showWelcomeMessage() {
        commentsPanel.removeAll();

        JBLabel welcomeLabel = new JBLabel("<html><div style='text-align: center; padding: 20px;'>" +
            "<p style='font-size: 14px;'>Java 파일을 열고 리뷰를 실행해주세요.</p>" +
            "<br>" +
            "<p style='font-size: 12px; color: gray;'>우클릭 → Code AI → AI Review</p>" +
            "<p style='font-size: 12px; color: gray;'>또는 Ctrl+Alt+R</p>" +
            "</div></html>");
        welcomeLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setBackground(JBColor.background());
        centerPanel.add(welcomeLabel);

        commentsPanel.add(centerPanel);
        commentsPanel.revalidate();
        commentsPanel.repaint();
    }

    @Override
    public void onResultUpdated(CodeAIService.ResultType type) {
        SwingUtilities.invokeLater(() -> {
            CodeAIService service = project.getService(CodeAIService.class);
            if (service == null) return;

            if (type == CodeAIService.ResultType.AI_REVIEW) {
                displayAIResult(service.getCurrentFileName(), service.getLatestAIResult());
            } else {
                displayASTResult(service.getCurrentFileName(), service.getLatestASTResult());
            }
        });
    }

    private void displayAIResult(String fileName, AICodeReviewer.AIReviewResult result) {
        if (result == null) return;

        titleLabel.setText("AI Review: " + fileName);

        // 등급 표시
        String grade = result.score.getGrade();
        gradeLabel.setText(grade);
        gradeLabel.setForeground(getGradeColor(grade));

        // 점수 패널 업데이트
        updateScorePanel(result.score);

        // 코멘트 패널 업데이트
        commentsPanel.removeAll();

        // 통계
        long critical = result.comments.stream()
            .filter(c -> c.type == AICodeReviewer.ReviewType.CRITICAL).count();
        long issues = result.comments.stream()
            .filter(c -> c.type == AICodeReviewer.ReviewType.ISSUE).count();
        long suggestions = result.comments.stream()
            .filter(c -> c.type == AICodeReviewer.ReviewType.SUGGESTION).count();
        long praises = result.comments.stream()
            .filter(c -> c.type == AICodeReviewer.ReviewType.PRAISE).count();

        JBLabel statsLabel = new JBLabel(String.format(
            "<html><b>리뷰 코멘트:</b> %d개 " +
            "( Critical: %d | Issue: %d | Suggestion: %d | Praise: %d )</html>",
            result.comments.size(), critical, issues, suggestions, praises
        ));
        statsLabel.setBorder(JBUI.Borders.emptyBottom(10));
        commentsPanel.add(statsLabel);

        // 코멘트 목록
        for (AICodeReviewer.ReviewComment comment : result.comments) {
            commentsPanel.add(createCommentPanel(comment));
        }

        commentsPanel.add(Box.createVerticalGlue());
        commentsPanel.revalidate();
        commentsPanel.repaint();
    }

    private void displayASTResult(String fileName, ASTAnalyzer.ASTAnalysisResult result) {
        if (result == null) return;

        titleLabel.setText("AST Review: " + fileName);
        gradeLabel.setText("");

        // 점수 패널 클리어
        scorePanel.removeAll();

        JBLabel metricsLabel = new JBLabel(String.format(
            "<html><b>메트릭스:</b><br>" +
            "클래스: %d | 메서드: %d | 필드: %d<br>" +
            "평균 복잡도: %.1f</html>",
            result.metrics.classCount,
            result.metrics.methodCount,
            result.metrics.fieldCount,
            result.metrics.methodComplexities.values().stream()
                .mapToInt(Integer::intValue).average().orElse(0)
        ));
        scorePanel.add(metricsLabel);
        scorePanel.revalidate();

        // 이슈 목록
        commentsPanel.removeAll();

        JBLabel issueLabel = new JBLabel(String.format("<html><b>발견된 이슈:</b> %d개</html>",
            result.issues.size()));
        issueLabel.setBorder(JBUI.Borders.emptyBottom(10));
        commentsPanel.add(issueLabel);

        for (ASTAnalyzer.ASTIssue issue : result.issues) {
            commentsPanel.add(createASTIssuePanel(issue));
        }

        commentsPanel.add(Box.createVerticalGlue());
        commentsPanel.revalidate();
        commentsPanel.repaint();
    }

    private void updateScorePanel(AICodeReviewer.CodeQualityScore score) {
        scorePanel.removeAll();

        String[] labels = {"구조", "가독성", "유지보수성", "신뢰성", "보안", "성능"};
        int[] scores = {
            score.structureScore, score.readabilityScore, score.maintainabilityScore,
            score.reliabilityScore, score.securityScore, score.performanceScore
        };

        for (int i = 0; i < labels.length; i++) {
            scorePanel.add(createScoreBar(labels[i], scores[i]));
        }

        scorePanel.add(Box.createVerticalStrut(10));
        JBLabel overallLabel = new JBLabel(String.format("<html><b>종합 점수:</b> %d/100</html>",
            score.getOverallScore()));
        scorePanel.add(overallLabel);

        scorePanel.revalidate();
        scorePanel.repaint();
    }

    private JPanel createScoreBar(String label, int score) {
        JPanel panel = new JPanel(new BorderLayout(5, 0));
        panel.setBackground(JBColor.background());
        panel.setBorder(JBUI.Borders.emptyBottom(3));

        JBLabel nameLabel = new JBLabel(label);
        nameLabel.setPreferredSize(new Dimension(80, 20));

        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue(score);
        bar.setStringPainted(true);
        bar.setString(score + "");
        bar.setForeground(getScoreColor(score));

        panel.add(nameLabel, BorderLayout.WEST);
        panel.add(bar, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createCommentPanel(AICodeReviewer.ReviewComment comment) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 3, 0, 0, getTypeColor(comment.type)),
            JBUI.Borders.empty(8)
        ));
        panel.setBackground(JBColor.background().brighter());

        String icon = comment.type.icon;
        String html = String.format(
            "<html><b>%s Line %d:</b><br>%s</html>",
            icon, comment.line, escapeHtml(comment.message)
        );

        JBLabel messageLabel = new JBLabel(html);
        panel.add(messageLabel, BorderLayout.CENTER);

        if (comment.suggestion != null && !comment.suggestion.isEmpty()) {
            JTextArea suggestionArea = new JTextArea(comment.suggestion);
            suggestionArea.setEditable(false);
            suggestionArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            suggestionArea.setBackground(JBColor.background());
            suggestionArea.setBorder(JBUI.Borders.empty(5));

            panel.add(suggestionArea, BorderLayout.SOUTH);
        }

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(panel, BorderLayout.NORTH);
        wrapper.setBorder(JBUI.Borders.emptyBottom(5));
        wrapper.setBackground(JBColor.background());

        return wrapper;
    }

    private JPanel createASTIssuePanel(ASTAnalyzer.ASTIssue issue) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 3, 0, 0, getSeverityColor(issue.severity)),
            JBUI.Borders.empty(8)
        ));
        panel.setBackground(JBColor.background().brighter());

        String html = String.format(
            "<html><b>[%s] Line %d:</b> %s<br><i>%s</i></html>",
            issue.severity, issue.line, issue.code, escapeHtml(issue.message)
        );

        JBLabel label = new JBLabel(html);
        panel.add(label, BorderLayout.CENTER);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(panel, BorderLayout.NORTH);
        wrapper.setBorder(JBUI.Borders.emptyBottom(5));
        wrapper.setBackground(JBColor.background());

        return wrapper;
    }

    private Color getGradeColor(String grade) {
        return switch (grade) {
            case "A" -> new Color(76, 175, 80);
            case "B" -> new Color(139, 195, 74);
            case "C" -> new Color(255, 193, 7);
            case "D" -> new Color(255, 152, 0);
            default -> new Color(244, 67, 54);
        };
    }

    private Color getScoreColor(int score) {
        if (score >= 80) return new Color(76, 175, 80);
        if (score >= 60) return new Color(255, 193, 7);
        return new Color(244, 67, 54);
    }

    private Color getTypeColor(AICodeReviewer.ReviewType type) {
        return switch (type) {
            case PRAISE -> new Color(76, 175, 80);
            case SUGGESTION -> new Color(33, 150, 243);
            case ISSUE -> new Color(255, 193, 7);
            case CRITICAL -> new Color(244, 67, 54);
            case ERROR -> new Color(156, 39, 176);
        };
    }

    private Color getSeverityColor(ASTAnalyzer.Severity severity) {
        return switch (severity) {
            case INFO -> new Color(33, 150, 243);
            case WARNING -> new Color(255, 193, 7);
            case ERROR -> new Color(244, 67, 54);
            case CRITICAL -> new Color(156, 39, 176);
        };
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("\n", "<br>");
    }

    @Override
    public void dispose() {
        // Cleanup
    }
}
