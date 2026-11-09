package com.example.policy.handler;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * Each handler owns one top-level XML module. Order is a protocol contract.
 */
public interface PolicyModuleHandler<T> {
    String key();

    int order();

    Class<T> configType();

    void validateBusiness(T config);

    void writeXml(XMLStreamWriter writer, T config) throws XMLStreamException;
}
