package com.talent.management.features.admin_management.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ChildItemRequest(
        @Size(max = 150) String fullName,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dateOfBirth,
        @Size(max = 20) String gender,
        @Size(max = 200) String schoolName,
        @Size(max = 500) String notes
) {}
