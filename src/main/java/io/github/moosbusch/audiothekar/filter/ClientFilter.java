/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package io.github.moosbusch.audiothekar.filter;

import java.io.IOException;
import java.util.Collections;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author komano
 */
@Provider
public class ClientFilter implements ClientResponseFilter {

    private static final Logger LOGGER = LogManager.getLogger(ClientFilter.class);

    @Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
        final MultivaluedMap<String, Object> requestHeaders = requestContext.getHeaders();
        final MultivaluedMap<String, String> responseHeaders = responseContext.getHeaders();
        final String contentType = responseContext.getHeaderString(HttpHeaders.CONTENT_TYPE);

        if (contentType != null) {
            if (contentType.startsWith(MediaType.TEXT_HTML)) {
                requestContext.abortWith(Response.status(Response.Status.NO_CONTENT).entity(null).build());
            }

            if (contentType.toLowerCase().contains("audio/mpeg")) {
                responseContext.getHeaders().put(HttpHeaders.CONTENT_TYPE, Collections.singletonList("application/octet-stream"));
            }
        }
    }

}
