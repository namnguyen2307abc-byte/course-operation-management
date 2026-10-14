/**
 * ==============================================================================
 * Course Operation Management (COM) - Talent Academy
 * Client-Side Authentication & Role-Based Access Control (RBAC) Module
 * File: auth.js
 * ==============================================================================
 * Cung cấp:
 *  1. Quản lý phiên làm việc & Token: getToken(), isAuthenticated(), requireAuth(), checkAuth()
 *  2. Dữ liệu người dùng: getCurrentUser(), setCurrentUser(), sanitizeUserData()
 *  3. Điều khiển hiển thị Navbar & Menu: updateNavbarUser(), fixNavbarLinks()
 *  4. Phân quyền hiển thị theo Role (Tuition, Class Management, Course Management)
 *  5. Chuyển đổi nhanh vai trò kiểm thử (quickSwitchUserRole) kèm đồng bộ JWT thật từ Backend
 *  6. Đăng nhập / Đăng xuất: login(), logout()
 * ==============================================================================
 */

// Danh sách tài khoản thử nghiệm theo dữ liệu hạt giống (Seed Data CSDL)
const DEMO_USERS = {
    'admin': {
        username: 'admin',
        fullName: 'Quản Trị Viên Hệ Thống',
        role: 'ADMIN',
        subject: null,
        email: 'admin@talentacademy.edu.vn'
    },
    'cashier_mai': {
        username: 'cashier_mai',
        fullName: 'Nguyễn Thanh Mai (Thu Ngân / Giáo Vụ)',
        role: 'STAFF',
        subject: null,
        email: 'mai.nguyen@talentacademy.edu.vn'
    },
    'staff_minh': {
        username: 'staff_minh',
        fullName: 'Trần Quang Minh (Tư Vấn & Xếp Lớp)',
        role: 'STAFF',
        subject: null,
        email: 'minh.tran@talentacademy.edu.vn'
    },
    'teacher_huong': {
        username: 'teacher_huong',
        fullName: 'Cô Vũ Thu Hương (GV Piano)',
        role: 'TEACHER',
        subject: 'DAN',
        email: 'huong.vu@talentacademy.edu.vn'
    },
    'teacher_tuan': {
        username: 'teacher_tuan',
        fullName: 'Thầy Trần Anh Tuấn (GV Guitar)',
        role: 'TEACHER',
        subject: 'DAN',
        email: 'tuan.tran@talentacademy.edu.vn'
    },
    'teacher_linh': {
        username: 'teacher_linh',
        fullName: 'Cô Phạm Khánh Linh (GV Múa & Ballet)',
        role: 'TEACHER',
        subject: 'MUA',
        email: 'linh.pham@talentacademy.edu.vn'
    },
    'teacher_long': {
        username: 'teacher_long',
        fullName: 'Thầy Hoàng Phi Long (GV Võ Thuật)',
        role: 'TEACHER',
        subject: 'VO',
        email: 'long.hoang@talentacademy.edu.vn'
    },
    'parent_lan': {
        username: 'parent_lan',
        fullName: 'Phụ Huynh Lê Thị Lan',
        role: 'PARENT',
        subject: null,
        email: 'lan.le@gmail.com'
    }
};

/**
 * Lấy tiền tố đường dẫn ứng dụng (/com_fe hoặc '') tùy theo môi trường triển khai
 */
function getAppPrefix() {
    return window.location.pathname.startsWith('/com_fe') ? '/com_fe' : '';
}

/**
 * Lấy JWT Token từ localStorage
 */
function getToken() {
    return localStorage.getItem("token") || "";
}

/**
 * Kiểm tra người dùng đã có token hợp lệ hay chưa
 */
function isAuthenticated() {
    return Boolean(getToken());
}

/**
 * Làm sạch và chuẩn hóa dữ liệu người dùng (tự phục hồi font tiếng Việt, bỏ tiền tố ROLE_)
 */
function sanitizeUserData(user) {
    if (!user) return null;

    if (user.role) {
        user.role = String(user.role).toUpperCase().replace("ROLE_", "");
    }

    // Tự động đối chiếu thông tin chuẩn từ danh bạ hệ thống nếu có
    const uname = (user.username || "").toLowerCase();
    if (uname && DEMO_USERS[uname]) {
        const demo = DEMO_USERS[uname];
        user.fullName = demo.fullName;
        user.role = demo.role;
        user.subject = demo.subject;
        user.email = demo.email;
    } else if (uname.includes("teacher") || uname.includes("gv")) {
        user.role = user.role || "TEACHER";
        if (!user.subject) {
            user.subject = uname.includes("tuan") || uname.includes("guitar") ? "DAN" :
                           uname.includes("linh") || uname.includes("mua") ? "MUA" :
                           uname.includes("long") || uname.includes("vo") ? "VO" : "DAN";
        }
    } else if (uname.includes("parent")) {
        user.role = user.role || "PARENT";
    } else if (uname.includes("cashier") || uname.includes("staff")) {
        user.role = user.role || "STAFF";
    }

    // Fallback mặc định
    if (!user.role) {
        user.role = "TEACHER";
        user.subject = "DAN";
    }

    return user;
}

