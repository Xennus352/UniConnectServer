package com.unicconnect.repository;

import com.unicconnect.model.User;
import com.unicconnect.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    List<User> findByRole(UserRole role);
    List<User> findByDepartmentId(Long departmentId);
    boolean existsByEmail(String email);
}