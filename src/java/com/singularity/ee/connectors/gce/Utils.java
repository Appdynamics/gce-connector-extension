package com.singularity.ee.connectors.gce;

import com.singularity.ee.connectors.api.IControllerServices;
import com.singularity.ee.connectors.entity.api.IProperty;

public class Utils {

    public static final String SERVICE_ACCOUNT_KEY_PROP = "Service Account ID";

    public static final String SERVICE_ACCOUNT_P12_FILE_KEY_PROP = "Service Account P12 File Path";

    public static final String PROJECT_ID_KEY_PROP = "Project ID";

    public static final String ZONE_KEY_PROP = "Zone";

    public static final String INSTANCE_NAME_KEY_PROP = "Name";
    public static final String MACHINE_TYPE_KEY_PROP = "Machine Type";
    public static final String IMAGE_KEY_PROP = "Image";

    public static String getServiceAccountId(IProperty[] properties, IControllerServices controllerServices) {
        return controllerServices.getStringPropertyValueByName(properties, SERVICE_ACCOUNT_KEY_PROP);
    }

    public static String getServiceAccountP12File(IProperty[] properties, IControllerServices controllerServices) {
        return controllerServices.getStringPropertyValueByName(properties, SERVICE_ACCOUNT_P12_FILE_KEY_PROP);
    }

    public static String getProjectId(IProperty[] properties, IControllerServices controllerServices) {
        return controllerServices.getStringPropertyValueByName(properties, PROJECT_ID_KEY_PROP);
    }

    public static String getZone(IProperty[] properties, IControllerServices controllerServices) {
        return controllerServices.getStringPropertyValueByName(properties, ZONE_KEY_PROP);
    }

    public static String getInstanceName(IProperty[] properties, IControllerServices controllerServices) {
        return controllerServices.getStringPropertyValueByName(properties, INSTANCE_NAME_KEY_PROP);
    }

    public static String getMachineType(IProperty[] properties, IControllerServices controllerServices) {
        return controllerServices.getStringPropertyValueByName(properties, MACHINE_TYPE_KEY_PROP);
    }

    public static String getImage(IProperty[] properties, IControllerServices controllerServices) {
        return controllerServices.getStringPropertyValueByName(properties, IMAGE_KEY_PROP);
    }
}
