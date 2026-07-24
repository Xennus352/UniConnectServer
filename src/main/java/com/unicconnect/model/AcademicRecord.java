package com.unicconnect.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "academic_records")
public class AcademicRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "subject_code", nullable = false, length = 50)
    private String subjectCode;

    @Column(name = "subject_name", nullable = false, length = 100)
    private String subjectName;

    @Column(name = "academic_year", nullable = false, length = 50)
    private String academicYear;

    @Column(name = "grade_letter", length = 10)
    private String gradeLetter;

    @Column(precision = 5, scale = 2)
    private BigDecimal marks;

    @Column(name = "published_at", updatable = false)
    private LocalDateTime publishedAt = LocalDateTime.now();

    public AcademicRecord() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }
    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }
    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }
    public String getAcademicYear() { return academicYear; }
    public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }
    public String getGradeLetter() { return gradeLetter; }
    public void setGradeLetter(String gradeLetter) { this.gradeLetter = gradeLetter; }
    public BigDecimal getMarks() { return marks; }
    public void setMarks(BigDecimal marks) { this.marks = marks; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
}