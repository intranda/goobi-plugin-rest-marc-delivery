package io.goobi.api.rest;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import lombok.extern.log4j.Log4j2;

@Log4j2
@javax.ws.rs.Path("/intern")
public class SampleRestPlugin {
    
    /**
     * This sample method to allow the upload of JSON data via POST
     * 
     * Sample call:
     * curl --location --request POST 'http://localhost:8080/goobi/api/intern/sample' \
     *   --header 'Content-Type: application/json' \
     *   --data-raw '{
     *       "result": "success",
     *       "message": "This is a sample message",
     *       "processName": "Sample name",
     *       "processId": 45
     *   }'
     * 
     * @return XML as result
     */
    @javax.ws.rs.Path("/sample")
    @POST
    @Produces("text/xml")
    @Consumes("application/json")
    public Response execute() {
        try {
            
            // your functionality comes here            
            System.out.println("This is a sample output to the command line.");
            
        } catch (Exception e) {
            log.error("An error occured while executing a REST call.", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .build();
        }
        return Response.status(Response.Status.OK).build();
    }
    
    /**
     * A sample method to request information as json for a specific process. This is just a kickstart method without any real functionality
     * 
     * Sample call:
     * curl --location --request GET 'http://localhost:8080/goobi/api/intern/status/45'
     * 
     * @param processId identifier of a process
     * @return JSON object with status information
     */
    @javax.ws.rs.Path("status/{processId}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public SampleResponse getProcessStatusAsJson(@PathParam("processId") int processId) {
        SampleResponse sr = new SampleResponse();
        sr.setMessage("This is a sample message");
        sr.setProcessId(processId);
        sr.setProcessName("Sample name");
        sr.setResult("success");
        return sr;
    }
}
