package com.talent.management.features.branch_facility_enrollment.service;

import com.talent.management.features.branch_facility_enrollment.dto.response.*;
import com.talent.management.features.branch_facility_enrollment.mapper.BranchFacilityMapper;
import com.talent.management.features.branch_facility_enrollment.repository.*;
import com.talent.management.shared.dto.PageResponse;
import com.talent.management.shared.enums.*;
import com.talent.management.shared.service.ListPaging;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacilityBrowsingService {
    private final RoomRepository rooms;
    private final EquipmentRepository equipments;
    private final FacilityClassRepository classes;
    private final FacilityLessonRepository lessons;
    private final BranchFacilityMapper mapper;
    private final FacilityRulesService rules;

    public record BranchCounts(long rooms, long equipment, long goodEquipment) { }
    public record RoomOverview(RoomResponse room, List<String> warnings) { }

    public BranchCounts counts(Long branchId) {
        return new BranchCounts(rooms.countByBranchId(branchId), equipments.countByRoomBranchId(branchId),
                equipments.countByRoomBranchIdAndStatus(branchId, EquipmentStatus.GOOD));
    }

    public RoomResponse roomDetail(Long roomId) {
        return mapper.toRoomResponse(rooms.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phòng học")));
    }

    public PageResponse<RoomResponse> rooms(Long branchId, String keyword, RoomStatus status, RoomType type,
                                             int page, int size, String order) {
        Sort sort = switch (order) {
            case "code" -> Sort.by("roomCode", "id");
            case "name" -> Sort.by("roomName", "id");
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thứ tự phòng không hợp lệ");
        };
        var result = rooms.searchPage(branchId, ListPaging.keyword(keyword), status, type, ListPaging.request(page, size, sort));
        // Reuse the same room warnings and inventory rules as the legacy screen, for this page only.
        return PageResponse.from(result.map(room -> {
            var overview = roomOverview(room.getId());
            return overview.room();
        }));
    }

    public PageResponse<EquipmentResponse> equipments(Long branchId, Long roomId, String keyword,
            EquipmentStatus status, EquipmentCategory category, int page, int size, String order) {
        Sort sort = switch (order) {
            case "code" -> Sort.by("code", "id");
            case "name" -> Sort.by("name", "id");
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thứ tự thiết bị không hợp lệ");
        };
        return PageResponse.from(equipments.searchPage(branchId, roomId, ListPaging.keyword(keyword), status, category,
                ListPaging.request(page, size, sort)).map(mapper::toEquipmentResponse));
    }

    public RoomOverview roomOverview(Long roomId) {
        var room = rooms.findById(roomId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phòng học"));
        // These complete collections are used to evaluate facility rules, not to render a table.
        var inventory = equipments.findByRoomIdOrderByIdAsc(roomId);
        var now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        var warnings = new java.util.ArrayList<>(rules.roomWarnings(room, inventory));
        for (var item : classes.findClassesUsingRoom(roomId, now.toLocalDate(), now.toLocalTime())) {
            var problems = rules.assignmentProblems(item.getCourse(), room, item.getMaxStudents(), inventory);
            if (!problems.isEmpty()) warnings.add("Lớp " + item.getClassCode() + ": " + String.join("; ", problems));
        }
        var response = mapper.toRoomResponse(room);
        response.setWarnings(warnings);
        response.setTotalEquipmentCount(inventory.size());
        response.setGoodEquipmentCount((int) inventory.stream().filter(e -> e.getStatus() == EquipmentStatus.GOOD).count());
        return new RoomOverview(response, warnings);
    }

    public PageResponse<EquipmentResponse> roomEquipment(Long roomId, int page, int size) {
        var room = rooms.findById(roomId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phòng học"));
        return equipments(room.getBranch().getId(), roomId, "", null, null, page, size, "code");
    }

    public PageResponse<RoomUsageResponse.ClassUsage> roomClasses(Long roomId, int page, int size) {
        var now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        return PageResponse.from(classes.findClassesUsingRoom(roomId, now.toLocalDate(), now.toLocalTime(),
                ListPaging.request(page, size, Sort.by("classCode", "id"))).map(item ->
                new RoomUsageResponse.ClassUsage(item.getId(), item.getClassCode(), item.getClassName(),
                        item.getCourse().getName(), item.getMaxStudents(), item.getStatus())));
    }

    public PageResponse<RoomUsageResponse.LessonUsage> roomLessons(Long roomId, int page, int size) {
        var now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        return PageResponse.from(lessons.findRemainingRoomLessons(roomId, now.toLocalDate(), now.toLocalTime(),
                ListPaging.request(page, size, Sort.by("lessonDate", "startTime", "id"))).map(lesson -> {
            var item = lesson.getClassEntity();
            var teacher = lesson.getTeacher() == null ? item.getTeacher() : lesson.getTeacher();
            return new RoomUsageResponse.LessonUsage(lesson.getId(), item.getId(), item.getClassCode(), item.getClassName(),
                    lesson.getLessonDate(), lesson.getStartTime(), lesson.getEndTime(), teacher == null ? null : teacher.getFullName());
        }));
    }
}
