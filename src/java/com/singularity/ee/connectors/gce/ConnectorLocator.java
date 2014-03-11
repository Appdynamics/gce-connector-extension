package com.singularity.ee.connectors.gce;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.compute.Compute;
import com.singularity.ee.connectors.api.IControllerServices;
import com.singularity.ee.connectors.api.InvalidObjectException;
import com.singularity.ee.connectors.entity.api.IProperty;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.log4j.Logger;

public class ConnectorLocator {

    private static final ConnectorLocator INSTANCE = new ConnectorLocator();

    private final Map<String, Compute> serviceIdVsCompute = new HashMap<String, Compute>();

    public static final String SCOPE_COMPUTE = "https://www.googleapis.com/auth/compute";
    public static final String APPLICATION_NAME = "AppD-GCEConnector/1.0";

    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    private static final Logger LOG = Logger.getLogger(ConnectorLocator.class);

    /**
     * Private constructor on singleton.
     */
    private ConnectorLocator() {
    }

    public static ConnectorLocator getInstance() {
        return INSTANCE;
    }

    public Compute getConnector(IProperty[] properties, IControllerServices controllerServices) throws InvalidObjectException {
        
        String serviceAccountId = Utils.getServiceAccountId(properties, controllerServices);
        String serviceAccountP12FilePath = Utils.getServiceAccountP12File(properties, controllerServices);

        Compute compute = getCompute(serviceAccountId);

        if (compute == null) {
            compute = setCompute(serviceAccountId, serviceAccountP12FilePath);
        }

        String projectId = Utils.getProjectId(properties, controllerServices);

        validate(compute, projectId);
        
        return compute;
    }

    private void validate(Compute compute, String projectId) throws InvalidObjectException{
        try {
            compute.projects().get(projectId).execute();
        } catch (Exception e) {
            LOG.error("The specified " + Utils.SERVICE_ACCOUNT_KEY_PROP +
                    " and/or " + Utils.SERVICE_ACCOUNT_P12_FILE_KEY_PROP + " is not valid.", e);

            throw new InvalidObjectException("The specified " + Utils.SERVICE_ACCOUNT_KEY_PROP +
                    " and/or " + Utils.SERVICE_ACCOUNT_P12_FILE_KEY_PROP + " is not valid.", e);
        }
    }

    private Compute setCompute(String serviceAccountId, String serviceAccountP12FilePath) {
        rwLock.writeLock().lock();
        try {
            Compute googleCompute = createGoogleCompute(serviceAccountId, serviceAccountP12FilePath);
            serviceIdVsCompute.put(serviceAccountId, googleCompute);
            return googleCompute;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private Compute getCompute(String serviceAccountId) {
        rwLock.readLock().lock();
        try {
            Compute compute = serviceIdVsCompute.get(serviceAccountId);
            if (compute != null) {
                return compute;
            }
        } finally {
            rwLock.readLock().unlock();
        }
        return null;
    }

    private Compute createGoogleCompute(String serviceAccountId, String serviceAccountP12FilePath) {
        NetHttpTransport transport = null;
        JacksonFactory jsonFactory = new JacksonFactory();
        GoogleCredential credential = null;

        try {
            transport = GoogleNetHttpTransport.newTrustedTransport();
            credential = new GoogleCredential.Builder().setTransport(transport)
                    .setJsonFactory(jsonFactory)
                    .setServiceAccountId(serviceAccountId)
                    .setServiceAccountScopes(Collections.singleton(SCOPE_COMPUTE))
                    .setServiceAccountPrivateKeyFromP12File(new File(serviceAccountP12FilePath))
                    .build();
        } catch (GeneralSecurityException e) {
            LOG.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

        return new Compute.Builder(
                transport, jsonFactory, null).setApplicationName(APPLICATION_NAME)
                .setHttpRequestInitializer(credential).build();
    }
}
