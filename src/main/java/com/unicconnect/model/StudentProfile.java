package com.unicconnect.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "student_profiles")
public class StudentProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "student_id_number", unique = true, nullable = false, length = 50)
    private String studentIdNumber;

    @Column(name = "batch_year", nullable = false)
    private Integer batchYear;

    @Column(name = "academic_year", nullable = false, length = 50)
    private String academicYear;

    @Column(length = 10)
    private String section;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public StudentProfile() {}

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getStudentIdNumber() { return studentIdNumber; }
    public void setStudentIdNumber(String studentIdNumber) { this.studentIdNumber = studentIdNumber; }
    public Integer getBatchYear() { return batchYear; }
    public void setBatchYear(Integer batchYear) { this.batchYear = batchYear; }
    public String getAcademicYear() { return academicYear; }
    public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}