if (checkAuth()) {
    const user = getCurrentUser();
    if (user.mustChangePassword) {
        document.getElementById('passwordContext').textContent = 'Tài khoản của bạn cần đổi mật khẩu trước khi tiếp tục sử dụng hệ thống.';
        document.getElementById('backHome').hidden = true;
    }
    document.getElementById('passwordLogout').addEventListener('click', logout);
    document.getElementById('passwordForm').addEventListener('submit', async event => {
        event.preventDefault();
        const message = document.getElementById('passwordMessage'); message.hidden = true;
        const button = document.getElementById('savePassword');
        const password = document.getElementById('newPassword').value;
        if (password !== document.getElementById('confirmPassword').value) {
            message.textContent = 'Hai mật khẩu mới chưa khớp.'; message.hidden = false; return;
        }
        button.disabled = true;
        try {
            const result = await callApi('/api/auth/change-password', 'POST', { currentPassword: document.getElementById('currentPassword').value, newPassword: password }, false, false);
            localStorage.setItem('token', result.token); localStorage.setItem('user', JSON.stringify(result));
            window.location.replace(appPath('/index.html'));
        } catch (error) { message.textContent = error.message; message.hidden = false; button.disabled = false; }
    });
}
