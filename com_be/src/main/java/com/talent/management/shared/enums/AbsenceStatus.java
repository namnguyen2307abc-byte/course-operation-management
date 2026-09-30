package com.talent.management.shared.enums;

public enum AbsenceStatus {
    PENDING,          // Chờ giáo viên duyệt
    APPROVED,         // Duyệt — cần học bù (auto tạo MakeupRegistration)
    EXCUSED,          // Duyệt — miễn học bù (nghỉ ốm có giấy, lý do đặc biệt)
    REJECTED,         // Từ chối — luồng kết thúc
    SPECIAL_APPROVED  // @Deprecated — giữ lại để tương thích dữ liệu cũ trong DB
}
