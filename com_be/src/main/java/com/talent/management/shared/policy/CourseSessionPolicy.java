package com.talent.management.shared.policy;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Session counts shared by course maintenance and new class scheduling. */
public final class CourseSessionPolicy {
    private CourseSessionPolicy() {
    }

    public static int requireStandardSessions(Integer sessions) {
        if (sessions == null || sessions < 12 || sessions > 60 || sessions % 12 != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Số buổi của khóa học phải là 12, 24, 36, 48 hoặc 60. "
                            + "Vui lòng cập nhật khóa học trước khi tạo lớp.");
        }
        return sessions;
    }
}
