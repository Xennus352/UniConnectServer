package com.unicconnect.service;

import com.unicconnect.dto.ApiResponse;
import com.unicconnect.dto.DepartmentRequest;
import com.unicconnect.model.Department;
import com.unicconnect.repository.DepartmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    public DepartmentService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    public List<Department> getAll() {
        return departmentRepository.findAll();
    }

    public Department getById(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Department not found with id: " + id));
    }

    public Department create(DepartmentRequest request) {
        if (departmentRepository.findByName(request.getName()).isPresent()) {
            throw new RuntimeException("Department name already exists: " + request.getName());
        }
        if (departmentRepository.findByCode(request.getCode()).isPresent()) {
            throw new RuntimeException("Department code already exists: " + request.getCode());
        }

        Department dept = new Department(request.getName(), request.getCode());
        return departmentRepository.save(dept);
    }

    public Department update(Long id, DepartmentRequest request) {
        Department dept = getById(id);

        departmentRepository.findByName(request.getName())
                .filter(d -> !d.getId().equals(id))
                .ifPresent(d -> { throw new RuntimeException("Department name already exists: " + request.getName()); });

        departmentRepository.findByCode(request.getCode())
                .filter(d -> !d.getId().equals(id))
                .ifPresent(d -> { throw new RuntimeException("Department code already exists: " + request.getCode()); });

        dept.setName(request.getName());
        dept.setCode(request.getCode());
        return departmentRepository.save(dept);
    }

    public ApiResponse delete(Long id) {
        Department dept = getById(id);
        departmentRepository.delete(dept);
        return ApiResponse.success("Department deleted successfully");
    }
}
