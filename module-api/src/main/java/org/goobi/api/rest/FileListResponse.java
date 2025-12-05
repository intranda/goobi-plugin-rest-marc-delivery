package org.goobi.api.rest;

import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import lombok.Data;

@Data
@XmlRootElement(name = "files")
@XmlAccessorType(XmlAccessType.FIELD)

public class FileListResponse {

    @XmlElement(name = "file")
    private List<String> files;
}
