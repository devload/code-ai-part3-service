package com.codeai.intellij.service;

import com.codeai.analyzer.ai.AICodeReviewer;
import com.codeai.analyzer.ast.ASTAnalyzer;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Code AI 서비스
 *
 * 분석 결과를 저장하고 리스너들에게 알림을 보냅니다.
 */
@Service(Service.Level.PROJECT)
public final class CodeAIService {

    private String currentFileName;
    private AICodeReviewer.AIReviewResult latestAIResult;
    private ASTAnalyzer.ASTAnalysisResult latestASTResult;
    private ResultType latestResultType;

    private final List<ResultListener> listeners = new CopyOnWriteArrayList<>();

    public enum ResultType {
        AI_REVIEW,
        AST_REVIEW
    }

    public interface ResultListener {
        void onResultUpdated(ResultType type);
    }

    public void addListener(ResultListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ResultListener listener) {
        listeners.remove(listener);
    }

    public void setLatestResult(String fileName, AICodeReviewer.AIReviewResult result) {
        this.currentFileName = fileName;
        this.latestAIResult = result;
        this.latestResultType = ResultType.AI_REVIEW;
        notifyListeners(ResultType.AI_REVIEW);
    }

    public void setLatestASTResult(String fileName, ASTAnalyzer.ASTAnalysisResult result) {
        this.currentFileName = fileName;
        this.latestASTResult = result;
        this.latestResultType = ResultType.AST_REVIEW;
        notifyListeners(ResultType.AST_REVIEW);
    }

    public String getCurrentFileName() {
        return currentFileName;
    }

    public AICodeReviewer.AIReviewResult getLatestAIResult() {
        return latestAIResult;
    }

    public ASTAnalyzer.ASTAnalysisResult getLatestASTResult() {
        return latestASTResult;
    }

    public ResultType getLatestResultType() {
        return latestResultType;
    }

    private void notifyListeners(ResultType type) {
        for (ResultListener listener : listeners) {
            listener.onResultUpdated(type);
        }
    }
}