/**
 * Lấy thông tin người dùng đang đăng nhập
 */
function getCurrentUser() {
    try {
        const userStr = localStorage.getItem("user");
        if (!userStr) {
            const defaultUser = DEMO_USERS['teacher_huong'];
            localStorage.setItem("user", JSON.stringify(defaultUser));
            return defaultUser;
        }
        const user = JSON.parse(userStr);
        return sanitizeUserData(user);
    } catch (e) {
        return DEMO_USERS['teacher_huong'];
    }
}

/**
 * Lưu thông tin người dùng vào localStorage
 */
function setCurrentUser(user) {
    if (!user) {
        localStorage.removeItem("user");
        return;
    }
    const cleanUser = sanitizeUserData(user);
    localStorage.setItem("user", JSON.stringify(cleanUser));
}

/**
 * Kiểm tra xem người dùng hiện tại có vai trò trong danh sách chỉ định không
 */
function hasRole(...roles) {
    const user = getCurrentUser();
    if (!user || !user.role) return false;
    const currentRole = user.role.toUpperCase().replace("ROLE_", "");
    const targetRoles = roles.flat().map(r => String(r).toUpperCase().replace("ROLE_", ""));
    return targetRoles.includes(currentRole);
}

/**
 * Kiểm tra xác thực - chuyển hướng về login nếu chưa có token
 */
function checkAuth() {
    const token = getToken();
    if (!token && !window.location.pathname.endsWith("login.html")) {
        const prefix = getAppPrefix();
        window.location.href = `${prefix}/login.html`;
        return false;
    }
    return true;
}

/**
 * Bảo vệ trang yêu cầu quyền hạn cụ thể (Guard)
 * @param {Array<string>} allowedRoles Danh sách vai trò được phép truy cập
 */
function requireAuth(allowedRoles = []) {
    if (!checkAuth()) return false;

    if (allowedRoles && allowedRoles.length > 0) {
        const user = getCurrentUser();
        const role = (user?.role || "").toUpperCase().replace("ROLE_", "");
        const normalized = allowedRoles.map(r => r.toUpperCase().replace("ROLE_", ""));

        if (!normalized.includes(role)) {
            const prefix = getAppPrefix();
            alert(`Tài khoản của bạn [${role || 'Chưa xác định'}] không có quyền truy cập trang này!\nYêu cầu vai trò: ${allowedRoles.join(', ')}.`);
            window.location.href = `${prefix}/index.html`;
            return false;
        }
    }
    return true;
}

/**
 * Cập nhật hiển thị huy hiệu người dùng và phân quyền các mục điều hướng trên Navbar
 */
