package com.talent.management.features.branch_facility_enrollment.service.impl;

import com.talent.management.features.branch_facility_enrollment.dto.request.CreateBranchRequest;
import com.talent.management.features.branch_facility_enrollment.dto.request.CreateEquipmentRequest;
import com.talent.management.features.branch_facility_enrollment.dto.request.CreateRoomRequest;
import com.talent.management.features.branch_facility_enrollment.dto.request.UpdateEquipmentRequest;
import com.talent.management.features.branch_facility_enrollment.dto.request.UpdateRoomRequest;
import com.talent.management.features.branch_facility_enrollment.dto.response.BranchOptionResponse;
import com.talent.management.features.branch_facility_enrollment.dto.response.BranchResponse;
import com.talent.management.features.branch_facility_enrollment.dto.response.EquipmentResponse;
import com.talent.management.features.branch_facility_enrollment.dto.response.RoomOptionResponse;
import com.talent.management.features.branch_facility_enrollment.dto.response.RoomResponse;
import com.talent.management.features.branch_facility_enrollment.dto.response.RoomUsageResponse;
import com.talent.management.features.branch_facility_enrollment.mapper.BranchFacilityMapper;
import com.talent.management.features.branch_facility_enrollment.repository.BranchRepository;
import com.talent.management.features.branch_facility_enrollment.repository.EquipmentRepository;
import com.talent.management.features.branch_facility_enrollment.repository.RoomRepository;
import com.talent.management.features.branch_facility_enrollment.repository.FacilityClassRepository;
import com.talent.management.features.branch_facility_enrollment.repository.FacilityLessonRepository;
import com.talent.management.features.branch_facility_enrollment.service.BranchFacilityService;
import com.talent.management.features.branch_facility_enrollment.service.FacilityRulesService;
import com.talent.management.shared.entity.Branch;
import com.talent.management.shared.entity.Equipment;
import com.talent.management.shared.entity.Room;
import com.talent.management.shared.entity.ClassEntity;
import com.talent.management.shared.entity.Lesson;
import com.talent.management.shared.enums.EquipmentCategory;
import com.talent.management.shared.enums.EquipmentStatus;
import com.talent.management.shared.enums.RoomStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BranchFacilityServiceImpl implements BranchFacilityService {

    private final BranchRepository branchRepository;
    private final RoomRepository roomRepository;
    private final EquipmentRepository equipmentRepository;
    private final BranchFacilityMapper branchFacilityMapper;
    private final FacilityClassRepository classRepository;
    private final FacilityLessonRepository lessonRepository;
    private final FacilityRulesService facilityRules;

    private static final ZoneId CENTER_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Override
    public List<BranchResponse> getAllBranches() {
        return branchRepository.findAll().stream()
                .map(branchFacilityMapper::toBranchResponse)
                .toList();
    }

    @Override
    public List<BranchOptionResponse> getBranchOptions() {
        return branchRepository.findByActiveTrue().stream()
                .map(branchFacilityMapper::toBranchOptionResponse)
                .toList();
    }

    @Override
    public List<RoomResponse> getRoomsByBranch(Long branchId) {
        return roomRepository.findByBranchId(branchId).stream()
                .map(this::roomResponseWithWarnings)
                .toList();
    }

    @Override
    public RoomUsageResponse getRoomUsage(Long roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phòng học"));
        List<Equipment> equipments = equipmentRepository.findByRoomIdOrderByIdAsc(roomId);
        List<ClassEntity> classes = classesUsingRoom(roomId);
        List<Lesson> lessons = remainingLessons(roomId);
        List<RoomUsageResponse.ClassUsage> classDetails = new ArrayList<>();
        List<RoomUsageResponse.LessonUsage> lessonDetails = new ArrayList<>();
        List<String> warnings = roomWarnings(room, equipments, classes);
        for (ClassEntity item : classes) {
            classDetails.add(new RoomUsageResponse.ClassUsage(item.getId(), item.getClassCode(), item.getClassName(),
                    item.getCourse().getName(), item.getMaxStudents(), item.getStatus()));
        }
        for (Lesson lesson : lessons) {
            ClassEntity item = lesson.getClassEntity();
            var teacher = lesson.getTeacher() == null ? item.getTeacher() : lesson.getTeacher();
            lessonDetails.add(new RoomUsageResponse.LessonUsage(lesson.getId(), item.getId(), item.getClassCode(),
                    item.getClassName(), lesson.getLessonDate(), lesson.getStartTime(), lesson.getEndTime(),
                    teacher == null ? null : teacher.getFullName()));
        }
        RoomResponse roomResponse = roomResponse(room, equipments, warnings);
        return new RoomUsageResponse(roomResponse, equipments.stream().map(branchFacilityMapper::toEquipmentResponse).toList(),
                classDetails, lessonDetails, warnings, lessons.size());
    }

    @Override
    public List<RoomOptionResponse> getRoomOptions(Long branchId) {
        return roomRepository.findByBranchId(branchId).stream()
                .map(branchFacilityMapper::toRoomOptionResponse)
                .toList();
    }

    @Override
    public List<EquipmentResponse> getEquipmentsByBranch(Long branchId, EquipmentCategory category) {
        var equipments = (category != null)
                ? equipmentRepository.findEquipmentsByBranchIdAndCategory(branchId, category)
                : equipmentRepository.findEquipmentsByBranchId(branchId);

        return equipments.stream()
                .map(branchFacilityMapper::toEquipmentResponse)
                .toList();
    }

    @Override
    @Transactional
    public BranchResponse createBranch(CreateBranchRequest request) {
        Branch branch = branchFacilityMapper.toBranch(request);
        Branch saved = branchRepository.save(branch);
        return branchFacilityMapper.toBranchResponse(saved);
    }

    @Override
    @Transactional
    public RoomResponse createRoom(CreateRoomRequest request) {
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy cơ sở với id: " + request.getBranchId()));
        Room room = branchFacilityMapper.toRoom(request, branch);
        Room saved = roomRepository.save(room);
        return branchFacilityMapper.toRoomResponse(saved);
    }

    @Override
    @Transactional
    public EquipmentResponse createEquipment(CreateEquipmentRequest request) {
        Room room = roomRepository.findByIdForScheduleUpdate(request.getRoomId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phòng học với id: " + request.getRoomId()));
        Equipment equipment = branchFacilityMapper.toEquipment(request, room);
        Equipment saved = equipmentRepository.save(equipment);
        return branchFacilityMapper.toEquipmentResponse(saved);
    }

    @Override
    @Transactional
    public RoomResponse updateRoom(Long roomId, UpdateRoomRequest request) {
        Room room = roomRepository.findByIdForScheduleUpdate(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phòng học"));
        List<ClassEntity> classes = classesUsingRoom(roomId);
        for (ClassEntity item : classes) {
            if (request.capacity() < item.getMaxStudents()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Sức chứa không được nhỏ hơn sĩ số tối đa của lớp "
                        + item.getClassCode() + " (" + item.getMaxStudents() + " học viên)");
            }
            if (request.roomType() != room.getRoomType()
                    && !facilityRules.supportsSubject(request.roomType(), facilityRules.courseSubject(item.getCourse()))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể đổi loại phòng: lớp "
                        + item.getClassCode() + " vẫn cần phòng phù hợp với môn học. Hãy đổi phòng cho lớp trước.");
            }
        }
        if (request.status() == RoomStatus.MAINTENANCE) {
            List<Lesson> lessons = remainingLessons(roomId);
            if (!lessons.isEmpty()) {
                Lesson first = lessons.get(0);
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Phòng còn " + lessons.size()
                        + " buổi đang diễn ra hoặc sắp tới; gần nhất là lớp " + first.getClassEntity().getClassCode()
                        + " ngày " + first.getLessonDate() + " (" + first.getStartTime() + " - " + first.getEndTime()
                        + "). Hãy chuyển phòng cho các buổi này trước khi bảo trì.");
            }
        }
        room.setRoomName(request.roomName().trim());
        room.setCapacity(request.capacity());
        room.setRoomType(request.roomType());
        room.setStatus(request.status());
        room.setDescription(request.description());
        return roomResponseWithWarnings(roomRepository.save(room));
    }

    @Override
    @Transactional
    public EquipmentResponse updateEquipment(Long equipmentId, UpdateEquipmentRequest request) {
        Long roomId = equipmentRepository.findRoomIdById(equipmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thiết bị"));
        Room room = roomRepository.findByIdForScheduleUpdate(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phòng của thiết bị"));
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thiết bị"));
        equipment.setName(request.name().trim());
        equipment.setCategory(request.category());
        equipment.setStatus(request.status());
        equipment.setSerialNumber(request.serialNumber());
        equipment.setDescription(request.description());
        EquipmentResponse response = branchFacilityMapper.toEquipmentResponse(equipmentRepository.saveAndFlush(equipment));
        List<Equipment> equipments = equipmentRepository.findByRoomIdOrderByIdAsc(roomId);
        Set<Long> affectedClassIds = new HashSet<>();
        List<String> warnings = new ArrayList<>();
        for (ClassEntity item : classesUsingRoom(roomId)) {
            List<String> problems = facilityRules.equipmentProblems(facilityRules.courseSubject(item.getCourse()),
                    item.getMaxStudents(), equipments);
            if (!problems.isEmpty()) {
                affectedClassIds.add(item.getId());
                warnings.add("Lớp " + item.getClassCode() + ": " + String.join("; ", problems));
            }
        }
        warnings.addAll(facilityRules.roomWarnings(room, equipments));
        int affectedLessons = 0;
        for (Lesson lesson : remainingLessons(roomId)) {
            if (affectedClassIds.contains(lesson.getClassEntity().getId())) affectedLessons++;
        }
        if (affectedLessons > 0) warnings.add("Có " + affectedLessons
                + " buổi học đang diễn ra hoặc sắp tới cần xử lý điều kiện thiết bị; lịch chưa bị thay đổi.");
        response.setWarnings(warnings);
        response.setAffectedLessonCount(affectedLessons);
        return response;
    }

    private List<ClassEntity> classesUsingRoom(Long roomId) {
        LocalDateTime now = LocalDateTime.now(CENTER_ZONE);
        return classRepository.findClassesUsingRoom(roomId, now.toLocalDate(), now.toLocalTime());
    }

    private List<Lesson> remainingLessons(Long roomId) {
        LocalDateTime now = LocalDateTime.now(CENTER_ZONE);
        return lessonRepository.findRemainingRoomLessons(roomId, now.toLocalDate(), now.toLocalTime());
    }

    private RoomResponse roomResponseWithWarnings(Room room) {
        List<Equipment> equipments = equipmentRepository.findByRoomIdOrderByIdAsc(room.getId());
        return roomResponse(room, equipments, roomWarnings(room, equipments, classesUsingRoom(room.getId())));
    }

    private List<String> roomWarnings(Room room, List<Equipment> equipments, List<ClassEntity> classes) {
        List<String> warnings = new ArrayList<>(facilityRules.roomWarnings(room, equipments));
        for (ClassEntity item : classes) {
            List<String> problems = facilityRules.assignmentProblems(item.getCourse(), room, item.getMaxStudents(), equipments);
            if (!problems.isEmpty()) warnings.add("Lớp " + item.getClassCode() + ": " + String.join("; ", problems));
        }
        return warnings;
    }

    private RoomResponse roomResponse(Room room, List<Equipment> equipments, List<String> warnings) {
        RoomResponse response = branchFacilityMapper.toRoomResponse(room);
        response.setWarnings(warnings);
        response.setTotalEquipmentCount(equipments.size());
        int good = 0;
        for (Equipment equipment : equipments) {
            if (equipment.getStatus() == EquipmentStatus.GOOD) good++;
        }
        response.setGoodEquipmentCount(good);
        return response;
    }
}
