const BASE_URL = "http://localhost:8080";

async function callApi(endpoint, method = "GET", body = null, isFormData = false) {
    const token = localStorage.getItem("token");
    const headers = {};

    if (token) {
        headers["Authorization"] = "Bearer " + token;
    }
    if (!isFormData) {
        headers["Content-Type"] = "application/json";
    }

    const options = {
        method: method,
        headers: headers,
        body: isFormData ? body : (body ? JSON.stringify(body) : null)
    };

    try {
        const response = await fetch(BASE_URL + endpoint, options);

        if (response.status === 401) {
            alert("Phiên đăng nhập đã hết hạn hoặc chưa đăng nhập. Vui lòng đăng nhập lại!");
            localStorage.removeItem("token");
            window.location.href = "/login.html";
            return null;
        }

        if (response.status === 403) {
            let roleName = "Chưa xác định";
            try {
                const u = JSON.parse(localStorage.getItem("user") || "{}");
                if (u.role) roleName = u.role;
            } catch (e) {}
            const forbiddenMsg = `Tài khoản của bạn [${roleName}] không có quyền thực hiện chức năng này. Hệ thống sẽ chuyển bạn về Trang Chủ!`;
            showPermissionDeniedModal(forbiddenMsg, "/index.html");
            throw new Error(forbiddenMsg);
        }

        if (!response.ok) {
            const errData = await response.json().catch(() => ({}));
            throw new Error(errData.message || `Lỗi yêu cầu: ${response.status}`);
        }

        const text = await response.text();
        return text ? JSON.parse(text) : null;
    } catch (error) {
        console.error("API Error:", error);
        throw error;
    }
}

function showPermissionDeniedModal(msg, redirectUrl = "/index.html") {
    let modalEl = document.getElementById("permissionDeniedModal");
    if (!modalEl) {
        const modalHtml = `
            <div class="modal fade" id="permissionDeniedModal" data-bs-backdrop="static" data-bs-keyboard="false" tabindex="-1" aria-hidden="true">
                <div class="modal-dialog modal-dialog-centered">
                    <div class="modal-content border-0 shadow-lg rounded-4">
                        <div class="modal-header bg-danger text-white">
                            <h5 class="modal-title fw-bold"><i class="bi bi-shield-slash-fill me-2"></i>Truy Cập Bị Từ Chối (403)</h5>
                        </div>
                        <div class="modal-body p-4 text-center">
                            <div class="mb-3 text-danger">
                                <i class="bi bi-exclamation-octagon-fill display-3"></i>
                            </div>
                            <h5 class="fw-bold text-dark mb-2">Không Có Quyền Truy Cập!</h5>
                            <p class="text-muted small mb-0" id="permissionDeniedMessage">${msg}</p>
                        </div>
                        <div class="modal-footer bg-light justify-content-center">
                            <button type="button" class="btn btn-danger px-4 rounded-pill fw-bold" id="btnRedirectHome">
                                <i class="bi bi-house-door-fill me-1"></i> Quay Về Trang Chủ
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        `;
        document.body.insertAdjacentHTML("beforeend", modalHtml);
        modalEl = document.getElementById("permissionDeniedModal");
    } else {
        const msgEl = document.getElementById("permissionDeniedMessage");
        if (msgEl) msgEl.innerText = msg;
    }

    const btnHome = document.getElementById("btnRedirectHome");
    if (btnHome) {
        btnHome.onclick = () => {
            window.location.href = redirectUrl;
        };
    }

    modalEl.addEventListener('hidden.bs.modal', () => {
        window.location.href = redirectUrl;
    });

    if (typeof bootstrap !== 'undefined' && bootstrap.Modal) {
        const bsModal = new bootstrap.Modal(modalEl, { backdrop: 'static', keyboard: false });
        bsModal.show();
    } else {
        alert(msg);
        window.location.href = redirectUrl;
    }
}
