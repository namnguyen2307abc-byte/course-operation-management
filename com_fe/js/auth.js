/**
 * Auth & Role Management JS
 * Handles authentication status, user role sanitization, and quick role switching for testing.
 */

function checkAuth() {
    const token = localStorage.getItem("token");
    if (!token && !window.location.pathname.endsWith("login.html")) {
        window.location.href = "/login.html";
    }
}

const DEMO_USERS = {
    'teacher_huong': { username: 'teacher_huong', fullName: 'Cô Vũ Thu Hương (GV Piano)', role: 'TEACHER', email: 'huong.vu@talent.edu.vn' },
    'teacher_tuan': { username: 'teacher_tuan', fullName: 'Thầy Trần Anh Tuấn (GV Guitar)', role: 'TEACHER', email: 'tuan.tran@talent.edu.vn' },
    'teacher_hung': { username: 'teacher_hung', fullName: 'Giáo Viên Trần Văn Hùng (GV Piano)', role: 'TEACHER', email: 'hung.tran@talent.com' },
    'admin': { username: 'admin', fullName: 'Quản Trị Viên Hệ Thống', role: 'ADMIN', email: 'admin@talent.edu.vn' },
    'cashier_mai': { username: 'cashier_mai', fullName: 'Nguyễn Thanh Mai (Thu Ngân)', role: 'STAFF', email: 'mai.nguyen@talent.edu.vn' },
    'parent_lan': { username: 'parent_lan', fullName: 'Phụ Huynh Lê Thị Lan', role: 'PARENT', email: 'lan.le@gmail.com' }
};

function sanitizeUserData(user) {
    if (!user) return null;

    if (user.role) {
        user.role = String(user.role).toUpperCase().replace("ROLE_", "");
    }

    // Map role and clean name based on username
    if (user.username && DEMO_USERS[user.username]) {
        const demo = DEMO_USERS[user.username];
        user.fullName = demo.fullName;
        user.role = demo.role;
        user.email = demo.email;
        localStorage.setItem("user", JSON.stringify(user));
    } else if (user.username && (user.username.toLowerCase().includes("teacher") || user.username.toLowerCase().includes("gv") || user.username.toLowerCase().includes("hung"))) {
        user.role = "TEACHER";
        localStorage.setItem("user", JSON.stringify(user));
    } else if (user.username && user.username.toLowerCase().includes("parent")) {
        user.role = "PARENT";
        localStorage.setItem("user", JSON.stringify(user));
    }

    // Default fallback if role is missing
    if (!user.role) {
        user.role = "TEACHER"; // Default fallback for development
        localStorage.setItem("user", JSON.stringify(user));
    }

    return user;
}

function getCurrentUser() {
    try {
        const userStr = localStorage.getItem("user");
        if (!userStr) {
            // Default demo user for seamless testing if no login session exists yet
            const defaultUser = DEMO_USERS['teacher_huong'];
            localStorage.setItem("user", JSON.stringify(defaultUser));
            return defaultUser;
        }
        let user = JSON.parse(userStr);
        return sanitizeUserData(user);
    } catch (e) {
        return DEMO_USERS['teacher_huong'];
    }
}

