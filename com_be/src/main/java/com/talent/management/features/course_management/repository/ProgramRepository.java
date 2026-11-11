package com.talent.management.features.course_management.repository;

import com.talent.management.shared.entity.Program;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProgramRepository extends JpaRepository<Program, Long> {
    List<Program> findAllByOrderByNameAsc();
    boolean existsByCode(String code);
    boolean existsByCodeAndIdNot(String code, Long id);
}
