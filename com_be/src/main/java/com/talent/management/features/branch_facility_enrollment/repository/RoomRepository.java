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
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "branch")
    @Query("""
            select r from Room r where r.branch.id = :branchId
              and (:status is null or r.status = :status) and (:type is null or r.roomType = :type)
              and (lower(r.roomCode) like concat('%', :keyword, '%') escape '!'
                or lower(r.roomName) like concat('%', :keyword, '%') escape '!')
            """)
    org.springframework.data.domain.Page<Room> searchPage(@Param("branchId") Long branchId,
            @Param("keyword") String keyword, @Param("status") RoomStatus status,
            @Param("type") com.talent.management.shared.enums.RoomType type,
            org.springframework.data.domain.Pageable pageable);

    long countByBranchId(Long branchId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForScheduleUpdate(@Param("id") Long id);

    List<Room> findByBranchId(Long branchId);

    List<Room> findByBranchIdAndStatus(Long branchId, RoomStatus status);

}
