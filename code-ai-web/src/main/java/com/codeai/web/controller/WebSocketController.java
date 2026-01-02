package com.codeai.web.controller;

import com.codeai.web.model.AnalysisRequest;
import com.codeai.web.model.AnalysisResponse;
import com.codeai.web.service.AnalysisService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * WebSocket 컨트롤러 - 실시간 분석
 */
@Controller
public class WebSocketController {

    private final AnalysisService analysisService;
    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketController(AnalysisService analysisService,
                               SimpMessagingTemplate messagingTemplate) {
        this.analysisService = analysisService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * 실시간 코드 분석
     */
    @MessageMapping("/analyze")
    @SendTo("/topic/analysis")
    public AnalysisResponse analyzeRealtime(AnalysisRequest request) {
        // 분석 시작 알림
        messagingTemplate.convertAndSend("/topic/status",
            Map.of("status", "ANALYZING", "message", "분석 중..."));

        try {
            AnalysisResponse response = analysisService.analyze(request);

            // 분석 완료 알림
            messagingTemplate.convertAndSend("/topic/status",
                Map.of("status", "COMPLETE", "message", "분석 완료"));

            return response;
        } catch (Exception e) {
            // 오류 알림
            messagingTemplate.convertAndSend("/topic/status",
                Map.of("status", "ERROR", "message", e.getMessage()));

            return AnalysisResponse.error(e.getMessage());
        }
    }

    /**
     * 실시간 점수 계산 (타이핑 중)
     */
    @MessageMapping("/score")
    @SendTo("/topic/score")
    public AnalysisResponse.ScoreSummary scoreRealtime(Map<String, String> payload) {
        String code = payload.get("code");
        if (code == null || code.isBlank()) {
            return new AnalysisResponse.ScoreSummary(0, "N/A", Map.of());
        }
        return analysisService.quickScore(code);
    }

    /**
     * 비동기 LLM 분석
     */
    @MessageMapping("/analyze-llm")
    public void analyzeLLMAsync(AnalysisRequest request) {
        // 분석 시작 알림
        messagingTemplate.convertAndSend("/topic/status",
            Map.of("status", "LLM_ANALYZING", "message", "LLM 분석 중..."));

        // 비동기 실행
        CompletableFuture.runAsync(() -> {
            try {
                AnalysisRequest llmRequest = new AnalysisRequest(
                    request.code(),
                    request.filename(),
                    new AnalysisRequest.AnalysisOptions(true, true,
                        request.optionsOrDefault().llmProvider(), false)
                );

                AnalysisResponse response = analysisService.analyze(llmRequest);

                // 결과 전송
                messagingTemplate.convertAndSend("/topic/llm-result", response);
                messagingTemplate.convertAndSend("/topic/status",
                    Map.of("status", "LLM_COMPLETE", "message", "LLM 분석 완료"));

            } catch (Exception e) {
                messagingTemplate.convertAndSend("/topic/status",
                    Map.of("status", "LLM_ERROR", "message", e.getMessage()));
            }
        });
    }
}
