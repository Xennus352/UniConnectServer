package com.unicconnect.service;

import com.unicconnect.config.JwtUtil;
import com.unicconnect.dto.*;
import com.unicconnect.model.RefreshToken;
import com.unicconnect.model.User;
import com.unicconnect.model.UserRole;
import com.unicconnect.model.Department;
import com.unicconnect.model.StudentProfile;
import com.unicconnect.repository.RefreshTokenRepository;
import com.unicconnect.repository.UserRepository;
import com.unicconnect.repository.DepartmentRepository;
import com.unicconnect.repository.StudentProfileRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final DepartmentRepository departmentRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 15;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       DepartmentRepository departmentRepository,
                       StudentProfileRepository studentProfileRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.departmentRepository = departmentRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public ApiResponse setupFirstAdmin(SetupAdminRequest request) {
        long userCount = userRepository.count();
        if (userCount > 0) {
            throw new RuntimeException("Setup already completed. Admin account already exists.");
        }

        User admin = new User();
        admin.setEmail(request.getEmail());
        admin.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        admin.setFullName(request.getFullName());
        admin.setRole(UserRole.MANAGE);
        admin.setMustChangePassword(true);
        admin.setIsActive(true);
        admin.setRegistrationStatus(com.unicconnect.model.RegistrationStatus.APPROVED);
        userRepository.save(admin);

        return ApiResponse.success("First admin account created successfully. Please login and change your password.");
    }

    public AuthResponse login(LoginRequest request, String deviceName, String ipAddress) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (user.getAccountLockedUntil() != null && LocalDateTime.now().isAfter(user.getAccountLockedUntil())) {
            user.setAccountLockedUntil(null);
            user.setFailedLoginAttempts(0);
            userRepository.save(user);
        }

        if (!user.getIsActive()) {
            throw new DisabledException("Account is disabled");
        }

        if (user.getAccountLockedUntil() != null && LocalDateTime.now().isBefore(user.getAccountLockedUntil())) {
            throw new LockedException("Account is locked due to too many failed login attempts. Try again later.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);

            if (attempts >= MAX_FAILED_ATTEMPTS) {
                user.setAccountLockedUntil(LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
                userRepository.save(user);
                throw new LockedException("Account locked due to too many failed login attempts. Try again in 15 minutes.");
            }

            userRepository.save(user);
            int remaining = MAX_FAILED_ATTEMPTS - attempts;
            throw new BadCredentialsException("Invalid email or password. " + remaining + " attempt(s) remaining before account lockout.");
        }

        user.setFailedLoginAttempts(0);
        user.setAccountLockedUntil(null);
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshTokenStr = jwtUtil.generateRefreshToken(user.getId(), user.getEmail());

        RefreshToken refreshToken = new RefreshToken(
                user,
                jwtUtil.hashToken(refreshTokenStr),
                LocalDateTime.now().plusNanos(jwtUtil.getRefreshTokenExpirationMs() * 1_000_000),
                deviceName,
                ipAddress
        );
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(
                accessToken,
                jwtUtil.getAccessTokenExpirationMs() / 1000,
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.getMustChangePassword()
        );
    }

    public AuthResponse refresh(String refreshTokenStr) {
        if (!jwtUtil.validateToken(refreshTokenStr)) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        String tokenHash = jwtUtil.hashToken(refreshTokenStr);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadCredentialsException("Refresh token not found"));

        if (storedToken.getRevoked() || storedToken.isExpired()) {
            throw new BadCredentialsException("Refresh token is revoked or expired");
        }

        User user = storedToken.getUser();

        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        String newAccessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getEmail());

        RefreshToken newStoredToken = new RefreshToken(
                user,
                jwtUtil.hashToken(newRefreshToken),
                LocalDateTime.now().plusNanos(jwtUtil.getRefreshTokenExpirationMs() * 1_000_000),
                storedToken.getDeviceName(),
                storedToken.getIpAddress()
        );
        refreshTokenRepository.save(newStoredToken);

        return new AuthResponse(
                newAccessToken,
                jwtUtil.getAccessTokenExpirationMs() / 1000,
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.getMustChangePassword()
        );
    }

    public CreatedAccountResponse createAccount(CreateAccountRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        String tempPassword = request.getPassword() != null ? request.getPassword() : generateTempPassword();

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        user.setFullName(request.getFullName());
        user.setRole(UserRole.valueOf(request.getRole()));
        user.setMustChangePassword(true);
        user.setIsActive(true);
        user.setRegistrationStatus(com.unicconnect.model.RegistrationStatus.APPROVED);

        if (request.getDepartmentId() != null) {
            Department department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new RuntimeException("Department not found"));
            user.setDepartment(department);
        }

        user = userRepository.save(user);

        if (UserRole.STUDENT.name().equals(request.getRole()) && request.getStudentIdNumber() != null) {
            StudentProfile profile = new StudentProfile();
            profile.setUser(user);
            profile.setStudentIdNumber(request.getStudentIdNumber());
            if (request.getBatchYear() != null) {
                profile.setBatchYear(Integer.parseInt(request.getBatchYear()));
            }
            profile.setAcademicYear(request.getAcademicYear());
            profile.setSection(request.getSection());
            studentProfileRepository.save(profile);
        }

        Department dept = user.getDepartment();

        return new CreatedAccountResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                dept != null ? dept.getId() : null,
                dept != null ? dept.getName() : null,
                tempPassword,
                true,
                user.getCreatedAt()
        );
    }

    private String generateTempPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%";
        StringBuilder password = new StringBuilder();
        java.security.SecureRandom random = new java.security.SecureRandom();
        for (int i = 0; i < 12; i++) {
            password.append(chars.charAt(random.nextInt(chars.length())));
        }
        return password.toString();
    }

    public ApiResponse changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            return ApiResponse.error("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);

        revokeAllRefreshTokens(user);

        return ApiResponse.success("Password changed successfully. Please login again.");
    }

    public ApiResponse logout(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        revokeAllRefreshTokens(user);
        return ApiResponse.success("Logged out successfully");
    }

    private void revokeAllRefreshTokens(User user) {
        refreshTokenRepository.findByUserAndRevokedFalse(user)
                .forEach(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }
}
