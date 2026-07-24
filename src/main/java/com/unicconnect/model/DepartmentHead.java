package com.unicconnect.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "department_heads")
public class DepartmentHead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false, unique = true)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    @Column(name = "assigned_at", updatable = false)
    private LocalDateTime assignedAt = LocalDateTime.now();

    @Column(name = "is_active")
    private Boolean isActive = true;

    public DepartmentHead() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }
    public User getTeacher() { return teacher; }
    public void setTeacher(User teacher) { this.teacher = teacher; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}