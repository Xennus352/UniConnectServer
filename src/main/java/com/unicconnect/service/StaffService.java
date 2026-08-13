package com.unicconnect.service;

import com.unicconnect.dto.request.StaffPositionAssignmentRequest;
import com.unicconnect.dto.request.StaffRequest;
import com.unicconnect.dto.response.StaffPositionAssignmentResponse;
import com.unicconnect.dto.response.StaffResponse;
import com.unicconnect.dto.response.TeachingAssignmentResponse;
import com.unicconnect.entity.*;
import com.unicconnect.exception.DuplicateResourceException;
import com.unicconnect.exception.ResourceNotFoundException;
import com.unicconnect.exception.ValidationException;
import com.unicconnect.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class StaffService {

    private final StaffRepository staffRepository;
    private final UserRepository userRepository;
    private final PositionRepository positionRepository;
    private final StaffPositionAssignmentRepository assignmentRepository;
    private final TeachingAssignmentRepository teachingAssignmentRepository;
    private final OrganizationalUnitRepository unitRepository;

    public StaffService(StaffRepository staffRepository,
                        UserRepository userRepository,
                        PositionRepository positionRepository,
                        StaffPositionAssignmentRepository assignmentRepository,
                        TeachingAssignmentRepository teachingAssignmentRepository,
                        OrganizationalUnitRepository unitRepository) {
        this.staffRepository = staffRepository;
        this.userRepository = userRepository;
        this.positionRepository = positionRepository;
        this.assignmentRepository = assignmentRepository;
        this.teachingAssignmentRepository = teachingAssignmentRepository;
        this.unitRepository = unitRepository;
    }

    public List<StaffResponse> getAll() {
        return staffRepository.findAll().stream().map(StaffService::toResponse).toList();
    }

    public StaffResponse getById(UUID staffId) {
        return toResponse(findStaff(staffId));
    }

    @Transactional
    public StaffResponse create(StaffRequest request) {
        if (staffRepository.existsByStaffNo(request.staffNo())) {
            throw new DuplicateResourceException("Staff number already exists: " + request.staffNo());
        }
        if (staffRepository.existsByUser_UserId(request.userId())) {
            throw new DuplicateResourceException("A staff profile already exists for this user");
        }
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Staff staff = new Staff();
        apply(staff, request, user);
        return toResponse(staffRepository.save(staff));
    }

    @Transactional
    public StaffResponse update(UUID staffId, StaffRequest request) {
        Staff staff = findStaff(staffId);
        if (!staff.getStaffNo().equals(request.staffNo()) && staffRepository.existsByStaffNo(request.staffNo())) {
            throw new DuplicateResourceException("Staff number already exists: " + request.staffNo());
        }
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        apply(staff, request, user);
        return toResponse(staffRepository.save(staff));
    }

    @Transactional
    public void delete(UUID staffId) {
        findStaff(staffId);
        staffRepository.deleteById(staffId);
    }

    private void apply(Staff staff, StaffRequest request, User user) {
        staff.setUser(user);
        staff.setStaffNo(request.staffNo());
        staff.setStaffName(request.staffName());
        staff.setPhoneNo(request.phoneNo());
        staff.setBatchYear(request.batchYear());
        staff.setAddress(request.address());
        if (request.unitId() != null) {
            staff.setUnit(unitRepository.findById(request.unitId())
                    .orElseThrow(() -> new ResourceNotFoundException("Organizational unit not found")));
        } else {
            staff.setUnit(null);
        }
        staff.setJoinedAt(request.joinedAt());
        staff.setLeftDate(request.leftDate());
    }

    // ---------- Position assignments ----------

    public List<StaffPositionAssignmentResponse> getPositionAssignments(UUID staffId) {
        findStaff(staffId);
        return assignmentRepository.findByStaff_StaffId(staffId).stream()
                .map(StaffService::toResponse).toList();
    }

    @Transactional
    public StaffPositionAssignmentResponse assignPosition(UUID staffId, StaffPositionAssignmentRequest request) {
        Staff staff = findStaff(staffId);
        Position position = positionRepository.findById(request.positionId())
                .orElseThrow(() -> new ResourceNotFoundException("Position not found"));

        if (assignmentRepository.existsByStaff_StaffIdAndPosition_PositionIdAndStartDate(
                staffId, position.getPositionId(), request.startDate())) {
            throw new DuplicateResourceException(
                    "This staff member already has this position starting on " + request.startDate());
        }
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new ValidationException("endDate must be on or after startDate");
        }

        StaffPositionAssignment assignment = new StaffPositionAssignment();
        assignment.setStaff(staff);
        assignment.setPosition(position);
        assignment.setStartDate(request.startDate());
        assignment.setEndDate(request.endDate());
        if (request.assignedByStaffId() != null) {
            assignment.setAssignedByStaff(findStaff(request.assignedByStaffId()));
        }
        return toResponse(assignmentRepository.save(assignment));
    }

    @Transactional
    public StaffPositionAssignmentResponse updatePositionAssignment(UUID staffId, UUID assignmentId,
                                                                    StaffPositionAssignmentRequest request) {
        StaffPositionAssignment assignment = assignmentRepository
                .findByStaff_StaffIdAndPositionAssignmentId(staffId, assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Position assignment not found"));

        if (request.endDate() != null && request.endDate().isBefore(assignment.getStartDate())) {
            throw new ValidationException("endDate must be on or after startDate");
        }
        Position position = positionRepository.findById(request.positionId())
                .orElseThrow(() -> new ResourceNotFoundException("Position not found"));
        assignment.setPosition(position);
        assignment.setEndDate(request.endDate());
        if (request.assignedByStaffId() != null) {
            assignment.setAssignedByStaff(findStaff(request.assignedByStaffId()));
        }
        return toResponse(assignmentRepository.save(assignment));
    }

    @Transactional
    public void removePositionAssignment(UUID staffId, UUID assignmentId) {
        StaffPositionAssignment assignment = assignmentRepository
                .findByStaff_StaffIdAndPositionAssignmentId(staffId, assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Position assignment not found"));
        assignmentRepository.delete(assignment);
    }

    // ---------- Teaching assignments ----------

    public List<TeachingAssignmentResponse> getTeachingAssignments(UUID staffId) {
        findStaff(staffId);
        return teachingAssignmentRepository.findByStaff_StaffId(staffId).stream()
                .map(TeachingAssignmentService::toResponse).toList();
    }

    public Staff findStaff(UUID staffId) {
        return staffRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found"));
    }

    static StaffResponse toResponse(Staff staff) {
        return new StaffResponse(
                staff.getStaffId(), staff.getUser().getUserId(),
                staff.getStaffNo(), staff.getStaffName(), staff.getPhoneNo(),
                staff.getBatchYear(), staff.getAddress(),
                staff.getUnit() != null ? staff.getUnit().getUnitId() : null,
                staff.getUnit() != null ? staff.getUnit().getUnitName() : null,
                staff.getJoinedAt(), staff.getLeftDate(), staff.getCreatedAt());
    }

    static StaffPositionAssignmentResponse toResponse(StaffPositionAssignment assignment) {
        return new StaffPositionAssignmentResponse(
                assignment.getPositionAssignmentId(),
                assignment.getStaff().getStaffId(),
                assignment.getPosition().getPositionId(),
                assignment.getPosition().getPositionName(),
                assignment.getStartDate(),
                assignment.getEndDate(),
                assignment.getAssignedByStaff() != null ? assignment.getAssignedByStaff().getStaffId() : null);
    }
}