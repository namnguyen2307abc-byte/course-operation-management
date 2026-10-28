package com.talent.management.features.branch_facility_enrollment.service;

import com.talent.management.features.branch_facility_enrollment.dto.request.UpdateEquipmentRequest;
import com.talent.management.features.branch_facility_enrollment.dto.request.UpdateRoomRequest;
import com.talent.management.features.branch_facility_enrollment.mapper.BranchFacilityMapper;
import com.talent.management.features.branch_facility_enrollment.repository.*;
import com.talent.management.features.branch_facility_enrollment.service.impl.BranchFacilityServiceImpl;
import com.talent.management.shared.entity.*;
import com.talent.management.shared.enums.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BranchFacilityUpdateTest {
    @Mock BranchRepository branchRepository;
    @Mock RoomRepository roomRepository;
    @Mock EquipmentRepository equipmentRepository;
    @Mock FacilityClassRepository classRepository;
    @Mock FacilityLessonRepository lessonRepository;
    private BranchFacilityServiceImpl service;
    private Room room;
    private Branch branch;

    @BeforeEach
    void setUp() {
        service = new BranchFacilityServiceImpl(branchRepository, roomRepository, equipmentRepository,
                new BranchFacilityMapper(), classRepository, lessonRepository, new FacilityRulesService(equipmentRepository));
        branch = Branch.builder().id(1L).name("Branch").build();
        room = Room.builder().id(2L).branch(branch).roomCode("R-01").roomName("Old room")
                .capacity(10).roomType(RoomType.THEORY_ROOM).build();
        lenient().when(roomRepository.findByIdForScheduleUpdate(2L)).thenReturn(Optional.of(room));
        lenient().when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private ClassEntity classUsingRoom(String subject, int maxStudents) {
        return ClassEntity.builder().id(5L).classCode("CLASS-01").className("Class")
                .course(Course.builder().name("Course").program(Program.builder().code(subject).build()).build())
                .room(room).branch(branch).maxStudents(maxStudents).status(ClassStatus.OPEN).build();
    }

    private Lesson futureLesson(ClassEntity target) {
        return Lesson.builder().id(9L).classEntity(target).room(room)
                .lessonDate(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(3))
                .startTime(LocalTime.of(17, 0)).endTime(LocalTime.of(19, 0)).status(LessonStatus.SCHEDULED).build();
    }

    @Test
    void updatesRoomWithoutChangingCodeOrBranch() {
        var response = service.updateRoom(2L, new UpdateRoomRequest(" New room ", 8,
                RoomType.THEORY_ROOM, RoomStatus.MAINTENANCE, "Repair"));
        assertThat(response.getRoomName()).isEqualTo("New room");
        assertThat(room.getCapacity()).isEqualTo(8);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.MAINTENANCE);
        assertThat(room.getRoomCode()).isEqualTo("R-01");
        assertThat(room.getBranch()).isSameAs(branch);
    }

    @Test
    void rejectsCapacityBelowActiveClassSize() {
        when(classRepository.findClassesUsingRoom(eq(2L), any(), any())).thenReturn(List.of(classUsingRoom("CHESS", 10)));
        assertThatThrownBy(() -> service.updateRoom(2L,
                new UpdateRoomRequest("Room", 8, RoomType.THEORY_ROOM, RoomStatus.AVAILABLE, "")))
                .hasMessageContaining("CLASS-01");
        verify(roomRepository, never()).save(any());
    }

    @Test
    void rejectsIncompatibleRoomTypeButAllowsCompatibleTypeChange() {
        when(classRepository.findClassesUsingRoom(eq(2L), any(), any())).thenReturn(List.of(classUsingRoom("CHESS", 8)));
        assertThatThrownBy(() -> service.updateRoom(2L,
                new UpdateRoomRequest("Room", 10, RoomType.PIANO_GROUP, RoomStatus.AVAILABLE, "")))
                .hasMessageContaining("Không thể đổi loại phòng");
        service.updateRoom(2L, new UpdateRoomRequest("Room", 10, RoomType.CHESS_ROOM, RoomStatus.AVAILABLE, ""));
        assertThat(room.getRoomType()).isEqualTo(RoomType.CHESS_ROOM);
    }

    @Test
    void roomMaintenanceIsBlockedUntilRemainingLessonsHaveBeenMoved() {
        ClassEntity target = classUsingRoom("CHESS", 8);
        when(lessonRepository.findRemainingRoomLessons(eq(2L), any(), any())).thenReturn(List.of(futureLesson(target)));
        assertThatThrownBy(() -> service.updateRoom(2L,
                new UpdateRoomRequest("Room", 10, RoomType.THEORY_ROOM, RoomStatus.MAINTENANCE, "Repair")))
                .hasMessageContaining("CLASS-01").hasMessageContaining("chuyển phòng");
        assertThat(room.getStatus()).isEqualTo(RoomStatus.AVAILABLE);
        verify(roomRepository, never()).save(any());
    }

    @Test
    void completedClassHistoryDoesNotBlockResizingOrMaintenance() {
        service.updateRoom(2L, new UpdateRoomRequest("Room", 2, RoomType.GUITAR_ROOM, RoomStatus.MAINTENANCE, ""));
        assertThat(room.getCapacity()).isEqualTo(2);
        assertThat(room.getRoomType()).isEqualTo(RoomType.GUITAR_ROOM);
    }

    @Test
    void equipmentCanBeMarkedBrokenAndWarnsAffectedClassesWithoutChangingLessons() {
        room.setRoomType(RoomType.PIANO_GROUP);
        Equipment piano = Equipment.builder().id(3L).room(room).code("E-01").name("Piano")
                .category(EquipmentCategory.KEYBOARD).build();
        when(equipmentRepository.findRoomIdById(3L)).thenReturn(Optional.of(2L));
        when(equipmentRepository.findById(3L)).thenReturn(Optional.of(piano));
        when(equipmentRepository.saveAndFlush(piano)).thenReturn(piano);
        when(equipmentRepository.findByRoomIdOrderByIdAsc(2L)).thenReturn(List.of(piano));
        ClassEntity target = classUsingRoom("PIANO", 1);
        Lesson lesson = futureLesson(target);
        when(classRepository.findClassesUsingRoom(eq(2L), any(), any())).thenReturn(List.of(target));
        when(lessonRepository.findRemainingRoomLessons(eq(2L), any(), any())).thenReturn(List.of(lesson));

        var response = service.updateEquipment(3L, new UpdateEquipmentRequest(" Broken piano ",
                EquipmentCategory.KEYBOARD, EquipmentStatus.BROKEN, "S1", "Repair needed"));

        assertThat(piano.getName()).isEqualTo("Broken piano");
        assertThat(piano.getStatus()).isEqualTo(EquipmentStatus.BROKEN);
        assertThat(piano.getCode()).isEqualTo("E-01");
        assertThat(piano.getRoom()).isSameAs(room);
        assertThat(response.getWarnings()).anyMatch(text -> text.contains("CLASS-01"));
        assertThat(response.getAffectedLessonCount()).isEqualTo(1);
        assertThat(lesson.getStatus()).isEqualTo(LessonStatus.SCHEDULED);
        verify(lessonRepository, never()).save(any());
        verify(lessonRepository, never()).delete(any());
    }

    @Test
    void repairingEssentialEquipmentRemovesShortageWarnings() {
        room.setRoomType(RoomType.PIANO_GROUP);
        Equipment piano = Equipment.builder().id(3L).room(room).name("Piano")
                .category(EquipmentCategory.KEYBOARD).status(EquipmentStatus.BROKEN).build();
        when(equipmentRepository.findRoomIdById(3L)).thenReturn(Optional.of(2L));
        when(equipmentRepository.findById(3L)).thenReturn(Optional.of(piano));
        when(equipmentRepository.saveAndFlush(piano)).thenReturn(piano);
        when(equipmentRepository.findByRoomIdOrderByIdAsc(2L)).thenReturn(List.of(piano));
        when(classRepository.findClassesUsingRoom(eq(2L), any(), any())).thenReturn(List.of(classUsingRoom("PIANO", 1)));
        var response = service.updateEquipment(3L, new UpdateEquipmentRequest("Piano", EquipmentCategory.KEYBOARD,
                EquipmentStatus.GOOD, null, null));
        assertThat(response.getWarnings()).isEmpty();
        assertThat(response.getAffectedLessonCount()).isZero();
    }

    @Test
    void usageIncludesEquipmentClassesWarningsAndRemainingLessons() {
        when(roomRepository.findById(2L)).thenReturn(Optional.of(room));
        ClassEntity target = classUsingRoom("CHESS", 8);
        when(classRepository.findClassesUsingRoom(eq(2L), any(), any())).thenReturn(List.of(target));
        when(lessonRepository.findRemainingRoomLessons(eq(2L), any(), any())).thenReturn(List.of(futureLesson(target)));
        var response = service.getRoomUsage(2L);
        assertThat(response.classes()).extracting("classCode").containsExactly("CLASS-01");
        assertThat(response.upcomingLessonCount()).isEqualTo(1);
        assertThat(response.upcomingLessons()).extracting("id").containsExactly(9L);
        assertThat(response.warnings()).anyMatch(text -> text.contains("bộ cờ"));
    }

    @Test
    void usageUsesClassTeacherWhenLegacyLessonHasNoTeacherOverride() {
        when(roomRepository.findById(2L)).thenReturn(Optional.of(room));
        ClassEntity target = classUsingRoom("CHESS", 8);
        target.setTeacher(User.builder().fullName("Class teacher").build());
        when(lessonRepository.findRemainingRoomLessons(eq(2L), any(), any()))
                .thenReturn(List.of(futureLesson(target)));
        assertThat(service.getRoomUsage(2L).upcomingLessons().get(0).teacherName()).isEqualTo("Class teacher");
    }
}
