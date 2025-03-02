package io.goobi.api.rest;

import jakarta.xml.bind.annotation.XmlRootElement;

import lombok.Data;

@Data
@XmlRootElement
public class Msg {

    private String type;
    private String message;
}
