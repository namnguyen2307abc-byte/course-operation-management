package com.talent.management.shared.config;

import com.talent.management.features.auth.repository.UserRepository;
import com.talent.management.features.placement_test.entity.PlacementLevel;
import com.talent.management.features.placement_test.entity.PlacementSchedule;
import com.talent.management.features.placement_test.entity.PlacementScheduleStatus;
import com.talent.management.features.placement_test.repository.PlacementScheduleRepository;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.enums.Role;
import com.talent.management.shared.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final UserRepository userRepository;
    private final PlacementScheduleRepository scheduleRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public CommandLineRunner initDefaultUsers() {
        return args -> {
            User parentLan = userRepository.findByUsername("parent_lan").orElseGet(() -> 
                userRepository.save(User.builder()
                        .username("parent_lan")
                        .password(passwordEncoder.encode("123456"))
                        .fullName("Phụ huynh Lê Thị Lan")
                        .email("lan.le@gmail.com")
                        .phone("0901234567")
                        .role(Role.PARENT)
                        .status(UserStatus.ACTIVE)
                        .build())
            );

            if (userRepository.findByUsername("admin").isEmpty()) {
                userRepository.save(User.builder()
                        .username("admin")
                        .password(passwordEncoder.encode("123456"))
                        .fullName("Quản Trị Viên Hệ Thống")
                        .email("admin@talent.com")
                        .phone("0999999999")
                        .role(Role.ADMIN)
                        .status(UserStatus.ACTIVE)
                        .build());
            }

            User teacherHuong = userRepository.findByUsername("teacher_huong").orElseGet(() ->
                userRepository.save(User.builder()
                        .username("teacher_huong")
                        .password(passwordEncoder.encode("123456"))
                        .fullName("Cô Vũ Thu Hương (GV Piano)")
                        .email("huong.vu@talent.edu.vn")
                        .phone("0988888888")
                        .role(Role.TEACHER)
                        .status(UserStatus.ACTIVE)
                        .build())
            );

            if (userRepository.findByUsername("teacher_tuan").isEmpty()) {
                userRepository.save(User.builder()
                        .username("teacher_tuan")
                        .password(passwordEncoder.encode("123456"))
                        .fullName("Thầy Trần Anh Tuấn (GV Guitar)")
                        .email("tuan.tran@talent.edu.vn")
                        .phone("0977777777")
                        .role(Role.TEACHER)
                        .status(UserStatus.ACTIVE)
                        .build());
            }

            User teacherHung = userRepository.findByUsername("teacher_hung").orElseGet(() ->
                userRepository.save(User.builder()
                        .username("teacher_hung")
                        .password(passwordEncoder.encode("123456"))
                        .fullName("Giáo viên Trần Văn Hùng")
                        .email("hung.tran@talent.com")
                        .phone("0988888888")
                        .role(Role.TEACHER)
                        .status(UserStatus.ACTIVE)
                        .build())
            );

            User teacherLinh = userRepository.findByUsername("teacher_linh").orElseGet(() ->
                userRepository.save(User.builder()
                        .username("teacher_linh")
                        .password(passwordEncoder.encode("123456"))
                        .fullName("Cô Phạm Khánh Linh (GV Múa)")
                        .email("linh.pham@talentcenter.edu.vn")
                        .phone("0933445566")
                        .role(Role.TEACHER)
                        .status(UserStatus.ACTIVE)
                        .build())
            );

            User teacherLong = userRepository.findByUsername("teacher_long").orElseGet(() ->
                userRepository.save(User.builder()
                        .username("teacher_long")
                        .password(passwordEncoder.encode("123456"))
                        .fullName("Thầy Hoàng Phi Long (GV Võ Thuật)")
                        .email("long.hoang@talentcenter.edu.vn")
                        .phone("0944556677")
                        .role(Role.TEACHER)
                        .status(UserStatus.ACTIVE)
                        .build())
            );

            if (userRepository.findByUsername("cashier_mai").isEmpty()) {
                userRepository.save(User.builder()
                        .username("cashier_mai")
                        .password(passwordEncoder.encode("123456"))
                        .fullName("Nguyễn Thanh Mai (Thu Ngân)")
                        .email("mai.nguyen@talent.edu.vn")
                        .phone("0966666666")
                        .role(Role.STAFF)
                        .status(UserStatus.ACTIVE)
                        .build());
            }

            // Seed Sample Placement Test Schedules if table is empty
            if (scheduleRepository.count() == 0) {
                // 1. Ca Piano CHƯA đánh giá (SCHEDULED)
                scheduleRepository.save(PlacementSchedule.builder()
                        .parent(parentLan)
                        .studentName("Nguyễn Hoàng Anh")
                        .title("Đánh Giá Năng Khiếu Piano Đầu Vào")
                        .roomName("Phòng Piano 101")
                        .branch("Cơ sở 1 - Cầu Giấy")
                        .testDate(LocalDateTime.now().plusDays(1).withHour(9).withMinute(30))
                        .note("Học viên 10 tuổi, đã tự tập organ 6 tháng ở nhà.")
                        .status(PlacementScheduleStatus.SCHEDULED)
                        .build());

                // 2. Ca Piano ĐÃ đánh giá (COMPLETED)
                scheduleRepository.save(PlacementSchedule.builder()
                        .parent(parentLan)
                        .studentName("Trần Bảo Ngọc")
                        .title("Kiểm Tra Trình Độ Piano Chuyên Sâu")
                        .roomName("Phòng Piano Grand 202")
                        .branch("Cơ sở 2 - Đống Đa")
                        .testDate(LocalDateTime.now().minusDays(1).withHour(15).withMinute(0))
                        .note("Có năng khiếu nổi trội, tai nghe cảm âm chuẩn.")
                        .status(PlacementScheduleStatus.COMPLETED)
                        .score(88)
                        .recommendedLevel(PlacementLevel.INTERMEDIATE)
                        .teacherNote("Tiêu chí Piano: Cảm âm (9.0), Nhịp phách (8.5), Kỹ thuật ngón (9.0), Thị tấu (8.5). Khuyến nghị xếp lớp Piano Intermediate 1.")
                        .evaluatedBy(teacherHuong)
                        .evaluatedAt(LocalDateTime.now().minusDays(1).withHour(15).withMinute(45))
                        .build());

                // 3. Ca MÚA & BALLET ĐÃ đánh giá (COMPLETED)
                scheduleRepository.save(PlacementSchedule.builder()
                        .parent(parentLan)
                        .studentName("Đỗ Ngọc Hân")
                        .title("Khảo Sát Độ Dẻo & Cảm Thụ Âm Nhạc Múa Ballet")
                        .roomName("Phòng Tập Múa & Ballet 103")
                        .branch("Cơ sở 1 - Cầu Giấy")
                        .testDate(LocalDateTime.now().minusDays(2).withHour(10).withMinute(0))
                        .note("Bé 5 tuổi, thích nhảy múa theo nhạc thiếu nhi.")
                        .status(PlacementScheduleStatus.COMPLETED)
                        .score(89)
                        .recommendedLevel(PlacementLevel.BEGINNER)
                        .teacherNote("Tiêu chí Múa: Độ dẻo & Uyển chuyển (9.5/10), Cảm thụ âm nhạc (8.5/10), Phom dáng & Tư thế (8.5/10), Thần thái biểu diễn (9.0/10). Cơ thể dẻo bẩm sinh, ép dẻo 180 độ tốt. Khuyên xếp lớp Múa Thiếu Nhi & Ballet Căn Bản (DAN-KIDS).")
                        .videoUrl("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4")
                        .evaluatedBy(teacherLinh)
                        .evaluatedAt(LocalDateTime.now().minusDays(2).withHour(11).withMinute(0))
                        .build());

                // 4. Ca VÕ THUẬT ĐÃ đánh giá (COMPLETED)
                scheduleRepository.save(PlacementSchedule.builder()
                        .parent(parentLan)
                        .studentName("Vũ Tuấn Kiệt")
                        .title("Kiểm Tra Thể Lực, Tấn Pháp & Phản Xạ Võ Thuật")
                        .roomName("Võ Đường & Thể Lực 203")
                        .branch("Cơ sở 2 - Đống Đa")
                        .testDate(LocalDateTime.now().minusDays(1).withHour(16).withMinute(30))
                        .note("Học viên 9 tuổi, mong muốn rèn luyện thể lực và tự vệ.")
                        .status(PlacementScheduleStatus.COMPLETED)
                        .score(89)
                        .recommendedLevel(PlacementLevel.BEGINNER)
                        .teacherNote("Tiêu chí Võ Thuật: Thể lực & Sức bền (9.0/10), Tấn pháp & Kỹ thuật đòn (8.5/10), Tốc độ & Phản xạ (8.5/10), Kỷ luật & Tinh thần võ đạo (9.5/10). Tấn pháp trung bình tấn rất vững, bật nhảy tốt. Đề xuất lớp Võ Thuật Nhập Môn - Đai Trắng (MA-BASIC).")
                        .videoUrl("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4")
                        .evaluatedBy(teacherLong)
                        .evaluatedAt(LocalDateTime.now().minusDays(1).withHour(17).withMinute(30))
                        .build());

                // 5. Ca MÚA CHƯA đánh giá (SCHEDULED)
                scheduleRepository.save(PlacementSchedule.builder()
                        .parent(parentLan)
                        .studentName("Nguyễn Mai Chi")
                        .title("Đánh Giá Năng Khiếu Múa & Cảm Xúc Hình Thể")
                        .roomName("Phòng Tập Múa & Ballet 103")
                        .branch("Cơ sở 1 - Cầu Giấy")
                        .testDate(LocalDateTime.now().plusDays(2).withHour(15).withMinute(0))
                        .note("Bé làm quen với bộ môn múa đương đại thiếu nhi.")
                        .status(PlacementScheduleStatus.SCHEDULED)
                        .build());

                // 6. Ca VÕ THUẬT CHƯA đánh giá (SCHEDULED)
                scheduleRepository.save(PlacementSchedule.builder()
                        .parent(parentLan)
                        .studentName("Phạm Minh Đức")
                        .title("Khảo Sát Thể Lực & Phản Xạ Võ Tự Vệ")
                        .roomName("Võ Đường & Thể Lực 203")
                        .branch("Cơ sở 2 - Đống Đa")
                        .testDate(LocalDateTime.now().plusDays(3).withHour(17).withMinute(30))
                        .note("Rèn luyện sự tập trung và thể lực.")
                        .status(PlacementScheduleStatus.SCHEDULED)
                        .build());
            }
        };
    }
}

