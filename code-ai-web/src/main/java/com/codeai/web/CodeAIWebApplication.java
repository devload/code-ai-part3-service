package com.codeai.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Code AI 웹 대시보드 애플리케이션
 *
 * 코드 분석 결과를 시각화하고 실시간 리뷰를 제공합니다.
 */
@SpringBootApplication
public class CodeAIWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeAIWebApplication.class, args);
    }
}
