package com.miniai.server.dto;

/**
 * /v1/train 요청 DTO
 *
 * 지원 옵션:
 * - modelType: "bigram", "trigram", "ngram" (기본: bigram)
 * - n: N-gram 크기 (modelType=ngram일 때, 기본: 5)
 * - tokenizerType: "whitespace", "code" (기본: whitespace)
 * - smoothingType: "simple", "kneser-ney" (기본: simple)
 */
public class TrainRequest {
    private String corpusPath;
    private String outputPath;
    private String tokenizerType = "whitespace"; // "whitespace" or "code"
    private String modelType = "bigram"; // "bigram", "trigram", or "ngram"
    private int n = 5; // N-gram size (for modelType=ngram)
    private String smoothingType = "simple"; // "simple" or "kneser-ney"

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

    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    public boolean useTrigram() {
        return "trigram".equalsIgnoreCase(modelType);
    }

    public boolean useNgram() {
        return "ngram".equalsIgnoreCase(modelType);
    }

    public int getN() {
        return n;
    }

    public void setN(int n) {
        this.n = n;
    }

    public String getSmoothingType() {
        return smoothingType;
    }

    public void setSmoothingType(String smoothingType) {
        this.smoothingType = smoothingType;
    }

    public boolean useKneserNey() {
        return "kneser-ney".equalsIgnoreCase(smoothingType);
    }
}
