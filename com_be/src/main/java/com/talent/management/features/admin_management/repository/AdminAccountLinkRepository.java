package com.talent.management.features.admin_management.repository;

import com.talent.management.features.admin_management.dto.response.AdminUserDetailResponse.LinkedRecord;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class AdminAccountLinkRepository {
    @PersistenceContext private EntityManager em;

    public List<LinkedRecord> classes(Long id) { return links("select c.id, c.className from ClassEntity c where c.teacher.id = :id order by c.id", id); }
    public List<LinkedRecord> children(Long id) { return links("select s.id, s.fullName from Student s where s.parent.id = :id order by s.id", id); }
    public List<LinkedRecord> studentProfiles(Long id) { return links("select s.id, s.fullName from Student s where s.account.id = :id order by s.id", id); }

    public boolean hasTeacherAssignments(Long id) {
        return count("select count(c) from ClassEntity c where c.teacher.id = :id", id) > 0
                || count("select count(l) from Lesson l where l.teacher.id = :id", id) > 0
                || count("select count(t) from TeacherAvailability t where t.teacher.id = :id", id) > 0
                || count("select count(p) from PlacementSchedule p where p.evaluatedBy.id = :id", id) > 0;
    }
    private long count(String query, Long id) { return em.createQuery(query, Long.class).setParameter("id", id).getSingleResult(); }
    private List<LinkedRecord> links(String query, Long id) {
        return em.createQuery(query, Object[].class).setParameter("id", id).getResultList().stream()
                .map(r -> new LinkedRecord((Long) r[0], (String) r[1])).toList();
    }
}
