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
                        .fullName("Cô Vũ Thu Hương (GV Đàn)")
                        .email("huong.vu@talent.edu.vn")
                        .phone("0988888888")
                        .role(Role.TEACHER)
                        .subject("DAN")
                        .status(UserStatus.ACTIVE)
                        .build())
            );

            if (userRepository.findByUsername("teacher_tuan").isEmpty()) {
                userRepository.save(User.builder()
                        .username("teacher_tuan")
                        .password(passwordEncoder.encode("123456"))
                        .fullName("Thầy Trần Anh Tuấn (GV Võ)")
                        .email("tuan.tran@talent.edu.vn")
                        .phone("0977777777")
                        .role(Role.TEACHER)
                        .subject("VO")
                        .status(UserStatus.ACTIVE)
                        .build());
            }

            User teacherHung = userRepository.findByUsername("teacher_hung").orElseGet(() ->
                userRepository.save(User.builder()
                        .username("teacher_hung")
                        .password(passwordEncoder.encode("123456"))
                        .fullName("Cô Nguyễn Mai Phương (GV Múa)")
                        .email("phuong.nguyen@talent.com")
                        .phone("0988888888")
                        .role(Role.TEACHER)
                        .subject("MUA")
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
                // 1. Ca CHƯA đánh giá (SCHEDULED) - Bộ Môn Đàn
                scheduleRepository.save(PlacementSchedule.builder()
                        .parent(parentLan)
                        .studentName("Nguyễn Hoàng Anh")
                        .title("Đánh Giá Năng Khiếu Đàn Đầu Vào")
                        .roomName("Phòng Đàn 101")
                        .branch("Cơ sở 1 - Cầu Giấy")
                        .subject("DAN")
                        .testDate(LocalDateTime.now().plusDays(1).withHour(9).withMinute(30))
                        .note("Học viên 10 tuổi, đã tự tập organ 6 tháng ở nhà.")
                        .status(PlacementScheduleStatus.SCHEDULED)
                        .build());

                // 2. Ca ĐÃ đánh giá (COMPLETED) - Bộ Môn Múa
                scheduleRepository.save(PlacementSchedule.builder()
                        .parent(parentLan)
                        .studentName("Trần Bảo Ngọc")
                        .title("Khảo Sát Thể Lực & Năng Khiếu Múa Nghệ Thuật")
                        .roomName("Phòng Múa Nghệ Thuật 202")
                        .branch("Cơ sở 2 - Đống Đa")
                        .subject("MUA")
                        .testDate(LocalDateTime.now().minusDays(1).withHour(15).withMinute(0))
                        .note("Độ dẻo dai cơ thể tốt, cảm thụ âm nhạc nhịp điệu nhanh.")
                        .status(PlacementScheduleStatus.COMPLETED)
                        .score(88)
                        .recommendedLevel(PlacementLevel.INTERMEDIATE)
                        .teacherNote("Độ mở khớp dẻo tốt (9/10), giữ thăng bằng vững. Khuyến nghị xếp lớp Múa Trung Cấp 1.")
                        .evaluatedBy(teacherHung)
                        .evaluatedAt(LocalDateTime.now().minusDays(1).withHour(15).withMinute(45))
                        .build());

                // 3. Ca CHƯA đánh giá (SCHEDULED) - Bộ Môn Võ
                scheduleRepository.save(PlacementSchedule.builder()
                        .parent(parentLan)
                        .studentName("Phạm Minh Đức")
                        .title("Khảo Sát Thể Lực & Phản Xạ Võ Thuật")
                        .roomName("Võ Đường & Thể Lực 203")
                        .branch("Cơ sở 1 - Cầu Giấy")
                        .subject("VO")
                        .testDate(LocalDateTime.now().plusDays(2).withHour(17).withMinute(30))
                        .note("Quan tâm đến lớp Võ tự vệ và rèn luyện thể lực kỷ luật.")
                        .status(PlacementScheduleStatus.SCHEDULED)
                        .build());

                // 4. Ca ĐÃ đánh giá (COMPLETED) - Bộ Môn Đàn
                scheduleRepository.save(PlacementSchedule.builder()
                        .parent(parentLan)
                        .studentName("Lê Hoàng Yến")
                        .title("Kiểm Tra Trình Độ Phím Đàn Chuyên Sâu")
                        .roomName("Phòng Hòa Tấu & Phím Đàn 301")
                        .branch("Cơ sở 2 - Đống Đa")
                        .subject("DAN")
                        .testDate(LocalDateTime.now().minusDays(3).withHour(10).withMinute(0))
                        .note("Học viên 12 tuổi, có nhạc cảm tốt và đọc bản nhạc nhanh.")
                        .status(PlacementScheduleStatus.COMPLETED)
                        .score(92)
                        .recommendedLevel(PlacementLevel.ADVANCED)
                        .teacherNote("Kỹ thuật phím đàn vững vàng, thị tấu tốt. Khuyến nghị xếp lớp Đàn Nâng Cao (Advanced).")
                        .evaluatedBy(teacherHuong)
                        .evaluatedAt(LocalDateTime.now().minusDays(3).withHour(11).withMinute(0))
                        .build());
            }
        };
    }
}
