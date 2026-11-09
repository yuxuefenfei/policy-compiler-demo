package com.example.policy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PolicyFlowIntegrationTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    private long createPolicy() throws Exception {
        var response = mvc.perform(post("/api/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"test\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        return json.readTree(response.getContentAsString()).get("policyId").asLong();
    }

    private String requestJson() throws Exception {
        try (var stream = getClass().getResourceAsStream("/save-policy.json")) {
            assertThat(stream).isNotNull();
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    @Test
    void savePublishAndReadBackCompiledXml() throws Exception {
        long id = createPolicy();
        mvc.perform(get("/api/policies/{id}/agent-xml", id))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/policies/{id}/draft", id)
                        .contentType(MediaType.APPLICATION_JSON).content(requestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draftRevision").value(1));
        mvc.perform(get("/api/policies/{id}/draft", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuration.modules.network.rules[0].id").value("n1"));
        mvc.perform(post("/api/policies/{id}/publish", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishedRevision").value(1));
        var xml = mvc.perform(get("/api/policies/{id}/agent-xml", id))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Policy-Revision", "1"))
                .andReturn().getResponse().getContentAsString();
        assertThat(xml).contains("<GatewayControl", "<NetworkControl", "<FileAudit", "<ApplicationControl");
        assertThat(xml).contains("R&amp;D"); // StAX escaped special characters
    }

    @Test
    void fullSubmissionRejectsMissingModules() throws Exception {
        long id = createPolicy();
        String input = """
                {"expectedRevision":0,"name":"bad","schemaVersion":1,
                 "modules":{"gateway":{"enabled":true,"allowChange":false}}}
                """;
        mvc.perform(put("/api/policies/{id}/draft", id)
                        .contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void rejectsStaleDraftRevision() throws Exception {
        long id = createPolicy();
        String input = requestJson();
        mvc.perform(put("/api/policies/{id}/draft", id)
                        .contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isOk());
        mvc.perform(put("/api/policies/{id}/draft", id)
                        .contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsUnknownFieldInModule() throws Exception {
        long id = createPolicy();
        JsonNode input = json.readTree(requestJson());
        ((com.fasterxml.jackson.databind.node.ObjectNode) input.path("modules").path("gateway"))
                .put("allowChnage", true);
        mvc.perform(put("/api/policies/{id}/draft", id)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void newerDraftDoesNotChangePublishedRevision() throws Exception {
        long id = createPolicy();
        mvc.perform(put("/api/policies/{id}/draft", id)
                        .contentType(MediaType.APPLICATION_JSON).content(requestJson()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/policies/{id}/publish", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":1}"))
                .andExpect(status().isOk());
        JsonNode newDraft = json.readTree(requestJson());
        ((com.fasterxml.jackson.databind.node.ObjectNode) newDraft).put("expectedRevision", 1);
        ((com.fasterxml.jackson.databind.node.ObjectNode) newDraft.path("modules").path("gateway"))
                .put("allowChange", true);
        mvc.perform(put("/api/policies/{id}/draft", id)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(newDraft)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draftRevision").value(2));
        String xml = mvc.perform(get("/api/policies/{id}/agent-xml", id))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Policy-Revision", "1"))
                .andReturn().getResponse().getContentAsString();
        assertThat(xml).contains("allowChange=\"false\"");
    }

    @Test
    void rejectsDuplicateNetworkRuleIds() throws Exception {
        long id = createPolicy();
        JsonNode input = json.readTree(requestJson());
        ((com.fasterxml.jackson.databind.node.ObjectNode) input.path("modules").path("network")
                .path("rules").get(1)).put("id", "n1");
        mvc.perform(put("/api/policies/{id}/draft", id)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input)))
                .andExpect(status().isBadRequest());
    }
}
