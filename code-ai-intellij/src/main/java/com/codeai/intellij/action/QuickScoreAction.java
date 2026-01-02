package com.codeai.intellij.action;

import com.codeai.analyzer.ai.AICodeReviewer;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * 빠른 코드 품질 점수 확인 액션
 *
 * 에디터 상단에 팝업으로 점수를 빠르게 표시합니다.
 */
public class QuickScoreAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        if (editor == null || psiFile == null) {
            return;
        }

        if (!(psiFile instanceof PsiJavaFile)) {
            showNotification(project, "Java 파일만 분석할 수 있어요.", NotificationType.WARNING);
            return;
        }

        String code = editor.getDocument().getText();

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "점수 계산 중...", false) {
            private AICodeReviewer.AIReviewResult result;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                AICodeReviewer reviewer = new AICodeReviewer();
                result = reviewer.review(code);
            }

            @Override
            public void onSuccess() {
                if (result != null && result.parseSuccess) {
                    showScoreBalloon(editor, result.score);
                }
            }
        });
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        e.getPresentation().setEnabledAndVisible(psiFile instanceof PsiJavaFile);
    }

    private void showScoreBalloon(Editor editor, AICodeReviewer.CodeQualityScore score) {
        String grade = score.getGrade();
        int overall = score.getOverallScore();

        // 등급별 색상
        Color bgColor = switch (grade) {
            case "A" -> new Color(76, 175, 80);   // Green
            case "B" -> new Color(139, 195, 74);  // Light Green
            case "C" -> new Color(255, 193, 7);   // Yellow
            case "D" -> new Color(255, 152, 0);   // Orange
            default -> new Color(244, 67, 54);    // Red
        };

        String html = String.format("""
            <html>
            <body style='padding: 8px; font-family: sans-serif;'>
                <div style='font-size: 24px; font-weight: bold; text-align: center;'>
                    %s
                </div>
                <div style='font-size: 14px; text-align: center; margin-top: 4px;'>
                    %d/100
                </div>
                <hr style='margin: 8px 0; border: none; border-top: 1px solid #ccc;'>
                <table style='font-size: 11px;'>
                    <tr><td>구조</td><td style='text-align: right;'>%d</td></tr>
                    <tr><td>가독성</td><td style='text-align: right;'>%d</td></tr>
                    <tr><td>유지보수성</td><td style='text-align: right;'>%d</td></tr>
                    <tr><td>신뢰성</td><td style='text-align: right;'>%d</td></tr>
                    <tr><td>보안</td><td style='text-align: right;'>%d</td></tr>
                    <tr><td>성능</td><td style='text-align: right;'>%d</td></tr>
                </table>
            </body>
            </html>
            """,
            grade, overall,
            score.structureScore,
            score.readabilityScore,
            score.maintainabilityScore,
            score.reliabilityScore,
            score.securityScore,
            score.performanceScore
        );

        JBPopupFactory.getInstance()
            .createHtmlTextBalloonBuilder(html, null, bgColor, null)
            .setFadeoutTime(5000)
            .setHideOnAction(true)
            .setHideOnClickOutside(true)
            .createBalloon()
            .show(RelativePoint.getNorthEastOf(editor.getComponent()), Balloon.Position.above);
    }

    private void showNotification(Project project, String content, NotificationType type) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Code AI Notifications")
            .createNotification("Code AI", content, type)
            .notify(project);
    }
}
