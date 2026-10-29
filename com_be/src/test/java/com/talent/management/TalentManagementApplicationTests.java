package com.talent.management;

import com.talent.management.features.branch_facility_enrollment.repository.FacilityClassRepository;
import com.talent.management.features.branch_facility_enrollment.repository.FacilityLessonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TalentManagementApplicationTests {
    @Autowired FacilityClassRepository facilityClassRepository;
    @Autowired FacilityLessonRepository facilityLessonRepository;

    @Test
    void contextLoads() {
    }

    @Test
    @Transactional(readOnly = true)
    void facilityTimeQueriesWorkOnConfiguredDatabase() {
        // Exercise the real JDBC driver's time binding as well as the H2 repository tests.
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        assertThat(facilityClassRepository.findClassesUsingRoom(-1L, now.toLocalDate(), now.toLocalTime())).isEmpty();
        assertThat(facilityLessonRepository.findRemainingRoomLessons(-1L, now.toLocalDate(), now.toLocalTime())).isEmpty();
    }

}
