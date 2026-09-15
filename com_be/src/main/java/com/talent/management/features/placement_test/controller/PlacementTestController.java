package com.talent.management.features.placement_test.controller;

import com.talent.management.features.placement_test.service.PlacementTestService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/placement-tests")
@RequiredArgsConstructor
@Tag(name = "3. Test đầu vào & Lộ trình", description = "Quản lý đánh giá năng khiếu đầu vào, ghi âm/video đàn và lộ trình phát triển")
public class PlacementTestController {

    private final PlacementTestService placementTestService;
}
