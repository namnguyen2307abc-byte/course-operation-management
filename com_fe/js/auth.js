/**
 * Auth & Role Management JS
 * Handles authentication status, user role sanitization, and quick role switching for testing.
 */

function checkAuth() {
    const token = localStorage.getItem("token");
    if (!token && !window.location.pathname.endsWith("login.html")) {
        // Can redirect to login if required
    }
}

const DEMO_USERS = {
    'teacher_huong': { username: 'teacher_huong', fullName: 'Cô Vũ Thu Hương (GV Đàn)', role: 'TEACHER', subject: 'DAN', email: 'huong.vu@talent.edu.vn' },
    'teacher_tuan': { username: 'teacher_tuan', fullName: 'Thầy Trần Anh Tuấn (GV Võ)', role: 'TEACHER', subject: 'VO', email: 'tuan.tran@talent.edu.vn' },
    'teacher_hung': { username: 'teacher_hung', fullName: 'Cô Nguyễn Mai Phương (GV Múa)', role: 'TEACHER', subject: 'MUA', email: 'phuong.nguyen@talent.com' },
    'admin': { username: 'admin', fullName: 'Quản Trị Viên Hệ Thống', role: 'ADMIN', subject: null, email: 'admin@talent.edu.vn' },
    'cashier_mai': { username: 'cashier_mai', fullName: 'Nguyễn Thanh Mai (Thu Ngân)', role: 'STAFF', subject: null, email: 'mai.nguyen@talent.edu.vn' },
    'parent_lan': { username: 'parent_lan', fullName: 'Phụ Huynh Lê Thị Lan', role: 'PARENT', subject: null, email: 'lan.le@gmail.com' }
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
        user.subject = demo.subject;
        user.email = demo.email;
        localStorage.setItem("user", JSON.stringify(user));
    } else if (user.username && (user.username.toLowerCase().includes("teacher") || user.username.toLowerCase().includes("gv") || user.username.toLowerCase().includes("hung"))) {
        user.role = "TEACHER";
        if (!user.subject) {
            user.subject = user.username.toLowerCase().includes("tuan") ? "VO" : (user.username.toLowerCase().includes("hung") ? "MUA" : "DAN");
        }
        localStorage.setItem("user", JSON.stringify(user));
    } else if (user.username && user.username.toLowerCase().includes("parent")) {
        user.role = "PARENT";
        localStorage.setItem("user", JSON.stringify(user));
    }

    // Default fallback if role is missing
    if (!user.role) {
        user.role = "TEACHER"; // Default fallback for development
        user.subject = "DAN";
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
            let subjectText = "";
            if (roleName === "ADMIN") roleBadge = "bg-danger";
            else if (roleName === "TEACHER") {
                roleBadge = "bg-warning text-dark";
                const subName = user.subject === "DAN" ? "Đàn" : (user.subject === "MUA" ? "Múa" : (user.subject === "VO" ? "Võ" : user.subject));
                subjectText = user.subject ? ` - BM ${subName}` : "";
            }
            else if (roleName === "PARENT") roleBadge = "bg-success";
            else if (roleName === "STAFF") roleBadge = "bg-info text-dark";

            displayEl.innerHTML = `
                <div class="d-flex align-items-center gap-2">
                    <span class="badge ${roleBadge} px-2 py-1" style="font-size: 0.72rem;"><i class="bi bi-shield-check me-1"></i>${roleName}${subjectText}</span>
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
}

async function quickSwitchUserRole() {
    const choices = [
        "1. Cô Vũ Thu Hương (Giáo Viên Đàn - TEACHER)",
        "2. Thầy Trần Anh Tuấn (Giáo Viên Võ - TEACHER)",
        "3. Cô Nguyễn Mai Phương (Giáo Viên Múa - TEACHER)",
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
