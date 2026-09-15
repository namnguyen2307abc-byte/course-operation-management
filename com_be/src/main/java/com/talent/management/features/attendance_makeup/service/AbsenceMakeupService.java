package com.talent.management.features.attendance_makeup.service;

import com.talent.management.features.attendance_makeup.repository.AbsenceRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AbsenceMakeupService {

    private final AbsenceRequestRepository absenceRequestRepository;
}
