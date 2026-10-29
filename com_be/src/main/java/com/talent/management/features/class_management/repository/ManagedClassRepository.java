package com.talent.management.features.class_management.repository;

import com.talent.management.shared.entity.ClassEntity;
import com.talent.management.shared.enums.ClassStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ManagedClassRepository extends JpaRepository<ClassEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ClassEntity c where c.id = :id")
    Optional<ClassEntity> findByIdForUpdate(@Param("id") Long id);

    boolean existsByClassCodeIgnoreCase(String classCode);

    boolean existsByClassCodeIgnoreCaseAndIdNot(String classCode, Long id);

    @EntityGraph(attributePaths = {"course", "branch"})
    @Query("""
            select c from ClassEntity c
            where (:courseId is null or c.course.id = :courseId)
              and (:branchId is null or c.branch.id = :branchId)
              and (:status is null or c.status = :status)
              and (lower(c.classCode) like lower(concat('%', :keyword, '%'))
                   or lower(c.className) like lower(concat('%', :keyword, '%')))
            order by c.id desc
            """)
    List<ClassEntity> search(@Param("keyword") String keyword,
                             @Param("courseId") Long courseId,
                             @Param("branchId") Long branchId,
                             @Param("status") ClassStatus status);
}
