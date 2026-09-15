package com.talent.management.features.placement_test.service;

import com.talent.management.features.placement_test.repository.PlacementTestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlacementTestService {

    private final PlacementTestRepository placementTestRepository;
}
