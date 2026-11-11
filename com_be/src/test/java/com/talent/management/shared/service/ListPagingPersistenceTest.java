package com.talent.management.shared.service;

import com.talent.management.features.branch_facility_enrollment.repository.*;
import com.talent.management.features.class_management.repository.ManagedClassRepository;
import com.talent.management.features.course_enrollment.repository.EnrollmentRequestRepository;
import com.talent.management.shared.entity.*;
import com.talent.management.shared.enums.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest(showSql = false)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class ListPagingPersistenceTest {
    @Autowired TestEntityManager em;
    @Autowired ManagedClassRepository classes;
    @Autowired RoomRepository rooms;
    @Autowired EquipmentRepository equipments;
    @Autowired EnrollmentRequestRepository requests;
    @Autowired FacilityClassRepository roomClasses;
    @Autowired FacilityLessonRepository lessons;

    private Branch branch(String code) {
        return em.persist(Branch.builder().code(code).name(code).address("Address").build());
    }

    private Course course() {
        var program = em.persist(Program.builder().code("CHESS").name("Chess").build());
        return em.persist(Course.builder().program(program).code("CHESS-1").name("Chess")
                .tuitionFee(BigDecimal.TEN).build());
    }

    private Room room(Branch branch, String code) {
        return em.persist(Room.builder().branch(branch).roomCode(code).roomName("Same name")
                .roomType(RoomType.CHESS_ROOM).capacity(12).build());
    }

    @Test
    void boundedClassPagesHaveStableOrderAndLiteralSearch() {
        var branch = branch("B1");
        var course = course();
        for (int i = 0; i < 12; i++) {
            em.persist(ClassEntity.builder().course(course).branch(branch).classCode("C" + i)
                    .className(i == 0 ? "100%_[test]!" : "Ordinary class").maxStudents(10)
                    .status(i == 11 ? ClassStatus.FINISHED : ClassStatus.OPEN).build());
        }
        em.flush(); em.clear();
        var first = classes.searchPage("", null, null, ClassStatus.OPEN, ListPaging.request(0, 5, Sort.by("id")));
        var second = classes.searchPage("", course.getId(), branch.getId(), ClassStatus.OPEN, ListPaging.request(1, 5, Sort.by("id")));
        assertThat(first.getTotalElements()).isEqualTo(11);
        assertThat(first.getTotalPages()).isEqualTo(3);
        assertThat(first.getContent()).hasSize(5);
        assertThat(second.getContent()).extracting(ClassEntity::getId)
                .doesNotContainAnyElementsOf(first.map(ClassEntity::getId).getContent());
        assertThat(classes.searchPage(ListPaging.keyword("%_[test]!"), null, null, null,
                ListPaging.request(0, 10, Sort.by("id"))).getContent()).hasSize(1);
        assertThat(classes.searchPage("", null, null, null,
                ListPaging.request(100, 5, Sort.by("id"))).getContent()).isEmpty();
    }

    @Test
    void facilityPagesFilterBranchTypeStatusAndCountAllRows() {
        var a = branch("A");
        var b = branch("B");
        Room first = null;
        for (int i = 0; i < 12; i++) {
            var room = room(a, "A" + String.format("%02d", i));
            if (first == null) first = room;
            em.persist(Equipment.builder().room(room).code("E" + i).name("Chess set")
                    .category(EquipmentCategory.CHESS_SET).status(i == 0 ? EquipmentStatus.BROKEN : EquipmentStatus.GOOD).build());
        }
        room(b, "B1");
        em.flush(); em.clear();
        var page = rooms.searchPage(a.getId(), "", RoomStatus.AVAILABLE, RoomType.CHESS_ROOM,
                ListPaging.request(1, 5, Sort.by("roomName", "id")));
        assertThat(page.getContent()).hasSize(5);
        assertThat(page.getTotalElements()).isEqualTo(12);
        assertThat(rooms.countByBranchId(a.getId())).isEqualTo(12);
        assertThat(equipments.countByRoomBranchIdAndStatus(a.getId(), EquipmentStatus.GOOD)).isEqualTo(11);
        assertThat(equipments.searchPage(a.getId(), null, "", EquipmentStatus.GOOD, EquipmentCategory.CHESS_SET,
                ListPaging.request(2, 5, Sort.by("code", "id"))).getContent()).hasSize(1);
        assertThat(equipments.searchPage(a.getId(), first.getId(), "", null, null,
                ListPaging.request(0, 5, Sort.by("id"))).getTotalElements()).isEqualTo(1);
        assertThat(equipments.searchPage(b.getId(), first.getId(), "", null, null,
                ListPaging.request(0, 5, Sort.by("id"))).getTotalElements()).isZero();
    }

    @Test
    void parentRequestsAreScopedBeforePagingAndNullCourseIsSearchable() {
        var parent = em.persist(User.builder().username("parent").password("test").fullName("Parent").role(Role.PARENT).build());
        var other = em.persist(User.builder().username("other").password("test").fullName("Other").role(Role.PARENT).build());
        var child = em.persist(Student.builder().parent(parent).fullName("Student").build());
        var timestamp = LocalDateTime.of(2026, 1, 1, 10, 0);
        for (int i = 0; i < 12; i++) {
            em.persist(EnrollmentRequest.builder().student(child).requestedBy(i == 11 ? other : parent)
                    .createdAt(timestamp).status(i == 0 ? EnrollmentRequestStatus.CANCELLED : EnrollmentRequestStatus.WAITING_PLACEMENT).build());
        }
        em.flush(); em.clear();
        var own = requests.searchPage(parent.getId(), EnumSet.allOf(EnrollmentRequestStatus.class), "student",
                ListPaging.request(0, 5, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        assertThat(own.getContent()).hasSize(5);
        assertThat(own.getTotalElements()).isEqualTo(11);
        assertThat(own.getContent()).allSatisfy(item -> assertThat(item.getRequestedBy().getId()).isEqualTo(parent.getId()));
        var queue = requests.searchPage(null, EnumSet.of(EnrollmentRequestStatus.WAITING_PLACEMENT), "",
                ListPaging.request(0, 5, Sort.by("createdAt", "id")));
        assertThat(queue.getTotalElements()).isEqualTo(11);
        assertThat(queue.getContent()).allSatisfy(item -> assertThat(item.getStatus()).isEqualTo(EnrollmentRequestStatus.WAITING_PLACEMENT));
    }

    @Test
    void roomUsagePagesRespectOverridesAndFutureScheduledLessons() {
        var branch = branch("B");
        var course = course();
        var a = room(branch, "A");
        var b = room(branch, "B");
        var cls = em.persist(ClassEntity.builder().course(course).branch(branch).room(a).classCode("CHESS")
                .className("Chess").maxStudents(10).status(ClassStatus.OPEN).build());
        var date = LocalDate.of(2027, 1, 1);
        for (int i = 0; i < 13; i++) {
            em.persist(Lesson.builder().classEntity(cls).room(i == 12 ? b : null)
                    .lessonDate(date.plusDays(i)).startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(10, 0))
                    .status(i == 11 ? LessonStatus.CANCELLED : LessonStatus.SCHEDULED).build());
        }
        em.flush(); em.clear();
        var page = lessons.findRemainingRoomLessons(a.getId(), date, LocalTime.of(10, 0),
                ListPaging.request(0, 5, Sort.by("lessonDate", "startTime", "id")));
        assertThat(page.getTotalElements()).isEqualTo(10);
        assertThat(page.getContent()).hasSize(5);
        assertThat(page.getContent().get(0).getLessonDate()).isEqualTo(date.plusDays(1));
        assertThat(roomClasses.findClassesUsingRoom(b.getId(), date, LocalTime.NOON,
                ListPaging.request(0, 5, Sort.by("classCode", "id"))).getTotalElements()).isEqualTo(1);
    }

    @Test
    void invalidPageSizesAndLongSearchAreRejected() {
        for (int size : new int[]{0, -1, 100, 10000}) {
            assertThatThrownBy(() -> ListPaging.request(0, size, Sort.by("id"))).hasMessageContaining("400");
        }
        assertThatThrownBy(() -> ListPaging.request(-1, 10, Sort.by("id"))).hasMessageContaining("400");
        assertThatThrownBy(() -> ListPaging.keyword("a".repeat(101))).hasMessageContaining("400");
    }
}
