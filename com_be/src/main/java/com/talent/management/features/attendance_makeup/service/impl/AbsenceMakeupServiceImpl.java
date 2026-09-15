package com.talent.management.features.attendance_makeup.service.impl;

import com.talent.management.features.attendance_makeup.mapper.AttendanceMakeupMapper;
import com.talent.management.features.attendance_makeup.repository.AbsenceRequestRepository;
import com.talent.management.features.attendance_makeup.service.AbsenceMakeupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AbsenceMakeupServiceImpl implements AbsenceMakeupService {

    private final AbsenceRequestRepository absenceRequestRepository;
    private final AttendanceMakeupMapper attendanceMakeupMapper;
}
