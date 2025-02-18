package io.goobi.api.rest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.goobi.beans.Process;
import org.goobi.beans.Step;
import org.goobi.production.enums.LogType;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.CloseStepHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.enums.StepStatus;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.persistence.managers.ProcessManager;
import de.sub.goobi.persistence.managers.StepManager;
import lombok.extern.log4j.Log4j2;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Prefs;
import ugh.exceptions.UGHException;

@Log4j2
@jakarta.ws.rs.Path("/delivery")
public class MarcDeliveryRestPlugin {
    /*
     * goobi_rest.xml:
    <endpoint path="/delivery/.*">
        <method name="get">
            <allow netmask="127.0.0.0/8" token="secret"/>
        </method>
        <method name="post">
            <allow netmask="127.0.0.0/8" token="secret"/>
        </method>
    </endpoint>
     */

    private String folderName;
    private List<String> stepNameWhiteList = new ArrayList<>();

    public MarcDeliveryRestPlugin() {

        // read configuration file
        XMLConfiguration conf = ConfigPlugins.getPluginConfig("intranda_rest_marcdelivery");
        conf.setExpressionEngine(new XPathExpressionEngine());
        folderName = conf.getString("/exportFolder");
        stepNameWhiteList = Arrays.asList(conf.getStringArray("/step"));
    }

    // curl -s -H "Content-Type: application/xml" -H "token:secret" http://localhost:8080/goobi/api/delivery/listfiles

    @jakarta.ws.rs.Path("/listfiles")
    @GET
    @Produces("application/xml")
    public FileListResponse getListOfFiles() {
        List<String> filenames = getRecordsFromFolder();
        FileListResponse flr = new FileListResponse();
        flr.setFiles(filenames);
        return flr;
    }

    // curl -s -H "Content-Type: application/xml" -H "token:secret" http://localhost:8080/goobi/api/delivery/get/56986741-e4e5-42b9-bf25-81d23d9cbe06.xml

    @jakarta.ws.rs.Path("/get/{filename}")
    @GET
    @Produces("application/xml")
    public Response getMarcFile(@PathParam("filename") final String filename) {
        Path existingFile = checkFilename(filename);
        if (existingFile == null) {
            return Response.status(Status.NOT_FOUND).build();
        }
        return Response.ok(existingFile.toFile()).build();
    }

    // curl -s -X POST -H "Content-Type: application/xml" -H "token:secret" http://localhost:8080/goobi/api/delivery/finish/56986741-e4e5-42b9-bf25-81d23d9cbe06.xml/ID12345

    @jakarta.ws.rs.Path("/finish/{filename}/{recordid}")
    @POST
    @Produces("application/xml")
    public Response finishProcess(@PathParam("filename") final String filename, @PathParam("recordid") String recordid) {

        Path existingFile = checkFilename(filename);
        if (existingFile == null) {
            return Response.status(Status.NOT_FOUND).build();
        }
        String processId = existingFile.getParent().getFileName().toString();
        // find process with that id
        Process process = ProcessManager.getProcessById(Integer.parseInt(processId));
        Step step = process.getAktuellerSchritt();

        Prefs prefs = process.getRegelsatz().getPreferences();

        MetadataType deliveryNumberType = prefs.getMetadataTypeByName("CatalogIDDigital_Delivery");
        MetadataType adisNumberType = prefs.getMetadataTypeByName("CatalogIDDigital");
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
                List<? extends Metadata> ids = anchor.getAllMetadataByType(deliveryNumberType);
                if (ids != null && ids.get(0).getValue().equals(currentId)) {
                    // check if field exists
                    List<? extends Metadata> identifierList = anchor.getAllMetadataByType(adisNumberType);
                    if (!identifierList.isEmpty()) {
                        // if yes, re-use it, overwrite entry
                        identifierList.get(0).setValue(recordid);
                    } else {
                        // if not, create a new one
                        try {
                            Metadata md = new Metadata(adisNumberType);
                            md.setValue(recordid);
                            anchor.addMetadata(md);
                        } catch (UGHException e) {
                            // ignore exception, adis id already exists
                        }
                    }
                }
            }

