package com.unicconnect.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_summary", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"student_id", "subject_code"})
})
public class AttendanceSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "subject_code", nullable = false, length = 50)
    private String subjectCode;

    @Column(name = "total_classes")
    private Integer totalClasses = 0;

    @Column(name = "attended_classes")
    private Integer attendedClasses = 0;

    @Column(precision = 5, scale = 2, insertable = false, updatable = false)
    private BigDecimal percentage;

    @Column(name = "is_below_75", insertable = false, updatable = false)
    private Boolean isBelow75;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public AttendanceSummary() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }
    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }
    public Integer getTotalClasses() { return totalClasses; }
    public void setTotalClasses(Integer totalClasses) { this.totalClasses = totalClasses; }
    public Integer getAttendedClasses() { return attendedClasses; }
    public void setAttendedClasses(Integer attendedClasses) { this.attendedClasses = attendedClasses; }
    public BigDecimal getPercentage() { return percentage; }
    public Boolean getIsBelow75() { return isBelow75; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}