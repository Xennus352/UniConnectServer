package com.unicconnect.rmi.remote;

import com.unicconnect.rmi.contract.CallerContext;
import com.unicconnect.rmi.dto.GenerationRequestDto;
import com.unicconnect.rmi.dto.GenerationHandleDto;
import com.unicconnect.rmi.dto.GenerationStatusDto;
import com.unicconnect.rmi.dto.TimetableEntryDto;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.UUID;

/** Timetable querying + asynchronous generation over RMI. Binding: "TimetableService". */
public interface TimetableRemote extends Remote {

    /** Schedules of the term's PUBLISHED generation. */
    List<TimetableEntryDto> publishedTimetable(UUID termId, CallerContext ctx) throws RemoteException;

    /** Filtered schedule query; null filters are wildcards. */
    List<TimetableEntryDto> querySchedules(UUID termId, UUID sectionId, UUID staffId,
                                           Integer dayOfWeek, CallerContext ctx) throws RemoteException;

    TimetableEntryDto getSchedule(UUID scheduleId, CallerContext ctx) throws RemoteException;

    /** Creates the generation session AND starts the solver asynchronously.
     *  Returns immediately with the handle (HOD+LECTURER required). */
    GenerationHandleDto startGeneration(UUID termId, GenerationRequestDto request,
                                        CallerContext ctx) throws RemoteException;

    /** Creates a PENDING generation session owned by the caller
     *  (HOD+LECTURER required). */
    GenerationHandleDto createGeneration(UUID termId, CallerContext ctx) throws RemoteException;

    /** Runs the solver for an EXISTING generation session asynchronously.
     *  Returns immediately with its handle (HOD+LECTURER required). */
    GenerationHandleDto runGeneration(UUID generationId, GenerationRequestDto request,
                                      CallerContext ctx) throws RemoteException;

    /** Current lifecycle status / failure report of one generation. */
    GenerationStatusDto getGenerationStatus(UUID generationId, CallerContext ctx) throws RemoteException;

    /** Generation history for a term (nullable = all). */
    List<GenerationStatusDto> listGenerations(UUID termId, CallerContext ctx) throws RemoteException;
}