            List<? extends Metadata> ids = logical.getAllMetadataByType(deliveryNumberType);
            if (ids != null && ids.get(0).getValue().equals(currentId)) {

                // check if field exists
                List<? extends Metadata> identifierList = logical.getAllMetadataByType(adisNumberType);
                if (!identifierList.isEmpty()) {
                    // if yes, re-use it, overwrite entry
                    identifierList.get(0).setValue(recordid);
                } else {
                    // if not, create a new one
                    try {
                        Metadata md = new Metadata(adisNumberType);
                        md.setValue(recordid);
                        logical.addMetadata(md);
                    } catch (UGHException e) {
                        // ignore exception, adis id already exists
                    }
                }
            }
            // update process
            process.writeMetadataFile(fileformat);
        } catch (UGHException | IOException | SwapException e) {
            if (step != null && stepNameWhiteList.contains(step.getTitel())) {
                step.setBearbeitungsstatusEnum(StepStatus.ERROR);
                try {
                    StepManager.saveStep(step);
                } catch (DAOException e1) {
                    log.error(e1);
                }
            }
            // create journal entry
            Helper.addMessageToProcessJournal(process.getId(), LogType.ERROR, e.getMessage(), "aDIS");

            // cleanup
            try {
                StorageProvider.getInstance().deleteFile(existingFile);
            } catch (IOException e1) {
                log.error(e1);
            }
            // cancel
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

            if (step != null && stepNameWhiteList.contains(step.getTitel())) {
                CloseStepHelper.closeStep(step, null);
            }
        }

        return Response.ok().build();
    }

    // curl -s -X POST -H "Content-Type: application/xml" -H "token:secret" -d'<msg><type>error</type><message>Corrupt filename</message></msg>' http://localhost:8080/goobi/api/delivery/error/56986741-e4e5-42b9-bf25-81d23d9cbe06.xml

    @jakarta.ws.rs.Path("/error/{filename}")
    @POST
    @Consumes("application/xml")
    @Produces("application/xml")
    public Response errorProcess(@PathParam("filename") final String filename, Msg msg) {
        // find file
        Path existingFile = checkFilename(filename);
        if (existingFile == null) {
            return Response.status(Status.NOT_FOUND).build();
        }
        // find process
        String processId = existingFile.getParent().getFileName().toString();
        Process process = ProcessManager.getProcessById(Integer.parseInt(processId));
        Step step = process.getAktuellerSchritt();
        if (step != null && stepNameWhiteList.contains(step.getTitel())) {
            step.setBearbeitungsstatusEnum(StepStatus.ERROR);
            try {
                StepManager.saveStep(step);
            } catch (DAOException e) {
                log.error(e);
            }
        }

        // write message to process log
        Helper.addMessageToProcessJournal(process.getId(), LogType.getByTitle(msg.getType().toLowerCase()), msg.getMessage(), "aDIS");

        // delete file, so it doesn't get listed until error is fixed
        try {
            StorageProvider.getInstance().deleteFile(existingFile);
        } catch (IOException e) {
            log.error(e);
        }
        // if folder is empty, all records where imported, delete folder
        if (StorageProvider.getInstance().list(existingFile.getParent().toString()).isEmpty()) {
            StorageProvider.getInstance().deleteDir(existingFile.getParent());

        }

        return Response.ok().build();
    }

    private List<String> getRecordsFromFolder() {
        List<String> allFilenames = new ArrayList<>();
        try {
            try (Stream<Path> input =
                    Files.find(Paths.get(folderName), 2, (p, file) -> file.isRegularFile() && p.getFileName().toString().endsWith(".xml"))) {
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
                    Files.find(Paths.get(folderName), 2, (p, file) -> file.isRegularFile() && p.getFileName().toString().equals(filename))) {
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

    private Path checkFilename(final String filename) {
        // check if file exists
        List<String> filenames = getRecordsFromFolder();
        if (!filenames.contains(filename)) {
            return null;
        }

        // get the actual file
        Path existingFile = getRecord(filename);
        if (existingFile == null) {
            return null;
        }
        return existingFile;
    }

}
