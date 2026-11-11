package com.talent.management.features.admin_management.repository;
import com.talent.management.shared.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.List;

public interface AdminUserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    // Lock the same ordered admin set before any account mutation. Concurrent demotions cannot remove the last admin.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.role = com.talent.management.shared.enums.Role.ADMIN order by u.id")
    List<User> lockAdministrators();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    java.util.Optional<User> lockById(@org.springframework.data.repository.query.Param("id") Long id);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
}
