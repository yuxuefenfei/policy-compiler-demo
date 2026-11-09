package com.example.policy.service;

import com.example.policy.api.PolicyException;
import com.example.policy.compiler.PolicyXmlCompiler;
import com.example.policy.domain.*;
import com.example.policy.model.PolicyModels.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class PolicyService {
    private final PolicyRepository policies;
    private final PolicyRevisionRepository revisions;
    private final OutboxRepository outbox;
    private final PolicyXmlCompiler compiler;
    private final ObjectMapper json;
    public PolicyService(PolicyRepository policies, PolicyRevisionRepository revisions,
                         OutboxRepository outbox, PolicyXmlCompiler compiler, ObjectMapper json) {
        this.policies = policies;
        this.revisions = revisions;
        this.outbox = outbox;
        this.compiler = compiler;
        this.json = json;
    }

    @Transactional
    public PolicyCreated create(CreatePolicyRequest request) {
        PolicyEntity p = policies.saveAndFlush(new PolicyEntity(request.name()));
        return new PolicyCreated(p.getId(), p.getDraftRevision());
    }

    @Transactional
    public SaveResult saveDraft(long policyId, SavePolicyRequest request) {
        PolicyEntity p = getPolicy(policyId);
        if (p.getDraftRevision() != request.expectedRevision()) {
            throw PolicyException.conflict("策略已被其他人修改，请刷新后重试");
        }
        // Compile before changing the revision. The compiler does not use old XML.
        PolicyXmlCompiler.Compiled compiled = compiler.compile(policyId, request);
        long next = request.expectedRevision() + 1;
        int updated = policies.advanceDraft(policyId, request.expectedRevision(), next,
                request.name(), Instant.now());
        if (updated != 1) throw PolicyException.conflict("策略版本冲突，请刷新后重试");

        ObjectNode configuration = json.createObjectNode();
        configuration.put("name", request.name());
        configuration.put("schemaVersion", request.schemaVersion());
        configuration.set("modules", json.valueToTree(request.modules()));
        try {
            revisions.save(new PolicyRevisionEntity(policyId, next,
                    json.writeValueAsString(configuration), compiled.xml(), compiled.sha256()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 快照序列化失败", e);
        }
        // All changes, including revision number and XML snapshot, commit atomically.
        return new SaveResult(policyId, next, compiled.sha256());
    }

    @Transactional(readOnly = true)
    public DraftResult getDraft(long policyId) {
        PolicyEntity policy = getPolicy(policyId);
        if (policy.getDraftRevision() == 0) throw PolicyException.notFound("策略草稿尚未保存");
        PolicyRevisionEntity snapshot = getSnapshot(policyId, policy.getDraftRevision());
        try {
            return new DraftResult(policyId, snapshot.getRevisionNo(), snapshot.getSha256(),
                    json.readTree(snapshot.getConfigJson()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("数据库中的策略 JSON 快照损坏", e);
        }
    }

    @Transactional
    public PublishResult publish(long policyId, PublishPolicyRequest request) {
        PolicyEntity policy = getPolicy(policyId);
        long expected = request.expectedRevision();
        if (policy.getDraftRevision() != expected) {
            throw PolicyException.conflict("发布版本必须是当前最新草稿版本");
        }
        PolicyRevisionEntity snapshot = getSnapshot(policyId, expected);
        if (policy.getPublishedRevision() != null && policy.getPublishedRevision() == expected) {
            return new PublishResult(policyId, expected, snapshot.getSha256());
        }
        int changed = policies.publishRevision(policyId, expected, Instant.now());
        if (changed != 1) throw PolicyException.conflict("发布时发生版本冲突");
        // Outbox event is inserted in the SAME DB transaction as the published pointer.
        String eventKey = policyId + ":" + expected;
        String payload = "{\"policyId\":" + policyId + ",\"revision\":" + expected
                + ",\"sha256\":\"" + snapshot.getSha256() + "\"}";
        outbox.save(new OutboxEntity(eventKey, payload));
        return new PublishResult(policyId, expected, snapshot.getSha256());
    }

    @Transactional(readOnly = true)
    public AgentPolicy getPublishedForAgent(long policyId) {
        PolicyEntity policy = getPolicy(policyId);
        if (policy.getPublishedRevision() == null) {
            throw PolicyException.notFound("该策略尚未发布");
        }
        PolicyRevisionEntity snapshot = getSnapshot(policyId, policy.getPublishedRevision());
        return new AgentPolicy(snapshot.getRevisionNo(), snapshot.getXmlContent(), snapshot.getSha256());
    }

    private PolicyEntity getPolicy(long id) {
        return policies.findById(id)
                .orElseThrow(() -> PolicyException.notFound("策略不存在: " + id));
    }

    private PolicyRevisionEntity getSnapshot(long policyId, long revision) {
        return revisions.findByPolicyIdAndRevisionNo(policyId, revision)
                .orElseThrow(() -> PolicyException.notFound("策略快照不存在: " + revision));
    }

    public record AgentPolicy(long revision, String xml, String sha256) {
    }
}
