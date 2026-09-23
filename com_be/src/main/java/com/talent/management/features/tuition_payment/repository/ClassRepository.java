package com.talent.management.features.tuition_payment.repository;

import com.talent.management.shared.entity.ClassEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("tuitionPaymentClassRepository")
public interface ClassRepository extends JpaRepository<ClassEntity, Long> {

    List<ClassEntity> findByBranchId(Long branchId);

    List<ClassEntity> findByCourseId(Long courseId);
}
