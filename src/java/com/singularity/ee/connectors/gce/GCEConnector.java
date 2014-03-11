package com.singularity.ee.connectors.gce;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.AttachedDisk;
import com.google.api.services.compute.model.Disk;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.compute.model.Operation;
import com.google.common.collect.Lists;
import com.singularity.ee.agent.resolver.AgentResolutionEncoder;
import com.singularity.ee.connectors.api.ConnectorException;
import com.singularity.ee.connectors.api.IConnector;
import com.singularity.ee.connectors.api.IControllerServices;
import com.singularity.ee.connectors.api.InvalidObjectException;
import com.singularity.ee.connectors.entity.api.IAccount;
import com.singularity.ee.connectors.entity.api.IComputeCenter;
import com.singularity.ee.connectors.entity.api.IImage;
import com.singularity.ee.connectors.entity.api.IImageStore;
import com.singularity.ee.connectors.entity.api.IMachine;
import com.singularity.ee.connectors.entity.api.IMachineDescriptor;
import com.singularity.ee.connectors.entity.api.MachineState;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.log4j.Logger;

import static com.singularity.ee.controller.KAppServerConstants.CONTROLLER_SERVICES_HOST_NAME_PROPERTY_KEY;
import static com.singularity.ee.controller.KAppServerConstants.CONTROLLER_SERVICES_PORT_PROPERTY_KEY;
import static com.singularity.ee.controller.KAppServerConstants.DEFAULT_CONTROLLER_PORT_VALUE;

public class GCEConnector implements IConnector {

    private static final Logger LOG = Logger.getLogger(GCEConnector.class);


    private IControllerServices controllerServices;

    //private static final String API_VERSION = "v1";
    //private static final String GOOGLE_PROJECT = "google";
    //private static final String DEFAULT_PROJECT = "<project-id>";

    //private static final String BASE_URL = "https://www.googleapis.com/compute/" + API_VERSION + "/projects/";
    //private static final String DEFAULT_IMAGE = BASE_URL + GOOGLE_PROJECT + "/global/images/gcel-12-04-v20130104";
    //private static final String DEFAULT_MACHINE_TYPE = BASE_URL + DEFAULT_PROJECT + "/global/machineTypes/n1-standard-1";
    //private static final String DEFAULT_NETWORK = BASE_URL + DEFAULT_PROJECT + "/global/networks/default";

    private static final Map<String, String> IMAGE_URL;

    static {

        HashMap<String, String> tempUrlMap = new HashMap<String, String>();
        tempUrlMap.put("centos-6-v20131120", "https://www.googleapis.com/compute/v1/projects/centos-cloud/global/images/centos-6-v20131120");
        tempUrlMap.put("backports-debian-7-wheezy-v20131127", "https://www.googleapis.com/compute/v1/projects/debian-cloud/global/images/backports-debian-7-wheezy-v20131127");
        tempUrlMap.put("debian-7-wheezy-v20131120", "https://www.googleapis.com/compute/v1/projects/debian-cloud/global/images/debian-7-wheezy-v20131120");
        IMAGE_URL = Collections.unmodifiableMap(tempUrlMap);

    }

    @Override
    public void setControllerServices(IControllerServices iControllerServices) {
        this.controllerServices = iControllerServices;
    }

    @Override
    public int getAgentPort() {
        return controllerServices.getDefaultAgentPort();
    }

    @Override
    public IMachine createMachine(IComputeCenter iComputeCenter, IImage iImage, IMachineDescriptor iMachineDescriptor) throws InvalidObjectException, ConnectorException {

        final Compute connector = ConnectorLocator.getInstance().getConnector(iComputeCenter.getProperties(), controllerServices);

        final String projectId = Utils.getProjectId(iComputeCenter.getProperties(), controllerServices);
        final String zone = Utils.getZone(iMachineDescriptor.getProperties(), controllerServices);
        String instanceName = Utils.getInstanceName(iMachineDescriptor.getProperties(), controllerServices);
        String machineType = Utils.getMachineType(iImage.getProperties(), controllerServices);
        String image = Utils.getImage(iImage.getProperties(), controllerServices);

        AgentResolutionEncoder agentResolutionEncoder = getAgentResolutionEncoder(iComputeCenter);

        //create the boot disk
        Operation insertDiskExecute = createBootDisk(connector, instanceName, projectId, zone, image);

        CountDownLatch doneSignal = new CountDownLatch(1);
        waitForDiskToCreate(connector, projectId, zone, insertDiskExecute, doneSignal);
        try {
            doneSignal.await();
        } catch (InterruptedException e) {
            LOG.error("Waiting for disk creation interrupted", e);
        }

        Instance instance = populateInstance(projectId, zone, instanceName, machineType);

        boolean instanceCreated = false;
        try {
            Compute.Instances.Insert insert = connector.instances().insert(projectId, zone, instance);
            insert.execute();

            instanceCreated = true;
            return controllerServices.createMachineInstance(instance.getName(),
                    agentResolutionEncoder.getUniqueHostIdentifier(), iComputeCenter, iMachineDescriptor, iImage,
                    getAgentPort());
        } catch (IOException e) {
            LOG.error("Unable to create instance", e);
            throw new ConnectorException("Unable to create instance", e);
        } finally {
            if (!instanceCreated) {
                try {
                    Compute.Disks.Delete delete = connector.disks().delete(projectId, zone, instanceName);
                    delete.execute();
                } catch (IOException e) {
                    String message = "Machine create failed and unable to delete the boot disk! " +
                            "We have a boot disk with name " + instanceName + " which is not used by any instance." +
                            " Please remove the boot disk manually.";
                    LOG.error(message, e);
                    throw new ConnectorException(message, e);
                }
            }
        }
    }

