package com.talent.management;

import com.talent.management.features.branch_facility_enrollment.repository.FacilityClassRepository;
import com.talent.management.features.branch_facility_enrollment.repository.FacilityLessonRepository;
import com.talent.management.features.branch_facility_enrollment.repository.RoomRepository;
import com.talent.management.features.branch_facility_enrollment.repository.EquipmentRepository;
import com.talent.management.features.class_management.repository.ManagedClassRepository;
import com.talent.management.features.course_enrollment.repository.EnrollmentRequestRepository;
import com.talent.management.shared.enums.EnrollmentRequestStatus;
import com.talent.management.shared.service.ListPaging;
import org.springframework.data.domain.Sort;
import java.util.EnumSet;
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
    @Autowired RoomRepository rooms;
    @Autowired EquipmentRepository equipments;
    @Autowired ManagedClassRepository classes;
    @Autowired EnrollmentRequestRepository requests;

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

    @Test
    @Transactional(readOnly = true)
    void pagedQueriesWorkWithConfiguredJdbcDriver() {
        var now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        var paging = ListPaging.request(0, 5, Sort.by("id"));
        assertThat(rooms.searchPage(-1L, "", null, null, paging).getContent()).isEmpty();
        assertThat(equipments.searchPage(-1L, null, "", null, null, paging).getContent()).isEmpty();
        assertThat(classes.searchPage(ListPaging.keyword("%_[x]!"), null, -1L, null, paging).getContent()).isEmpty();
        assertThat(requests.searchPage(-1L, EnumSet.allOf(EnrollmentRequestStatus.class), "", paging).getContent()).isEmpty();
        assertThat(facilityClassRepository.findClassesUsingRoom(-1L, now.toLocalDate(), now.toLocalTime(), paging).getContent()).isEmpty();
        assertThat(facilityLessonRepository.findRemainingRoomLessons(-1L, now.toLocalDate(), now.toLocalTime(), paging).getContent()).isEmpty();
        assertThat(classes.searchPage("", null, null, null, paging).getContent().size()).isLessThanOrEqualTo(5);
        assertThat(requests.searchPage(null, EnumSet.allOf(EnrollmentRequestStatus.class), "", paging).getContent().size()).isLessThanOrEqualTo(5);
    }

}
