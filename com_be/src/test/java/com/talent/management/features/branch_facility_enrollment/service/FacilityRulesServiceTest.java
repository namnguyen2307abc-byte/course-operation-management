package com.talent.management.features.branch_facility_enrollment.service;

import com.talent.management.features.branch_facility_enrollment.repository.EquipmentRepository;
import com.talent.management.shared.entity.Branch;
import com.talent.management.shared.entity.Course;
import com.talent.management.shared.entity.Equipment;
import com.talent.management.shared.entity.Program;
import com.talent.management.shared.entity.Room;
import com.talent.management.shared.enums.EquipmentCategory;
import com.talent.management.shared.enums.EquipmentStatus;
import com.talent.management.shared.enums.RoomStatus;
import com.talent.management.shared.enums.RoomType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacilityRulesServiceTest {
    @Mock EquipmentRepository equipmentRepository;
    @InjectMocks FacilityRulesService service;

    @ParameterizedTest
    @CsvSource({
            "SWIMMING_POOL, SWIMMING, true",
            "SPORTS_AREA, SWIMMING, false",
            "SPORTS_AREA, MARTIAL_ARTS, true",
            "SWIMMING_POOL, MARTIAL_ARTS, false",
            "MULTIPURPOSE_ROOM, PIANO, true",
            "MULTIPURPOSE_ROOM, GUITAR, true",
            "MULTIPURPOSE_ROOM, CHESS, true",
            "MULTIPURPOSE_ROOM, SWIMMING, false",
            "MULTIPURPOSE_ROOM, MARTIAL_ARTS, false",
            "MULTIPURPOSE_ROOM, DANCE, false",
            "PIANO_INDIVIDUAL, PIANO, true",
            "PIANO_GROUP, PIANO, true",
            "GUITAR_ROOM, GUITAR, true",
            "CHESS_ROOM, CHESS, true",
            "THEORY_ROOM, CHESS, true",
            "DANCE_ROOM, DANCE, true",
            "THEORY_ROOM, PIANO, false",
            "GUITAR_ROOM, CHESS, false"
    })
    void checksSubjectEligibility(RoomType roomType, String subject, boolean eligible) {
        assertThat(service.supportsSubject(roomType, subject)).isEqualTo(eligible);
    }

    @ParameterizedTest
    @CsvSource({" swim , SWIMMING", " swimming , SWIMMING", "martial-arts, MARTIAL_ARTS",
            "martial arts, MARTIAL_ARTS", "piano, PIANO"})
    void normalizesProgramCodes(String code, String expectedSubject) {
        assertThat(service.courseSubject(course(code))).isEqualTo(expectedSubject);
    }

    @ParameterizedTest
    @EnumSource(RoomType.class)
    void rejectsUnknownSubjectForEveryRoomType(RoomType roomType) {
        assertThat(service.supportsSubject(roomType, "UNKNOWN")).isFalse();
        assertThat(service.assignmentProblems(course("UNKNOWN"), room(roomType), 4, List.of()))
                .hasSize(1);
    }

    @Test
    void rejectsMissingCourseProgramCodeOrRoomType() {
        assertThat(service.courseSubject(null)).isEmpty();
        assertThat(service.courseSubject(Course.builder().build())).isEmpty();
        assertThat(service.courseSubject(course(null))).isEmpty();
        assertThat(service.supportsSubject(null, "GUITAR")).isFalse();
        assertThat(service.assignmentProblems(null, room(RoomType.MULTIPURPOSE_ROOM), 4, List.of()))
                .hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(value = EquipmentCategory.class, names = {"GRAND_PIANO", "UPRIGHT_PIANO", "KEYBOARD"})
    void acceptsEachGoodPianoCategoryButRequiresOnePerStudent(EquipmentCategory category) {
        List<Equipment> instruments = List.of(equipment(category, EquipmentStatus.GOOD));

        assertThat(service.equipmentProblems("PIANO", 1, instruments)).isEmpty();
        assertThat(service.equipmentProblems("PIANO", 2, instruments)).hasSize(1);
    }

    @Test
    void combinesPianoCategoriesAndAcceptsEquipmentAboveTheMinimum() {
        List<Equipment> instruments = List.of(
                equipment(EquipmentCategory.GRAND_PIANO, EquipmentStatus.GOOD),
                equipment(EquipmentCategory.UPRIGHT_PIANO, EquipmentStatus.GOOD),
                equipment(EquipmentCategory.KEYBOARD, EquipmentStatus.GOOD));

        assertThat(service.assignmentProblems(course("PIANO"), room(RoomType.PIANO_GROUP), 3, instruments))
                .isEmpty();
        assertThat(service.equipmentProblems("PIANO", 2, instruments)).isEmpty();
        assertThat(service.equipmentProblems("PIANO", 4, instruments)).hasSize(1);
    }

    @ParameterizedTest
    @CsvSource({"1, 0, false", "1, 1, true", "2, 1, true", "3, 1, false", "3, 2, true",
            "4, 2, true", "5, 2, false", "5, 3, true", "5, 4, true"})
    void requiresOneChessSetPerPairRoundingUp(int students, int sets, boolean eligible) {
        List<Equipment> equipment = java.util.stream.IntStream.range(0, sets)
                .mapToObj(i -> equipment(EquipmentCategory.CHESS_SET, EquipmentStatus.GOOD)).toList();

        assertThat(service.equipmentProblems("CHESS", students, equipment).isEmpty()).isEqualTo(eligible);
    }

    @ParameterizedTest
    @CsvSource({"PIANO, GRAND_PIANO", "PIANO, UPRIGHT_PIANO", "PIANO, KEYBOARD",
            "CHESS, CHESS_SET", "SWIMMING, RESCUE_EQUIPMENT"})
    void brokenAndMaintenanceEquipmentDoNotSatisfyMinimums(String subject, EquipmentCategory category) {
        List<Equipment> equipment = List.of(equipment(category, EquipmentStatus.GOOD),
                equipment(category, EquipmentStatus.BROKEN), equipment(category, EquipmentStatus.MAINTENANCE));
        int students = subject.equals("CHESS") ? 3 : 2;
        if (subject.equals("SWIMMING")) {
            equipment = equipment.subList(1, 3);
        }

        assertThat(service.equipmentProblems(subject, students, equipment)).hasSize(1);
    }

    @ParameterizedTest
    @CsvSource({"PIANO, GUITAR", "CHESS, KEYBOARD", "SWIMMING, SPORTS_EQUIPMENT"})
    void unrelatedGoodEquipmentDoesNotSatisfyMinimums(String subject, EquipmentCategory category) {
        assertThat(service.equipmentProblems(subject, 1, List.of(equipment(category, EquipmentStatus.GOOD))))
                .hasSize(1);
    }

    @Test
    void swimmingRequiresOneGoodRescueItemRegardlessOfClassSize() {
        Room pool = room(RoomType.SWIMMING_POOL);

        assertThat(service.assignmentProblems(course("SWIM"), pool, 20, List.of())).hasSize(1);
        assertThat(service.assignmentProblems(course("SWIM"), pool, 20,
                List.of(equipment(EquipmentCategory.RESCUE_EQUIPMENT, EquipmentStatus.GOOD)))).isEmpty();
    }

    @Test
    void guitarNeedsNoMandatoryInstruments() {
        assertThat(service.assignmentProblems(course("GUITAR"), room(RoomType.GUITAR_ROOM), 20, List.of()))
                .isEmpty();
        assertThat(service.assignmentProblems(course("GUITAR"), room(RoomType.MULTIPURPOSE_ROOM), 20,
                List.of(equipment(EquipmentCategory.GUITAR, EquipmentStatus.BROKEN)))).isEmpty();
    }

    @Test
    void rejectsInactiveOrMissingBranchActivity() {
        Room room = room(RoomType.GUITAR_ROOM);
        room.getBranch().setActive(false);

        assertThat(service.assignmentProblems(course("GUITAR"), room, 4, List.of())).hasSize(1);
        room.getBranch().setActive(null);
        assertThat(service.assignmentProblems(course("GUITAR"), room, 4, List.of())).hasSize(1);
    }

    @Test
    void rejectsMissingBranch() {
        Room room = room(RoomType.GUITAR_ROOM);
        room.setBranch(null);

        assertThat(service.assignmentProblems(course("GUITAR"), room, 4, List.of())).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(value = RoomStatus.class, names = {"AVAILABLE", "OCCUPIED"})
    void acceptsUsableRoomAtExactCapacity(RoomStatus status) {
        Room room = room(RoomType.GUITAR_ROOM);
        room.setStatus(status);

        assertThat(service.assignmentProblems(course("GUITAR"), room, room.getCapacity(), List.of())).isEmpty();
        assertThat(service.assignmentProblems(course("GUITAR"), room, room.getCapacity() + 1, List.of()))
                .hasSize(1);
    }

    @Test
    void rejectsMaintenanceAndMissingRoomStatusOrCapacity() {
        Room room = room(RoomType.GUITAR_ROOM);
        room.setStatus(RoomStatus.MAINTENANCE);
        assertThat(service.assignmentProblems(course("GUITAR"), room, 4, List.of())).hasSize(1);
        room.setStatus(null);
        assertThat(service.assignmentProblems(course("GUITAR"), room, 4, List.of())).hasSize(1);
        room.setStatus(RoomStatus.AVAILABLE);
        room.setCapacity(null);
        assertThat(service.assignmentProblems(course("GUITAR"), room, 4, List.of())).hasSize(1);
    }

    @Test
    void loadsEquipmentForTheAssignedRoom() {
        Room room = room(RoomType.PIANO_GROUP);
        when(equipmentRepository.findByRoomIdOrderByIdAsc(room.getId())).thenReturn(
                List.of(equipment(EquipmentCategory.KEYBOARD, EquipmentStatus.GOOD)));

        assertThat(service.assignmentProblems(course("PIANO"), room, 1)).isEmpty();
        assertThat(service.assignmentProblems(course("PIANO"), room, 2)).hasSize(1);
        verify(equipmentRepository, org.mockito.Mockito.times(2)).findByRoomIdOrderByIdAsc(room.getId());
    }

    private Course course(String subject) {
        return Course.builder().program(Program.builder().code(subject).build()).build();
    }

    private Room room(RoomType type) {
        return Room.builder().id(7L).branch(Branch.builder().active(true).build())
                .roomType(type).capacity(20).status(RoomStatus.AVAILABLE).build();
    }

    private Equipment equipment(EquipmentCategory category, EquipmentStatus status) {
        return Equipment.builder().category(category).status(status).build();
    }
}