    private AgentResolutionEncoder getAgentResolutionEncoder(IComputeCenter iComputeCenter) throws ConnectorException {
        IAccount account = iComputeCenter.getAccount();

        String controllerHost = null;
        try {
            controllerHost = System.getProperty(CONTROLLER_SERVICES_HOST_NAME_PROPERTY_KEY, InetAddress
                    .getLocalHost().getHostAddress());
        } catch (UnknownHostException e) {
            LOG.error(e);
            throw new ConnectorException(e);
        }

        int controllerPort = Integer.getInteger(CONTROLLER_SERVICES_PORT_PROPERTY_KEY, DEFAULT_CONTROLLER_PORT_VALUE);

        return new AgentResolutionEncoder(controllerHost, controllerPort,
                account.getName(), account.getAccessKey());
    }

    private Instance populateInstance(String projectId, String zone, String instanceName, String machineType) {
        Instance instance = new Instance();
        instance.setName(instanceName);
        instance.setMachineType("https://www.googleapis.com/compute/v1/projects/"+projectId+"/zones/"+zone+"/machineTypes/"+machineType);
        instance.setZone(zone);

        NetworkInterface networkInterface = new NetworkInterface();
        networkInterface.setName("Default");
        networkInterface.setNetwork("https://www.googleapis.com/compute/v1/projects/"+projectId+"/global/networks/default");

        AccessConfig accessConfig = new AccessConfig();
        accessConfig.setName("External NAT");
        accessConfig.setType("ONE_TO_ONE_NAT");
        
        networkInterface.setAccessConfigs(Lists.newArrayList(accessConfig));
        instance.setNetworkInterfaces(Lists.newArrayList(networkInterface));
        
        AttachedDisk attachedDisk = new AttachedDisk();
        attachedDisk.setBoot(true);
        attachedDisk.setType("PERSISTENT");
        attachedDisk.setMode("READ_WRITE");
        attachedDisk.setDeviceName(instanceName);
        String diskURL = getDiskURL(projectId, zone, instanceName);
        attachedDisk.setSource(diskURL);

        instance.setDisks(Lists.newArrayList(attachedDisk));
        return instance;
    }

    private void waitForDiskToCreate(final Compute connector, final String projectId, final String zone, Operation insertDiskExecute, final CountDownLatch doneSignal) {
        final String operationName = insertDiskExecute.getName();
        final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
        executorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    Compute.ZoneOperations.Get get = connector.zoneOperations().get(projectId, zone, operationName);
                    Operation operation = get.execute();
                    if ("DONE".equals(operation.getStatus())) {
                        doneSignal.countDown();
                        executorService.shutdown();
                    }
                } catch (IOException e) {
                    LOG.error("Unable to get the boot disk creation status", e);
                }
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    private Operation createBootDisk(Compute compute, String instanceName, String projectId, String zone, String image) throws ConnectorException {

        Disk disk = new Disk();
        disk.setName(instanceName);
        disk.setSourceImage(IMAGE_URL.get(image));
        Compute.Disks.Insert insertDisk = null;
        try {
            insertDisk = compute.disks().insert(projectId, zone, disk);
            insertDisk.setSourceImage(IMAGE_URL.get(image));
            return insertDisk.execute();
        } catch (IOException e) {
            LOG.error("Unable to create boot disk", e);
            throw new ConnectorException("Unable to create boot disk", e);
        }
    }

