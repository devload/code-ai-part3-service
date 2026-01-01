package com.miniai.server.dto;

/**
 * /v1/train 요청 DTO
 */
public class TrainRequest {
    private String corpusPath;
    private String outputPath;
    private String tokenizerType = "whitespace"; // "whitespace" or "code"

    public TrainRequest() {
    }

    public TrainRequest(String corpusPath, String outputPath) {
        this.corpusPath = corpusPath;
        this.outputPath = outputPath;
    }

    public TrainRequest(String corpusPath, String outputPath, String tokenizerType) {
        this.corpusPath = corpusPath;
        this.outputPath = outputPath;
        this.tokenizerType = tokenizerType;
    }

    public String getCorpusPath() {
        return corpusPath;
    }

    public void setCorpusPath(String corpusPath) {
        this.corpusPath = corpusPath;
    }

    public String getOutputPath() {
        return outputPath;
    }

    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }

    public String getTokenizerType() {
        return tokenizerType;
    }

    public void setTokenizerType(String tokenizerType) {
        this.tokenizerType = tokenizerType;
    }

    public boolean useCodeTokenizer() {
        return "code".equalsIgnoreCase(tokenizerType);
    }
}
