package com.talent.management.features.branch_facility_enrollment.repository;

import com.talent.management.shared.entity.Room;
import com.talent.management.shared.enums.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByBranchId(Long branchId);

    List<Room> findByBranchIdAndStatus(Long branchId, RoomStatus status);
}
