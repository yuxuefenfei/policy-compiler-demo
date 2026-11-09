package com.example.policy.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface PolicyRepository extends JpaRepository<PolicyEntity, Long> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PolicyEntity p set p.name = :name, p.draftRevision = :next, p.updatedAt = :now
            where p.id = :id and p.draftRevision = :expected
            """)
    int advanceDraft(@Param("id") long id, @Param("expected") long expected,
                     @Param("next") long next, @Param("name") String name,
                     @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PolicyEntity p set p.publishedRevision = :revision, p.updatedAt = :now
            where p.id = :id and p.draftRevision = :revision
              and (p.publishedRevision is null or p.publishedRevision <> :revision)
            """)
    int publishRevision(@Param("id") long id, @Param("revision") long revision,
                        @Param("now") Instant now);
}
