package com.talent.management.features.placement_test.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePlacementScheduleRequest {

    @NotBlank(message = "Tên học viên không được để trống")
    private String studentName;

    @NotBlank(message = "Tiêu đề không được để trống")
    private String title;

    @NotBlank(message = "Tên phòng không được để trống")
    private String roomName;

    private String branch;

    @NotNull(message = "Ngày thi không được để trống")
    private LocalDateTime testDate;

    private String note;
}
