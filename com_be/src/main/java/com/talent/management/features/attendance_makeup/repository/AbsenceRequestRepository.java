package com.talent.management.features.attendance_makeup.repository;

import com.talent.management.shared.entity.AbsenceRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AbsenceRequestRepository extends JpaRepository<AbsenceRequest, Long> {
}
