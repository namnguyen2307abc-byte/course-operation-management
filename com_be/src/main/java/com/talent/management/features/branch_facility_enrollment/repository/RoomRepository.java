package com.talent.management.features.branch_facility_enrollment.repository;

import com.talent.management.shared.entity.Room;
import com.talent.management.shared.enums.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByBranchId(Long branchId);

    List<Room> findByBranchIdAndStatus(Long branchId, RoomStatus status);

    @Query("select coalesce(max(c.maxStudents), 0) from ClassEntity c where c.room.id = :roomId")
    int maxAssignedClassSize(@Param("roomId") Long roomId);

    @Query("select count(c) > 0 from ClassEntity c where c.room.id = :roomId")
    boolean hasAssignedClass(@Param("roomId") Long roomId);
}
