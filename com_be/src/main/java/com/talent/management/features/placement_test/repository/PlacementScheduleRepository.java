package com.talent.management.features.placement_test.repository;

import com.talent.management.features.placement_test.entity.PlacementSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlacementScheduleRepository extends JpaRepository<PlacementSchedule, Long> {

    List<PlacementSchedule> findAllByOrderByTestDateDesc();

    List<PlacementSchedule> findByParentIdOrderByTestDateAsc(Long parentId);

    List<PlacementSchedule> findByParentEmailOrderByTestDateAsc(String parentEmail);
}
