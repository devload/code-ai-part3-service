package com.codeai.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 대시보드 페이지 컨트롤러
 */
@Controller
public class DashboardController {

    @GetMapping("/")
    public String index() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("title", "Code AI Dashboard");
        return "dashboard";
    }

    @GetMapping("/analyze")
    public String analyzePage(Model model) {
        model.addAttribute("title", "코드 분석");
        return "analyze";
    }

    @GetMapping("/history")
    public String historyPage(Model model) {
        model.addAttribute("title", "분석 기록");
        return "history";
    }

    @GetMapping("/settings")
    public String settingsPage(Model model) {
        model.addAttribute("title", "설정");
        return "settings";
    }
}
