package org.hortonmachine.geopaparazzi.simpleserver.servlets;
import java.io.File;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.hortonmachine.geopaparazzi.simpleserver.GeopaparazziServerUtilities;

public class ProjectUploadHandler extends AbstractHandler {

    private File gpapProjectsFolder;

    public ProjectUploadHandler( File gpapProjectsFolder ) {
        this.gpapProjectsFolder = gpapProjectsFolder;
    }

    public void handle( String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response )
            throws IOException, ServletException {
        try {
            GeopaparazziServerUtilities.handleProjectUpload(gpapProjectsFolder, response, request);
        } catch (Exception e) {
            e.printStackTrace();
        }
        baseRequest.setHandled(true);
    }

}