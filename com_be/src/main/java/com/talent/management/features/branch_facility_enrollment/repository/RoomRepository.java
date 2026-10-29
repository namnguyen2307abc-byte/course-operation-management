package com.talent.management.features.branch_facility_enrollment.repository;

import com.talent.management.shared.entity.Room;
import com.talent.management.shared.enums.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForScheduleUpdate(@Param("id") Long id);

    List<Room> findByBranchId(Long branchId);

    List<Room> findByBranchIdAndStatus(Long branchId, RoomStatus status);

}
