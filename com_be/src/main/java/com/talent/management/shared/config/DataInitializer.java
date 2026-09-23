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
                // 1. Ca CHƯA đánh giá (SCHEDULED)
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

                // 2. Ca ĐÃ đánh giá (COMPLETED)
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
                        .teacherNote("Năng khiếu cảm âm vượt trội (9/10), nhịp điệu vững. Khuyến nghị xếp lớp Piano Intermediate 1.")
                        .evaluatedBy(teacherHuong)
                        .evaluatedAt(LocalDateTime.now().minusDays(1).withHour(15).withMinute(45))
                        .build());

                // 3. Ca CHƯA đánh giá (SCHEDULED)
                scheduleRepository.save(PlacementSchedule.builder()
                        .parent(parentLan)
                        .studentName("Phạm Minh Đức")
                        .title("Đánh Giá Khả Năng Cảm Âm & Nhịp Điệu Guitar")
                        .roomName("Phòng Hòa Tấu 1")
                        .branch("Cơ sở 1 - Cầu Giấy")
                        .testDate(LocalDateTime.now().plusDays(2).withHour(17).withMinute(30))
                        .note("Quan tâm đến Guitar Acoustic.")
                        .status(PlacementScheduleStatus.SCHEDULED)
                        .build());

                // 4. Ca ĐÃ đánh giá (COMPLETED)
                scheduleRepository.save(PlacementSchedule.builder()
                        .parent(parentLan)
                        .studentName("Lê Hoàng Yến")
                        .title("Khảo Sát Năng Khiếu Thanh Nhạc & Phím Đàn")
                        .roomName("Studio Âm Nhạc 301")
                        .branch("Cơ sở 2 - Đống Đa")
                        .testDate(LocalDateTime.now().minusDays(3).withHour(10).withMinute(0))
                        .note("Học viên 12 tuổi, có tố chất chất giọng truyền cảm.")
                        .status(PlacementScheduleStatus.COMPLETED)
                        .score(92)
                        .recommendedLevel(PlacementLevel.ADVANCED)
                        .teacherNote("Kỹ thuật thanh nhạc cơ bản tốt, đọc bản nhạc nhanh. Khuyến nghị xếp lớp Nâng Cao (Advanced).")
                        .evaluatedBy(teacherHung)
                        .evaluatedAt(LocalDateTime.now().minusDays(3).withHour(11).withMinute(0))
                        .build());
            }
        };
    }
}
