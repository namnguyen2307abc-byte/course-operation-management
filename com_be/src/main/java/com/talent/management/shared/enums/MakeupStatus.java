package com.talent.management.shared.enums;

public enum MakeupStatus {
    PENDING,       // Chờ nhân viên/admin xếp lịch
    SCHEDULED,     // Đã xếp lịch — chờ điểm danh
    COMPLETED,     // Đã hoàn thành (điểm danh PRESENT)
    MISSED,        // Bỏ lỡ / Vắng mặt tại buổi học bù đã xếp (điểm danh ABSENT)
    CANCELLED,     // Đã hủy (kể cả trường hợp gia đình báo không cần học bù)
    NO_SLOT        // Không có lớp phù hợp — đã báo phụ huynh, chờ phản hồi
}
