package dk.dbc.z3950IllProxy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.yaz4j.ConnectionExtended;
import org.yaz4j.Package;
import org.yaz4j.exception.Bib1Exception;
import org.yaz4j.exception.ZoomException;

import javax.ejb.Stateless;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.HttpURLConnection;

@Stateless
@Path("")
public class Z3950Endpoint {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(Z3950Endpoint.class);

    private ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @GET
    @Path("howru")
    public Response getStatus() {
        return Response.ok().entity("{}").build();
    }

    @POST
    @Path("ill")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response sendIll(String inputData) {
        LOGGER.entry(inputData);
        StopWatch stopWatch = new Log4JStopWatch("Z3950Endpoint.sendIll");
        try {
            JsonQuery jsonQuery = objectMapper.readValue(inputData, JsonQuery.class);
            String targetRef = "[target ref missing]";
            String returnDoc = "[return doc missing]";

            String targetHost = jsonQuery.getServer() + ":" + jsonQuery.getPort();
            LOGGER.info("targetHost: " + targetHost);

            try (ConnectionExtended connection = new ConnectionExtended(targetHost, 0)) {
                connection.option("implementationName", "DBC z3950 ill proxy");
                connection.option("user", jsonQuery.getUser());
                connection.option("group", jsonQuery.getGroup());
                connection.option("password", jsonQuery.getPassword());
                connection.connect();

                Package ill = connection.getPackage("itemorder");
                ill.option("doc", jsonQuery.getData());
                ill.send();
            } catch (Bib1Exception e) {
                LOGGER.catching(XLogger.Level.ERROR, e);
                return Response.status(HttpURLConnection.HTTP_BAD_GATEWAY).type(MediaType.APPLICATION_JSON).entity("{errorMessage: " + e.getMessage() + "}").build();
            } catch (ZoomException e) {
                LOGGER.catching(XLogger.Level.ERROR, e);
                return Response.status(HttpURLConnection.HTTP_BAD_GATEWAY).type(MediaType.APPLICATION_JSON).entity("{errorMessage: " + e.getMessage()
                        + ", targetRef: " + targetRef
                        + ", returnDoc: " + returnDoc
                        + "}").build();
            }
            return Response.ok().entity("{inputData: " + inputData
                    + ", targetRef: " + targetRef
                    + ", returnDoc: " + returnDoc
                    + "}").build();
        } catch (IOException e) {
            return Response.status(HttpURLConnection.HTTP_BAD_GATEWAY).type(MediaType.APPLICATION_JSON).entity("{error: " + e.getMessage() + "}").build();
        } finally {
            stopWatch.stop();
            LOGGER.exit();
        }
    }
}
