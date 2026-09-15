package com.talent.management.features.branch_facility_enrollment.repository;

import com.talent.management.shared.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Long> {
    List<Branch> findByActiveTrue();
}