    private String getDiskURL(String projectId, String zone, String instanceName) {

        StringBuilder sb = new StringBuilder("https://www.googleapis.com/compute/v1/projects/");
        sb.append(projectId).append("/").append("zones/").append(zone).append("/").append("disks/").append(instanceName);
        return sb.toString();
    }

    @Override
    public void refreshMachineState(IMachine iMachine) throws InvalidObjectException, ConnectorException {
        IComputeCenter computeCenter = iMachine.getComputeCenter();
        IMachineDescriptor machineDescriptor = iMachine.getMachineDescriptor();
        final Compute connector = ConnectorLocator.getInstance().getConnector(computeCenter.getProperties(), controllerServices);

        final String projectId = Utils.getProjectId(computeCenter.getProperties(), controllerServices);
        final String zone = Utils.getZone(machineDescriptor.getProperties(), controllerServices);

        MachineState currentState = iMachine.getState();
        try {
            Compute.Instances.Get get = connector.instances().get(projectId, zone, iMachine.getName());
            Instance instance = get.execute();
            if (instance == null) {
                if (currentState != MachineState.STOPPED) {
                    iMachine.setState(MachineState.STOPPED);
                }
            } else {
                if ("RUNNING".equals(instance.getStatus())) {
                    List<NetworkInterface> networkInterfaces = instance.getNetworkInterfaces();
                    for (NetworkInterface networkInterface : networkInterfaces) {
                        List<AccessConfig> accessConfigs = networkInterface.getAccessConfigs();
                        for (AccessConfig accessConfig : accessConfigs) {
                            String natIP = accessConfig.getNatIP();
                            iMachine.setIpAddress(natIP);
                        }
                    }
                    iMachine.setState(MachineState.STARTED);
                } else if ("PROVISIONING".equals(instance.getStatus()) || "STAGING".equals(instance.getStatus())) {
                    iMachine.setState(MachineState.STARTING);
                } else {
                    iMachine.setState(MachineState.STOPPED);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    @Override
    public void terminateMachine(IMachine iMachine) throws InvalidObjectException, ConnectorException {
        IComputeCenter computeCenter = iMachine.getComputeCenter();
        IMachineDescriptor machineDescriptor = iMachine.getMachineDescriptor();
        final Compute connector = ConnectorLocator.getInstance().getConnector(computeCenter.getProperties(), controllerServices);

        final String projectId = Utils.getProjectId(computeCenter.getProperties(), controllerServices);
        final String zone = Utils.getZone(machineDescriptor.getProperties(), controllerServices);

        try {
            //Delete instance
            Compute.Instances.Delete deleteInstance = connector.instances().delete(projectId, zone, iMachine.getName());
            deleteInstance.execute();
            iMachine.setState(MachineState.STOPPED);

            //Delete boot disk
            Compute.Disks.Delete deleteDisk = connector.disks().delete(projectId, zone, iMachine.getName());
            deleteDisk.execute();

        } catch (IOException e) {
            LOG.error("Unable to terminate the instance", e);
            throw new ConnectorException("Unable to terminate the instance", e);

        }
    }

    @Override
    public void restartMachine(IMachine iMachine) throws InvalidObjectException, ConnectorException {

    }

    @Override
    public void deleteImage(IImage iImage) throws InvalidObjectException, ConnectorException {

    }

    @Override
    public void refreshImageState(IImage iImage) throws InvalidObjectException, ConnectorException {

    }

    @Override
    public void validate(IComputeCenter iComputeCenter) throws InvalidObjectException, ConnectorException {

        ConnectorLocator.getInstance().getConnector(iComputeCenter.getProperties(), controllerServices);
    }

    @Override
    public void configure(IComputeCenter iComputeCenter) throws InvalidObjectException, ConnectorException {

    }

    @Override
    public void unconfigure(IComputeCenter iComputeCenter) throws InvalidObjectException, ConnectorException {

    }

    @Override
    public void validate(IImageStore iImageStore) throws InvalidObjectException, ConnectorException {

    }

    @Override
    public void configure(IImageStore iImageStore) throws InvalidObjectException, ConnectorException {

    }

    @Override
    public void unconfigure(IImageStore iImageStore) throws InvalidObjectException, ConnectorException {

    }

    @Override
    public void validate(IImage iImage) throws InvalidObjectException, ConnectorException {

    }

    @Override
    public void configure(IImage iImage) throws InvalidObjectException, ConnectorException {

    }

    @Override
    public void unconfigure(IImage iImage) throws InvalidObjectException, ConnectorException {

    }
}
