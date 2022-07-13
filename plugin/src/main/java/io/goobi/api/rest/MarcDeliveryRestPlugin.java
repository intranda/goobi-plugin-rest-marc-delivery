package io.goobi.api.rest;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Produces;

import de.sub.goobi.helper.StorageProvider;
import lombok.extern.log4j.Log4j2;

@Log4j2
@javax.ws.rs.Path("/delivery")
public class MarcDeliveryRestPlugin {
    /*
     * goobi_rest.xml:
    <endpoint path="/delivery/.*">
        <method name="get">
            <allow netmask="127.0.0.0/8" token="secret"/>
        </method>
    </endpoint>
     */

    // TODO get this from configuration file
    private static final String FOLDERNAME = "/opt/digiverso/goobi/marcexport";

    @javax.ws.rs.Path("/listfiles")
    @GET
    @Produces("text/xml")
    public FileListResponse getListOfFiles() {

        List<String> filenames = StorageProvider.getInstance().list(FOLDERNAME);
        FileListResponse flr = new FileListResponse();
        flr.setFiles(filenames);

        return flr;
    }
}
