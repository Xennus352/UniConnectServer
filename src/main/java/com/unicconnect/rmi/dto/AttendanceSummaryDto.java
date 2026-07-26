package com.unicconnect.rmi.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AttendanceSummaryDto implements Serializable {
    private Long id;
    private Long studentId;
    private String studentName;
    private String subjectCode;
    private Integer totalClasses;
    private Integer attendedClasses;
    private BigDecimal percentage;
    private Boolean isBelow75;
    private LocalDateTime updatedAt;

    public AttendanceSummaryDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }
    public Integer getTotalClasses() { return totalClasses; }
    public void setTotalClasses(Integer totalClasses) { this.totalClasses = totalClasses; }
    public Integer getAttendedClasses() { return attendedClasses; }
    public void setAttendedClasses(Integer attendedClasses) { this.attendedClasses = attendedClasses; }
    public BigDecimal getPercentage() { return percentage; }
    public void setPercentage(BigDecimal percentage) { this.percentage = percentage; }
    public Boolean getIsBelow75() { return isBelow75; }
    public void setIsBelow75(Boolean isBelow75) { this.isBelow75 = isBelow75; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
