package com.codeai.intellij.action;

import com.codeai.analyzer.ast.ASTAnalyzer;
import com.codeai.intellij.service.CodeAIService;
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
 * AST 기반 코드 리뷰 액션
 */
public class ASTReviewAction extends AnAction {

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
            showNotification(project, "Java 파일만 분석할 수 있어요.", NotificationType.WARNING);
            return;
        }

        String code = editor.getDocument().getText();
        String fileName = psiFile.getName();

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "AST 분석 중...", true) {
            private ASTAnalyzer.ASTAnalysisResult result;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText("AST 분석 중...");
                ASTAnalyzer analyzer = new ASTAnalyzer();
                result = analyzer.analyze(code);
            }

            @Override
            public void onSuccess() {
                if (result != null) {
                    // Tool Window에 결과 표시
                    showResultInToolWindow(project, fileName, result);

                    int issueCount = result.issues.size();
                    showNotification(project,
                        String.format("AST 분석 완료! 이슈: %d개", issueCount),
                        NotificationType.INFORMATION);
                }
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                showNotification(project, "분석 실패: " + error.getMessage(), NotificationType.ERROR);
            }
        });
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        e.getPresentation().setEnabledAndVisible(psiFile instanceof PsiJavaFile);
    }

    private void showResultInToolWindow(Project project, String fileName, ASTAnalyzer.ASTAnalysisResult result) {
        ApplicationManager.getApplication().invokeLater(() -> {
            ToolWindow toolWindow = ToolWindowManager.getInstance(project)
                .getToolWindow("Code AI Review");

            if (toolWindow != null) {
                toolWindow.show(() -> {
                    CodeAIService service = project.getService(CodeAIService.class);
                    if (service != null) {
                        service.setLatestASTResult(fileName, result);
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
