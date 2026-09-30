package com.talent.management.features.placement_test.repository;

import com.talent.management.shared.entity.PlacementTest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlacementTestRepository extends JpaRepository<PlacementTest, Long> {
    Optional<PlacementTest> findFirstByStudentIdOrderByTestDateDescCreatedAtDesc(Long studentId);
}
