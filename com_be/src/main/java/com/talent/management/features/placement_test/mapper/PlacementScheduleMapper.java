package com.talent.management.features.placement_test.mapper;

import com.talent.management.features.placement_test.dto.request.CreatePlacementScheduleRequest;
import com.talent.management.features.placement_test.dto.response.PlacementScheduleResponse;
import com.talent.management.features.placement_test.entity.PlacementSchedule;
import com.talent.management.features.placement_test.entity.PlacementScheduleStatus;
import org.springframework.stereotype.Component;

@Component
public class PlacementScheduleMapper {

    public PlacementScheduleResponse toResponse(PlacementSchedule schedule) {
        if (schedule == null) {
            return null;
        }

        return PlacementScheduleResponse.builder()
                .id(schedule.getId())
                .enrollmentRequestId(schedule.getEnrollmentRequest() == null ? null : schedule.getEnrollmentRequest().getId())
                .studentName(schedule.getStudentName())
                .title(schedule.getTitle())
                .roomName(schedule.getRoomName())
                .branch(schedule.getBranch())
                .testDate(schedule.getTestDate())
                .subject(schedule.getSubject() != null ? schedule.getSubject() : inferSubject(schedule.getTitle()))
                .note(schedule.getNote())
                .status(schedule.getStatus())
                .score(schedule.getScore())
                .recommendedLevel(schedule.getRecommendedLevel())
                .teacherNote(schedule.getTeacherNote())
                .audioUrl(schedule.getAudioUrl())
                .videoUrl(schedule.getVideoUrl())
                .imageUrl(schedule.getImageUrl())
                .recordUrl(schedule.getRecordUrl())
                .evaluatedById(schedule.getEvaluatedBy() != null ? schedule.getEvaluatedBy().getId() : null)
                .evaluatedByName(schedule.getEvaluatedBy() != null ? schedule.getEvaluatedBy().getFullName() : null)
                .evaluatedAt(schedule.getEvaluatedAt())
                .parentId(schedule.getParent() != null ? schedule.getParent().getId() : null)
                .parentName(schedule.getParent() != null ? schedule.getParent().getFullName() : null)
                .parentEmail(schedule.getParent() != null ? schedule.getParent().getEmail() : null)
                .createdAt(schedule.getCreatedAt())
                .build();
    }

    public PlacementSchedule toEntity(CreatePlacementScheduleRequest request) {
        if (request == null) {
            return null;
        }

        String subject = request.getSubject();
        if (subject == null || subject.isBlank()) {
            subject = inferSubject(request.getTitle());
        }

        return PlacementSchedule.builder()
                .studentName(request.getStudentName())
                .title(request.getTitle())
                .roomName(request.getRoomName())
                .branch(request.getBranch())
                .testDate(request.getTestDate())
                .subject(subject)
                .note(request.getNote())
                .status(PlacementScheduleStatus.SCHEDULED)
                .build();
    }

    public String normalizeSubject(String subject) {
        if (subject == null || subject.isBlank()) return "DAN";
        String s = subject.toUpperCase().trim();
        if (s.equals("DAN") || s.equals("PIANO") || s.equals("GUITAR") || s.equals("NHAC") || s.equals("MUSIC") || s.equals("VOCAL") || s.equals("VIOLIN") || s.equals("DRUMS") || s.contains("ĐÀN") || s.contains("NHẠC")) {
            return "DAN";
        }
        if (s.equals("MUA") || s.equals("DANCE") || s.contains("MÚA") || s.contains("BALLET")) {
            return "MUA";
        }
        if (s.equals("VO") || s.equals("MARTIAL_ARTS") || s.contains("VÕ") || s.contains("TAEKWONDO")) {
            return "VO";
        }
        return s;
    }

    public String inferSubject(String text) {
        if (text == null) return "DAN";
        String lower = text.toLowerCase();
        if (lower.contains("võ") || lower.contains("vo") || lower.contains("taekwondo") || lower.contains("martial") || lower.contains("thể lực")) return "VO";
        if (lower.contains("múa") || lower.contains("mua") || lower.contains("dance") || lower.contains("ballet") || lower.contains("nhảy")) return "MUA";
        if (lower.contains("đàn") || lower.contains("dan") || lower.contains("piano") || lower.contains("guitar") || lower.contains("nhạc") || lower.contains("phím") || lower.contains("vocal") || lower.contains("organ")) return "DAN";
        return "DAN";
    }
}
