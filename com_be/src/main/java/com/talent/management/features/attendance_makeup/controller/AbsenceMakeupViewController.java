package com.talent.management.features.attendance_makeup.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller điều hướng giao diện JSP cho phân hệ Nghỉ học & Học bù
 * Hỗ trợ các đường dẫn truy cập trực tiếp từ trình duyệt trên cổng 8080.
 */
@Controller
public class AbsenceMakeupViewController {

    /**
     * Màn hình Nghỉ học & Học bù (JSP)
     * Ánh xạ tới file /WEB-INF/views/pages/absence-makeup.jsp
     */
    @GetMapping({"/absence-makeup", "/pages/absence-makeup", "/pages/absence-makeup.jsp"})
    public String absenceMakeupPage() {
        return "pages/absence-makeup";
    }

    /**
     * Màn hình Trang Chủ / Bảng Điều Khiển Học Viện (JSP)
     * Ánh xạ tới file /WEB-INF/views/index.jsp
     */
    @GetMapping({"/", "/index", "/index.html"})
    public String indexPage() {
        return "index";
    }

    /**
     * Màn hình đăng nhập (JSP)
     */
    @GetMapping({"/login", "/login.html"})
    public String loginPage() {
        return "login";
    }
}
