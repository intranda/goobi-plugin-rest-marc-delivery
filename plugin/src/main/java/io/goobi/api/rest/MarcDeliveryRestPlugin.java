package io.goobi.api.rest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.goobi.beans.Process;
import org.goobi.beans.Step;

import de.sub.goobi.helper.CloseStepHelper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.persistence.managers.ProcessManager;
import lombok.extern.log4j.Log4j2;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Prefs;
import ugh.exceptions.UGHException;

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
        List<String> filenames = getRecordsFromFolder();
        FileListResponse flr = new FileListResponse();
        flr.setFiles(filenames);
        return flr;
    }

    @javax.ws.rs.Path("/get/{filename}")
    @GET
    @Produces("text/xml")
    public Response getMarcFile(@PathParam("filename") final String filename) {
        List<String> filenames = getRecordsFromFolder();
        if (!filenames.contains(filename)) {
            return Response.status(Status.NOT_FOUND).build();
        }
        Path existingFile = getRecord(filename);
        if (existingFile == null) {
            return Response.status(Status.NOT_FOUND).build();
        }
        return Response.ok(existingFile.toFile()).build();
    }

    @javax.ws.rs.Path("/finish/{filename}/{recordid}")
    @PUT
    @Produces("text/xml")
    public Response finishProcess(@PathParam("filename") final String filename, @PathParam("recordid") String recordid) {

        // check if file exists
        List<String> filenames = getRecordsFromFolder();
        if (!filenames.contains(filename)) {
            return Response.status(Status.NOT_FOUND).build();
        }

        // get the actual file
        Path existingFile = getRecord(filename);
        if (existingFile == null) {
            return Response.status(Status.NOT_FOUND).build();
        }
        String processId = existingFile.getParent().getFileName().toString();
        // find process with that id
        Process process = ProcessManager.getProcessById(Integer.parseInt(processId));
        Prefs prefs = process.getRegelsatz().getPreferences();

        MetadataType identifierType = prefs.getMetadataTypeByName("CatalogIDDigital");
        MetadataType adisType = prefs.getMetadataTypeByName("CatalogIDDigital_DS");
        String currentId = filename.replace(".xml", "");
        // read metadata
        try {
            Fileformat fileformat = process.readMetadataFile();
            DocStruct logical = fileformat.getDigitalDocument().getLogicalDocStruct();
            DocStruct anchor = null;
            if (logical.getType().isAnchor()) {
                anchor = logical;
                logical = logical.getAllChildren().get(0);
            }

            // write adis id to metadata
            if (anchor != null) {
                List<? extends Metadata> ids = anchor.getAllMetadataByType(identifierType);
                if (ids != null && ids.get(0).getValue().equals(currentId)) {
                    Metadata md = new Metadata(adisType);
                    md.setValue(recordid);
                    anchor.addMetadata(md);
                }
            }

            List<? extends Metadata> ids = logical.getAllMetadataByType(identifierType);
            if (ids != null && ids.get(0).getValue().equals(currentId)) {
                Metadata md = new Metadata(adisType);
                md.setValue(recordid);
                logical.addMetadata(md);
            }
            // update process
            process.writeMetadataFile(fileformat);
        } catch (UGHException | IOException |  SwapException  e) {
            log.error(e);
            return Response.serverError().build();
        }

        // if successful, delete file
        try {
            StorageProvider.getInstance().deleteFile(existingFile);
        } catch (IOException e) {
            log.error(e);
        }
        // if folder is empty, all records where imported, delete folder and close current step
        if (StorageProvider.getInstance().list(existingFile.getParent().toString()).isEmpty()) {
            StorageProvider.getInstance().deleteDir(existingFile.getParent());
            Step step = process.getAktuellerSchritt();
            CloseStepHelper.closeStep(step, null);
        }


        return Response.ok().build();
    }

    @javax.ws.rs.Path("/error/{filename}/")
    @PUT
    @Consumes("text/plain")
    @Produces("text/xml")
    public Response finishProcess(@PathParam("filename") final String filename) {
        // find file, remove it
        // find process, set current step to error


        return Response.ok().build();
    }


    private List<String> getRecordsFromFolder() {
        List<String> allFilenames = new ArrayList<>();
        try {
            try (Stream<Path> input =
                    Files.find(Paths.get(FOLDERNAME), 2, (p, file) -> file.isRegularFile() && p.getFileName().toString().endsWith(".xml"))) {
                input.forEach(p -> allFilenames.add(p.getFileName().toString()));
            }
        } catch (IOException e) {
            log.error(e);
        }
        return allFilenames;
    }

    private Path getRecord(String filename) {
        try {
            Optional<Path> path = Optional.empty();
            try (Stream<Path> input =
                    Files.find(Paths.get(FOLDERNAME), 2, (p, file) -> file.isRegularFile() && p.getFileName().toString().equals(filename))) {
                path = input.findFirst();
            }

            if (path.isPresent()) {
                return path.get();
            }
        } catch (IOException e) {
            log.error(e);
        }
        return null;
    }

}
