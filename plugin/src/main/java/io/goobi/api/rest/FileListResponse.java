package io.goobi.api.rest;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import lombok.Data;

@Data
@XmlRootElement(name = "files")
@XmlAccessorType(XmlAccessType.FIELD)

public class FileListResponse {

    @XmlElement(name = "file")
    private List<String> files;
}
