package com.talent.management.shared.enums;

public enum MakeupStatus {
    PENDING,       // Chờ nhân viên/admin xếp lịch
    SCHEDULED,     // Đã xếp lịch — chờ điểm danh
    COMPLETED,     // Đã hoàn thành (điểm danh PRESENT)
    CANCELLED,     // Đã hủy
    REGISTERED     // @Deprecated — giữ lại để tương thích dữ liệu cũ trong DB; không dùng cho logic mới
}
