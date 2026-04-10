package com.adags.hospital.service.lab;

import com.adags.hospital.dto.lab.AnalyzerResultDto;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class AnalyzerService {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private volatile AnalyzerResultDto latestResult;

    public void broadcastResult(AnalyzerResultDto result) {
        this.latestResult = result;
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("result-ready").data(result));
            } catch (Exception e) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
    }

    public SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(300_000L);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitters.add(emitter);
        if (latestResult != null) {
            try {
                emitter.send(SseEmitter.event().name("result-ready").data(latestResult));
            } catch (Exception ignored) {
            }
        }
        return emitter;
    }
}