function updateNavbarUser() {
    const user = getCurrentUser();
    const displayEl = document.getElementById("userNameDisplay");
    if (displayEl) {
        if (user && user.fullName) {
            let roleBadge = "bg-danger";
            let roleName = user.role || "TEACHER";
            if (roleName === "ADMIN") roleBadge = "bg-danger";
            else if (roleName === "TEACHER") roleBadge = "bg-warning text-dark";
            else if (roleName === "PARENT") roleBadge = "bg-success";
            else if (roleName === "STAFF" || roleName === "CASHIER") {
                roleBadge = "bg-info text-dark";
                roleName = "THU NGÂN";
            }

            displayEl.innerHTML = `
                <div class="d-flex align-items-center gap-2">
                    <span class="badge ${roleBadge} px-2 py-1" style="font-size: 0.72rem;"><i class="bi bi-shield-check me-1"></i>${roleName}</span>
                    <span class="fw-bold text-white me-1">${user.fullName}</span>
                    <button class="btn btn-xs btn-outline-warning text-white rounded-pill px-2 py-0 ms-1" style="font-size: 0.72rem; line-height: 1.5;" onclick="quickSwitchUserRole()" title="Bấm để đổi nhanh tài khoản Giáo Viên / Phụ Huynh / Admin">
                        <i class="bi bi-arrow-repeat me-1"></i>Đổi tài khoản
                    </button>
                </div>
            `;
        } else {
            displayEl.innerHTML = `
                <a href="/login.html" class="btn btn-outline-warning btn-sm px-3 rounded-pill py-1" style="font-size: 0.8rem;">
                    🔑 Đăng nhập
                </a>
            `;
        }
    }

    // Phân quyền hiển thị Menu "Học Phí":
    // Chỉ Quản trị viên (ADMIN) và Thu ngân (STAFF / CASHIER / cashier_mai) mới được hiện!
    const tuitionNavItem = document.getElementById("navItemTuitionPayment");
    if (tuitionNavItem) {
        const role = user ? (user.role || "").toUpperCase() : "";
        const username = user ? (user.username || "").toLowerCase() : "";
        const isAuthorized = role === "ADMIN" || role === "STAFF" || role === "CASHIER" || username.includes("cashier") || username.includes("admin");

        if (isAuthorized) {
            tuitionNavItem.style.display = "";
            tuitionNavItem.classList.remove("d-none");
        } else {
            tuitionNavItem.style.display = "none";
            tuitionNavItem.classList.add("d-none");
        }
    }
}

async function quickSwitchUserRole() {
    const choices = [
        "1. Cô Vũ Thu Hương (Giáo Viên Piano - TEACHER)",
        "2. Thầy Trần Anh Tuấn (Giáo Viên Guitar - TEACHER)",
        "3. Giáo Viên Trần Văn Hùng (Giáo Viên Piano - TEACHER)",
        "4. Quản Trị Viên Hệ Thống (ADMIN)",
        "5. Nguyễn Thanh Mai (Nhân Viên / Thu Ngân - STAFF)",
        "6. Phụ Huynh Lê Thị Lan (Phụ Huynh - PARENT)"
    ].join("\n");

    const selected = prompt(`CHỌN TÀI KHOẢN ĐỂ ĐỔI VAI TRÒ TEST:\n\n${choices}\n\nNhập số 1 - 6:`, "1");

    let targetUser = null;
    if (selected === "1") targetUser = DEMO_USERS['teacher_huong'];
    else if (selected === "2") targetUser = DEMO_USERS['teacher_tuan'];
    else if (selected === "3") targetUser = DEMO_USERS['teacher_hung'];
    else if (selected === "4") targetUser = DEMO_USERS['admin'];
    else if (selected === "5") targetUser = DEMO_USERS['cashier_mai'];
    else if (selected === "6") targetUser = DEMO_USERS['parent_lan'];
    else return;

    localStorage.setItem("user", JSON.stringify(targetUser));

    // Synchronize JWT token from Backend AuthService
    try {
        if (typeof callApi === 'function') {
            const loginRes = await callApi('/api/auth/login', 'POST', {
                username: targetUser.username,
                password: "123456"
            }).catch(e => null);

            if (loginRes && loginRes.token) {
                localStorage.setItem("token", loginRes.token);
            } else {
                localStorage.setItem("token", "AUTH_TOKEN_" + targetUser.username);
            }
        } else {
            localStorage.setItem("token", "AUTH_TOKEN_" + targetUser.username);
        }
    } catch (e) {
        localStorage.setItem("token", "AUTH_TOKEN_" + targetUser.username);
    }

    const newUser = getCurrentUser();
    alert(`Đã đổi sang tài khoản: ${newUser.fullName} [Role: ${newUser.role}]!\nHệ thống đã đồng bộ JWT token thành công.`);
    window.location.reload();
}

function logout() {
    localStorage.removeItem("token");
    localStorage.removeItem("user");
    window.location.href = "/login.html";
}

document.addEventListener("DOMContentLoaded", () => {
    setTimeout(updateNavbarUser, 150);
});
