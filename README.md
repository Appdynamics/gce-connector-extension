Google Compute Engine Connector Extension
===========================================

##Use Case

Elastically grow/shrink instances into cloud/virtualized environments. There are four use cases for the connector. 

First, if the Controller detects that the load on the machine instances hosting an application is too high, the gce-connector-extension may be used to automate creation of new virtual machines to host that application. The end goal is to reduce the load across the application by horizontally scaling up application machine instances.

Second, if the Controller detects that the load on the machine instances hosting an application is below some minimum threshold, the gce-connector-extension may be used to terminate virtual machines running that application. The end goal is to save power/usage costs without sacrificing application performance by horizontally scaling down application machine instances.

Third, if the Controller detects that a machine instance has terminated unexpectedly when the connector refreshes an application machine state, the gce-connector-extension may be used to create a replacement virtual machine to replace the terminated application machine instance. This is known as our failover feature.

Lastly, the gce-connector-extension may be used to stage migration of an application from a physical to virtual infrastructure. Or the gce-connector-extension may be used to add additional virtual capacity to an application to augment a preexisting physical infrastructure hosting the application.   

##Directory Structure

<table><tbody>
<tr>
<th align="left"> File/Folder </th>
<th align="left"> Description </th>
</tr>
<tr>
<td class='confluenceTd'> src </td>
<td class='confluenceTd'> Contains source code to the gce connector extension </td>
</tr>
<tr>
<td class='confluenceTd'> target </td>
<td class='confluenceTd'> Only obtained when using maven. Run 'maven clean install' to get distributable .zip file </td>
</tr>
<tr>
<td class='confluenceTd'> pom.xml </td>
<td class='confluenceTd'> maven script file (required only if changing Java code) </td>
</tr>
</tbody>
</table>

##Prerequisite
Create a service account for your GCE project and download the privatekey file (.p12 file). To do this:

1. Log into the Google Cloud Console
2. Click on the project you want to use the GCE monitoring extension with (or create one if you don't have one yet).
3. Click "APIs & auth" in the left sidebar
4. Click "Credentials" in the left sidebar
5. Click "Create New Client ID" and choose "Service Account"
A private key file (.p12 file) will be downloaded for you. Note the password for the private key! This private key is your client private key.

##Installation

1. Clone the gce-connector-extension from GitHub
2. Run 'maven clean install' from the cloned gce-connector-extension directory
3. Download the file gce-connector.zip located in the 'dist' directory into \<controller install dir\>/lib/connectors
4. Unzip the downloaded file
5. Restart the Controller
6. Go to the controller dashboard on the browser. Under Setup->My Preferences->Advanced Features enable "Show Cloud Auto-Scaling features" if it is not enabled. 
7. On the controller dashboard click "Cloud Auto-Scaling" and configure the compute cloud and the image.

Click Compute Cloud->Register Compute Cloud. Refer to the image below

![alt tag](https://github.com/Appdynamics/gce-connector-extension/raw/master/gce_compute_cloud.png)

Click Image->Register Image. Refer to the image below

![alt tag](https://github.com/Appdynamics/gce-connector-extension/raw/master/gce_image.png)

To launch an instance click the image created in the above step and click on Launch Instance. Refer to the image below

![alt tag](https://github.com/Appdynamics/gce-connector-extension/raw/master/gce_launch_instance.png)

Google compute engine does not allow us to stop the instance (delete instance is allowed), so restart operation does not do anything. Refer to the image below

![alt tag](https://github.com/Appdynamics/gce-connector-extension/raw/master/gce_restart.png)


##Contributing

Always feel free to fork and contribute any changes directly here on GitHub.

##Community

Find out more in the [AppSphere](https://www.appdynamics.com/community/exchange/extension/google-compute-engine-cloud-connector-extension/) community.

##Support

For any questions or feature request, please contact [AppDynamics Center of Excellence](mailto:help@appdynamics.com).

