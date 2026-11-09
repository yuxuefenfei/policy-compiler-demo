package com.example.policy.handler;

import com.example.policy.api.PolicyException;
import com.example.policy.model.PolicyModels.NetworkConfig;
import com.example.policy.model.PolicyModels.NetworkRule;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

@Component
public class NetworkHandler implements PolicyModuleHandler<NetworkConfig> {
    private static boolean isIpv4(String value) {
        if (value == null) return false;
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String p : parts) {
            if (p.isEmpty() || p.length() > 3 || !p.matches("[0-9]+")) return false;
            if (p.length() > 1 && p.charAt(0) == '0') return false;
            if (Integer.parseInt(p) > 255) return false;
        }
        return true;
    }

    public String key() {
        return "network";
    }

    public int order() {
        return 20;
    }

    public Class<NetworkConfig> configType() {
        return NetworkConfig.class;
    }

    public void validateBusiness(NetworkConfig cfg) {
        HandlerSupport.uniqueIds(cfg.rules(), NetworkRule::id, key());
        for (NetworkRule r : cfg.rules()) {
            if (!isIpv4(r.ip())) throw PolicyException.badRequest("network.rules.ip 必须是 IPv4 地址: " + r.ip());
        }
    }

    public void writeXml(XMLStreamWriter x, NetworkConfig c) throws XMLStreamException {
        x.writeStartElement("NetworkControl");
        x.writeAttribute("enabled", c.enabled().toString());
        x.writeAttribute("defaultAction", c.defaultAction().name());
        for (NetworkRule r : c.rules()) {
            x.writeEmptyElement("Rule");
            x.writeAttribute("id", r.id());
            x.writeAttribute("action", r.action().name());
            x.writeAttribute("ip", r.ip());
        }
        x.writeEndElement();
    }
}
