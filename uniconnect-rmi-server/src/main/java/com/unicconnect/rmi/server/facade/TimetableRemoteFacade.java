package com.unicconnect.rmi.server.facade;

import com.unicconnect.dto.request.CreateGenerationRequest;
import com.unicconnect.dto.request.GenerateTimetableRequest;
import com.unicconnect.rmi.contract.CallerContext;
import com.unicconnect.rmi.server.CallerContextVerifier;
import com.unicconnect.rmi.dto.GenerationHandleDto;
import com.unicconnect.rmi.dto.GenerationRequestDto;
import com.unicconnect.rmi.dto.GenerationStatusDto;
import com.unicconnect.rmi.dto.TimetableEntryDto;
import com.unicconnect.rmi.remote.TimetableRemote;
import com.unicconnect.service.ClassScheduleService;
import com.unicconnect.service.TimetableGenerationService;
import jakarta.annotation.PreDestroy;
import com.unicconnect.rmi.server.RmiCurrentUserHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.rmi.RemoteException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Thin RMI boundary for timetable querying + ASYNCHRONOUS generation.
 * startGeneration/runGeneration submit the EXISTING core solver to a bounded
 * executor inside this JVM and return immediately; clients poll status via
 * generation_sessions. The algorithm is never duplicated.
 */
@Component
public class TimetableRemoteFacade implements TimetableRemote {

    private static final Logger log = LoggerFactory.getLogger(TimetableRemoteFacade.class);

    private final ClassScheduleService scheduleService;
    private final TimetableGenerationService generationService;
    private final CallerContextVerifier verifier;
    private final ExecutorService generationExecutor =
            Executors.newFixedThreadPool(1, r -> {
                Thread t = new Thread(r, "rmi-generation-worker");
                t.setDaemon(true);
                return t;
            });

    public TimetableRemoteFacade(ClassScheduleService scheduleService,
                                 TimetableGenerationService generationService,
                                 CallerContextVerifier verifier) {
        this.scheduleService = scheduleService;
        this.generationService = generationService;
        this.verifier = verifier;
    }

    @PreDestroy
    void shutdown() { generationExecutor.shutdownNow(); }

    // ---------- queries ----------

    @Override
    public List<TimetableEntryDto> publishedTimetable(UUID termId, CallerContext ctx) throws RemoteException {
        try {
            UUID caller = verifier.verify(ctx);
            RmiCurrentUserHolder.set(caller);
            log.info("[RMI] TimetableRemote.publishedTimetable caller={} term={}", caller, termId);
            return com.unicconnect.rmi.dto.RmiMappers.toEntryDtos(scheduleService.getPublished(termId));
        } catch (RuntimeException e) { RmiCurrentUserHolder.clear(); throw FacadeGuard.translate(e); }
            finally { RmiCurrentUserHolder.clear(); }
    }

    @Override
    public List<TimetableEntryDto> querySchedules(UUID termId, UUID sectionId, UUID staffId,
                                                  Integer dayOfWeek, CallerContext ctx) throws RemoteException {
        try {
            UUID caller = verifier.verify(ctx);
            RmiCurrentUserHolder.set(caller);
            log.info("[RMI] TimetableRemote.querySchedules caller={} term={} section={} staff={} day={}",
                    caller, termId, sectionId, staffId, dayOfWeek);
            return com.unicconnect.rmi.dto.RmiMappers.toEntryDtos(
                    scheduleService.getAll(termId, sectionId, staffId, dayOfWeek));
        } catch (RuntimeException e) { RmiCurrentUserHolder.clear(); throw FacadeGuard.translate(e); }
            finally { RmiCurrentUserHolder.clear(); }
    }

    @Override
    public TimetableEntryDto getSchedule(UUID scheduleId, CallerContext ctx) throws RemoteException {
        try {
            UUID caller = verifier.verify(ctx);
            RmiCurrentUserHolder.set(caller);
            log.info("[RMI] TimetableRemote.getSchedule caller={} schedule={}", caller, scheduleId);
            return com.unicconnect.rmi.dto.RmiMappers.toEntryDto(scheduleService.getById(scheduleId));
        } catch (RuntimeException e) { RmiCurrentUserHolder.clear(); throw FacadeGuard.translate(e); }
            finally { RmiCurrentUserHolder.clear(); }
    }

