package com.unicconnect.service;

import com.unicconnect.dto.UserResponse;
import com.unicconnect.model.*;
import com.unicconnect.repository.StudentProfileRepository;
import com.unicconnect.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;

    public UserService(UserRepository userRepository, StudentProfileRepository studentProfileRepository) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        return toDetailedResponse(user);
    }

    public List<UserResponse> getUsersByRole(String role) {
        UserRole userRole = UserRole.valueOf(role);
        return userRepository.findByRole(userRole).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<UserResponse> getUsersByRoleDetailed(String role) {
        UserRole userRole = UserRole.valueOf(role);
        return userRepository.findByRole(userRole).stream()
                .map(this::toDetailedResponse)
                .collect(Collectors.toList());
    }

    public List<UserResponse> getUsersByDepartment(Long departmentId) {
        return userRepository.findByDepartmentId(departmentId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<UserResponse> getUsersByDepartmentDetailed(Long departmentId) {
        return userRepository.findByDepartmentId(departmentId).stream()
                .map(this::toDetailedResponse)
                .collect(Collectors.toList());
    }

    private UserResponse toResponse(User user) {
        UserResponse resp = new UserResponse();
        resp.setId(user.getId());
        resp.setEmail(user.getEmail());
        resp.setFullName(user.getFullName());
        resp.setRole(user.getRole().name());
        resp.setDepartmentId(user.getDepartment() != null ? user.getDepartment().getId() : null);
        resp.setDepartmentName(user.getDepartment() != null ? user.getDepartment().getName() : null);
        resp.setRegistrationStatus(user.getRegistrationStatus().name());
        resp.setIsActive(user.getIsActive());
        resp.setMustChangePassword(user.getMustChangePassword());
        resp.setLastLogin(user.getLastLogin());
        resp.setCreatedAt(user.getCreatedAt());
        return resp;
    }

    private UserResponse toDetailedResponse(User user) {
        UserResponse resp = toResponse(user);

        if (user.getRole() == UserRole.STUDENT) {
            studentProfileRepository.findById(user.getId()).ifPresent(profile -> {
                resp.setStudentIdNumber(profile.getStudentIdNumber());
                resp.setBatchYear(profile.getBatchYear());
                resp.setAcademicYear(profile.getAcademicYear());
                resp.setSection(profile.getSection());
            });
        }

        return resp;
    }
}
