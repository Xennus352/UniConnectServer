package com.unicconnect.rmi.dto;

import java.io.Serializable;
import java.util.List;

public record MarkAttendanceDto(java.util.List<MarkEntryDto> entries) implements Serializable {
    private static final long serialVersionUID = 1L;
}
