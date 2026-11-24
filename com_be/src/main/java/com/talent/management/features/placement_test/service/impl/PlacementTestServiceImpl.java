package com.talent.management.features.placement_test.service.impl;

import com.talent.management.features.auth.repository.UserRepository;
import com.talent.management.features.course_management.repository.ProgramRepository;
import com.talent.management.shared.entity.EnrollmentRequest;
import com.talent.management.shared.enums.EnrollmentRequestStatus;
import com.talent.management.features.course_enrollment.repository.EnrollmentRequestRepository;
import com.talent.management.features.placement_test.dto.request.CreatePlacementScheduleRequest;
import com.talent.management.features.placement_test.dto.request.PlacementAssessmentRequest;
import com.talent.management.features.placement_test.dto.request.PlacementCriteriaScoreItemRequest;
import com.talent.management.features.placement_test.dto.request.SavePlacementCriteriaRequest;
import com.talent.management.features.placement_test.dto.response.PlacementCriteriaResponse;
import com.talent.management.features.placement_test.dto.response.PlacementScheduleCriteriaScoreResponse;
import com.talent.management.features.placement_test.dto.response.PlacementScheduleResponse;
import com.talent.management.features.placement_test.dto.response.PlacementRecommendationResponse;
import com.talent.management.features.placement_test.dto.response.PlacementProgramResponse;
import com.talent.management.features.placement_test.entity.PlacementCriteria;
import com.talent.management.features.placement_test.entity.PlacementSchedule;
import com.talent.management.features.placement_test.entity.PlacementScheduleCriteriaScore;
import com.talent.management.features.placement_test.entity.PlacementScheduleStatus;
import com.talent.management.features.placement_test.mapper.PlacementScheduleMapper;
import com.talent.management.features.placement_test.repository.PlacementCriteriaRepository;
import com.talent.management.features.placement_test.repository.PlacementScheduleCriteriaScoreRepository;
import com.talent.management.features.placement_test.repository.PlacementScheduleRepository;
import com.talent.management.features.placement_test.repository.PlacementTestRepository;
import com.talent.management.features.placement_test.service.PlacementTestService;
import com.talent.management.features.auth.repository.StudentRepository;
import com.talent.management.features.course_enrollment.repository.CourseRepository;
import com.talent.management.shared.entity.Course;
import com.talent.management.shared.entity.PlacementTest;
import com.talent.management.shared.entity.Program;
import com.talent.management.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlacementTestServiceImpl implements PlacementTestService {

    private static final Set<String> PLACEMENT_SUBJECTS = Set.of("DAN", "MUA", "VO");
    private final PlacementScheduleRepository scheduleRepository;
    private final PlacementTestRepository placementTestRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final PlacementScheduleMapper scheduleMapper;

    @Autowired(required = false)
    private com.talent.management.features.notification.service.NotificationService notificationService;
    private final EnrollmentRequestRepository enrollmentRequestRepository;
    private final CourseRepository courseRepository;
    private final ProgramRepository programRepository;

    @Autowired(required = false)
    private PlacementCriteriaRepository criteriaRepository;

    @Autowired(required = false)
    private PlacementScheduleCriteriaScoreRepository criteriaScoreRepository;

    @Override
    @Transactional
    public List<PlacementCriteriaResponse> getCriteria(String subject, boolean includeInactive) {
        if (criteriaRepository == null) {
            return List.of();
        }
        ensureDefaultCriteriaSeeded();
        backfillCompletedSchedulesWithoutSnapshots();

        List<PlacementCriteria> list;
        if (subject != null && !subject.isBlank()) {
            String norm = normalizeSubjectCode(subject);
            list = includeInactive
                    ? criteriaRepository.findBySubjectIgnoreCaseOrderByDisplayOrderAscIdAsc(norm)
                    : criteriaRepository.findBySubjectIgnoreCaseAndActiveTrueOrderByDisplayOrderAscIdAsc(norm);
        } else {
            list = includeInactive
                    ? criteriaRepository.findAllByOrderBySubjectAscDisplayOrderAscIdAsc()
                    : criteriaRepository.findByActiveTrueOrderBySubjectAscDisplayOrderAscIdAsc();
        }
        return list.stream()
                .map(this::toCriteriaResponse)
                .toList();
    }

    @Override
    @Transactional
    public PlacementCriteriaResponse createCriteria(SavePlacementCriteriaRequest request) {
        if (criteriaRepository == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Kho lưu trữ tiêu chí chưa sẵn sàng");
        }
        ensureDefaultCriteriaSeeded();
        backfillCompletedSchedulesWithoutSnapshots();

        String normSubject = normalizeAndValidateSubject(request.getSubject());
        String trimmedName = request.getCriteriaName() == null ? "" : request.getCriteriaName().trim();
        if (trimmedName.isEmpty()) {
            throw new BusinessException("Tên tiêu chí đánh giá không được để trống!");
        }

        int order = request.getDisplayOrder() != null && request.getDisplayOrder() >= 1
                ? request.getDisplayOrder()
                : (int) criteriaRepository.countBySubjectIgnoreCase(normSubject) + 1;

        PlacementCriteria entity = PlacementCriteria.builder()
                .subject(normSubject)
                .criteriaName(trimmedName)
                .displayOrder(order)
                .active(request.getActive() == null || request.getActive())
                .build();
        PlacementCriteria saved = criteriaRepository.save(entity);
        return toCriteriaResponse(saved);
    }

    @Override
    @Transactional
    public PlacementCriteriaResponse updateCriteria(Long id, SavePlacementCriteriaRequest request) {
        if (criteriaRepository == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Kho lưu trữ tiêu chí chưa sẵn sàng");
        }
        PlacementCriteria entity = criteriaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tiêu chí đánh giá với ID: " + id));

        String normSubject = normalizeAndValidateSubject(request.getSubject());
        String trimmedName = request.getCriteriaName() == null ? "" : request.getCriteriaName().trim();
        if (trimmedName.isEmpty()) {
            throw new BusinessException("Tên tiêu chí đánh giá không được để trống!");
        }

        entity.setSubject(normSubject);
        entity.setCriteriaName(trimmedName);
        if (request.getDisplayOrder() != null && request.getDisplayOrder() >= 1) {
            entity.setDisplayOrder(request.getDisplayOrder());
        }
        if (request.getActive() != null) {
            entity.setActive(request.getActive());
        }
        PlacementCriteria saved = criteriaRepository.save(entity);
        return toCriteriaResponse(saved);
    }

    @Override
    @Transactional
    public void deleteCriteria(Long id) {
        if (criteriaRepository == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Kho lưu trữ tiêu chí chưa sẵn sàng");
        }
        PlacementCriteria entity = criteriaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tiêu chí đánh giá với ID: " + id));

        boolean used = criteriaScoreRepository != null && criteriaScoreRepository.existsByCriteriaId(id);
        if (used) {
            // Soft delete: giữ nguyên bản ghi trong DB để bảo toàn lịch sử các bài đã chấm
            entity.setActive(false);
            criteriaRepository.save(entity);
        } else {
            criteriaRepository.delete(entity);
        }
    }

    private void ensureDefaultCriteriaSeeded() {
        if (criteriaRepository == null || criteriaRepository.count() > 0) {
            return;
        }
        List<PlacementCriteria> defaults = List.of(
                PlacementCriteria.builder().subject("DAN").criteriaName("1. Cảm âm & Nhạc cảm (Pitch)").displayOrder(1).active(true).build(),
                PlacementCriteria.builder().subject("DAN").criteriaName("2. Nhịp phách & Tiết tấu (Rhythm)").displayOrder(2).active(true).build(),
                PlacementCriteria.builder().subject("DAN").criteriaName("3. Kỹ thuật ngón & Tư thế tay (Technique)").displayOrder(3).active(true).build(),
                PlacementCriteria.builder().subject("DAN").criteriaName("4. Đọc bản nhạc & Thị tấu (Sight Reading)").displayOrder(4).active(true).build(),
                PlacementCriteria.builder().subject("MUA").criteriaName("1. Độ dẻo & Uyển chuyển (Flexibility)").displayOrder(1).active(true).build(),
                PlacementCriteria.builder().subject("MUA").criteriaName("2. Cảm thụ âm nhạc & Nhịp điệu (Musicality)").displayOrder(2).active(true).build(),
                PlacementCriteria.builder().subject("MUA").criteriaName("3. Phom dáng & Kỹ thuật động tác (Posture)").displayOrder(3).active(true).build(),
                PlacementCriteria.builder().subject("MUA").criteriaName("4. Thần thái & Biểu cảm biểu diễn (Stage Presence)").displayOrder(4).active(true).build(),
                PlacementCriteria.builder().subject("VO").criteriaName("1. Thể lực & Sức bền (Stamina & Strength)").displayOrder(1).active(true).build(),
                PlacementCriteria.builder().subject("VO").criteriaName("2. Tốc độ & Phản xạ tự vệ (Reflexes & Speed)").displayOrder(2).active(true).build(),
                PlacementCriteria.builder().subject("VO").criteriaName("3. Tấn pháp & Kỹ thuật đòn (Stances & Technique)").displayOrder(3).active(true).build(),
                PlacementCriteria.builder().subject("VO").criteriaName("4. Tác phong & Kỷ luật võ đạo (Martial Discipline)").displayOrder(4).active(true).build()
        );
        criteriaRepository.saveAll(defaults);
        backfillCompletedSchedulesWithoutSnapshots();
    }

    private void backfillCompletedSchedulesWithoutSnapshots() {
        if (criteriaRepository == null || criteriaScoreRepository == null || scheduleRepository == null) {
            return;
        }
        List<PlacementSchedule> allSchedules = scheduleRepository.findAllByOrderByTestDateDesc();
        if (allSchedules == null || allSchedules.isEmpty()) {
            return;
        }
        List<PlacementSchedule> completedSchedules = allSchedules.stream()
                .filter(s -> s != null && s.getId() != null && s.getStatus() == PlacementScheduleStatus.COMPLETED)
                .toList();
        if (completedSchedules.isEmpty()) {
            return;
        }
        List<Long> completedIds = completedSchedules.stream().map(PlacementSchedule::getId).toList();
        Set<Long> schedulesWithScores = criteriaScoreRepository
                .findByScheduleIdInOrderByScheduleIdAscDisplayOrderAscIdAsc(completedIds)
                .stream()
                .map(row -> row.getSchedule().getId())
                .collect(Collectors.toSet());

        List<PlacementSchedule> missing = completedSchedules.stream()
                .filter(s -> !schedulesWithScores.contains(s.getId()))
                .toList();
        if (missing.isEmpty()) {
            return;
        }

        List<PlacementCriteria> activeCriteria = criteriaRepository.findByActiveTrueOrderBySubjectAscDisplayOrderAscIdAsc();
        if (activeCriteria == null || activeCriteria.isEmpty()) {
            return;
        }
        Map<String, List<PlacementCriteria>> bySubject = activeCriteria.stream()
                .collect(Collectors.groupingBy(c -> normalizeSubjectCode(c.getSubject())));

        List<PlacementScheduleCriteriaScore> backfilled = new ArrayList<>();
        for (PlacementSchedule schedule : missing) {
            String subj = normalizeSubjectCode(schedule.getSubject() != null
                    ? schedule.getSubject()
                    : (scheduleMapper != null ? scheduleMapper.inferSubject(schedule.getTitle()) : "DAN"));
            List<PlacementCriteria> subjectCriteria = bySubject.getOrDefault(subj, List.of()).stream()
                    .limit(4)
                    .toList();
            double score10 = schedule.getScore() != null ? Math.round((schedule.getScore() / 10.0) * 10.0) / 10.0 : 0.0;
            for (PlacementCriteria c : subjectCriteria) {
                backfilled.add(PlacementScheduleCriteriaScore.builder()
                        .schedule(schedule)
                        .criteria(c)
                        .criteriaNameSnapshot(c.getCriteriaName())
                        .score(score10)
                        .displayOrder(c.getDisplayOrder() != null ? c.getDisplayOrder() : 1)
                        .build());
            }
        }
        if (!backfilled.isEmpty()) {
            criteriaScoreRepository.saveAll(backfilled);
        }
    }

    private String normalizeSubjectCode(String subject) {
        if (scheduleMapper != null) {
            String mapped = scheduleMapper.normalizeSubject(subject);
            if (mapped != null) {
                return mapped;
            }
        }
        if (subject == null || subject.isBlank()) {
            return "DAN";
        }
        return subject.trim().toUpperCase();
    }

    private String normalizeAndValidateSubject(String subject) {
        String norm = normalizeSubjectCode(subject);
        if (!PLACEMENT_SUBJECTS.contains(norm)) {
            throw new BusinessException("Bộ môn tiêu chí không hợp lệ (chỉ hỗ trợ DAN, MUA, VO)");
        }
        return norm;
    }

    private PlacementCriteriaResponse toCriteriaResponse(PlacementCriteria entity) {
        boolean used = criteriaScoreRepository != null && entity.getId() != null
                && criteriaScoreRepository.existsByCriteriaId(entity.getId());
        return PlacementCriteriaResponse.builder()
                .id(entity.getId())
                .subject(entity.getSubject())
                .criteriaName(entity.getCriteriaName())
                .displayOrder(entity.getDisplayOrder())
                .active(entity.getActive())
                .usedInAssessments(used)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlacementProgramResponse> getPlacementPrograms() {
        return programRepository.findByPlacementSubjectIsNotNullOrderByNameAsc().stream()
                .filter(this::isPlacementEnabled)
                .map(program -> new PlacementProgramResponse(program.getId(), program.getName(), program.getPlacementSubject()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Program requirePlacementProgram(Long programId) {
        if (programId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn môn muốn kiểm tra");
        }
        return programRepository.findById(programId).filter(this::isPlacementEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Môn đã chọn chưa hỗ trợ Placement Test"));
    }

    private boolean isPlacementEnabled(Program program) {
        return program.getPlacementSubject() != null && PLACEMENT_SUBJECTS.contains(program.getPlacementSubject());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlacementScheduleResponse> getAllSchedules() {
        return getAllSchedules(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlacementScheduleResponse> getAllSchedules(String currentUsername) {
        List<PlacementScheduleResponse> responses;
        if (currentUsername != null && !currentUsername.isBlank()) {
            var userOpt = userRepository.findByUsername(currentUsername);
            if (userOpt.isPresent() && userOpt.get().getRole() == com.talent.management.shared.enums.Role.TEACHER) {
                String teacherSubject = userOpt.get().getSubject();
                if (teacherSubject != null && !teacherSubject.isBlank()) {
                    responses = scheduleRepository.findBySubjectIgnoreCaseOrderByTestDateDesc(teacherSubject.trim())
                            .stream()
                            .map(scheduleMapper::toResponse)
                            .collect(Collectors.toList());
                    return enrichWithCriteriaScores(responses);
                }
            }
        }
        responses = scheduleRepository.findAllByOrderByTestDateDesc()
                .stream()
                .map(scheduleMapper::toResponse)
                .collect(Collectors.toList());
        return enrichWithCriteriaScores(responses);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlacementScheduleResponse> getSchedulesForParent(String parentEmail) {
        return getSchedulesForParent(parentEmail, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlacementScheduleResponse> getSchedulesForParent(String parentEmail, String currentUsername) {
        if (parentEmail == null || parentEmail.isBlank()) {
            return getAllSchedules(currentUsername);
        }

        String normalizedEmail = parentEmail.trim().toLowerCase();
        if (currentUsername != null && !currentUsername.isBlank()) {
            var userOpt = userRepository.findByUsername(currentUsername);
            if (userOpt.isPresent() && userOpt.get().getRole() == com.talent.management.shared.enums.Role.TEACHER) {
                String teacherSubject = userOpt.get().getSubject();
                if (teacherSubject != null && !teacherSubject.isBlank()) {
                    List<PlacementScheduleResponse> responses = scheduleRepository.findBySubjectIgnoreCaseAndParentEmailOrderByTestDateAsc(teacherSubject.trim(), normalizedEmail)
                            .stream()
                            .map(scheduleMapper::toResponse)
                            .collect(Collectors.toList());
                    return enrichWithCriteriaScores(responses);
                }
            }
        }

        List<PlacementScheduleResponse> responses = scheduleRepository.findByParentEmailOrderByTestDateAsc(normalizedEmail)
                .stream()
                .map(scheduleMapper::toResponse)
                .collect(Collectors.toList());
        return enrichWithCriteriaScores(responses);
    }

    @Override
    @Transactional(readOnly = true)
    public PlacementScheduleResponse getScheduleById(Long id) {
        PlacementSchedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Lịch thi xếp lớp không tồn tại với ID: " + id));
        PlacementScheduleResponse response = scheduleMapper.toResponse(schedule);
        if (response != null) {
            enrichWithCriteriaScores(List.of(response));
        }
        return response;
    }

    @Override
    @Transactional
    public PlacementScheduleResponse saveAssessment(Long scheduleId, PlacementAssessmentRequest request, String teacherUsername) {
        PlacementSchedule schedule = scheduleRepository.findByIdForUpdate(scheduleId)
                .orElseThrow(() -> new BusinessException("Lịch thi xếp lớp không tồn tại với ID: " + scheduleId));

        if (schedule.getStatus() == PlacementScheduleStatus.CANCELLED) {
            throw new BusinessException("Không thể chấm điểm cho đơn Placement Test đã bị hủy!");
        }

        EnrollmentRequest enrollmentRequest = schedule.getEnrollmentRequest();
        if (enrollmentRequest != null && request.getRecommendedCourseId() == null) {
            throw new BusinessException("Vui lòng chọn khóa học đề xuất cho yêu cầu ghi danh");
        }
        Course recommendedCourse = request.getRecommendedCourseId() == null ? null : courseRepository
                .findById(request.getRecommendedCourseId())
                .orElseThrow(() -> new BusinessException("Khóa học đề xuất không tồn tại"));
        if (schedule.getProgram() != null && recommendedCourse != null
                && !Objects.equals(schedule.getProgram().getId(), recommendedCourse.getProgram().getId())) {
            throw new BusinessException("Khóa học đề xuất phải thuộc môn của ca kiểm tra");
        }

        if (teacherUsername != null && !teacherUsername.isBlank()) {
            var teacherOpt = userRepository.findByUsername(teacherUsername);
            if (teacherOpt.isPresent()) {
                var teacher = teacherOpt.get();
                if (teacher.getRole() != com.talent.management.shared.enums.Role.TEACHER) {
                    throw new BusinessException("Chỉ tài khoản Giáo viên (TEACHER) mới có quyền chấm điểm đánh giá bài thi!");
                }
                String teacherSubject = teacher.getSubject();
                String scheduleSubject = schedule.getSubject() != null ? schedule.getSubject() : scheduleMapper.inferSubject(schedule.getTitle());
                if (teacherSubject != null && !teacherSubject.isBlank() && scheduleSubject != null && !scheduleSubject.isBlank()) {
                    String normTeacher = scheduleMapper.normalizeSubject(teacherSubject);
                    String normSchedule = scheduleMapper.normalizeSubject(scheduleSubject);
                    if (!normTeacher.equalsIgnoreCase(normSchedule)) {
                        throw new BusinessException("Bạn là giáo viên bộ môn " + teacherSubject + ", không có quyền chấm bài thi xếp lớp thuộc bộ môn " + scheduleSubject + "!");
                    }
                }
                schedule.setEvaluatedBy(teacher);
            }
        }

        Integer finalScore = request.getScore();
        List<PlacementCriteriaScoreItemRequest> scoreItems = request.getCriteriaScores();
        if (scoreItems != null && !scoreItems.isEmpty()) {
            double sum = 0.0;
            int validCount = 0;
            Set<Long> submittedCriteria = new java.util.HashSet<>();
            String assessmentSubject = normalizeSubjectCode(schedule.getSubject() != null
                    ? schedule.getSubject() : scheduleMapper.inferSubject(schedule.getTitle()));
            for (PlacementCriteriaScoreItemRequest item : scoreItems) {
                if (item == null || item.getScore() == null) {
                    throw new BusinessException("Vui lòng nhập đầy đủ điểm cho từng tiêu chí đánh giá!");
                }
                if (!Double.isFinite(item.getScore()) || item.getScore() < 0.0 || item.getScore() > 10.0) {
                    throw new BusinessException("Điểm từng tiêu chí phải nằm trong khoảng từ 0 đến 10!");
                }
                if (item.getCriteriaId() != null && criteriaRepository != null) {
                    if (!submittedCriteria.add(item.getCriteriaId())) {
                        throw new BusinessException("Không được gửi trùng tiêu chí đánh giá: " + item.getCriteriaId());
                    }
                    PlacementCriteria criterion = criteriaRepository.findById(item.getCriteriaId())
                            .orElseThrow(() -> new BusinessException("Tiêu chí đánh giá không tồn tại: " + item.getCriteriaId()));
                    if (assessmentSubject != null && !assessmentSubject.isBlank()
                            && !assessmentSubject.equalsIgnoreCase(normalizeSubjectCode(criterion.getSubject()))) {
                        throw new BusinessException("Tiêu chí " + criterion.getCriteriaName() + " không thuộc bộ môn của ca kiểm tra!");
                    }
                    if (Boolean.FALSE.equals(criterion.getActive())) {
                        boolean previouslyUsed = criteriaScoreRepository != null
                                && criteriaScoreRepository.findByScheduleIdOrderByDisplayOrderAscIdAsc(scheduleId).stream()
                                        .anyMatch(row -> row.getCriteria() != null
                                                && item.getCriteriaId().equals(row.getCriteria().getId()));
                        if (!previouslyUsed) {
                            throw new BusinessException("Tiêu chí " + criterion.getCriteriaName() + " đã ngưng áp dụng. Vui lòng tải lại danh sách tiêu chí!");
                        }
                    }
                }
                sum += item.getScore();
                validCount++;
            }
            if (validCount > 0) {
                finalScore = (int) Math.round((sum / validCount) * 10.0);
            }
        }

        schedule.setScore(finalScore);
        schedule.setRecommendedLevel(request.getRecommendedLevel());
        schedule.setRecommendedCourse(recommendedCourse);
        schedule.setTeacherNote(request.getTeacherNote());
        schedule.setAudioUrl(request.getAudioUrl());
        schedule.setVideoUrl(request.getVideoUrl());
        schedule.setImageUrl(request.getImageUrl());
        schedule.setRecordUrl(request.getRecordUrl());
        schedule.setEvaluatedAt(LocalDateTime.now());
        schedule.setStatus(PlacementScheduleStatus.COMPLETED);

        PlacementSchedule saved = scheduleRepository.save(schedule);
        saveScheduleCriteriaScores(saved, scoreItems);

        if (enrollmentRequest != null && enrollmentRequest.getStatus() == EnrollmentRequestStatus.WAITING_PLACEMENT) {
            enrollmentRequest.setPreferredCourse(recommendedCourse);
            enrollmentRequest.setStatus(EnrollmentRequestStatus.READY_FOR_ASSIGNMENT);
            enrollmentRequestRepository.save(enrollmentRequest);
        }
        if (notificationService != null && saved != null) {
            String recCourseName = recommendedCourse != null ? recommendedCourse.getName() : "Theo đề xuất giáo viên";
            if (saved.getParent() != null) {
                notificationService.sendNotification(
                        saved.getParent(), null,
                        "Đã có kết quả đánh giá đầu vào (Placement Test)",
                        "Bé " + saved.getStudentName() + " đã hoàn thành bài kiểm tra đầu vào (Điểm: " + saved.getScore() + "). Khóa học đề xuất: " + recCourseName + ".",
                        "PLACEMENT_COMPLETED", saved.getId(), "/pages/course-enrollment.html");
            }
            notificationService.sendToRoles(
                    List.of(com.talent.management.shared.enums.Role.STAFF, com.talent.management.shared.enums.Role.ADMIN), null,
                    "Đã có kết quả Placement Test — Chờ xếp lớp",
                    "Học viên " + saved.getStudentName() + " đạt " + saved.getScore() + " điểm (Đề xuất: " + recCourseName + "), sẵn sàng xếp lớp.",
                    "PLACEMENT_COMPLETED", saved.getId(), "/pages/course-enrollment.html");
        }
        PlacementScheduleResponse response = scheduleMapper.toResponse(saved);
        if (response != null) {
            enrichWithCriteriaScores(List.of(response));
        }
        return response;
    }

    private void saveScheduleCriteriaScores(PlacementSchedule savedSchedule, List<PlacementCriteriaScoreItemRequest> scoreItems) {
        if (criteriaScoreRepository == null || savedSchedule == null || savedSchedule.getId() == null
                || scoreItems == null || scoreItems.isEmpty()) {
            return;
        }
        criteriaScoreRepository.deleteByScheduleId(savedSchedule.getId());
        List<PlacementScheduleCriteriaScore> toSave = new ArrayList<>();
        int index = 1;
        for (PlacementCriteriaScoreItemRequest item : scoreItems) {
            if (item == null || item.getScore() == null) {
                continue;
            }
            PlacementCriteria foundCriteria = null;
            if (item.getCriteriaId() != null && criteriaRepository != null) {
                foundCriteria = criteriaRepository.findById(item.getCriteriaId()).orElse(null);
            }
            String snapshotName = foundCriteria != null
                    ? foundCriteria.getCriteriaName()
                    : (item.getCriteriaName() != null && !item.getCriteriaName().isBlank()
                            ? item.getCriteriaName().trim()
                            : "Tiêu chí " + index);
            int order = foundCriteria != null && foundCriteria.getDisplayOrder() != null
                    ? foundCriteria.getDisplayOrder()
                    : index;
            toSave.add(PlacementScheduleCriteriaScore.builder()
                    .schedule(savedSchedule)
                    .criteria(foundCriteria)
                    .criteriaNameSnapshot(snapshotName)
                    .score(item.getScore())
                    .displayOrder(order)
                    .build());
            index++;
        }
        if (!toSave.isEmpty()) {
            criteriaScoreRepository.saveAll(toSave);
        }
    }

    private List<PlacementScheduleResponse> enrichWithCriteriaScores(List<PlacementScheduleResponse> responses) {
        if (criteriaScoreRepository == null || responses == null || responses.isEmpty()) {
            return responses;
        }
        List<Long> ids = responses.stream()
                .filter(Objects::nonNull)
                .map(PlacementScheduleResponse::getId)
                .filter(Objects::nonNull)
                .toList();
        if (ids.isEmpty()) {
            return responses;
        }
        Map<Long, List<PlacementScheduleCriteriaScoreResponse>> byScheduleId = criteriaScoreRepository
                .findByScheduleIdInOrderByScheduleIdAscDisplayOrderAscIdAsc(ids)
                .stream()
                .collect(Collectors.groupingBy(
                        row -> row.getSchedule().getId(),
                        Collectors.mapping(
                                row -> PlacementScheduleCriteriaScoreResponse.builder()
                                        .criteriaId(row.getCriteria() != null ? row.getCriteria().getId() : null)
                                        .criteriaName(row.getCriteriaNameSnapshot())
                                        .score(row.getScore())
                                        .displayOrder(row.getDisplayOrder())
                                        .build(),
                                Collectors.toList()
                        )
                ));
        for (PlacementScheduleResponse resp : responses) {
            if (resp != null && resp.getId() != null) {
                resp.setCriteriaScores(byScheduleId.getOrDefault(resp.getId(), Collections.emptyList()));
            }
        }
        return responses;
    }

    @Override
    @Transactional
    public PlacementScheduleResponse createSchedule(CreatePlacementScheduleRequest request, String parentUsername) {
        if (request.getTestDate() == null) {
            throw new BusinessException("Thời gian hẹn test không được để trống!");
        }
        if (request.getTestDate().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Thời gian hẹn test không được ở trong quá khứ tính từ thời điểm hiện tại! Vui lòng chọn thời gian từ hiện tại trở đi.");
        }

        EnrollmentRequest enrollmentRequest = null;
        Program program;
        if (request.getEnrollmentRequestId() != null) {
            enrollmentRequest = enrollmentRequestRepository
                    .findByIdForUpdate(request.getEnrollmentRequestId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu đăng ký"));
            if (!enrollmentRequest.isPlacementRequested()
                    || enrollmentRequest.getStatus() != EnrollmentRequestStatus.WAITING_PLACEMENT) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Yêu cầu không còn chờ xếp lịch Placement Test");
            }
            if (scheduleRepository.existsByEnrollmentRequestId(enrollmentRequest.getId())
                    || scheduleRepository.existsByEnrollmentRequestIdAndStatusNot(enrollmentRequest.getId(), PlacementScheduleStatus.CANCELLED)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Yêu cầu đã có lịch Placement Test");
            }
            Program requestedProgram = enrollmentRequest.getRequestedProgram();
            if (requestedProgram != null && request.getProgramId() != null
                    && !requestedProgram.getId().equals(request.getProgramId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Môn kiểm tra phải trùng với môn ghi trên đơn");
            }
            program = requirePlacementProgram(requestedProgram == null ? request.getProgramId() : requestedProgram.getId());
            if (requestedProgram == null) {
                enrollmentRequest.setRequestedProgram(program);
            }
        } else {
            program = requirePlacementProgram(request.getProgramId());
        }

        PlacementSchedule schedule = scheduleMapper.toEntity(request);
        schedule.setProgram(program);
        schedule.setSubject(program.getPlacementSubject());
        if (enrollmentRequest != null) {
            schedule.setEnrollmentRequest(enrollmentRequest);
            schedule.setStudentName(enrollmentRequest.getStudent().getFullName());
            schedule.setParent(enrollmentRequest.getRequestedBy());
        } else if (parentUsername != null && !parentUsername.isBlank()) {
            userRepository.findByUsername(parentUsername).ifPresent(schedule::setParent);
        }
        PlacementSchedule saved = scheduleRepository.save(schedule);
        if (notificationService != null && saved != null) {
            if (saved.getParent() != null) {
                notificationService.sendNotification(
                        saved.getParent(), null,
                        "Đã xếp lịch kiểm tra đầu vào (Placement Test)",
                        "Bé " + saved.getStudentName() + " đã được hẹn lịch đánh giá năng khiếu đầu vào vào lúc " + saved.getTestDate() + ".",
                        "PLACEMENT_SCHEDULED", saved.getId(), "/pages/course-enrollment.html");
            }
            notificationService.sendToRoles(
                    List.of(com.talent.management.shared.enums.Role.TEACHER, com.talent.management.shared.enums.Role.STAFF), null,
                    "Lịch đánh giá đầu vào mới",
                    "Học viên " + saved.getStudentName() + " có lịch kiểm tra năng khiếu vào lúc " + saved.getTestDate() + ".",
                    "PLACEMENT_SCHEDULED", saved.getId(), "/pages/placement-test.html");
        }
        return scheduleMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public PlacementScheduleResponse cancelSchedule(Long scheduleId, String cancelReason, String currentUsername) {
        if (cancelReason == null || cancelReason.trim().isEmpty() || cancelReason.length() > 500) {
            throw new BusinessException("Vui lòng nhập lý do hủy đơn Placement Test!");
        }
        String trimmedReason = cancelReason.trim();

        PlacementSchedule schedule = scheduleRepository.findByIdForUpdate(scheduleId)
                .orElseThrow(() -> new BusinessException("Lịch thi xếp lớp không tồn tại với ID: " + scheduleId));

        if (schedule.getStatus() == PlacementScheduleStatus.CANCELLED) {
            throw new BusinessException("Đơn Placement Test này đã được hủy trước đó!");
        }
        if (schedule.getStatus() == PlacementScheduleStatus.COMPLETED) {
            throw new BusinessException("Không thể hủy đơn Placement Test đã hoàn thành chấm điểm!");
        }

        if (currentUsername != null && !currentUsername.isBlank()) {
            var userOpt = userRepository.findByUsername(currentUsername);
            if (userOpt.isPresent() && userOpt.get().getRole() == com.talent.management.shared.enums.Role.TEACHER) {
                var teacher = userOpt.get();
                String teacherSubject = teacher.getSubject();
                String scheduleSubject = schedule.getSubject() != null ? schedule.getSubject() : scheduleMapper.inferSubject(schedule.getTitle());
                if (teacherSubject != null && !teacherSubject.isBlank() && scheduleSubject != null && !scheduleSubject.isBlank()) {
                    String normTeacher = scheduleMapper.normalizeSubject(teacherSubject);
                    String normSchedule = scheduleMapper.normalizeSubject(scheduleSubject);
                    if (!normTeacher.equalsIgnoreCase(normSchedule)) {
                        throw new BusinessException("Bạn là giáo viên bộ môn " + teacherSubject + ", không có quyền hủy lịch thi thuộc bộ môn " + scheduleSubject + "!");
                    }
                }
            }
        }

        schedule.setStatus(PlacementScheduleStatus.CANCELLED);
        schedule.setCancelReason(trimmedReason);
        schedule.setCancelledAt(LocalDateTime.now());
        EnrollmentRequest enrollmentRequest = schedule.getEnrollmentRequest();
        if (enrollmentRequest != null && enrollmentRequest.getStatus() == EnrollmentRequestStatus.WAITING_PLACEMENT) {
            schedule.setEnrollmentRequest(null);
        }

        PlacementSchedule saved = scheduleRepository.save(schedule);
        if (notificationService != null && saved != null && saved.getParent() != null) {
            notificationService.sendNotification(
                    saved.getParent(), null,
                    "Lịch kiểm tra đầu vào (Placement Test) đã hủy",
                    "Lịch đánh giá năng khiếu đầu vào của bé " + saved.getStudentName() + " (#PT-" + saved.getId() + ") đã được hủy. Lý do: " + trimmedReason,
                    "PLACEMENT_CANCELLED", saved.getId(), "/pages/course-enrollment.html");
        }
        return scheduleMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PlacementRecommendationResponse getLatestRecommendation(Long studentId) {
        if (!studentRepository.existsById(studentId)) {
            throw new BusinessException("Học viên không tồn tại với ID: " + studentId);
        }

        var schedule = scheduleRepository.findFirstByEnrollmentRequestStudentIdOrderByTestDateDescIdDesc(studentId);
        if (schedule.isPresent()) {
            return toRecommendationResponse(schedule.get(), studentId);
        }

        return placementTestRepository.findFirstByStudentIdOrderByTestDateDescCreatedAtDesc(studentId)
                .map(this::toRecommendationResponse)
                .orElseGet(() -> PlacementRecommendationResponse.notAvailable(studentId));
    }

    @Override
    @Transactional(readOnly = true)
    public PlacementRecommendationResponse getLatestRecommendation(Long studentId, Long programId) {
        if (programId == null) {
            return getLatestRecommendation(studentId);
        }
        if (!studentRepository.existsById(studentId)) {
            throw new BusinessException("Học viên không tồn tại với ID: " + studentId);
        }
        return scheduleRepository
                .findFirstByEnrollmentRequestStudentIdAndProgramIdAndStatusAndRecommendedCourseProgramIdOrderByTestDateDescIdDesc(
                        studentId, programId, PlacementScheduleStatus.COMPLETED, programId)
                .map(schedule -> toRecommendationResponse(schedule, studentId))
                .orElseGet(() -> placementTestRepository
                        .findFirstByStudentIdAndProgramIdAndRecommendedCourseProgramIdOrderByTestDateDescCreatedAtDesc(
                                studentId, programId, programId)
                        .map(this::toRecommendationResponse)
                        .orElseGet(() -> PlacementRecommendationResponse.notAvailable(studentId)));
    }

    private PlacementRecommendationResponse toRecommendationResponse(PlacementSchedule schedule, Long studentId) {
        if (schedule.getStatus() != PlacementScheduleStatus.COMPLETED) {
            return PlacementRecommendationResponse.notAvailable(studentId);
        }
        Course course = schedule.getRecommendedCourse();
        return new PlacementRecommendationResponse(
                studentId,
                "COMPLETED",
                course != null,
                schedule.getScore() == null ? null : schedule.getScore() / 10.0,
                schedule.getRecommendedLevel() == null ? null : schedule.getRecommendedLevel().name(),
                course == null ? null : course.getId(),
                course == null ? null : course.getName(),
                schedule.getTeacherNote(),
                schedule.getEvaluatedAt()
        );
    }

    private PlacementRecommendationResponse toRecommendationResponse(PlacementTest placementTest) {
        Course course = placementTest.getRecommendedCourse();
        return new PlacementRecommendationResponse(
                placementTest.getStudent().getId(),
                "COMPLETED",
                course != null,
                placementTest.getTotalScore(),
                placementTest.getRecommendedLevel(),
                course == null ? null : course.getId(),
                course == null ? null : course.getName(),
                placementTest.getTeacherNotes(),
                placementTest.getCreatedAt()
        );
    }
}
