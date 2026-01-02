package com.codeai.intellij.action;

import com.codeai.analyzer.ai.AICodeReviewer;
import com.codeai.intellij.service.CodeAIService;
import com.codeai.intellij.toolwindow.CodeAIToolWindowFactory;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;

/**
 * AI 코드 리뷰 액션
 *
 * 에디터에서 Java 파일에 대해 AI 기반 코드 리뷰를 실행합니다.
 */
public class AIReviewAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        if (editor == null || psiFile == null) {
            showNotification(project, "파일을 열어주세요.", NotificationType.WARNING);
            return;
        }

        if (!(psiFile instanceof PsiJavaFile)) {
            showNotification(project, "Java 파일만 리뷰할 수 있어요.", NotificationType.WARNING);
            return;
        }

        String code = editor.getDocument().getText();
        String fileName = psiFile.getName();

        // 백그라운드에서 분석 실행
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "AI 코드 리뷰 중...", true) {
            private AICodeReviewer.AIReviewResult result;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText("코드 분석 중...");
                indicator.setFraction(0.3);

                AICodeReviewer reviewer = new AICodeReviewer();
                result = reviewer.review(code);

                indicator.setFraction(1.0);
            }

            @Override
            public void onSuccess() {
                if (result != null) {
                    // Tool Window에 결과 표시
                    showResultInToolWindow(project, fileName, result);

                    // 간단한 알림
                    String grade = result.score.getGrade();
                    int commentCount = result.comments.size();
                    showNotification(project,
                        String.format("리뷰 완료! 등급: %s, 코멘트: %d개", grade, commentCount),
                        NotificationType.INFORMATION);
                }
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                showNotification(project, "리뷰 실패: " + error.getMessage(), NotificationType.ERROR);
            }
        });
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // Java 파일일 때만 활성화
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        e.getPresentation().setEnabledAndVisible(psiFile instanceof PsiJavaFile);
    }

    private void showResultInToolWindow(Project project, String fileName, AICodeReviewer.AIReviewResult result) {
        ApplicationManager.getApplication().invokeLater(() -> {
            ToolWindow toolWindow = ToolWindowManager.getInstance(project)
                .getToolWindow("Code AI Review");

            if (toolWindow != null) {
                toolWindow.show(() -> {
                    CodeAIService service = project.getService(CodeAIService.class);
                    if (service != null) {
                        service.setLatestResult(fileName, result);
                    }
                });
            }
        });
    }

    private void showNotification(Project project, String content, NotificationType type) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Code AI Notifications")
            .createNotification("Code AI", content, type)
            .notify(project);
    }
}