function updateNavbarUser() {
    const user = getCurrentUser();
    const displayEl = document.getElementById("userNameDisplay");

    if (displayEl) {
        if (user && user.fullName) {
            let roleBadge = "bg-secondary";
            const roleName = user.role || "TEACHER";
            let subjectText = "";

            if (roleName === "ADMIN") {
                roleBadge = "bg-danger";
            } else if (roleName === "BRANCH_MANAGER") {
                roleBadge = "bg-primary";
            } else if (roleName === "STAFF" || roleName === "CASHIER") {
                roleBadge = "bg-info text-dark";
            } else if (roleName === "TEACHER") {
                roleBadge = "bg-warning text-dark";
                const subName = user.subject === "DAN" ? "Đàn" :
                               (user.subject === "MUA" ? "Múa" :
                               (user.subject === "VO" ? "Võ" : (user.subject || "")));
                subjectText = subName ? ` - BM ${subName}` : "";
            } else if (roleName === "PARENT") {
                roleBadge = "bg-success";
            } else if (roleName === "STUDENT") {
                roleBadge = "bg-light text-dark border";
            }

            displayEl.innerHTML = `
                <div class="d-flex align-items-center gap-2">
                    <span class="badge ${roleBadge} px-2 py-1" style="font-size: 0.72rem;">
                        <i class="bi bi-shield-check me-1"></i>${roleName}${subjectText}
                    </span>
                    <span class="fw-bold text-white me-1">${user.fullName}</span>
                    <button class="btn btn-xs btn-outline-warning text-white rounded-pill px-2 py-0 ms-1"
                            style="font-size: 0.72rem; line-height: 1.5;"
                            onclick="quickSwitchUserRole()"
                            title="Bấm để đổi nhanh tài khoản Giáo Viên / Phụ Huynh / Admin / Thu Ngân">
                        <i class="bi bi-arrow-repeat me-1"></i>Đổi tài khoản
                    </button>
                </div>
            `;
        } else {
            const prefix = getAppPrefix();
            displayEl.innerHTML = `
                <a href="${prefix}/login.html" class="btn btn-outline-warning btn-sm px-3 rounded-pill py-1" style="font-size: 0.8rem;">
                    🔑 Đăng nhập
                </a>
            `;
        }
    }

    const currentRole = user ? (user.role || "").toUpperCase() : "";
    const currentUsername = user ? (user.username || "").toLowerCase() : "";

    // 1. Phân quyền hiển thị Menu "Học Phí":
    // Chỉ Quản trị viên (ADMIN), Thu ngân/Nhân viên (STAFF/CASHIER), Quản lý chi nhánh (BRANCH_MANAGER)
    const tuitionNavItem = document.getElementById("navItemTuitionPayment");
    if (tuitionNavItem) {
        const isTuitionAuthorized = currentRole === "ADMIN" ||
                                    currentRole === "STAFF" ||
                                    currentRole === "CASHIER" ||
                                    currentRole === "BRANCH_MANAGER" ||
                                    currentUsername.includes("cashier") ||
                                    currentUsername.includes("admin");
        if (isTuitionAuthorized) {
            tuitionNavItem.style.display = "";
            tuitionNavItem.classList.remove("d-none");
        } else {
            tuitionNavItem.style.display = "none";
            tuitionNavItem.classList.add("d-none");
        }
    }

    // 2. Phân quyền hiển thị Menu "Quản Lý Lớp":
    // Chỉ Quản trị viên (ADMIN), Nhân viên giáo vụ (STAFF), Quản lý chi nhánh (BRANCH_MANAGER)
    const classManagementNavItem = document.getElementById("navItemClassManagement");
    if (classManagementNavItem) {
        const isClassAuthorized = currentRole === "ADMIN" ||
                                  currentRole === "STAFF" ||
                                  currentRole === "BRANCH_MANAGER";
        if (isClassAuthorized) {
            classManagementNavItem.style.display = "";
            classManagementNavItem.classList.remove("d-none");
        } else {
            classManagementNavItem.style.display = "none";
            classManagementNavItem.classList.add("d-none");
        }
    }

    // 3. Phân quyền hiển thị Menu "Khóa Học":
    // Chỉ Quản trị viên (ADMIN), Nhân viên giáo vụ (STAFF), Quản lý chi nhánh (BRANCH_MANAGER)
    const courseManagementNavItem = document.getElementById("navItemCourseManagement");
    if (courseManagementNavItem) {
        const isCourseAuthorized = currentRole === "ADMIN" ||
                                   currentRole === "STAFF" ||
                                   currentRole === "BRANCH_MANAGER";
        if (isCourseAuthorized) {
            courseManagementNavItem.style.display = "";
            courseManagementNavItem.classList.remove("d-none");
        } else {
            courseManagementNavItem.style.display = "none";
            courseManagementNavItem.classList.add("d-none");
        }
    }

    // Chuẩn hóa và sửa lỗi đường dẫn điều hướng trên Navbar kèm highlight link đang mở
    fixNavbarLinks();
}

/**
 * Chuẩn hóa các đường link trên Navbar để tương thích cả Live-Server và Spring Boot
 * Tự động kích hoạt class "active" cho trang hiện tại
 */
function fixNavbarLinks() {
    const prefix = getAppPrefix();
    const currentPath = window.location.pathname.toLowerCase();

    const brand = document.querySelector('.navbar-brand');
    if (brand) brand.setAttribute('href', prefix + '/index.html');

    document.querySelectorAll('#mainNav a.nav-link, .navbar-nav a.nav-link').forEach(link => {
        const href = link.getAttribute('href') || '';
        let targetPath = '';

        if (href.includes('index.html')) {
            targetPath = prefix + '/index.html';
        } else if (href.includes('branch-facility')) {
            targetPath = prefix + '/pages/branch-facility.html';
        } else if (href.includes('course-enrollment')) {
            targetPath = prefix + '/pages/course-enrollment.html';
        } else if (href.includes('class-management')) {
            targetPath = prefix + '/pages/class-management.html';
        } else if (href.includes('weekly-schedule')) {
            targetPath = prefix + '/pages/weekly-schedule.html';
        } else if (href.includes('absence-makeup')) {
            targetPath = prefix + '/pages/absence-makeup.html';
        } else if (href.includes('placement-test')) {
            targetPath = prefix + '/pages/placement-test.html';
        } else if (href.includes('tuition-payment')) {
            targetPath = prefix + '/pages/tuition-payment.html';
        } else if (href.includes('course-management')) {
            targetPath = prefix + '/pages/course-management.html';
        }

        if (targetPath) {
            link.setAttribute('href', targetPath);

            // Highlight tab đang mở
            const targetFilename = targetPath.split('/').pop().toLowerCase();
            const currentFilename = currentPath.split('/').pop().toLowerCase() || 'index.html';
            if (targetFilename === currentFilename) {
                link.classList.add('active');
            } else {
                link.classList.remove('active');
            }
        }
    });
}

