package com.talent.management.features.class_management.repository;

import com.talent.management.features.course_enrollment.repository.ClassRepository;
import com.talent.management.shared.entity.Branch;
import com.talent.management.shared.entity.ClassEntity;
import com.talent.management.shared.entity.Course;
import com.talent.management.shared.entity.Program;
import com.talent.management.shared.enums.ClassStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.show_sql=false",
        "spring.jpa.show-sql=false"
})
class ClassManagementPersistenceTest {
    @Autowired TestEntityManager entityManager;
    @Autowired ManagedClassRepository managementRepository;
    @Autowired ClassRepository enrollmentRepository;

    @Test
    void newOpenClassIsVisibleToEnrollmentRepository() {
        Program program = entityManager.persist(Program.builder().code("PIANO").name("Piano").build());
        Course course = entityManager.persist(Course.builder().program(program).code("PIA-G1")
                .name("Piano Grade 1").tuitionFee(BigDecimal.valueOf(4800000)).build());
        Branch branch = entityManager.persist(Branch.builder().code("BR-01").name("Cơ sở A")
                .address("Hà Nội").build());
        ClassEntity classEntity = entityManager.persistAndFlush(ClassEntity.builder()
                .course(course).branch(branch).classCode("PIA-G1-02").className("Piano Grade 1 - Sáng")
                .maxStudents(8).currentStudents(0).status(ClassStatus.OPEN)
                .startDate(LocalDate.of(2026, 11, 1)).endDate(LocalDate.of(2027, 2, 1)).build());
        entityManager.clear();

        assertThat(managementRepository.search("g1-02", course.getId(), branch.getId(), ClassStatus.OPEN))
                .extracting(ClassEntity::getId).containsExactly(classEntity.getId());
        assertThat(managementRepository.search("", null, null, null))
                .extracting(ClassEntity::getId).containsExactly(classEntity.getId());
        assertThat(enrollmentRepository.findByCourseIdAndStatusOrderByStartDateAsc(course.getId(), ClassStatus.OPEN))
                .extracting(ClassEntity::getId).containsExactly(classEntity.getId());
    }
}
