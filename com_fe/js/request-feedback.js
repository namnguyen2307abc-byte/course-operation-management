// Show actionable validation messages, while keeping transport/server details in logs.
function requestErrorMessage(error, fallback) {
    if (error?.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
    if (error?.status === 403) return 'Bạn không có quyền thực hiện thao tác này.';
    if ([400, 404, 409, 422].includes(error?.status) && error.message) return error.message;
    return fallback;
}
