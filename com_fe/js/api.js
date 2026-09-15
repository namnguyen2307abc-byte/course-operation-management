

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

        if (!response.ok) {
            const errData = await response.json().catch(() => ({}));
            throw new Error(errData.message || `Lỗi yêu cầu: ${response.status}`);
        }

        
        const text = await response.text();
        return text ? JSON.parse(text) : null;
    } catch (error) {
        console.error("API Error:", error);
        alert(error.message);
        throw error;
    }
}
