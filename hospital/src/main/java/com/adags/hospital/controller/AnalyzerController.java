package com.adags.hospital.controller;

import com.adags.hospital.service.lab.AnalyzerService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class AnalyzerController {

    private final AnalyzerService analyzerService;

    public AnalyzerController(AnalyzerService analyzerService) {
        this.analyzerService = analyzerService;
    }

    @GetMapping(value = "/labtech/api/analyzer/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        return analyzerService.createEmitter();
    }
}
