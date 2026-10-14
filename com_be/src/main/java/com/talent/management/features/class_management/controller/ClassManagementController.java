package com.talent.management.features.class_management.controller;

import com.talent.management.features.class_management.dto.ClassUpsertRequest;
import com.talent.management.features.class_management.dto.ManagedClassResponse;
import com.talent.management.features.class_management.service.ClassManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/class-management/classes")
@PreAuthorize("hasAnyRole('STAFF', 'ADMIN', 'BRANCH_MANAGER')")
@RequiredArgsConstructor
public class ClassManagementController {
    private final ClassManagementService service;

    @GetMapping
    public List<ManagedClassResponse> search(@RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Long courseId,
                                              @RequestParam(required = false) Long branchId,
                                              @RequestParam(required = false) String status) {
        return service.search(keyword, courseId, branchId, status);
    }

    @GetMapping("/{id}")
    public ManagedClassResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<ManagedClassResponse> create(@Valid @RequestBody ClassUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public ManagedClassResponse update(@PathVariable Long id, @Valid @RequestBody ClassUpsertRequest request) {
        return service.update(id, request);
    }

}
