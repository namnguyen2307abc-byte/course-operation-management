package com.talent.management.features.placement_test.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePlacementScheduleRequest {

    private Long enrollmentRequestId;
    private Long programId;

    @NotBlank(message = "Tên học viên không được để trống")
    @Size(max = 100, message = "Tên học viên không được vượt quá 100 ký tự")
    private String studentName;

    @NotBlank(message = "Tiêu đề không được để trống")
    @Size(max = 150, message = "Tiêu đề ca thi không được vượt quá 150 ký tự")
    private String title;

    @NotBlank(message = "Tên phòng không được để trống")
    @Size(max = 100, message = "Tên phòng không được vượt quá 100 ký tự")
    private String roomName;

    @Size(max = 150, message = "Tên cơ sở không được vượt quá 150 ký tự")
    private String branch;

    @NotNull(message = "Ngày thi không được để trống")
    private LocalDateTime testDate;

    @Size(max = 50, message = "Mã bộ môn không được vượt quá 50 ký tự")
    private String subject;

    @Size(max = 1000, message = "Ghi chú không được vượt quá 1000 ký tự")
    private String note;
}
