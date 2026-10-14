package com.talent.management.shared.enums;

public enum MakeupStatus {
    PENDING,       // Chờ nhân viên/admin xếp lịch
    SCHEDULED,     // Đã xếp lịch — chờ điểm danh
    COMPLETED,     // Đã hoàn thành (điểm danh PRESENT)
    CANCELLED,     // Đã hủy (kể cả trường hợp gia đình báo không cần học bù)
    NO_SLOT,       // Không có lớp phù hợp — đã báo phụ huynh, chờ phản hồi (đi học được hay bảo lưu)
    DEFERRED,      // Buổi quan trọng không bù được — chuyển sang khóa học kỳ sau
    REGISTERED     // @Deprecated — giữ lại để tương thích dữ liệu cũ trong DB; không dùng cho logic mới
}
