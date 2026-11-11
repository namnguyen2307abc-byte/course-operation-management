package com.talent.management.features.branch_facility_enrollment.repository;

import com.talent.management.shared.entity.Equipment;
import com.talent.management.shared.enums.EquipmentCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EquipmentRepository extends JpaRepository<Equipment, Long> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"room", "room.branch"})
    @Query("""
            select e from Equipment e where e.room.branch.id = :branchId
              and (:roomId is null or e.room.id = :roomId)
              and (:status is null or e.status = :status) and (:category is null or e.category = :category)
              and (lower(coalesce(e.code, '')) like concat('%', :keyword, '%') escape '!'
                or lower(e.name) like concat('%', :keyword, '%') escape '!'
                or lower(e.room.roomCode) like concat('%', :keyword, '%') escape '!')
            """)
    org.springframework.data.domain.Page<Equipment> searchPage(@Param("branchId") Long branchId,
            @Param("roomId") Long roomId, @Param("keyword") String keyword,
            @Param("status") com.talent.management.shared.enums.EquipmentStatus status,
            @Param("category") EquipmentCategory category, org.springframework.data.domain.Pageable pageable);

    long countByRoomBranchId(Long branchId);
    long countByRoomBranchIdAndStatus(Long branchId, com.talent.management.shared.enums.EquipmentStatus status);
    long countByRoomId(Long roomId);

    List<Equipment> findByRoomIdOrderByIdAsc(Long roomId);

    @Query("select e.room.id from Equipment e where e.id = :id")
    Optional<Long> findRoomIdById(@Param("id") Long id);

    @Query("SELECT e FROM Equipment e " +
           "JOIN e.room r " +
           "WHERE r.branch.id = :branchId " +
           "ORDER BY e.id ASC")
    List<Equipment> findEquipmentsByBranchId(@Param("branchId") Long branchId);

    @Query("SELECT e FROM Equipment e " +
           "JOIN e.room r " +
           "WHERE r.branch.id = :branchId AND e.category = :category " +
           "ORDER BY e.id ASC")
    List<Equipment> findEquipmentsByBranchIdAndCategory(
            @Param("branchId") Long branchId,
            @Param("category") EquipmentCategory category
    );
}
