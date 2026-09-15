package com.talent.management.features.branch_facility_enrollment.repository;

import com.talent.management.shared.entity.Equipment;
import com.talent.management.shared.enums.EquipmentCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

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
