package com.talent.management.features.placement_test.dto.request;

import com.talent.management.features.placement_test.entity.PlacementLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

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

    private String teacherNote;

    private String audioUrl;

    private String videoUrl;

    private String imageUrl;

    private String recordUrl;
}
