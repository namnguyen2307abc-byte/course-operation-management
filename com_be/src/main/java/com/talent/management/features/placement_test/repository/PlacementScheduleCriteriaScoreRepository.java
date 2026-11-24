package com.talent.management.features.placement_test.repository;

import com.talent.management.features.placement_test.entity.PlacementScheduleCriteriaScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PlacementScheduleCriteriaScoreRepository extends JpaRepository<PlacementScheduleCriteriaScore, Long> {

    List<PlacementScheduleCriteriaScore> findByScheduleIdOrderByDisplayOrderAscIdAsc(Long scheduleId);

    List<PlacementScheduleCriteriaScore> findByScheduleIdInOrderByScheduleIdAscDisplayOrderAscIdAsc(Collection<Long> scheduleIds);

    boolean existsByCriteriaId(Long criteriaId);

    void deleteByScheduleId(Long scheduleId);
}
