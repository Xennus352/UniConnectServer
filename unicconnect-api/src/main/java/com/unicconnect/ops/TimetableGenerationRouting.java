package com.unicconnect.ops;

import com.unicconnect.dto.request.CreateGenerationRequest;
import com.unicconnect.dto.request.GenerateTimetableRequest;
import com.unicconnect.dto.response.GenerationSessionResponse;
import com.unicconnect.rmi.client.RmiClientConfig.TimetableRmiClient;
import com.unicconnect.rmi.contract.CallerContext;
import com.unicconnect.rmi.dto.GenerationRequestDto;
import com.unicconnect.rmi.dto.GenerationStatusDto;
import com.unicconnect.rmi.dto.SemesterSelectionDto;
import com.unicconnect.security.CallerContextFactory;
import com.unicconnect.service.TimetableGenerationService;

import java.util.List;
import java.util.UUID;

/**
 * Hybrid routing for timetable generation.
 * LOCAL : the existing synchronous in-process solver path (unchanged).
 * RMI   : startGeneration/runGeneration submit to the RMI Server's executor
 *         and return immediately; the UI keeps polling the status endpoint.
 *         The 2.6k-line algorithm itself is NEVER duplicated — it lives only
 *         in uniconnect-core and runs inside the RMI Server JVM.
 */
public final class TimetableGenerationRouting {

    private TimetableGenerationRouting() {}

    public static final class Local implements TimetableGenerationOperations {
        private final TimetableGenerationService service;
        public Local(TimetableGenerationService service) { this.service = service; }
        @Override public GenerationSessionResponse create(CreateGenerationRequest r) { return service.create(r); }
        @Override public GenerationSessionResponse generate(UUID id, GenerateTimetableRequest r) { return service.generate(id, r); }
        @Override public GenerationSessionResponse getById(UUID id) { return service.getById(id); }
        @Override public List<GenerationSessionResponse> getAll(UUID termId) { return service.getAll(termId); }
    }

    public static final class Remote implements TimetableGenerationOperations {
        private final TimetableRmiClient client;
        private final CallerContextFactory ctxFactory;

        public Remote(TimetableRmiClient client, CallerContextFactory ctxFactory) {
            this.client = client;
            this.ctxFactory = ctxFactory;
        }

        @Override public GenerationSessionResponse create(CreateGenerationRequest r) {
            // Cheap metadata insert executed inside the RMI Server so session
            // ownership stays consistent with where the solver will run.
            var handle = client.write(remote ->
                    remote.createGeneration(r.termId(), ctxFactory.forCurrentUser()));
            return getById(handle.generationId());
        }

        @Override public GenerationSessionResponse generate(UUID id, GenerateTimetableRequest r) {
            client.write(remote -> remote.runGeneration(id,
                    toRequestDto(r), ctxFactory.forCurrentUser()));
            // Snapshot right after submission: PENDING/RUNNING; UI polls status.
            return getById(id);
        }

        @Override public GenerationSessionResponse getById(UUID id) {
            return com.unicconnect.rmi.dto.RmiMappers.fromStatusDto(
                    client.read(remote -> remote.getGenerationStatus(id, ctxFactory.forCurrentUser())));
        }

        @Override public List<GenerationSessionResponse> getAll(UUID termId) {
            return client.read(remote -> remote.listGenerations(termId, ctxFactory.forCurrentUser()))
                    .stream().map(com.unicconnect.rmi.dto.RmiMappers::fromStatusDto).toList();
        }

        private static GenerationRequestDto toRequestDto(GenerateTimetableRequest r) {
            if (r == null) return new GenerationRequestDto(null, List.of(), null);
            List<SemesterSelectionDto> sels = r.semesters() == null ? List.of()
                    : r.semesters().stream()
                        .map(s -> new SemesterSelectionDto(s.semesterId(), s.sectionIds()))
                        .toList();
            return new GenerationRequestDto(r.examTypeId(), sels, r.autoBindCurriculum());
        }
    }
}
