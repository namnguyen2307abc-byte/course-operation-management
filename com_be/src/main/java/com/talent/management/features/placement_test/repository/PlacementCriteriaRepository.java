package com.talent.management.features.placement_test.repository;

import com.talent.management.features.placement_test.entity.PlacementCriteria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlacementCriteriaRepository extends JpaRepository<PlacementCriteria, Long> {

    List<PlacementCriteria> findBySubjectIgnoreCaseAndActiveTrueOrderByDisplayOrderAscIdAsc(String subject);

    List<PlacementCriteria> findBySubjectIgnoreCaseOrderByDisplayOrderAscIdAsc(String subject);

    List<PlacementCriteria> findByActiveTrueOrderBySubjectAscDisplayOrderAscIdAsc();

    List<PlacementCriteria> findAllByOrderBySubjectAscDisplayOrderAscIdAsc();

    long countBySubjectIgnoreCase(String subject);

    long countBySubjectIgnoreCaseAndActiveTrue(String subject);
}
