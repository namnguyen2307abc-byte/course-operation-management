package com.talent.management.features.placement_test.service.impl;

import com.talent.management.features.placement_test.mapper.PlacementTestMapper;
import com.talent.management.features.placement_test.repository.PlacementTestRepository;
import com.talent.management.features.placement_test.service.PlacementTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlacementTestServiceImpl implements PlacementTestService {

    private final PlacementTestRepository placementTestRepository;
    private final PlacementTestMapper placementTestMapper;
}
