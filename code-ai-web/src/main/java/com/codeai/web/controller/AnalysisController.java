package com.codeai.web.controller;

import com.codeai.web.model.AnalysisRequest;
import com.codeai.web.model.AnalysisResponse;
import com.codeai.web.model.AnalysisResponse.ScoreSummary;
import com.codeai.web.service.AnalysisService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 코드 분석 REST API 컨트롤러
 */
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /**
     * 코드 분석 (JSON)
     */
    @PostMapping("/analyze")
    public ResponseEntity<AnalysisResponse> analyze(@Valid @RequestBody AnalysisRequest request) {
        AnalysisResponse response = analysisService.analyze(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 파일 업로드 분석
     */
    @PostMapping("/analyze/file")
    public ResponseEntity<AnalysisResponse> analyzeFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "includeAST", defaultValue = "true") boolean includeAST,
            @RequestParam(value = "includeLLM", defaultValue = "false") boolean includeLLM,
            @RequestParam(value = "provider", defaultValue = "claude") String provider,
            @RequestParam(value = "autoFix", defaultValue = "false") boolean autoFix
    ) throws IOException {
        String code = new String(file.getBytes(), StandardCharsets.UTF_8);
        String filename = file.getOriginalFilename();

        AnalysisRequest request = new AnalysisRequest(
            code,
            filename,
            new AnalysisRequest.AnalysisOptions(includeAST, includeLLM, provider, autoFix)
        );

        AnalysisResponse response = analysisService.analyze(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 빠른 점수 조회
     */
    @PostMapping("/score")
    public ResponseEntity<ScoreSummary> quickScore(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        ScoreSummary score = analysisService.quickScore(code);
        return ResponseEntity.ok(score);
    }

    /**
     * 분석 결과 조회
     */
    @GetMapping("/analysis/{id}")
    public ResponseEntity<AnalysisResponse> getAnalysis(@PathVariable String id) {
        return analysisService.getAnalysis(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 최근 분석 목록
     */
    @GetMapping("/analyses")
    public ResponseEntity<List<AnalysisResponse>> getRecentAnalyses(
            @RequestParam(value = "limit", defaultValue = "10") int limit
    ) {
        List<AnalysisResponse> analyses = analysisService.getRecentAnalyses(limit);
        return ResponseEntity.ok(analyses);
    }

    /**
     * 헬스 체크
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "version", "1.0.0",
            "service", "code-ai-web"
        ));
    }

    /**
     * API 정보
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        return ResponseEntity.ok(Map.of(
            "name", "Code AI Web API",
            "version", "1.0.0",
            "endpoints", List.of(
                Map.of("method", "POST", "path", "/api/v1/analyze", "description", "코드 분석"),
                Map.of("method", "POST", "path", "/api/v1/analyze/file", "description", "파일 업로드 분석"),
                Map.of("method", "POST", "path", "/api/v1/score", "description", "빠른 점수 조회"),
                Map.of("method", "GET", "path", "/api/v1/analysis/{id}", "description", "분석 결과 조회"),
                Map.of("method", "GET", "path", "/api/v1/analyses", "description", "최근 분석 목록")
            )
        ));
    }
}
