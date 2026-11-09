package com.example.policy.handler;

import com.example.policy.api.PolicyException;
import com.example.policy.model.PolicyModels.FileAuditConfig;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

@Component
public class FileAuditHandler implements PolicyModuleHandler<FileAuditConfig> {
    public String key() {
        return "fileAudit";
    }

    public int order() {
        return 30;
    }

    public Class<FileAuditConfig> configType() {
        return FileAuditConfig.class;
    }

    public void validateBusiness(FileAuditConfig cfg) {
        if (cfg.paths().stream().distinct().count() != cfg.paths().size()) {
            throw PolicyException.badRequest("fileAudit.paths 存在重复路径");
        }
    }

    public void writeXml(XMLStreamWriter x, FileAuditConfig c) throws XMLStreamException {
        x.writeStartElement("FileAudit");
        x.writeAttribute("enabled", c.enabled().toString());
        for (String path : c.paths()) {
            x.writeEmptyElement("Path");
            x.writeAttribute("value", path);
        }
        x.writeEndElement();
    }
}
