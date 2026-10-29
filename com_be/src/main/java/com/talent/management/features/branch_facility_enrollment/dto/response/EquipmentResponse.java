package com.talent.management.features.branch_facility_enrollment.dto.response;

import com.talent.management.shared.enums.EquipmentCategory;
import com.talent.management.shared.enums.EquipmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EquipmentResponse {
    private Long id;
    private Long roomId;
    private String roomName;
    private String roomCode;
    private String branchName;
    private String name;
    private String code;
    private EquipmentCategory category;
    private EquipmentStatus status;
    private String serialNumber;
    private String description;
    @Builder.Default
    private List<String> warnings = List.of();
    private int affectedLessonCount;
}
