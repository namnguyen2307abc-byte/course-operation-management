checkAuth();
const homeUser = getCurrentUser();
if (homeUser) {
    document.getElementById('heroUserName').textContent = homeUser.fullName || homeUser.username;
    document.getElementById('roleContext').textContent = roleLabel(homeUser.role);
    const parent = homeUser.role === 'PARENT';
    const teacher = homeUser.role === 'TEACHER';
    document.getElementById('welcomeDescription').textContent = parent ? 'Đăng ký lớp và theo dõi việc học của con tại trung tâm.' : teacher ? 'Theo dõi học vụ và thực hiện đánh giá đầu vào cho học viên.' : 'Điều phối hoạt động đào tạo và xử lý nghiệp vụ hằng ngày.';
    document.getElementById('roleGuide').textContent = parent ? 'Vào Đăng ký & xếp lớp để chọn lớp và theo dõi yêu cầu. Khi cần nghỉ học, hãy vào Nghỉ học & học bù.' : teacher ? 'Vào Đánh giá đầu vào để xem lịch kiểm tra. Các yêu cầu nghỉ và học bù nằm trong mục Nghỉ học & học bù.' : 'Quản lý nội dung đào tạo tại Khóa học; tiếp nhận và xử lý yêu cầu tại Đăng ký & xếp lớp. Các nghiệp vụ liên quan luôn có sẵn trên thanh bên.';
    const features = visibleFeatures(homeUser);
    const grid = document.getElementById('featureGrid');
    features.forEach((feature, index) => {
        const link = document.createElement('a');
        link.className = 'workspace-feature';
        link.href = appPath('/pages/' + feature.file + '.html');
        link.innerHTML = `<span class="feature-symbol tone-${index % 4}"><i class="bi bi-${feature.icon}"></i></span><h3>${feature.label}</h3><p>${feature.description}</p><span class="feature-open">Mở chức năng <i class="bi bi-arrow-up-right"></i></span>`;
        grid.appendChild(link);
    });
    const primary = features.find(f => f.file === (teacher || parent || homeUser.role === 'STUDENT' ? 'weekly-schedule' : 'course-enrollment')) || features[0];
    if (primary) {
        const action = document.getElementById('primaryAction');
        action.href = appPath('/pages/' + primary.file + '.html');
        action.textContent = primary.label + '  →';
        action.hidden = false;
    }
}
document.getElementById('todayLabel').textContent = new Intl.DateTimeFormat('vi-VN', {weekday: 'long', day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'Asia/Ho_Chi_Minh'}).format(new Date());
fetch('./components/navbar.html').then(response => {
    if (!response.ok) throw new Error('Không tải được điều hướng');
    return response.text();
}).then(html => {
    document.getElementById('navbar-container').innerHTML = html;
    updateNavbarUser();
}).catch(() => {
    document.getElementById('navbar-container').textContent = 'Không tải được thanh điều hướng. Vui lòng tải lại trang.';
});
