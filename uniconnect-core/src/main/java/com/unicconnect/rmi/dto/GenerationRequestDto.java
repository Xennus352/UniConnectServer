package com.unicconnect.rmi.dto;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

public record GenerationRequestDto(
        UUID examTypeId,
        List<SemesterSelectionDto> semesters,
        Boolean autoBindCurriculum
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
