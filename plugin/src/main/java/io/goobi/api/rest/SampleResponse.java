package io.goobi.api.rest;

import javax.xml.bind.annotation.XmlRootElement;

import lombok.Data;

@XmlRootElement
public @Data class SampleResponse {

    private String result;

    private String message;

    private String processName;

    private int processId;
}
