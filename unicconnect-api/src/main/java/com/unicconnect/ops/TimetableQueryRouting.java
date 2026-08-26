package com.unicconnect.ops;

import com.unicconnect.dto.response.ScheduleResponse;
import com.unicconnect.rmi.client.RmiClientConfig.TimetableRmiClient;
import com.unicconnect.rmi.contract.CallerContext;
import com.unicconnect.security.CallerContextFactory;
import com.unicconnect.service.ClassScheduleService;

import java.util.List;
import java.util.UUID;

/** Hybrid routing for timetable QUERY operations (editing stays local). */
public final class TimetableQueryRouting {

    private TimetableQueryRouting() {}

    public static final class Local implements TimetableQueryOperations {
        private final ClassScheduleService service;
        public Local(ClassScheduleService service) { this.service = service; }
        @Override public List<ScheduleResponse> getAll(UUID termId, UUID sectionId, UUID staffId, Integer dayOfWeek) {
            return service.getAll(termId, sectionId, staffId, dayOfWeek);
        }
        @Override public List<ScheduleResponse> getPublished(UUID termId) { return service.getPublished(termId); }
        @Override public ScheduleResponse getById(UUID scheduleId) { return service.getById(scheduleId); }
    }

    public static final class Remote implements TimetableQueryOperations {
        private final TimetableRmiClient client;
        private final CallerContextFactory ctxFactory;

        public Remote(TimetableRmiClient client, CallerContextFactory ctxFactory) {
            this.client = client;
            this.ctxFactory = ctxFactory;
        }

        @Override public List<ScheduleResponse> getAll(UUID termId, UUID sectionId, UUID staffId, Integer dayOfWeek) {
            return com.unicconnect.rmi.dto.RmiMappers.fromEntryDtos(
                    client.read(remote -> remote.querySchedules(termId, sectionId, staffId, dayOfWeek,
                            ctxFactory.forCurrentUser())));
        }

        @Override public List<ScheduleResponse> getPublished(UUID termId) {
            return com.unicconnect.rmi.dto.RmiMappers.fromEntryDtos(
                    client.read(remote -> remote.publishedTimetable(termId, ctxFactory.forCurrentUser())));
        }

        @Override public ScheduleResponse getById(UUID scheduleId) {
            return com.unicconnect.rmi.dto.RmiMappers.fromEntryDto(
                    client.read(remote -> remote.getSchedule(scheduleId, ctxFactory.forCurrentUser())));
        }
    }
}
