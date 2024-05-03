package io.goobi.api.rest;

import javax.xml.bind.annotation.XmlRootElement;

import lombok.Data;

@Data
@XmlRootElement
public class Msg {

    private String type;
    private String message;
}
