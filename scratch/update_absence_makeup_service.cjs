const fs = require('fs');
const file = 'com_be/src/main/java/com/talent/management/features/attendance_makeup/service/impl/AbsenceMakeupServiceImpl.java';
let content = fs.readFileSync(file, 'utf8');
const isCrlf = content.includes('\r\n');
const nl = isCrlf ? '\r\n' : '\n';

// 1. Cập nhật getMakeupRegistrations cho TEACHER
const teacherBranch = `        } else if (currentUser.getRole() == Role.TEACHER) {
            // Đối với GIÁO VIÊN: Lấy các ca bù mà giáo viên phụ trách lớp gốc (học viên lớp mình đi bù)
            // HOẶC giáo viên phụ trách lớp bù mục tiêu (học viên ngoài đến lớp mình bù)
            List<MakeupRegistration> all = (status != null)
                    ? makeupRegistrationRepository.findByStatusOrderByCreatedAtDesc(status)
                    : makeupRegistrationRepository.findAllByOrderByCreatedAtDesc();
            Long tid = currentUser.getId();
            list = all.stream().filter(mr -> {
                boolean isOrigTeacher = mr.getOriginalLesson() != null
                        && mr.getOriginalLesson().getTeacher() != null
                        && mr.getOriginalLesson().getTeacher().getId().equals(tid);
                boolean isTargetTeacher = mr.getTargetLesson() != null
                        && mr.getTargetLesson().getTeacher() != null
                        && mr.getTargetLesson().getTeacher().getId().equals(tid);
                return isOrigTeacher || isTargetTeacher;
            }).toList();
        } else {`;

content = content.replace(/\r?\n\s+} else {\r?\n\s+list = \(status != null\)/, nl + teacherBranch + nl + '            list = (status != null)');

// 2. Cập nhật scheduleMakeup để gửi notification
const notificationSnippet = `        // GỬI THÔNG BÁO TỰ ĐỘNG KHI ĐIỀU PHỐI HỌC BÙ
        if (notificationService != null) {
            Lesson origLesson = saved.getOriginalLesson();
            Student student = saved.getStudent();
            String studentName = (student != null) ? student.getFullName() : "Học viên";
            String targetDateStr = (targetLesson.getLessonDate() != null)
                    ? targetLesson.getLessonDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "";
            String targetTimeStr = (targetLesson.getStartTime() != null && targetLesson.getEndTime() != null)
                    ? targetLesson.getStartTime().format(TIME_FMT) + " - " + targetLesson.getEndTime().format(TIME_FMT) : "";
            String targetClassName = (targetLesson.getClassEntity() != null)
                    ? targetLesson.getClassEntity().getClassName()
                    : (targetLesson.getTitle() != null ? targetLesson.getTitle() : "Lớp học bù");
            String targetTeacherName = (targetLesson.getTeacher() != null)
                    ? targetLesson.getTeacher().getFullName() : "Giáo viên trung tâm";
            String targetRoomName = (targetLesson.getRoom() != null)
                    ? targetLesson.getRoom().getRoomName() : "Phòng học";
            String origClassName = (origLesson != null && origLesson.getClassEntity() != null)
                    ? origLesson.getClassEntity().getClassName() : "lớp phụ trách";

            // 1. Thông báo cho GIÁO VIÊN LỚP GỐC: Biết học viên lớp mình được chuyển sang lớp nào
            if (origLesson != null && origLesson.getTeacher() != null) {
                User origTeacher = origLesson.getTeacher();
                String title = "Học viên điều phối học bù: " + studentName;
                String message = String.format("Học viên %s (lớp %s) đã được xếp lịch học bù sang lớp %s vào ngày %s (%s, Phòng %s, GV: %s).",
                        studentName, origClassName, targetClassName, targetDateStr, targetTimeStr, targetRoomName, targetTeacherName);
                notificationService.sendNotification(origTeacher, currentUser, title, message, "MAKEUP_COORDINATED_OUT", saved.getId(), "/pages/absence-makeup.html");
            }

            // 2. Thông báo cho GIÁO VIÊN LỚP HỌC BÙ (Lớp nhận bù): Biết sắp tới có học viên ngoài vào lớp
            if (targetLesson.getTeacher() != null) {
                User targetTeacher = targetLesson.getTeacher();
                String title = "Có học viên học bù vào lớp bạn: " + studentName;
                String message = String.format("Buổi học ngày %s (%s, lớp %s, Phòng %s) của bạn sẽ có học viên %s (từ lớp %s) đến học bù.",
                        targetDateStr, targetTimeStr, targetClassName, targetRoomName, studentName, origClassName);
                notificationService.sendNotification(targetTeacher, currentUser, title, message, "MAKEUP_COORDINATED_IN", saved.getId(), "/pages/absence-makeup.html");
            }

            // 3. Thông báo cho PHỤ HUYNH HỌC VIÊN: Nắm lịch học bù mới của con
            if (student != null && student.getParent() != null) {
                User parent = student.getParent();
                String title = "Đã xếp lịch học bù cho bé " + studentName;
                String message = String.format("Bé %s đã được xếp lịch học bù tại lớp %s vào ngày %s (ca %s, Phòng %s, GV: %s).",
                        studentName, targetClassName, targetDateStr, targetTimeStr, targetRoomName, targetTeacherName);
                notificationService.sendNotification(parent, currentUser, title, message, "MAKEUP_SCHEDULED", saved.getId(), "/pages/absence-makeup.html");
            }
        }`.replace(/\n/g, nl);

content = content.replace(/(log\.info\("Ca bù #\{\} xếp lịch[^\n]+\r?\n\s+[^\n]+\r?\n\s+[^\n]+\r?\n)(\s+return attendanceMakeupMapper\.toMakeupResponse\(saved\);)/,
    `$1${nl}${notificationSnippet}${nl}$2`);

fs.writeFileSync(file, content, 'utf8');
console.log('Successfully updated AbsenceMakeupServiceImpl.java');
