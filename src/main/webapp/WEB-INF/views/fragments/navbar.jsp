<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<nav class="navbar navbar-expand-lg navbar-dark navbar-custom sticky-top py-2">
    <div class="container">
        <a class="navbar-brand d-flex align-items-center text-white text-decoration-none" href="<c:url value='/pages/absence-makeup'/>">
            <span class="logo-icon">🎹</span>
            <div>
                <span class="fw-bold tracking-tight">TALENT ACADEMY</span>
                <span class="d-none d-md-block text-warning small" style="font-size: 0.68rem; letter-spacing: 0.08em; text-transform: uppercase;">Học Viện Âm Nhạc & Nghệ Thuật</span>
            </div>
        </a>
        <button class="navbar-toggler border-0 shadow-none" type="button" data-bs-toggle="collapse" data-bs-target="#mainNav">
            <span class="navbar-toggler-icon"></span>
        </button>
        <div class="collapse navbar-collapse" id="mainNav">
            <ul class="navbar-nav me-auto mb-2 mb-lg-0 ms-lg-4">
                <li class="nav-item">
                    <a class="nav-link active" href="<c:url value='/pages/absence-makeup'/>">
                        <i class="bi bi-calendar2-check me-1"></i>Nghỉ Học & Bù (JSP)
                    </a>
                </li>
                <li class="nav-item">
                    <a class="nav-link" href="<c:url value='/swagger-ui.html'/>" target="_blank">
                        <i class="bi bi-code-slash me-1"></i>Swagger API
                    </a>
                </li>
            </ul>
            <div class="d-flex align-items-center gap-2">
                <div id="userProfileBadge" class="d-flex align-items-center text-white small">
                    <span id="userNameDisplay" class="text-white-50">Đang tải...</span>
                </div>
                <button class="btn btn-outline-light btn-sm px-3 rounded-pill" onclick="logout()">
                    <i class="bi bi-box-arrow-right me-1"></i>Đăng xuất
                </button>
            </div>
        </div>
    </div>
</nav>
