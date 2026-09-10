package com.example.courseoperationmanagement.repository;

import com.example.courseoperationmanagement.entity.Role;
import com.example.courseoperationmanagement.entity.User;
import com.example.courseoperationmanagement.entity.UserStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("""
            select u
            from User u
            where (:keyword is null
                or :keyword = ''
                or lower(u.fullName) like lower(concat('%', :keyword, '%'))
                or lower(u.email) like lower(concat('%', :keyword, '%')))
              and (:role is null or u.role = :role)
              and (:status is null or u.status = :status)
            """)
    Page<User> searchUsers(
            @Param("keyword") String keyword,
            @Param("role") Role role,
            @Param("status") UserStatus status,
            Pageable pageable);
}
