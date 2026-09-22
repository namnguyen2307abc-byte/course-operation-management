function checkAuth() {
    const token = localStorage.getItem("token");
    if (!token && !window.location.pathname.endsWith("login.html")) {
    }
}

function sanitizeUserData(user) {
    if (!user) return null;
    const nameMap = {
        'admin': 'Quản Trị Viên Hệ Thống',
        'teacher_huong': 'Cô Vũ Thu Hương (GV Piano)',
        'teacher_tuan': 'Thầy Trần Anh Tuấn (GV Guitar)',
        'cashier_mai': 'Nguyễn Thanh Mai (Thu Ngân)',
        'parent_lan': 'Phụ Huynh Lê Thị Lan'
    };

    if (user.username && nameMap[user.username]) {
        if (!user.fullName || user.fullName.includes("Ã") || user.fullName.includes("Â") || user.fullName.includes("áº") || user.fullName.includes("á»") || user.fullName.includes("Ă")) {
            user.fullName = nameMap[user.username];
            localStorage.setItem("user", JSON.stringify(user));
        }
    }
    return user;
}

function getCurrentUser() {
    try {
        const userStr = localStorage.getItem("user");
        if (!userStr) return null;
        let user = JSON.parse(userStr);
        return sanitizeUserData(user);
    } catch (e) {
        return null;
    }
}

function updateNavbarUser() {
    const user = getCurrentUser();
    const displayEl = document.getElementById("userNameDisplay");
    if (displayEl) {
        if (user && user.fullName) {
            let roleBadge = "bg-danger";
            let roleName = user.role || "USER";
            if (roleName === "ADMIN") roleBadge = "bg-danger";
            else if (roleName === "TEACHER") roleBadge = "bg-warning text-dark";
            else if (roleName === "PARENT") roleBadge = "bg-success";
            else if (roleName === "STAFF") roleBadge = "bg-info text-dark";

            displayEl.innerHTML = `
                <span class="badge ${roleBadge} me-2" style="font-size: 0.72rem;">${roleName}</span>
                <span class="fw-semibold text-white">${user.fullName}</span>
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

function logout() {
    localStorage.removeItem("token");
    localStorage.removeItem("user");
    window.location.href = "/login.html";
}

document.addEventListener("DOMContentLoaded", () => {
    setTimeout(updateNavbarUser, 150);
});
