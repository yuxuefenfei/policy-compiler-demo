package com.example.policy.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "policy_revisions", uniqueConstraints =
@UniqueConstraint(name = "uq_policy_revision", columnNames = {"policy_id", "revision_no"}))
public class PolicyRevisionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "policy_id", nullable = false)
    private long policyId;
    @Column(name = "revision_no", nullable = false)
    private long revisionNo;
    @Lob
    @Column(name = "config_json", nullable = false)
    private String configJson;
    @Lob
    @Column(name = "xml_content", nullable = false)
    private String xmlContent;
    @Column(nullable = false, length = 64)
    private String sha256;
    @Column(nullable = false)
    private Instant createdAt;

    protected PolicyRevisionEntity() {
    }

    public PolicyRevisionEntity(long policyId, long revisionNo,
                                String configJson, String xmlContent, String sha256) {
        this.policyId = policyId;
        this.revisionNo = revisionNo;
        this.configJson = configJson;
        this.xmlContent = xmlContent;
        this.sha256 = sha256;
        this.createdAt = Instant.now();
    }

    public long getRevisionNo() {
        return revisionNo;
    }

    public String getConfigJson() {
        return configJson;
    }

    public String getXmlContent() {
        return xmlContent;
    }

    public String getSha256() {
        return sha256;
    }
}
