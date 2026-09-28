package com.talent.management.features.auth.repository;

import com.talent.management.shared.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {
    List<Student> findByParentId(Long parentId);
    Optional<Student> findByIdAndParentId(Long id, Long parentId);
}
