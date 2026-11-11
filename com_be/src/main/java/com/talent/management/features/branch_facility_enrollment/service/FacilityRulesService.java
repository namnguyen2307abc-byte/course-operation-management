package com.talent.management.features.branch_facility_enrollment.service;

import com.talent.management.features.branch_facility_enrollment.repository.EquipmentRepository;
import com.talent.management.shared.entity.Course;
import com.talent.management.shared.entity.Equipment;
import com.talent.management.shared.entity.Room;
import com.talent.management.shared.enums.EquipmentCategory;
import com.talent.management.shared.enums.EquipmentStatus;
import com.talent.management.shared.enums.RoomStatus;
import com.talent.management.shared.enums.RoomType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class FacilityRulesService {
    private final EquipmentRepository equipmentRepository;

    public String courseSubject(Course course) {
        if (course == null || course.getProgram() == null || course.getProgram().getCode() == null) return "";
        String code = course.getProgram().getCode().trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return code.equals("SWIM") ? "SWIMMING" : code;
    }

    public boolean supportsSubject(RoomType type, String subject) {
        if (type == null) return false;
        return switch (subject) {
            case "PIANO" -> type == RoomType.PIANO_INDIVIDUAL || type == RoomType.PIANO_GROUP
                    || type == RoomType.MULTIPURPOSE_ROOM;
            case "GUITAR" -> type == RoomType.GUITAR_ROOM || type == RoomType.MULTIPURPOSE_ROOM;
            case "CHESS" -> type == RoomType.CHESS_ROOM || type == RoomType.THEORY_ROOM
                    || type == RoomType.MULTIPURPOSE_ROOM;
            case "SWIMMING" -> type == RoomType.SWIMMING_POOL;
            case "DANCE" -> type == RoomType.DANCE_ROOM;
            case "MARTIAL_ARTS" -> type == RoomType.SPORTS_AREA;
            default -> false;
        };
    }

    public List<String> assignmentProblems(Course course, Room room, int maxStudents) {
        List<Equipment> equipments = equipmentRepository.findByRoomIdOrderByIdAsc(room.getId());
        return assignmentProblems(course, room, maxStudents, equipments);
    }

    public List<String> assignmentProblems(Course course, Room room, int maxStudents, List<Equipment> equipments) {
        List<String> problems = new ArrayList<>();
        if (room.getBranch() == null || !Boolean.TRUE.equals(room.getBranch().getActive())) {
            problems.add("Cơ sở đã ngừng hoạt động");
        }
        if (room.getStatus() == null || room.getStatus() == RoomStatus.MAINTENANCE) {
            problems.add("Phòng học đang bảo trì hoặc chưa có tình trạng hợp lệ");
        }
        if (room.getCapacity() == null || maxStudents > room.getCapacity()) {
            problems.add("Sĩ số tối đa của lớp vượt sức chứa phòng học");
        }
        String subject = courseSubject(course);
        if (!supportsSubject(room.getRoomType(), subject)) {
            problems.add("Loại phòng không phù hợp với môn " + subject);
        }
        problems.addAll(equipmentProblems(subject, maxStudents, equipments));
        return problems;
    }

    public List<String> equipmentProblems(String subject, int maxStudents, List<Equipment> equipments) {
        List<String> problems = new ArrayList<>();
        int available = 0;
        for (Equipment equipment : equipments) {
            if (equipment.getStatus() != EquipmentStatus.GOOD) continue;
            EquipmentCategory category = equipment.getCategory();
            if (subject.equals("PIANO") && (category == EquipmentCategory.GRAND_PIANO
                    || category == EquipmentCategory.UPRIGHT_PIANO || category == EquipmentCategory.KEYBOARD)) available++;
            if (subject.equals("CHESS") && category == EquipmentCategory.CHESS_SET) available++;
            if (subject.equals("SWIMMING") && category == EquipmentCategory.RESCUE_EQUIPMENT) available++;
        }
        switch (subject) {
            case "PIANO" -> {
                if (available < maxStudents) problems.add("Piano cần " + maxStudents
                        + " đàn hoạt động tốt (mỗi học viên một đàn); hiện có " + available);
            }
            case "CHESS" -> {
                int required = (maxStudents + 1) / 2;
                if (available < required) problems.add("Cờ vua cần " + required
                        + " bộ cờ hoạt động tốt (hai học viên một bộ); hiện có " + available);
            }
            case "SWIMMING" -> {
                if (available < 1) problems.add("Bể bơi chưa có dụng cụ cứu hộ trạng thái Tốt");
            }
            // Guitar students bring their own instruments; other subjects have no equipment minimum yet.
            default -> { }
        }
        return problems;
    }

    public List<String> roomWarnings(Room room, List<Equipment> equipments) {
        List<String> warnings = new ArrayList<>();
        if (room.getBranch() == null || !Boolean.TRUE.equals(room.getBranch().getActive())) {
            warnings.add("Cơ sở ngừng hoạt động: không nhận phân công mới");
        }
        if (room.getStatus() == RoomStatus.MAINTENANCE) warnings.add("Phòng bảo trì: không nhận phân công mới");
        String subject = switch (room.getRoomType()) {
            case PIANO_GROUP, PIANO_INDIVIDUAL -> "PIANO";
            case CHESS_ROOM -> "CHESS";
            case SWIMMING_POOL -> "SWIMMING";
            default -> "";
        };
        warnings.addAll(equipmentProblems(subject, 1, equipments));
        return warnings;
    }
}
