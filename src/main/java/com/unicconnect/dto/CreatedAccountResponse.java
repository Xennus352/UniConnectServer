package com.unicconnect.dto;

import java.time.LocalDateTime;

public class CreatedAccountResponse {

    private Long id;
    private String email;
    private String fullName;
    private String role;
    private Long departmentId;
    private String departmentName;
    private String tempPassword;
    private boolean mustChangePassword;
    private LocalDateTime createdAt;

    public CreatedAccountResponse() {}

    public CreatedAccountResponse(Long id, String email, String fullName, String role, Long departmentId,
                                   String departmentName, String tempPassword, boolean mustChangePassword,
                                   LocalDateTime createdAt) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.tempPassword = tempPassword;
        this.mustChangePassword = mustChangePassword;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
    public String getTempPassword() { return tempPassword; }
    public void setTempPassword(String tempPassword) { this.tempPassword = tempPassword; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
