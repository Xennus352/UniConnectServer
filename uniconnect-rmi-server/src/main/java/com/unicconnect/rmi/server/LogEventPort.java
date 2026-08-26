package com.unicconnect.rmi.server;

import com.unicconnect.service.port.TimetableEventPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * RMI-tier event sink: lifecycle events are logged for the demo terminal but
 * never relayed to SSE (that stays an HTTP-tier feature of Spring Boot).
 */
@Component
public class LogEventPort implements TimetableEventPort {

    private static final Logger log = LoggerFactory.getLogger(LogEventPort.class);

    @Override
    public void publishForGeneration(UUID generationId, String eventType, Map<String, Object> payload) {
        log.info("[RMI][event] generation={} type={} payload={}", generationId, eventType, payload);
    }

    @Override
    public void publish(UUID lobbyId, String eventType, Map<String, Object> payload) {
        log.info("[RMI][event] lobby={} type={} payload={}", lobbyId, eventType, payload);
    }

    @Override
    public void publishForTerm(UUID termId, String eventType, Map<String, Object> payload) {
        log.info("[RMI][event] term={} type={} payload={}", termId, eventType, payload);
    }

    @Override
    public void publishForCourse(UUID courseId, String eventType, Map<String, Object> payload) {
        log.info("[RMI][event] course={} type={} payload={}", courseId, eventType, payload);
    }
}
