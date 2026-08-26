package com.unicconnect.service.port;

import java.util.Map;
import java.util.UUID;

/**
 * Event sink for timetable lifecycle broadcasts. The Spring Boot API relays
 * these to SSE lobby streams; the RMI Server logs them (SSE is an HTTP-tier
 * feature and never crosses RMI). Event name constants live HERE so both
 * tiers emit identical strings over the wire.
 */
public interface TimetableEventPort {

    String SCHEDULE_CREATED       = "SCHEDULE_CREATED";
    String SCHEDULE_UPDATED       = "SCHEDULE_UPDATED";
    String SCHEDULE_DELETED       = "SCHEDULE_DELETED";
    String GENERATION_STARTED     = "GENERATION_STARTED";
    String GENERATION_COMPLETED   = "GENERATION_COMPLETED";
    String GENERATION_FAILED      = "GENERATION_FAILED";
    String TIMETABLE_PUBLISHED    = "TIMETABLE_PUBLISHED";
    String TIMETABLE_DELETED      = "TIMETABLE_DELETED";
    String DRAG_STARTED           = "DRAG_STARTED";
    String DRAG_MOVED             = "DRAG_MOVED";
    String DRAG_ENDED             = "DRAG_ENDED";
    String TEACHING_GROUP_CREATED = "TEACHING_GROUP_CREATED";
    String TEACHING_GROUP_DELETED = "TEACHING_GROUP_DELETED";
    String COURSE_REQUIREMENT_CREATED = "COURSE_REQUIREMENT_CREATED";
    String COURSE_REQUIREMENT_UPDATED = "COURSE_REQUIREMENT_UPDATED";
    String COURSE_REQUIREMENT_DELETED = "COURSE_REQUIREMENT_DELETED";

    void publishForGeneration(UUID generationId, String eventType, Map<String, Object> payload);

    /** Course-scoped broadcast (course meeting requirements). */
    void publishForCourse(UUID courseId, String eventType, Map<String, Object> payload);

    void publish(UUID lobbyId, String eventType, Map<String, Object> payload);

    /** Term-scoped broadcast (teaching-group lifecycle). */
    void publishForTerm(UUID termId, String eventType, Map<String, Object> payload);
}
