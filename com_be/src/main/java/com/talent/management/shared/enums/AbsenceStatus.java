package com.talent.management.shared.enums;

public enum AbsenceStatus {
    PENDING,          // Chờ xét duyệt (Giáo viên phụ trách / Staff / Admin)
    APPROVED,         // Đồng ý — cần học bù (tự động tạo MakeupRegistration{PENDING})
    REJECTED          // Từ chối — luồng kết thúc
}
