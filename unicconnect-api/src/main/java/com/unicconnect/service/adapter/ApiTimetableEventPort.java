package com.unicconnect.service.adapter;

import com.unicconnect.service.TimetableRealtimeEventService;
import com.unicconnect.service.port.TimetableEventPort;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/** API-side event sink: relays lifecycle events to the SSE lobby streams. */
@Component
public class ApiTimetableEventPort implements TimetableEventPort {

    private final TimetableRealtimeEventService realtime;

    public ApiTimetableEventPort(TimetableRealtimeEventService realtime) {
        this.realtime = realtime;
    }

    @Override
    public void publishForGeneration(UUID generationId, String eventType, Map<String, Object> payload) {
        realtime.publishForGeneration(generationId, eventType, payload);
    }

    @Override
    public void publish(UUID lobbyId, String eventType, Map<String, Object> payload) {
        realtime.publish(lobbyId, eventType, payload);
    }

    @Override
    public void publishForTerm(UUID termId, String eventType, Map<String, Object> payload) {
        realtime.publishForTerm(termId, eventType, payload);
    }

    @Override
    public void publishForCourse(UUID courseId, String eventType, Map<String, Object> payload) {
        realtime.publishForCourse(courseId, eventType, payload);
    }
}
