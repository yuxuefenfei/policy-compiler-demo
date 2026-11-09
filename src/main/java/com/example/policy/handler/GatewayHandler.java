package com.example.policy.handler;

import com.example.policy.model.PolicyModels.GatewayConfig;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

@Component
public class GatewayHandler implements PolicyModuleHandler<GatewayConfig> {
    public String key() {
        return "gateway";
    }

    public int order() {
        return 10;
    }

    public Class<GatewayConfig> configType() {
        return GatewayConfig.class;
    }

    public void validateBusiness(GatewayConfig cfg) {
    }

    public void writeXml(XMLStreamWriter x, GatewayConfig c) throws XMLStreamException {
        x.writeEmptyElement("GatewayControl");
        x.writeAttribute("enabled", c.enabled().toString());
        x.writeAttribute("allowChange", c.allowChange().toString());
    }
}
