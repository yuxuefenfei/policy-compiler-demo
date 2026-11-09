package com.example.policy.compiler;

import com.example.policy.api.PolicyException;
import com.example.policy.handler.PolicyModuleHandler;
import com.example.policy.model.PolicyModels.SavePolicyRequest;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Full JSON -> deterministic, schema-checked XML. Never reads or patches the previous XML.
 */
@Component
public class PolicyXmlCompiler {
    private static final int SUPPORTED_SCHEMA_VERSION = 1;
    private static final int MAX_XML_BYTES = 5 * 1024 * 1024;
    private final ObjectMapper mapper;
    private final Validator beanValidator;
    private final Map<String, PolicyModuleHandler<?>> handlers;
    private final List<PolicyModuleHandler<?>> ordered;
    private final Schema xsd;
    public PolicyXmlCompiler(ObjectMapper mapper, Validator beanValidator,
                             List<PolicyModuleHandler<?>> registered) {
        this.mapper = mapper;
        this.beanValidator = beanValidator;
        this.handlers = registered.stream().collect(Collectors.toUnmodifiableMap(
                PolicyModuleHandler::key, h -> h));
        this.ordered = registered.stream()
                .sorted(Comparator.comparingInt(PolicyModuleHandler::order))
                .toList();
        if (ordered.stream().map(PolicyModuleHandler::order).distinct().count() != ordered.size()) {
            throw new IllegalStateException("策略 Handler 的 order 必须唯一");
        }
        this.xsd = loadSchema();
    }

    public Compiled compile(long policyId, SavePolicyRequest request) {
        if (request.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
            throw PolicyException.badRequest("不支持的 schemaVersion: " + request.schemaVersion());
        }
        // Full submission semantics: every supported module must be present, even when disabled.
        if (!request.modules().keySet().equals(handlers.keySet())) {
            Set<String> missing = new TreeSet<>(handlers.keySet());
            missing.removeAll(request.modules().keySet());
            Set<String> unexpected = new TreeSet<>(request.modules().keySet());
            unexpected.removeAll(handlers.keySet());
            throw PolicyException.badRequest("策略模块不完整，缺少=" + missing + "，未知=" + unexpected);
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            XMLStreamWriter xml = XMLOutputFactory.newFactory()
                    .createXMLStreamWriter(output, StandardCharsets.UTF_8.name());
            try {
                xml.writeStartDocument("UTF-8", "1.0");
                xml.writeStartElement("Policy");
                xml.writeAttribute("schemaVersion", "1");
                xml.writeAttribute("policyId", Long.toString(policyId));
                xml.writeAttribute("name", request.name());

                for (PolicyModuleHandler<?> handler : ordered) {
                    appendModule(xml, handler, request.modules().get(handler.key()));
                }
                xml.writeEndElement();
                xml.writeEndDocument();
                xml.flush();
            } finally {
                xml.close();
            }
            byte[] bytes = output.toByteArray();
            if (bytes.length > MAX_XML_BYTES) {
                throw PolicyException.badRequest("生成的 XML 超过 5 MiB 限制");
            }
            xsd.newValidator().validate(new StreamSource(new ByteArrayInputStream(bytes)));
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            return new Compiled(new String(bytes, StandardCharsets.UTF_8), digest);
        } catch (PolicyException e) {
            throw e;
        } catch (XMLStreamException | SAXException | IOException | NoSuchAlgorithmException e) {
            // Compiler output failing XSD indicates protocol/compiler drift; not a user-correctable error.
            throw new IllegalStateException("策略 XML 生成或协议校验失败", e);
        }
    }

    private <T> void appendModule(XMLStreamWriter xml, PolicyModuleHandler<T> handler, JsonNode json)
            throws XMLStreamException {
        if (json == null || !json.isObject()) {
            throw PolicyException.badRequest(handler.key() + " 必须是一个 JSON 对象");
        }
        final T config;
        try (JsonParser parser = json.traverse(mapper)) {
            config = mapper.readerFor(handler.configType())
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(parser);
        } catch (IOException | IllegalArgumentException e) {
            throw PolicyException.badRequest(handler.key() + " 的字段类型或字段名不合法: " + e.getMessage());
        }
        Set<ConstraintViolation<T>> violations = beanValidator.validate(config);
        if (!violations.isEmpty()) {
            String error = violations.stream().map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted().collect(Collectors.joining("; "));
            throw PolicyException.badRequest(handler.key() + " 参数校验失败: " + error);
        }
        handler.validateBusiness(config);
        handler.writeXml(xml, config);
    }

    private Schema loadSchema() {
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var resource = PolicyXmlCompiler.class.getResource("/xsd/policy-v1.xsd");
            if (resource == null) throw new IllegalStateException("缺少 /xsd/policy-v1.xsd");
            // This is a trusted, bundled classpath resource; never accept user-supplied schema URLs.
            return factory.newSchema(resource);
        } catch (SAXException e) {
            throw new IllegalStateException("初始化 XML Schema 失败", e);
        }
    }

    public record Compiled(String xml, String sha256) {
    }
}
