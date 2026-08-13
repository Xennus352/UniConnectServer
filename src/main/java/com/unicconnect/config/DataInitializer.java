package com.unicconnect.config;

import com.unicconnect.entity.RegistrationStatus;
import com.unicconnect.entity.Role;
import com.unicconnect.entity.User;
import com.unicconnect.repository.RoleRepository;
import com.unicconnect.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(RoleRepository roleRepository,
                           UserRepository userRepository,
                           PasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedRoles();
        seedAdminUser();
    }

    private void seedRoles() {
        createRoleIfNotExists("SYSTEM_ADMIN", "System administrator with full access");
        createRoleIfNotExists("STAFF", "Staff member with administrative access");
        createRoleIfNotExists("STUDENT", "Student user");
        log.info("Roles seeded successfully");
    }

    private void createRoleIfNotExists(String roleName, String description) {
        if (!roleRepository.existsByRoleName(roleName)) {
            roleRepository.save(new Role(roleName, description));
        }
    }

    private void seedAdminUser() {
        String adminEmail = "admin@unicconnect.com";
        if (!userRepository.existsByEmail(adminEmail)) {
            Role adminRole = roleRepository.findByRoleName("SYSTEM_ADMIN")
                    .orElseThrow(() -> new RuntimeException("SYSTEM_ADMIN role not found"));

            User admin = new User();
            admin.setEmail(adminEmail);
            admin.setPasswordHash(passwordEncoder.encode("Admin@12345"));
            admin.setRole(adminRole);
            admin.setActive(true);
            admin.setRegistrationStatus(RegistrationStatus.APPROVED);
            userRepository.save(admin);
            log.info("Admin user created: {} / Admin@12345", adminEmail);
        }
    }
}