    // ---------- generation ----------

    @Override
    public GenerationHandleDto createGeneration(UUID termId, CallerContext ctx) throws RemoteException {
        try {
            UUID caller = verifier.verify(ctx);
            RmiCurrentUserHolder.set(caller);
            log.info("[RMI] TimetableRemote.createGeneration caller={} term={}", caller, termId);
            var response = generationService.create(new CreateGenerationRequest(termId, null));
            return new GenerationHandleDto(response.generationId());
        } catch (RuntimeException e) { RmiCurrentUserHolder.clear(); throw FacadeGuard.translate(e); }
            finally { RmiCurrentUserHolder.clear(); }
    }

    @Override
    public GenerationHandleDto startGeneration(UUID termId, GenerationRequestDto request,
                                               CallerContext ctx) throws RemoteException {
        var handle = createGeneration(termId, ctx);
        return runGeneration(handle.generationId(), request, ctx);
    }

    @Override
    public GenerationHandleDto runGeneration(UUID generationId, GenerationRequestDto request,
                                             CallerContext ctx) throws RemoteException {
        try {
            UUID caller = verifier.verify(ctx);
            RmiCurrentUserHolder.set(caller);
            log.info("[RMI] TimetableRemote.runGeneration caller={} generation={} async=true",
                    caller, generationId);
            final UUID taskCaller = caller;
            final GenerateTimetableRequest gtr = toLocal(request);
            generationExecutor.submit(() -> {
                // The solver resolves HOD/lobby rules through the access port,
                // which needs the caller identity on THIS worker thread too.
                com.unicconnect.rmi.server.RmiCurrentUserHolder.set(taskCaller);
                try {
                    generationService.generate(generationId, gtr);
                    log.info("[RMI] generation {} finished", generationId);
                } catch (Exception e) {
                    log.error("[RMI] generation {} FAILED: {}", generationId, e.toString());
                } finally {
                    com.unicconnect.rmi.server.RmiCurrentUserHolder.clear();
                }
            });
            return new GenerationHandleDto(generationId);
        } catch (RuntimeException e) { RmiCurrentUserHolder.clear(); throw FacadeGuard.translate(e); }
            finally { RmiCurrentUserHolder.clear(); }
    }

    @Override
    public GenerationStatusDto getGenerationStatus(UUID generationId, CallerContext ctx) throws RemoteException {
        try {
            UUID caller = verifier.verify(ctx);
            RmiCurrentUserHolder.set(caller);
            log.debug("[RMI] TimetableRemote.getGenerationStatus caller={} id={}", caller, generationId);
            return com.unicconnect.rmi.dto.RmiMappers.toStatusDto(generationService.getById(generationId));
        } catch (RuntimeException e) { RmiCurrentUserHolder.clear(); throw FacadeGuard.translate(e); }
            finally { RmiCurrentUserHolder.clear(); }
    }

    @Override
    public List<GenerationStatusDto> listGenerations(UUID termId, CallerContext ctx) throws RemoteException {
        try {
            UUID caller = verifier.verify(ctx);
            RmiCurrentUserHolder.set(caller);
            log.info("[RMI] TimetableRemote.listGenerations caller={} term={}", caller, termId);
            return generationService.getAll(termId).stream()
                    .map(com.unicconnect.rmi.dto.RmiMappers::toStatusDto).toList();
        } catch (RuntimeException e) { RmiCurrentUserHolder.clear(); throw FacadeGuard.translate(e); }
            finally { RmiCurrentUserHolder.clear(); }
    }

    /** Publishes the verified identity for the shared-core access ports. */
    private UUID verified(UUID rawCaller) {
        // CallerContextVerifier already resolved the real userId into rawCaller.
        return rawCaller;
    }

    private static GenerateTimetableRequest toLocal(GenerationRequestDto d) {
        if (d == null) return null;
        List<GenerateTimetableRequest.SemesterSelection> sels = d.semesters() == null ? List.of()
                : d.semesters().stream()
                    .map(s -> new GenerateTimetableRequest.SemesterSelection(s.semesterId(), s.sectionIds()))
                    .toList();
        return new GenerateTimetableRequest(d.examTypeId(), sels, d.autoBindCurriculum());
    }
}
