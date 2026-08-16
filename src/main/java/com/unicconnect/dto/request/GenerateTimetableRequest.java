package com.unicconnect.dto.request;

import java.util.List;
import java.util.UUID;

/**
 * Generation configuration chosen by the lobby creator on the Timetable page.
 *
 * <p>The Mid/Final selection reuses the existing {@code exam_types} data
 * ({@code examTypeId}); applicable semesters are derived from the exam type's
 * semester convention (Mid Term = semesters 1/3/5/7, Final Term = 2/4/6/8) and
 * the exact {@code semester_id} values are resolved from {@code semesters}.
 *
 * @param examTypeId existing exam_type id ('Mid Term' or 'Final Term'); may be null
 * @param semesters  explicit semester + section selections; when empty/null the
 *                   whole term (all semesters, all sections) is generated
 */
public record GenerateTimetableRequest(
        UUID examTypeId,
        List<SemesterSelection> semesters
) {
    public record SemesterSelection(
            UUID semesterId,
            List<UUID> sectionIds
    ) {}
}
