package com.codeai.web.model;

import jakarta.validation.constraints.NotBlank;

/**
 * 코드 분석 요청
 */
public record AnalysisRequest(
    @NotBlank(message = "코드는 필수입니다")
    String code,

    String filename,

    AnalysisOptions options
) {
    public record AnalysisOptions(
        boolean includeAST,
        boolean includeLLM,
        String llmProvider,
        boolean autoFix
    ) {
        public static AnalysisOptions defaults() {
            return new AnalysisOptions(true, false, "claude", false);
        }
    }

    public AnalysisOptions optionsOrDefault() {
        return options != null ? options : AnalysisOptions.defaults();
    }
}
