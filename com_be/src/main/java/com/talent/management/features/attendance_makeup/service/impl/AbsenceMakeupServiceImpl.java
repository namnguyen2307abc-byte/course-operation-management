package com.talent.management.features.attendance_makeup.service.impl;

import com.talent.management.features.attendance_makeup.dto.request.AbsenceRequestCreateRequest;
import com.talent.management.features.attendance_makeup.dto.request.AbsenceReviewRequest;
import com.talent.management.features.attendance_makeup.dto.request.AttendanceMarkRequest;
import com.talent.management.features.attendance_makeup.dto.request.MakeupScheduleRequest;
import com.talent.management.features.attendance_makeup.dto.response.AbsenceRequestResponse;
import com.talent.management.features.attendance_makeup.dto.response.LessonOptionResponse;
import com.talent.management.features.attendance_makeup.dto.response.MakeupRegistrationResponse;
import com.talent.management.features.attendance_makeup.dto.response.StudentOptionResponse;
import com.talent.management.features.attendance_makeup.exception.AttendanceMakeupException;
import com.talent.management.features.attendance_makeup.mapper.AttendanceMakeupMapper;
import com.talent.management.features.attendance_makeup.repository.AbsenceRequestRepository;
import com.talent.management.features.attendance_makeup.repository.AttendanceLessonRepository;
import com.talent.management.features.attendance_makeup.repository.AttendanceRecordRepository;
import com.talent.management.features.attendance_makeup.repository.MakeupRegistrationRepository;
import com.talent.management.features.attendance_makeup.service.AbsenceMakeupService;
import com.talent.management.features.auth.repository.StudentRepository;
import com.talent.management.shared.entity.*;
import com.talent.management.shared.enums.AbsenceStatus;
import com.talent.management.shared.enums.AttendanceStatus;
import com.talent.management.shared.enums.MakeupStatus;
import com.talent.management.shared.enums.Role;
import com.talent.management.shared.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Hiện thực nghiệp vụ Phân hệ Nghỉ học & Học bù (Attendance & Makeup Module)
 * Thiết kế chặt chẽ, sạch sẽ, phân quyền chuẩn và mang tính nhân văn trong xử lý học vụ.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AbsenceMakeupServiceImpl implements AbsenceMakeupService {

    private final AbsenceRequestRepository absenceRequestRepository;
    private final MakeupRegistrationRepository makeupRegistrationRepository;
    private final AttendanceLessonRepository attendanceLessonRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final StudentRepository studentRepository;
    private final AttendanceMakeupMapper attendanceMakeupMapper;
    private final CurrentUserService currentUserService;

    // =========================================================================
    // LUỒNG 1: TẠO VÀ QUẢN LÝ YÊU CẦU NGHỈ HỌC
    // =========================================================================

    /**
     * Chức năng 1: Tạo yêu cầu nghỉ học
     * Phụ huynh (hoặc Giáo viên tạo hộ) gửi đơn xin nghỉ kèm lý do.
     */
    @Override
    @Transactional
    public AbsenceRequestResponse createAbsenceRequest(AbsenceRequestCreateRequest request) {
        User currentUser = currentUserService.getCurrentUser();

        // 1. Kiểm tra học viên tồn tại
        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy thông tin học viên với ID: " + request.getStudentId()));

        // 2. Nếu là PHỤ HUYNH, xác thực học viên phải là con của phụ huynh đang đăng nhập
        if (currentUser.getRole() == Role.PARENT) {
            if (student.getParent() == null || !student.getParent().getId().equals(currentUser.getId())) {
                throw new AttendanceMakeupException("Bạn chỉ có thể gửi đơn xin nghỉ học cho con của mình!");
            }
        }

        // 3. Kiểm tra buổi học tồn tại
        Lesson lesson = attendanceLessonRepository.findById(request.getLessonId())
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy buổi học với ID: " + request.getLessonId()));

        // 4. Chống trùng lặp đơn: Nếu đã có đơn xin nghỉ đang PENDING hoặc APPROVED cho buổi này
        Optional<AbsenceRequest> existing = absenceRequestRepository.findByStudentIdAndLessonId(student.getId(), lesson.getId());
        if (existing.isPresent()) {
            AbsenceStatus existingStatus = existing.get().getStatus();
            if (existingStatus == AbsenceStatus.PENDING || existingStatus == AbsenceStatus.APPROVED || existingStatus == AbsenceStatus.SPECIAL_APPROVED) {
                throw new AttendanceMakeupException("Buổi học này đã có đơn xin nghỉ đang chờ duyệt hoặc đã được phê duyệt!");
            }
        }

        // 5. Khởi tạo đơn xin nghỉ ở trạng thái PENDING
        AbsenceRequest absenceRequest = AbsenceRequest.builder()
                .student(student)
                .lesson(lesson)
                .requestedByUser(currentUser)
                .reason(request.getReason().trim())
                .status(AbsenceStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        AbsenceRequest saved = absenceRequestRepository.save(absenceRequest);
        log.info("Đã tạo đơn xin nghỉ học mới #{} cho học viên [{}] bởi người dùng [{}]", 
                saved.getId(), student.getFullName(), currentUser.getUsername());

        return attendanceMakeupMapper.toAbsenceResponse(saved, null);
    }

    /**
     * Chức năng 2: Danh sách yêu cầu nghỉ học
     * Tự động lọc theo Role: Phụ huynh chỉ xem con mình, Giáo viên xem lớp mình, Admin xem tất cả.
     */
    @Override
    public List<AbsenceRequestResponse> getAbsenceRequests(AbsenceStatus status, Long studentId, Long classId) {
        User currentUser = currentUserService.getCurrentUser();
        List<AbsenceRequest> list;

        if (currentUser.getRole() == Role.PARENT) {
            // Phụ huynh xem đơn của con mình
            if (status != null) {
                list = absenceRequestRepository.findByStudentParentIdAndStatusOrderByCreatedAtDesc(currentUser.getId(), status);
            } else {
                list = absenceRequestRepository.findByStudentParentIdOrderByCreatedAtDesc(currentUser.getId());
            }
        } else if (currentUser.getRole() == Role.TEACHER) {
            // Giáo viên xem đơn thuộc các buổi học do mình phụ trách
            if (status != null) {
                list = absenceRequestRepository.findByLessonTeacherIdAndStatusOrderByCreatedAtDesc(currentUser.getId(), status);
            } else {
                list = absenceRequestRepository.findByLessonTeacherIdOrderByCreatedAtDesc(currentUser.getId());
            }
            // Nếu chưa có đơn lớp mình phụ trách, hỗ trợ xem thêm các đơn theo bộ lọc để giáo viên linh hoạt hỗ trợ đồng nghiệp
            if (list.isEmpty()) {
                list = (status != null)
                        ? absenceRequestRepository.findByStatusOrderByCreatedAtDesc(status)
                        : absenceRequestRepository.findAllByOrderByCreatedAtDesc();
            }
        } else {
            // Admin, Branch Manager, Staff: Xem toàn bộ hệ thống
            if (status != null) {
                list = absenceRequestRepository.findByStatusOrderByCreatedAtDesc(status);
            } else {
                list = absenceRequestRepository.findAllByOrderByCreatedAtDesc();
            }
        }

        // Áp dụng bộ lọc bổ sung theo học viên hoặc lớp nếu có
        return list.stream()
                .filter(ar -> studentId == null || (ar.getStudent() != null && ar.getStudent().getId().equals(studentId)))
                .filter(ar -> classId == null || (ar.getLesson() != null && ar.getLesson().getClassEntity() != null && ar.getLesson().getClassEntity().getId().equals(classId)))
                .map(ar -> {
                    MakeupRegistration mr = makeupRegistrationRepository.findByAbsenceRequestId(ar.getId()).orElse(null);
                    return attendanceMakeupMapper.toAbsenceResponse(ar, mr);
                })
                .toList();
    }

    /**
     * Chức năng 9: Xem chi tiết yêu cầu nghỉ học
     */
    @Override
    public AbsenceRequestResponse getAbsenceRequestById(Long id) {
        User currentUser = currentUserService.getCurrentUser();
        AbsenceRequest ar = absenceRequestRepository.findById(id)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy đơn xin nghỉ với ID: " + id));

        // Ràng buộc phụ huynh chỉ xem đơn của con mình
        if (currentUser.getRole() == Role.PARENT) {
            if (ar.getStudent().getParent() == null || !ar.getStudent().getParent().getId().equals(currentUser.getId())) {
                throw new AttendanceMakeupException("Bạn không có quyền truy cập vào đơn xin nghỉ này!");
            }
        }

        MakeupRegistration mr = makeupRegistrationRepository.findByAbsenceRequestId(ar.getId()).orElse(null);
        return attendanceMakeupMapper.toAbsenceResponse(ar, mr);
    }

    // =========================================================================
    // LUỒNG 2: GIÁO VIÊN XỬ LÝ YÊU CẦU NGHỈ HỌC
    // =========================================================================

    /**
     * Chức năng 3 & 6: Duyệt đơn xin nghỉ học thông thường (Approve)
     * Đổi trạng thái sang APPROVED + Tự động sinh bản ghi học bù (PENDING)
     */
    @Override
    @Transactional
    public AbsenceRequestResponse approveAbsenceRequest(Long id, AbsenceReviewRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        validateReviewPermission(currentUser);

        AbsenceRequest ar = absenceRequestRepository.findById(id)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy đơn xin nghỉ với ID: " + id));

        if (ar.getStatus() != AbsenceStatus.PENDING) {
            throw new AttendanceMakeupException("Đơn xin nghỉ này đã được xử lý trước đó (Trạng thái hiện tại: " + ar.getStatus() + ")");
        }

        // Cập nhật trạng thái duyệt
        ar.setStatus(AbsenceStatus.APPROVED);
        ar.setApprovedByTeacher(currentUser);
        if (request != null && request.getReviewNote() != null && !request.getReviewNote().isBlank()) {
            ar.setReviewNote(request.getReviewNote().trim());
        } else {
            ar.setReviewNote("Giáo viên đã phê duyệt đơn xin nghỉ học hợp lệ.");
        }
        AbsenceRequest updatedAr = absenceRequestRepository.save(ar);

        // Chức năng 6: Tự động tạo yêu cầu học bù ở trạng thái PENDING (Chờ xếp lịch)
        MakeupRegistration mr = makeupRegistrationRepository.findByAbsenceRequestId(updatedAr.getId())
                .orElseGet(() -> MakeupRegistration.builder()
                        .absenceRequest(updatedAr)
                        .student(updatedAr.getStudent())
                        .originalLesson(updatedAr.getLesson())
                        .targetLesson(null)
                        .status(MakeupStatus.PENDING)
                        .note("Tự động tạo ca học bù sau khi giáo viên phê duyệt đơn nghỉ học")
                        .createdAt(LocalDateTime.now())
                        .build());
        mr = makeupRegistrationRepository.save(mr);

        log.info("Giáo viên [{}] đã phê duyệt đơn nghỉ #{} của học viên [{}] -> Tạo ca bù #{}", 
                currentUser.getUsername(), updatedAr.getId(), updatedAr.getStudent().getFullName(), mr.getId());

        return attendanceMakeupMapper.toAbsenceResponse(updatedAr, mr);
    }

    /**
     * Chức năng 4 & 6: Duyệt đặc biệt (Special Approve)
     * Dành cho các trường hợp nghỉ đột xuất, ốm đau có hoàn cảnh đặc biệt mang tính nhân văn.
     * Bắt buộc phải có ghi chú xét duyệt (reviewNote).
     */
    @Override
    @Transactional
    public AbsenceRequestResponse specialApproveAbsenceRequest(Long id, AbsenceReviewRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        validateReviewPermission(currentUser);

        // Validate bắt buộc ghi chú xét duyệt
        if (request == null || request.getReviewNote() == null || request.getReviewNote().trim().isEmpty()) {
            throw new AttendanceMakeupException("Duyệt đặc biệt mang tính nhân văn và cần căn cứ cụ thể, vui lòng nhập ghi chú xét duyệt (reviewNote)!");
        }

        AbsenceRequest ar = absenceRequestRepository.findById(id)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy đơn xin nghỉ với ID: " + id));

        if (ar.getStatus() != AbsenceStatus.PENDING) {
            throw new AttendanceMakeupException("Đơn xin nghỉ này đã được xử lý trước đó (Trạng thái hiện tại: " + ar.getStatus() + ")");
        }

        ar.setStatus(AbsenceStatus.SPECIAL_APPROVED);
        ar.setApprovedByTeacher(currentUser);
        ar.setReviewNote(request.getReviewNote().trim());
        AbsenceRequest updatedAr = absenceRequestRepository.save(ar);

        // Chức năng 6: Tự động tạo ca học bù ở trạng thái PENDING
        MakeupRegistration mr = makeupRegistrationRepository.findByAbsenceRequestId(updatedAr.getId())
                .orElseGet(() -> MakeupRegistration.builder()
                        .absenceRequest(updatedAr)
                        .student(updatedAr.getStudent())
                        .originalLesson(updatedAr.getLesson())
                        .targetLesson(null)
                        .status(MakeupStatus.PENDING)
                        .note("Ca học bù diện duyệt đặc biệt: " + updatedAr.getReviewNote())
                        .createdAt(LocalDateTime.now())
                        .build());
        mr = makeupRegistrationRepository.save(mr);

        log.info("Giáo viên [{}] duyệt đặc biệt đơn nghỉ #{} của học viên [{}] với ghi chú: {}", 
                currentUser.getUsername(), updatedAr.getId(), updatedAr.getStudent().getFullName(), updatedAr.getReviewNote());

        return attendanceMakeupMapper.toAbsenceResponse(updatedAr, mr);
    }

    /**
     * Chức năng 5: Từ chối đơn xin nghỉ (Reject)
     * Bắt buộc nhập lý do từ chối để thông báo lịch sự, rõ ràng tới phụ huynh.
     */
    @Override
    @Transactional
    public AbsenceRequestResponse rejectAbsenceRequest(Long id, AbsenceReviewRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        validateReviewPermission(currentUser);

        // Validate bắt buộc lý do từ chối
        if (request == null || request.getReviewNote() == null || request.getReviewNote().trim().isEmpty()) {
            throw new AttendanceMakeupException("Từ chối đơn xin nghỉ cần giải thích rõ ràng và lịch sự với phụ huynh, vui lòng nhập lý do từ chối (reviewNote)!");
        }

        AbsenceRequest ar = absenceRequestRepository.findById(id)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy đơn xin nghỉ với ID: " + id));

        if (ar.getStatus() != AbsenceStatus.PENDING) {
            throw new AttendanceMakeupException("Đơn xin nghỉ này đã được xử lý trước đó (Trạng thái hiện tại: " + ar.getStatus() + ")");
        }

        ar.setStatus(AbsenceStatus.REJECTED);
        ar.setApprovedByTeacher(currentUser);
        ar.setReviewNote(request.getReviewNote().trim());
        AbsenceRequest updatedAr = absenceRequestRepository.save(ar);

        log.info("Giáo viên [{}] đã từ chối đơn nghỉ #{} của học viên [{}] - Lý do: {}", 
                currentUser.getUsername(), updatedAr.getId(), updatedAr.getStudent().getFullName(), updatedAr.getReviewNote());

        return attendanceMakeupMapper.toAbsenceResponse(updatedAr, null);
    }

    // =========================================================================
    // LUỒNG 3: XẾP LỊCH HỌC BÙ
    // =========================================================================

    /**
     * Danh sách yêu cầu học bù theo bộ lọc trạng thái và học viên
     */
    @Override
    public List<MakeupRegistrationResponse> getMakeupRegistrations(MakeupStatus status, Long studentId) {
        User currentUser = currentUserService.getCurrentUser();
        List<MakeupRegistration> list;

        if (currentUser.getRole() == Role.PARENT) {
            // Phụ huynh xem lịch bù của các con
            if (status != null) {
                list = makeupRegistrationRepository.findByStudentParentIdAndStatusOrderByCreatedAtDesc(currentUser.getId(), status);
            } else {
                list = makeupRegistrationRepository.findByStudentParentIdOrderByCreatedAtDesc(currentUser.getId());
            }
        } else {
            // Giáo viên và Ban Quản Lý
            if (status != null) {
                list = makeupRegistrationRepository.findByStatusOrderByCreatedAtDesc(status);
            } else {
                list = makeupRegistrationRepository.findAllByOrderByCreatedAtDesc();
            }
        }

        return list.stream()
                .filter(mr -> studentId == null || (mr.getStudent() != null && mr.getStudent().getId().equals(studentId)))
                .map(attendanceMakeupMapper::toMakeupResponse)
                .toList();
    }

    /**
     * Chức năng 9: Xem chi tiết yêu cầu học bù
     */
    @Override
    public MakeupRegistrationResponse getMakeupRegistrationById(Long id) {
        User currentUser = currentUserService.getCurrentUser();
        MakeupRegistration mr = makeupRegistrationRepository.findById(id)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy thông tin ca học bù với ID: " + id));

        if (currentUser.getRole() == Role.PARENT) {
            if (mr.getStudent().getParent() == null || !mr.getStudent().getParent().getId().equals(currentUser.getId())) {
                throw new AttendanceMakeupException("Bạn không có quyền truy cập vào thông tin ca học bù này!");
            }
        }

        return attendanceMakeupMapper.toMakeupResponse(mr);
    }

    /**
     * Chức năng 7: Xếp lịch học bù
     * Giáo viên/Admin chọn buổi học bù mục tiêu (targetLessonId) và chuyển trạng thái sang SCHEDULED.
     */
    @Override
    @Transactional
    public MakeupRegistrationResponse scheduleMakeup(Long id, MakeupScheduleRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        validateReviewPermission(currentUser);

        MakeupRegistration mr = makeupRegistrationRepository.findById(id)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy ca học bù với ID: " + id));

        if (mr.getStatus() == MakeupStatus.COMPLETED) {
            throw new AttendanceMakeupException("Ca học bù này đã hoàn thành, không thể xếp lại lịch!");
        }
        if (mr.getStatus() == MakeupStatus.CANCELLED) {
            throw new AttendanceMakeupException("Ca học bù này đã bị hủy!");
        }

        // Buổi học bù được chọn
        Lesson targetLesson = attendanceLessonRepository.findById(request.getTargetLessonId())
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy ca học bù mục tiêu với ID: " + request.getTargetLessonId()));

        // Chặn xếp lịch trùng với chính buổi đã xin nghỉ
        if (mr.getOriginalLesson() != null && mr.getOriginalLesson().getId().equals(targetLesson.getId())) {
            throw new AttendanceMakeupException("Buổi học bù không được trùng với chính buổi học đã xin nghỉ!");
        }

        mr.setTargetLesson(targetLesson);
        mr.setStatus(MakeupStatus.SCHEDULED);
        if (request.getNote() != null && !request.getNote().isBlank()) {
            mr.setNote(request.getNote().trim());
        }
        MakeupRegistration saved = makeupRegistrationRepository.save(mr);

        log.info("Đã xếp lịch học bù #{} cho học viên [{}] vào buổi học [{}] ngày {}", 
                saved.getId(), saved.getStudent().getFullName(), targetLesson.getTitle(), targetLesson.getLessonDate());

        return attendanceMakeupMapper.toMakeupResponse(saved);
    }

    // =========================================================================
    // LUỒNG 4: HOÀN THÀNH HỌC BÙ & ĐIỂM DANH
    // =========================================================================

    /**
     * Chức năng 8: Hoàn thành học bù trực tiếp qua ID ca học bù
     * Cập nhật trạng thái sang COMPLETED và ghi nhận bản ghi điểm danh PRESENT.
     */
    @Override
    @Transactional
    public MakeupRegistrationResponse completeMakeup(Long id) {
        User currentUser = currentUserService.getCurrentUser();
        validateReviewPermission(currentUser);

        MakeupRegistration mr = makeupRegistrationRepository.findById(id)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy ca học bù với ID: " + id));

        if (mr.getStatus() != MakeupStatus.SCHEDULED && mr.getStatus() != MakeupStatus.REGISTERED) {
            throw new AttendanceMakeupException("Chỉ có thể hoàn thành ca học bù đã được xếp lịch cụ thể (Hiện tại: " + mr.getStatus() + ")");
        }

        if (mr.getTargetLesson() == null) {
            throw new AttendanceMakeupException("Ca học bù này chưa có thông tin buổi học bù cụ thể!");
        }

        mr.setStatus(MakeupStatus.COMPLETED);
        MakeupRegistration saved = makeupRegistrationRepository.save(mr);

        // Lưu vết điểm danh PRESENT vào bảng attendances
        Attendance attendance = attendanceRecordRepository.findByLessonIdAndStudentId(mr.getTargetLesson().getId(), mr.getStudent().getId())
                .orElse(Attendance.builder()
                        .lesson(mr.getTargetLesson())
                        .student(mr.getStudent())
                        .build());

        attendance.setStatus(AttendanceStatus.PRESENT);
        attendance.setMarkedAt(LocalDateTime.now());
        attendance.setNote("Điểm danh học viên tham gia học bù hoàn thành (Ca bù #" + mr.getId() + ")");
        attendanceRecordRepository.save(attendance);

        log.info("Ca học bù #{} của học viên [{}] đã hoàn thành thành công!", saved.getId(), saved.getStudent().getFullName());

        return attendanceMakeupMapper.toMakeupResponse(saved);
    }

    /**
     * Điểm danh buổi học và tự động cập nhật trạng thái học bù liên quan nếu học viên có mặt
     */
    @Override
    @Transactional
    public void markAttendance(AttendanceMarkRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        validateReviewPermission(currentUser);

        Lesson lesson = attendanceLessonRepository.findById(request.getLessonId())
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy buổi học với ID: " + request.getLessonId()));

        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy học viên với ID: " + request.getStudentId()));

        AttendanceStatus status = AttendanceStatus.PRESENT;
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            try {
                status = AttendanceStatus.valueOf(request.getStatus().toUpperCase());
            } catch (IllegalArgumentException e) {
                status = AttendanceStatus.PRESENT;
            }
        }

        // Lưu vết điểm danh
        Attendance attendance = attendanceRecordRepository.findByLessonIdAndStudentId(lesson.getId(), student.getId())
                .orElse(Attendance.builder()
                        .lesson(lesson)
                        .student(student)
                        .build());

        attendance.setStatus(status);
        attendance.setMarkedAt(LocalDateTime.now());
        if (request.getNote() != null && !request.getNote().isBlank()) {
            attendance.setNote(request.getNote().trim());
        }
        attendanceRecordRepository.save(attendance);

        // Nếu điểm danh CÓ MẶT và đây là buổi học bù của học viên -> cập nhật ca bù sang COMPLETED
        if (status == AttendanceStatus.PRESENT) {
            Optional<MakeupRegistration> mrOpt = makeupRegistrationRepository
                    .findFirstByTargetLessonIdAndStudentIdAndStatus(lesson.getId(), student.getId(), MakeupStatus.SCHEDULED);

            if (mrOpt.isPresent()) {
                MakeupRegistration mr = mrOpt.get();
                mr.setStatus(MakeupStatus.COMPLETED);
                if (request.getNote() != null && !request.getNote().isBlank()) {
                    mr.setNote(request.getNote().trim());
                }
                makeupRegistrationRepository.save(mr);
                log.info("Tự động chuyển ca học bù #{} sang COMPLETED khi điểm danh có mặt", mr.getId());
            }
        }
    }

    // =========================================================================
    // HELPER APIs HỖ TRỢ GIAO DIỆN
    // =========================================================================

    /**
     * Lấy danh sách học viên có thể chọn xin nghỉ
     * Phụ huynh: Chỉ hiển thị các con của mình
     * Giáo viên / Quản trị viên: Hiển thị học viên kèm tên lớp đang theo học
     */
    @Override
    public List<StudentOptionResponse> getSelectableStudents() {
        User currentUser = currentUserService.getCurrentUser();
        List<Student> students;

        if (currentUser.getRole() == Role.PARENT) {
            students = studentRepository.findByParentId(currentUser.getId());
        } else {
            students = studentRepository.findAll();
        }

        return students.stream().map(s -> {
            List<String> classNames = attendanceLessonRepository.findClassNamesByStudentId(s.getId());
            String currentClass = classNames.isEmpty() ? "Chưa xếp lớp" : String.join(", ", classNames);
            return attendanceMakeupMapper.toStudentOption(s, currentClass);
        }).toList();
    }

    /**
     * Lấy danh sách các buổi học của học viên để người dùng chọn buổi xin nghỉ
     */
    @Override
    public List<LessonOptionResponse> getLessonsForAbsenceRequest(Long studentId) {
        User currentUser = currentUserService.getCurrentUser();
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy học viên với ID: " + studentId));

        if (currentUser.getRole() == Role.PARENT) {
            if (student.getParent() == null || !student.getParent().getId().equals(currentUser.getId())) {
                throw new AttendanceMakeupException("Bạn chỉ có thể xem lịch học của con mình!");
            }
        }

        List<Lesson> lessons = attendanceLessonRepository.findLessonsByStudentId(studentId);
        return lessons.stream()
                .map(attendanceMakeupMapper::toLessonOption)
                .toList();
    }

    /**
     * Lấy danh sách các ca học khả dụng để giáo viên chọn xếp lịch học bù
     * Ưu tiên các ca học cùng khóa học, diễn ra từ hôm nay trở đi.
     */
    @Override
    public List<LessonOptionResponse> getAvailableLessonsForMakeup(Long makeupId) {
        MakeupRegistration mr = makeupRegistrationRepository.findById(makeupId)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy thông tin ca học bù với ID: " + makeupId));

        Long courseId = null;
        if (mr.getOriginalLesson() != null 
                && mr.getOriginalLesson().getClassEntity() != null 
                && mr.getOriginalLesson().getClassEntity().getCourse() != null) {
            courseId = mr.getOriginalLesson().getClassEntity().getCourse().getId();
        }

        LocalDate today = LocalDate.now();
        List<Lesson> availableLessons;

        if (courseId != null) {
            availableLessons = attendanceLessonRepository.findUpcomingLessonsByCourseId(courseId, today);
        } else {
            availableLessons = List.of();
        }

        // Nếu chưa có buổi học cùng khóa phù hợp, lấy tất cả ca học sắp tới của trung tâm để giáo viên linh hoạt xếp
        if (availableLessons.isEmpty()) {
            availableLessons = attendanceLessonRepository.findAvailableUpcomingLessons(today);
        }

        // Loại trừ chính buổi học đã xin nghỉ
        Long origLessonId = mr.getOriginalLesson() != null ? mr.getOriginalLesson().getId() : null;

        return availableLessons.stream()
                .filter(l -> origLessonId == null || !origLessonId.equals(l.getId()))
                .map(attendanceMakeupMapper::toLessonOption)
                .toList();
    }

    // =========================================================================
    // HÀM TIỆN ÍCH KIỂM TRA PHÂN QUYỀN
    // =========================================================================

    private void validateReviewPermission(User user) {
        if (user.getRole() == Role.PARENT || user.getRole() == Role.STUDENT) {
            throw new AttendanceMakeupException("Tài khoản phụ huynh/học viên không có quyền thực hiện thao tác xét duyệt hoặc điều phối học bù!");
        }
    }
}
