package io.goobi.api.rest;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

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
    private static final String FOLDERNAME = "/opt/digiverso/goobi/marcexport/";

    @javax.ws.rs.Path("/listfiles")
    @GET
    @Produces("text/xml")
    public FileListResponse getListOfFiles() {
        List<String> filenames = StorageProvider.getInstance().list(FOLDERNAME);
        FileListResponse flr = new FileListResponse();
        flr.setFiles(filenames);
        return flr;
    }

    @javax.ws.rs.Path("/get/{filename}")
    @GET
    //    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Produces("text/xml")
    public Response getMarcFile(@PathParam("filename") final String filename) throws FileNotFoundException {
        List<String> filenames = StorageProvider.getInstance().list(FOLDERNAME);
        if (!filenames.contains(filename)) {
            return Response.status(Status.NOT_FOUND).build();
        }
        File file =new File( FOLDERNAME +filename );
        return Response.ok(file)
                .build();


    }
}
