package com.example.policy.api;

import com.example.policy.model.PolicyModels.*;
import com.example.policy.service.PolicyService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/policies")
public class PolicyController {
    private final PolicyService service;

    public PolicyController(PolicyService service) {
        this.service = service;
    }

    @PostMapping
    public PolicyCreated create(@Valid @RequestBody CreatePolicyRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}/draft")
    public SaveResult save(@PathVariable long id, @Valid @RequestBody SavePolicyRequest request) {
        return service.saveDraft(id, request);
    }

    @GetMapping("/{id}/draft")
    public DraftResult draft(@PathVariable long id) {
        return service.getDraft(id);
    }

    @PostMapping("/{id}/publish")
    public PublishResult publish(@PathVariable long id,
                                 @Valid @RequestBody PublishPolicyRequest request) {
        return service.publish(id, request);
    }

    // Demo-only pull endpoint. Production must authenticate/authorize the endpoint agent.
    @GetMapping(value = "/{id}/agent-xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> publishedXml(@PathVariable long id) {
        var p = service.getPublishedForAgent(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .eTag(p.sha256())
                .header("X-Policy-Revision", Long.toString(p.revision()))
                .body(p.xml());
    }
}
