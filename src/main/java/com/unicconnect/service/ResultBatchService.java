package com.unicconnect.service;

import com.unicconnect.dto.request.CreateResultBatchRequest;
import com.unicconnect.dto.response.ResultBatchResponse;
import com.unicconnect.entity.*;
import com.unicconnect.exception.BusinessRuleException;
import com.unicconnect.exception.ResourceNotFoundException;
import com.unicconnect.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ResultBatchService {

    private final ResultBatchRepository batchRepository;
    private final AcademicTermRepository termRepository;
    private final ExamTypeRepository examTypeRepository;
    private final SemesterRepository semesterRepository;
    private final StaffRepository staffRepository;

    public ResultBatchService(ResultBatchRepository batchRepository,
                              AcademicTermRepository termRepository,
                              ExamTypeRepository examTypeRepository,
                              SemesterRepository semesterRepository,
                              StaffRepository staffRepository) {
        this.batchRepository = batchRepository;
        this.termRepository = termRepository;
        this.examTypeRepository = examTypeRepository;
        this.semesterRepository = semesterRepository;
        this.staffRepository = staffRepository;
    }

    public List<ResultBatchResponse> getAll(UUID termId, UUID semesterId, UUID examTypeId) {
        List<ResultBatch> batches;
        if (termId != null) {
            batches = batchRepository.findByTerm_TermId(termId);
        } else if (semesterId != null) {
            batches = batchRepository.findBySemester_SemesterId(semesterId);
        } else if (examTypeId != null) {
            batches = batchRepository.findByExamType_ExamTypeId(examTypeId);
        } else {
            batches = batchRepository.findAll();
        }
        return batches.stream().map(ResultBatchService::toResponse).toList();
    }

    public ResultBatchResponse getById(UUID batchId) {
        return toResponse(findBatch(batchId));
    }

    @Transactional
    public ResultBatchResponse create(CreateResultBatchRequest request) {
        AcademicTerm term = termRepository.findById(request.termId())
                .orElseThrow(() -> new ResourceNotFoundException("Academic term not found"));
        ExamType examType = examTypeRepository.findById(request.examTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Exam type not found"));
        Semester semester = semesterRepository.findById(request.semesterId())
                .orElseThrow(() -> new ResourceNotFoundException("Semester not found"));
        Staff staff = staffRepository.findById(request.uploadedByStaffId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));

        ResultBatch batch = new ResultBatch();
        batch.setTerm(term);
        batch.setExamType(examType);
        batch.setSemester(semester);
        batch.setUploadedByStaff(staff);
        batch.setUploadedType(request.uploadedType());
        batch.setSourceFileName(request.sourceFileName());
        batch.setTotalFiles(request.totalFiles() != null ? request.totalFiles() : 0);
        batch.setMatchedFiles(request.matchedFiles() != null ? request.matchedFiles() : 0);
        batch.setFailedFiles(request.failedFiles() != null ? request.failedFiles() : 0);
        batch.setStatus(BatchStatus.UPLOADED);
        return toResponse(batchRepository.save(batch));
    }

    @Transactional
    public ResultBatchResponse update(UUID batchId, CreateResultBatchRequest request) {
        ResultBatch batch = findBatch(batchId);
        if (batch.getStatus() == BatchStatus.PUBLISHED) {
            throw new BusinessRuleException("Cannot modify a published batch");
        }
        batch.setTerm(termRepository.findById(request.termId())
                .orElseThrow(() -> new ResourceNotFoundException("Academic term not found")));
        batch.setExamType(examTypeRepository.findById(request.examTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Exam type not found")));
        batch.setSemester(semesterRepository.findById(request.semesterId())
                .orElseThrow(() -> new ResourceNotFoundException("Semester not found")));
        batch.setUploadedByStaff(staffRepository.findById(request.uploadedByStaffId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found")));
        batch.setUploadedType(request.uploadedType());
        batch.setSourceFileName(request.sourceFileName());
        if (request.totalFiles() != null) batch.setTotalFiles(request.totalFiles());
        if (request.matchedFiles() != null) batch.setMatchedFiles(request.matchedFiles());
        if (request.failedFiles() != null) batch.setFailedFiles(request.failedFiles());
        return toResponse(batchRepository.save(batch));
    }

    @Transactional
    public void delete(UUID batchId) {
        ResultBatch batch = findBatch(batchId);
        if (batch.getStatus() == BatchStatus.PUBLISHED) {
            throw new BusinessRuleException("Cannot delete a published batch");
        }
        batchRepository.deleteById(batchId);
    }

    @Transactional
    public ResultBatchResponse publish(UUID batchId) {
        ResultBatch batch = findBatch(batchId);
        if (batch.getStatus() == BatchStatus.PUBLISHED) {
            throw new BusinessRuleException("Batch is already published");
        }
        batch.setStatus(BatchStatus.PUBLISHED);
        batch.setPublishedAt(Instant.now());
        return toResponse(batchRepository.save(batch));
    }

    public ResultBatch findBatch(UUID batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Result batch not found"));
    }

    static ResultBatchResponse toResponse(ResultBatch batch) {
        return new ResultBatchResponse(
                batch.getBatchId(),
                batch.getTerm().getTermId(),
                batch.getTerm().getAcademicYear(),
                batch.getExamType().getExamTypeId(),
                batch.getExamType().getExamTypeName(),
                batch.getSemester().getSemesterId(),
                batch.getSemester().getSemesterNo(),
                batch.getUploadedByStaff().getStaffId(),
                batch.getUploadedByStaff().getStaffNo(),
                batch.getUploadedType(),
                batch.getSourceFileName(),
                batch.getTotalFiles(),
                batch.getMatchedFiles(),
                batch.getFailedFiles(),
                batch.getStatus(),
                batch.getUploadedAt(),
                batch.getPublishedAt());
    }
}
