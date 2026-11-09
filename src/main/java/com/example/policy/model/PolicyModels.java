package com.example.policy.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public final class PolicyModels {
    private PolicyModels() {
    }

    public enum Action {ALLOW, DENY}

    public record CreatePolicyRequest(@NotBlank @Size(max = 128) String name) {
    }

    public record SavePolicyRequest(
            @NotNull @Min(0) Long expectedRevision,
            @NotBlank @Size(max = 128) String name,
            @NotNull Integer schemaVersion,
            @NotNull @Size(min = 1, max = 50) Map<String, JsonNode> modules) {
    }

    public record PublishPolicyRequest(@NotNull @Min(1) Long expectedRevision) {
    }

    public record GatewayConfig(@NotNull Boolean enabled,
                                @NotNull Boolean allowChange) {
    }

    public record NetworkRule(@NotBlank @Size(max = 64) String id,
                              @NotNull Action action,
                              @NotBlank String ip) {
    }

    public record NetworkConfig(@NotNull Boolean enabled,
                                @NotNull Action defaultAction,
                                @NotNull @Size(max = 2000) List<@NotNull @Valid NetworkRule> rules) {
    }

    public record FileAuditConfig(@NotNull Boolean enabled,
                                  @NotNull @Size(max = 1000) List<@NotBlank @Size(max = 1024) String> paths) {
    }

    public record ApplicationRule(@NotBlank @Size(max = 64) String id,
                                  @NotNull Action action,
                                  @NotBlank @Size(max = 260) String process) {
    }

    public record ApplicationConfig(@NotNull Boolean enabled,
                                    @NotNull @Size(max = 2000) List<@NotNull @Valid ApplicationRule> rules) {
    }

    public record PolicyCreated(long policyId, long draftRevision) {
    }

    public record SaveResult(long policyId, long draftRevision, String sha256) {
    }

    public record PublishResult(long policyId, long publishedRevision, String sha256) {
    }

    public record DraftResult(long policyId, long draftRevision,
                              String sha256, JsonNode configuration) {
    }
}
