const loginForm = document.getElementById('loginForm');
const passwordInput = document.getElementById('password');
const toggleButton = document.getElementById('togglePasswordBtn');
toggleButton.addEventListener('click', () => {
    const show = passwordInput.type === 'password';
    passwordInput.type = show ? 'text' : 'password';
    toggleButton.setAttribute('aria-pressed', String(show));
    toggleButton.setAttribute('aria-label', show ? 'Ẩn mật khẩu' : 'Hiện mật khẩu');
    document.getElementById('passwordEyeIcon').className = show ? 'bi bi-eye-slash' : 'bi bi-eye';
});
['keydown', 'keyup'].forEach(type => passwordInput.addEventListener(type, event => {
    document.getElementById('capsLockHint').hidden = !event.getModifierState('CapsLock');
}));
passwordInput.addEventListener('blur', () => document.getElementById('capsLockHint').hidden = true);
loginForm.addEventListener('submit', async event => {
    event.preventDefault();
    const button = document.getElementById('submitBtn');
    if (button.disabled) return;
    const error = document.getElementById('errorAlert');
    const username = document.getElementById('username').value.trim();
    error.hidden = true;
    if (!username) { document.getElementById('username').focus(); return; }
    button.disabled = true;
    loginForm.setAttribute('aria-busy', 'true');
    button.textContent = 'Đang đăng nhập…';
    let signedIn = false;
    try {
        const response = await fetch(BASE_URL + '/api/auth/login', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password: passwordInput.value }),
            signal: AbortSignal.timeout(20000)
        });
        if (!response.ok) throw new Error(response.status === 401 || response.status === 403 ? 'Tên đăng nhập hoặc mật khẩu chưa đúng. Vui lòng kiểm tra lại.' : 'Hiện chưa thể đăng nhập. Vui lòng thử lại sau.');
        const result = await response.json();
        if (!result?.token || !result?.role) throw new Error('Thông tin đăng nhập chưa đầy đủ. Vui lòng liên hệ quản trị viên.');
        localStorage.setItem('token', result.token);
        localStorage.setItem('user', JSON.stringify(result));
        signedIn = true;
        window.location.href = './index.html';
    } catch (err) {
        document.getElementById('errorMsg').textContent = err.name === 'TimeoutError' ? 'Kết nối quá lâu. Vui lòng thử lại.' : err instanceof TypeError ? 'Không thể kết nối đến hệ thống. Vui lòng kiểm tra kết nối và thử lại.' : err.message;
        error.hidden = false;
    } finally {
        if (!signedIn) { button.disabled = false; button.innerHTML = 'Đăng nhập <i class="bi bi-arrow-right"></i>'; }
        loginForm.removeAttribute('aria-busy');
    }
});
