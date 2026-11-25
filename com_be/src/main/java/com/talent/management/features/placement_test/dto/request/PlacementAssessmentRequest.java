package com.talent.management.features.placement_test.dto.request;

import com.talent.management.features.placement_test.entity.PlacementLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlacementAssessmentRequest {

    @NotNull(message = "Vui lòng nhập điểm số")
    @Min(value = 0, message = "Điểm số không được nhỏ hơn 0")
    @Max(value = 100, message = "Điểm số không được vượt quá 100")
    private Integer score;

    @NotNull(message = "Vui lòng chọn trình độ gợi ý")
    private PlacementLevel recommendedLevel;

    private Long recommendedCourseId;

    @Size(max = 2000, message = "Nhận xét của giáo viên không được vượt quá 2000 ký tự")
    private String teacherNote;

    @Size(max = 500, message = "Đường dẫn file âm thanh không được vượt quá 500 ký tự")
    private String audioUrl;

    @Size(max = 500, message = "Đường dẫn file video không được vượt quá 500 ký tự")
    private String videoUrl;

    @Size(max = 500, message = "Đường dẫn file hình ảnh không được vượt quá 500 ký tự")
    private String imageUrl;

    @Size(max = 500, message = "Đường dẫn file bản ghi không được vượt quá 500 ký tự")
    private String recordUrl;

    @Valid
    private List<PlacementCriteriaScoreItemRequest> criteriaScores;
}
