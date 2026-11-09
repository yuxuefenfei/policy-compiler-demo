package com.example.policy.handler;

import com.example.policy.model.PolicyModels.ApplicationConfig;
import com.example.policy.model.PolicyModels.ApplicationRule;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

@Component
public class ApplicationHandler implements PolicyModuleHandler<ApplicationConfig> {
    public String key() {
        return "application";
    }

    public int order() {
        return 40;
    }

    public Class<ApplicationConfig> configType() {
        return ApplicationConfig.class;
    }

    public void validateBusiness(ApplicationConfig cfg) {
        HandlerSupport.uniqueIds(cfg.rules(), ApplicationRule::id, key());
    }

    public void writeXml(XMLStreamWriter x, ApplicationConfig c) throws XMLStreamException {
        x.writeStartElement("ApplicationControl");
        x.writeAttribute("enabled", c.enabled().toString());
        for (ApplicationRule r : c.rules()) {
            x.writeEmptyElement("Rule");
            x.writeAttribute("id", r.id());
            x.writeAttribute("action", r.action().name());
            x.writeAttribute("process", r.process());
        }
        x.writeEndElement();
    }
}
