package com.example.policy.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PolicyRevisionRepository extends JpaRepository<PolicyRevisionEntity, Long> {
    Optional<PolicyRevisionEntity> findByPolicyIdAndRevisionNo(long policyId, long revisionNo);
}