/**
 * Đổi nhanh tài khoản kiểm thử và tự động đăng nhập lấy JWT token thật từ backend
 */
async function quickSwitchUserRole() {
    const choices = [
        "1. Quản Trị Viên Hệ Thống (ADMIN)",
        "2. Nguyễn Thanh Mai (Thu Ngân / Giáo Vụ - STAFF)",
        "3. Trần Quang Minh (Tư Vấn & Xếp Lớp - STAFF)",
        "4. Cô Vũ Thu Hương (GV Piano - TEACHER)",
        "5. Thầy Trần Anh Tuấn (GV Guitar - TEACHER)",
        "6. Cô Phạm Khánh Linh (GV Múa & Ballet - TEACHER)",
        "7. Thầy Hoàng Phi Long (GV Võ Thuật - TEACHER)",
        "8. Phụ Huynh Lê Thị Lan (Phụ Huynh - PARENT)"
    ].join("\n");

    const selected = prompt(`CHỌN TÀI KHOẢN ĐỂ ĐỔI VAI TRÒ TEST:\n\n${choices}\n\nNhập số 1 - 8:`, "1");
    if (!selected) return;

    let targetUser = null;
    switch (selected.trim()) {
        case "1": targetUser = DEMO_USERS['admin']; break;
        case "2": targetUser = DEMO_USERS['cashier_mai']; break;
        case "3": targetUser = DEMO_USERS['staff_minh']; break;
        case "4": targetUser = DEMO_USERS['teacher_huong']; break;
        case "5": targetUser = DEMO_USERS['teacher_tuan']; break;
        case "6": targetUser = DEMO_USERS['teacher_linh']; break;
        case "7": targetUser = DEMO_USERS['teacher_long']; break;
        case "8": targetUser = DEMO_USERS['parent_lan']; break;
        default: return;
    }

    // Lưu thông tin người dùng ngay lập tức
    localStorage.setItem("user", JSON.stringify(targetUser));

    // Cố gắng đồng bộ JWT Token thật từ backend Spring Boot
    const isDev = window.location.port !== "8080" && window.location.port !== "";
    const baseUrl = isDev ? `http://${window.location.hostname || 'localhost'}:8080` : "";

    try {
        const response = await fetch(`${baseUrl}/api/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                username: targetUser.username,
                password: "123456"
            })
        });

        if (response.ok) {
            const data = await response.json();
            if (data && data.token) {
                localStorage.setItem("token", data.token);
                localStorage.setItem("user", JSON.stringify(data));
            } else {
                localStorage.setItem("token", "AUTH_TOKEN_" + targetUser.username);
            }
        } else {
            localStorage.setItem("token", "AUTH_TOKEN_" + targetUser.username);
        }
    } catch (e) {
        // Backend chưa khởi động, dùng token giả lập để kiểm thử UI offline
        localStorage.setItem("token", "AUTH_TOKEN_" + targetUser.username);
    }

    const newUser = getCurrentUser();
    alert(`Đã đổi sang tài khoản: ${newUser.fullName} [Role: ${newUser.role}]!\nHệ thống đã đồng bộ phiên làm việc thành công.`);
    window.location.reload();
}

/**
 * Đăng nhập tài khoản qua API
 */
async function login(username, password) {
    const isDev = window.location.port !== "8080" && window.location.port !== "";
    const baseUrl = isDev ? `http://${window.location.hostname || 'localhost'}:8080` : "";

    const response = await fetch(`${baseUrl}/api/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password })
    });

    if (!response.ok) {
        const errData = await response.json().catch(() => ({}));
        throw new Error(errData.message || `Đăng nhập thất bại (Mã lỗi: ${response.status})`);
    }

    const data = await response.json();
    if (data && data.token) {
        localStorage.setItem("token", data.token);
        localStorage.setItem("user", JSON.stringify(data));
    }
    return data;
}

/**
 * Đăng xuất khỏi hệ thống
 */
function logout() {
    localStorage.removeItem("token");
    localStorage.removeItem("user");
    const prefix = getAppPrefix();
    window.location.href = `${prefix}/login.html`;
}

// Tự động khởi chạy cập nhật Navbar khi trang hoàn tất nạp DOM
document.addEventListener("DOMContentLoaded", () => {
    setTimeout(updateNavbarUser, 150);
});
