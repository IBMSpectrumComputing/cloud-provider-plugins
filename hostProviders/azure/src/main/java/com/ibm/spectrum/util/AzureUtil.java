/*
 * Copyright International Business Machines Corp, 2020
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ibm.spectrum.util;

import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.Date;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.UserPrincipal;
import java.net.Proxy;
import java.net.InetSocketAddress;

import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.time.StopWatch;


import com.microsoft.azure.PagedList;
import com.microsoft.azure.management.Azure;
import com.ibm.spectrum.util.StringUtils;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ibm.spectrum.constant.AzureConst;
import com.ibm.spectrum.model.AzureEntity;
import com.ibm.spectrum.model.AzureMachine;
import com.ibm.spectrum.model.AzureRequest;
import com.ibm.spectrum.model.AzureTemplate;
import com.ibm.spectrum.model.AzureConfig;
import com.microsoft.azure.credentials.AzureTokenCredentials;
import com.microsoft.azure.management.resources.Deployment;
import com.microsoft.azure.management.resources.DeploymentMode;
import com.microsoft.azure.management.resources.ResourceGroup;
import com.microsoft.azure.management.resources.fluentcore.arm.Region;
import com.microsoft.azure.management.resources.fluentcore.arm.models.Resource.DefinitionWithTags;
import com.microsoft.azure.management.resources.fluentcore.model.Creatable;
import com.microsoft.azure.management.compute.VirtualMachines;
import com.microsoft.azure.management.compute.AvailabilitySet;
import com.microsoft.azure.management.compute.GalleryImage;
import com.microsoft.azure.management.compute.PowerState;
import com.microsoft.azure.management.compute.StorageAccountTypes;
import com.microsoft.azure.management.compute.VirtualMachine;
import com.microsoft.azure.management.compute.VirtualMachineCustomImage;
import com.microsoft.azure.management.compute.VirtualMachine.DefinitionStages.WithExtension;
import com.microsoft.azure.management.compute.VirtualMachine.DefinitionStages.WithStorageAccount;
import com.microsoft.azure.management.compute.VirtualMachineSizeTypes;
import com.microsoft.azure.management.network.Network;
import com.microsoft.azure.management.network.NetworkInterface;
import com.microsoft.azure.management.network.NetworkInterface.DefinitionStages.WithCreate;
import com.microsoft.azure.management.network.NetworkSecurityGroup;
import com.microsoft.azure.management.network.PublicIPAddress;
import com.microsoft.azure.management.network.Subnet;
import com.microsoft.azure.management.storage.StorageAccount;
import com.microsoft.azure.management.storage.StorageAccounts;
import com.microsoft.azure.management.resources.fluentcore.utils.SdkContext;
import com.microsoft.rest.LogLevel;
/**
 * @ClassName: AzureUtil
 * @Description: The common utilities
 * @author xawangyd
 * @date Jan 26, 2016 3:36:12 PM
 * @version 1.0
 */
public class AzureUtil {
    private static Logger log = LogManager.getLogger(AzureUtil.class);

    /**
     * home directory
     */
    private static String homeDir = null;

    /**
     * conf directory
     */
    private static String confDir = null;

    /**
     * log directory
     */
    private static String logDir = null;

    /**
     * work directory
     */
    private static String workDir = null;

    /**
     * Azure client
     */
    private static Azure azure = null;

    /**
     * Azure region
     */
    private static Region region =  null;

    /**
     * EC2 configuration
     */
    private static AzureConfig config;

    /**
     * provider name
     */
    private static String providerName;

    /**
     * Azure db file
     */
    private static String provStatusFile;

    /**
     * @return homeDir
     */
    public static String getHomeDir() {
        return homeDir;
    }

    /**
     * @param homeDir
     *            the homeDir to set
     */
    public static void setHomeDir(String dir) {
        if (null == AzureUtil.homeDir) {
            AzureUtil.homeDir = dir;
        }
    }

    /**
     * @return confDir
     */
    public static String getConfDir() {
        return confDir;
    }

    /**
     * @param confDir
     *            the confDir to set
     */
    public static void setConfDir(String dir) {
        if (null == AzureUtil.confDir) {
            AzureUtil.confDir = dir;
        }
    }

    /**
     * @return logDir
     */
    public static String getLogDir() {
        return logDir;
    }

    /**
     * @param logDir
     *            the logDir to set
     */
    public static void setLogDir(String dir) {
        if (null == AzureUtil.logDir) {
            AzureUtil.logDir = dir;
        }
    }

    /**
     * @return workDir
     */
    public static String getWorkDir() {
        return workDir;
    }

    /**
     * @param workDir
     *            the workDir to set
     */
    public static void setWorkDir(String dir) {
        if (null == AzureUtil.workDir) {
            AzureUtil.workDir = dir;
        }
    }

    /**
     * @return config
     */
    public static AzureConfig getConfig() {
        return config;
    }

    /**
     * @param config
     *            the config to set
     */
    public static void setConfig(AzureConfig config) {
        if (null == AzureUtil.config) {
            AzureUtil.config = config;
        }
    }

    /**
     * @return providerName
     */
    public static String getProviderName() {
        return providerName;
    }

    /**
     * @param providerName
     *            the providerName to set
     */
    public static void setProviderName(String providerName) {
        if (null == AzureUtil.providerName) {
            AzureUtil.providerName = providerName;
        }
    }

    /**
     * @return provider Status File
     */
    public static String getProvStatusFile() {
        return provStatusFile;
    }

    /**
     * @param provStatusFile
     *            the provStatusFile to set
     */
    public static void setProvStatusFile(String provStatusFile) {
        if (null == AzureUtil.provStatusFile) {
            AzureUtil.provStatusFile = provStatusFile;
        }
    }

    /**
     * @param resourceGroupName
     * @param networkName
     * Get the network based on the provide network name
     * @return
     */
    public static Network getNetwork(String resourceGroupName, String networkName) {
        Network network = null;
        try {
            network =  azure.networks().getByResourceGroup(resourceGroupName, networkName);
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            log.error("Get network error. Illegal Network ID. " + e.getMessage());
        } catch (Exception e) {
            // TODO Auto-generated catch block
            log.error("Get network error." + e.getMessage());
        }

        return network;
    }


    /**
     * Get the region
     * @return
     */
    public static synchronized Region getRegion() {
        if (null != region) {
            return region;
        }

        String regionName = "";
        regionName = config.getAzureRegion();
        if(StringUtils.isNullOrEmpty(regionName)) {
            regionName = "ASIA_SOUTHEAST";
            config.setAzureRegion(regionName);
            log.warn("AZURE_REGION is not defined in azureprov_config.json. Using default region: " + regionName);
        }
        region = Region.fromName(regionName);
        return region;
    }

    /**
     * Get the Azure LogLel as user defined
     * @return
     */
    public static LogLevel getAzureLogLevel(String logLevel) {
        LogLevel azureLogLevel = LogLevel.NONE;
        if ("DEBUG".equalsIgnoreCase(logLevel)) {
            azureLogLevel = LogLevel.BODY_AND_HEADERS;
        } else if ("INFO".equalsIgnoreCase(logLevel)) {
            azureLogLevel = LogLevel.BODY;
        } else if ("WARN".equalsIgnoreCase(logLevel)) {
            azureLogLevel = LogLevel.NONE;
        } else if ("ERROR".equalsIgnoreCase(logLevel)) {
            azureLogLevel = LogLevel.NONE;
        } else if ("FATAL".equalsIgnoreCase(logLevel)) {
            azureLogLevel = LogLevel.NONE;
        } else {
            azureLogLevel = LogLevel.NONE;
        }
        return azureLogLevel;
    }

    /**
     *
     * @Title: getAzureClient @Description: Initialize Azure
     * @param @return @return Azure @throws
     */
    public static synchronized Azure getAzureClient() {
        if (null != azure) {
            return azure;
        }

        String httpProxyHost = "", httpProxyPort = "";
        Boolean https = false, proxy = false;
        try {
            if ((httpProxyHost = System.getProperty("http.proxyHost")) != null) {
                httpProxyPort = System.getProperty("http.proxyPort");
            } else {
                if ((httpProxyHost = System.getProperty("https.proxyHost")) != null) {
                    httpProxyPort = System.getProperty("https.proxyPort");
                    https = true;
                }
            }
        } catch (Exception e) {}
        int pPort = 80;
        if (httpProxyHost != null && !httpProxyHost.isEmpty()) {
            proxy = true;
            try {
                pPort = Integer.parseInt(httpProxyPort);
            } catch (NumberFormatException e) {
                pPort = (https ? 443 : 80);
            }
        }
        System.clearProperty("http.proxyHost");
        System.clearProperty("https.proxyHost");
        System.clearProperty("http.proxyPort");
        System.clearProperty("https.proxyPort");

        String credentialsFile = "";
        try {
            credentialsFile = config.getAzureCredentialFile();
            if (StringUtils.isNullOrEmpty(credentialsFile)) {
                credentialsFile = confDir + "/conf/credentials";
            }
            final File credFile = new File(credentialsFile);

            /*
             *  Create the Azure client object so we can call various APIs.
             *
             *  .withLogLevel(getAzureLogLevel(config.getLogLevel()))
             */
            if (!proxy) {
                azure = Azure.configure()
                        //.withLogLevel(LogLevel.BODY)
                        .authenticate(credFile)
                        .withDefaultSubscription();
            } else {
                Proxy prox = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(httpProxyHost, pPort));
                azure = Azure.configure()
                        .withProxy(prox)
                        //.withLogLevel(LogLevel.BODY)
                        .authenticate(credFile)
                        .withDefaultSubscription();
            }
        } catch (Exception e) {
            log.error("Failed to load the " + providerName + " credentials file.", e);

            StringBuilder b = new StringBuilder();
            b.append("Cannot load the credentials from the credential profiles file. ")
            .append("Make sure that your credentials file is at the correct location (").append(confDir)
            .append("), and is in valid format.");
            log.error(b);
        }

        return azure;
    }


    /**
     *
     */
    public static String getSshPubKeyString(String keyFile) {
        if(StringUtils.isNullOrEmpty(keyFile)) {
            keyFile = confDir + "/data";
            log.warn("sshPubKeyFile is not defined in azureprov_templates.json. Using default key file location: " + keyFile);
        }
        File kf = new File(keyFile);
        if (kf.exists()) {
            log.info("The local key pair exists:" + kf.getPath());
        } else {
            log.error("The local ssh public key file does not exist.");
            return null;
        }

        BufferedReader reader = null;
        String keyString = null;
        try {
            reader = new BufferedReader(new FileReader(kf));
            keyString =  reader.readLine();
            if (StringUtils.isNullOrEmpty(keyString)) {
                log.error("The local ssh public key file is emputy.");
            }
            reader.close();
        }   catch (IOException e) {
            log.error("Failed to read sshPubKeyFile." + e.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    log.error("Failed to close sshPubKeyFile." + e.getMessage());
                }
            }
        }
        return keyString;

    }

    private static void validateAndAddFieldValue(String type, String fieldValue, String fieldName, String errorMessage,
            JsonNode tmp) throws IllegalAccessException {
        final ObjectMapper mapper = new ObjectMapper();
        final ObjectNode parameter = mapper.createObjectNode();
        parameter.put("type", type);
        if (type == "int") {
            parameter.put("defaultValue", Integer.parseInt(fieldValue));
        } else {
            parameter.put("defaultValue", fieldValue);
        }
        ObjectNode.class.cast(tmp.get("parameters")).replace(fieldName, parameter);
    }

    @SuppressWarnings("deprecation")
    private static void validateAndAddJsonNode(String type, JsonNode fieldValue, String fieldName, String errorMessage,
            JsonNode tmp) throws IllegalAccessException {
        final ObjectMapper mapper = new ObjectMapper();
        final ObjectNode parameter = mapper.createObjectNode();
        parameter.put("type", type);
        parameter.put("defaultValue", fieldValue);
        ObjectNode.class.cast(tmp.get("parameters")).replace(fieldName, parameter);
    }

    static class DeployVMThread extends Thread {

        AzureTemplate t = null;
        JsonNode tmp = null;
        NetworkSecurityGroup netSg= null;
        Map<String, String> tags = null;
        boolean hasPublicIP = false;
        Collection<AzureMachine> azureMachines = null;
        AtomicInteger rstCount;
        DeployVMThread(AzureTemplate t, Map<String, String> tags, JsonNode tmp, NetworkSecurityGroup netSg, boolean hasPublicIP, Collection<AzureMachine> azureMachines, AtomicInteger rstCount) {
            this.t = t;
            this.tags = tags;
            this.tmp = tmp;
            this.netSg = netSg;
            this.hasPublicIP  = hasPublicIP;
            this.azureMachines = azureMachines;
            this.rstCount = rstCount;
        }

        public void run() {

            // create resource group
            String rgName = SdkContext.randomResourceName("vmrg", 24);
            AzureMachine machine = null;
            try {
                azure.resourceGroups().define(rgName).withRegion(getRegion()).create();
                log.info("Created a resource group with name: " + rgName);

                /* Fix binding the hostname and the ip address
                * create a network interface first
                */
                String networkInterfaceName = SdkContext.randomResourceName(AzureConst.AZURE_NET_INTERFACE_PREFIX, 24);
                NetworkInterface networkInterface = null;
                if (hasPublicIP) {
                    networkInterface = azure.networkInterfaces().define(networkInterfaceName)
                                       .withRegion(getRegion())
                                       .withExistingResourceGroup(rgName)
                                       .withExistingPrimaryNetwork(getNetwork(t.getResourceGroup(), t.getVirtualNetwork()))
                                       .withSubnet(t.getSubnetName())
                                       .withPrimaryPrivateIPAddressDynamic()
                                       .withExistingNetworkSecurityGroup(netSg)
                                       .withNewPrimaryPublicIPAddress()
                                       /* SUP_BY_DEV# 237161 exception occurs for the long tag platformsettings.host_environment.disablehyperthreading */
                                       //.withTags(tags)
                                       .create();

                } else {
                    networkInterface = azure.networkInterfaces().define(networkInterfaceName)
                                       .withRegion(getRegion())
                                       .withExistingResourceGroup(rgName)
                                       .withExistingPrimaryNetwork(getNetwork(t.getResourceGroup(), t.getVirtualNetwork()))
                                       .withSubnet(t.getSubnetName())
                                       .withPrimaryPrivateIPAddressDynamic()
                                       .withExistingNetworkSecurityGroup(netSg)
                                       /* SUP_BY_DEV# 237161 exception occurs for the long tag platformsettings.host_environment.disablehyperthreading */
                                       //.withTags(tags)
                                       .create();
                }
                /**
                 * get the private ip address, and create hostname base on private ip address
                 */
                String primaryPrivateIp = networkInterface.primaryPrivateIP();
                networkInterface.update().withPrimaryPrivateIPAddressStatic(primaryPrivateIp).apply();
                log.info("Done update the dynamic private Ip to static Ip " + primaryPrivateIp);

                String hostname = AzureConst.AZURE_HOSTNAME_PREFIX + primaryPrivateIp.replace('.', '-');

                //validateAndAddFieldValue("string", hostname, "availabilitySetsName", null, tmp);
                String publicIpAdress = null;
                if (hasPublicIP) {
                    String publicIpAddressId = networkInterface.ipConfigurations().get("primary").publicIPAddressId();
                    publicIpAdress = publicIpAddressId.substring(publicIpAddressId.lastIndexOf('/') + 1);
                    validateAndAddFieldValue("string", publicIpAdress, "publicIPAddressesName", null, tmp);
                }
                validateAndAddFieldValue("string", primaryPrivateIp, "privateIpAddress", null, tmp);
                validateAndAddFieldValue("string", hostname, "virtualMachineName", null, tmp);
                validateAndAddFieldValue("string", networkInterfaceName, "networkInterfaceName", null, tmp);
                String templateJson = tmp.toString();
                azure.deployments().define(hostname)
                .withExistingResourceGroup(rgName)
                .withTemplate(templateJson)
                .withParameters("{}")
                .withMode(DeploymentMode.INCREMENTAL)
                .beginCreate();

                log.info("Started a deployment for an Azure Virtual Machine with managed disks: " + hostname);

                // bellow while loop may create thousands of read api calls when VM creation very slow.
//                while (true) {
//                    if (azure.virtualMachines().getByResourceGroup(rgName, hostname) != null){
//                	    synchronized (virtualMachines) {
//                		    virtualMachines.add(azure.virtualMachines().getByResourceGroup(rgName, hostname));
//	                    }
//          	            log.info("Current deployment status : " + azure.deployments().getByResourceGroup(rgName, hostname).provisioningState());
//                        break;
//                    }else if ("Failed".equals(azure.deployments().getByResourceGroup(rgName, hostname).provisioningState())){
//                	    log.info("Deleting Resource Group: " + rgName);
//                        azure.resourceGroups().beginDeleteByName(rgName);
//                	    break;
//                    }
//                }
                machine = new AzureMachine();
                machine.setName(hostname);
                machine.setResourceGroup(rgName);
                machine.setPrivateIpAddress(primaryPrivateIp);
                if (hasPublicIP) {
                    // the public ip is still null now
                    machine.setPublicIpAddress(null);
                }
                synchronized (azureMachines) {
                    azureMachines.add(machine);
                }

            } catch (Exception e) {
                log.error("Create instance error.", e);
                log.info("Deleting Resource Group: " + rgName);
                azure.resourceGroups().beginDeleteByName(rgName);
            } finally {
                rstCount.decrementAndGet();
            }
        }
    }

    /**
     * @Title: getSubnet
     * @Description: get Virtual Network on Azure
     * @param
     * @return List<Instance> @throws
     */
    public static Network getVNet(AzureTemplate t) {
        Network network = null;
        try {
            azure = getAzureClient();
            network = azure.networks().getByResourceGroup(t.getResourceGroup(), t.getVirtualNetwork());
        } catch (Exception e) {
            log.error("Failed to get Virtual Network <" + t.getVirtualNetwork() + "> in Resource Group <" + t.getResourceGroup() + ">.");
            log.error(e);
        }

        return network;
    }

    /**
     * @Title: getSubnet
     * @Description: get Subnet on Azure
     * @param
     * @return List<Instance> @throws
     */
    public static Subnet getSubnet(AzureTemplate t, Network vnet) {
        Subnet subnet = null;

        if (vnet == null) {
            log.error("Input parameter error. Network cannot be null.");
            return null;
        }
        try {
            azure = getAzureClient();
            if (t.getSubnetName() != null) {
                subnet = vnet.subnets().get(t.getSubnetName());
            }
        } catch (Exception e) {
            log.error("Failed to get Subnet <" + t.getSubnetName() + "> in VirualNetwork <" + t.getVirtualNetwork() + "> in resource group <" + t.getResourceGroup() + ">.");
            log.error(e);
        }

        return subnet;
    }

    /**
     * @Title: getNSG
     * @Description: get network Security Group on Azure
     * @param
     * @return List<Instance> @throws
     */
    public static NetworkSecurityGroup getNSG(AzureTemplate t, Subnet subnet) {
        NetworkSecurityGroup netSg = null;

        try {
            if (t.getSgId() == null) {
                log.info("Tempalte <" + t.getTemplateId()
                         + "> doesn't configure securityGroup. Directly get secutity group of subnet <"
                         + t.getSubnetName() + ">");
                if (subnet != null) {
                    netSg = subnet.getNetworkSecurityGroup();
                } else {
                    log.error("Cannot get Security Group for tempalte <" + t.getTemplateId()
                              + "> because subnet is null.");
                }
            } else {
                netSg = azure.networkSecurityGroups().getByResourceGroup(t.getResourceGroup(), t.getSgId());
            }
        } catch (Exception e) {
            log.error("Failed to get secutity group <" + t.getSgId() + "> in resource group <" + t.getResourceGroup()
                      + "> or the subnet <" + t.getSubnetName() + "> default security group .");
            log.error(e);
        }

        return netSg;
    }

    /**
     * @Title: getImageReferenceJson
     * @Description: get lsf image from either custom image or compute galleries on Azure
     * @param
     * @return JSON string representing the image reference
     */
    public static String getImageReferenceJson(AzureTemplate t) {
        // Use getters to access the values
        String imageId = t.getImageId();
        String imageName = t.getImageName();

        String imageReferenceId;

        if (imageId != null && !imageId.trim().isEmpty()) {
            try {
                Azure azure = getAzureClient();
                VirtualMachineCustomImage image = azure.virtualMachineCustomImages()
                    .getByResourceGroup(t.getResourceGroup(), imageId);

                if (image == null) {
                    throw new RuntimeException("Custom image not found: " + imageId);
                }

                imageReferenceId = image.id();  // This gives the full Azure Resource ID
            } catch (Exception e) {
                log.error("Failed to get custom image from Azure: " + imageId);
                throw new RuntimeException("Error retrieving custom image", e);
            }

        } else if (imageName != null && !imageName.trim().isEmpty()) {
            // Gallery image ID is already complete
            imageReferenceId = imageName;
        } else {
            throw new IllegalArgumentException("Either imageId or imageName must be provided.");
        }

        return String.format("{ \"id\": \"%s\" }", imageReferenceId);
    }

    /**
     * @Title: createVM
     * @Description: create EC2 VM
     * @param
     * @return List<Instance> @throws
     */
    @SuppressWarnings("unchecked")
    public static Collection<AzureMachine> createVM(AzureTemplate t, String tagValue) {
        String[] instanceTagStr = null;
        int vmCount = 0;
        String rootUserName = null;
        Collection<AzureMachine> azurelMachines = new ArrayList<>();

        try {
            azure = getAzureClient();
            vmCount = t.getVmNumber();

            // attach tags
            Map<String, String> tags = new HashMap<String, String>();
            if (!StringUtils.isNullOrEmpty(tagValue)) {
                tags.put("RC_ACCOUNT", tagValue);
            }
            // tags.put("InstanceID", instance.getInstanceId());
            if (!StringUtils.isNullOrEmpty(t.getInstanceTags())) {
                instanceTagStr = t.getInstanceTags().split(";");
                for (String inst : instanceTagStr) {
                    String[] instSubStr = inst.split("=", 2);
                    tags.put(instSubStr[0], instSubStr[1]);
                }
            }

            if (!StringUtils.isNullOrEmpty(t.getRootUserName())) {
                rootUserName = t.getRootUserName();
            } else {
                rootUserName = AzureConst.AZURE_ROOT_USER_NAME;
            }

            Network network = getVNet(t);
            if (network == null) {
                log.error("Can not find Virtual Network <" + t.getVirtualNetwork() + "> in resource group <"
                          + t.getResourceGroup() + "> for tempalte <" + t.getTemplateId() + "> to create VM.");
                return null;
            }
            Subnet primarySubnet = getSubnet(t, network);
            if (primarySubnet == null) {
                log.error("Can not find Subnet <" + t.getSubnetName() + "> in resource group <" + t.getResourceGroup()
                          + "> for tempalte <" + t.getTemplateId() + "> to create VM.");
                return null;
            }
            NetworkSecurityGroup netSg = getNSG(t, primarySubnet);
            if (netSg == null) {
                log.error("Cannot find Network security group <" + t.getSgId() + "> in resource group <"
                          + t.getResourceGroup() + "> for template <" + t.getTemplateId() + "> to create VM.");
                return null;
            }
            // Handle image reference with imageId taking precedence
            String imageRefJson = getImageReferenceJson(t);
            if (imageRefJson == null) {
                log.error("No valid image reference could be created");
                return null;
            }
            String templatefile = "";
            boolean hasPublicIP = false;
            if (t.getWithPublicIP() == null || t.getWithPublicIP().equals(Boolean.FALSE)) {
                templatefile = "/template.json";
            } else {
                templatefile = "/templateWithPublicIp.json";
                hasPublicIP = true;
            }
            final InputStream embeddedTemplate;
            embeddedTemplate = AzureUtil.class.getResourceAsStream(templatefile);
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode tmp = mapper.readTree(embeddedTemplate);

            validateAndAddFieldValue("string", rootUserName, "adminUsername", null, tmp);
            validateAndAddFieldValue("string", getSshPubKeyString(t.getSshPubKeyFile()), "adminPublicKey", null, tmp);

            StringBuilder execCmd = new StringBuilder();
            if (!StringUtils.isNullOrEmpty(tagValue)) {
                execCmd.append("export rc_account=");
                execCmd.append(tagValue);
                execCmd.append(";");
            }
            if (!StringUtils.isNullOrEmpty(t.getTemplateId())) {
                execCmd.append("export template_id=" + t.getTemplateId() + ";");
            }
            String clusterName = System.getProperty("clusterName");
            if (!StringUtils.isNullOrEmpty(clusterName)) {
                execCmd.append("export clustername=" + clusterName + ";");
            }
            String providerName = System.getenv("PROVIDER_NAME");
            if (!StringUtils.isNullOrEmpty(providerName)) {
                execCmd.append("export providerName=" + providerName + ";");
            }
            String userDataStr = t.getUserData();
            if (!StringUtils.isNullOrEmpty(userDataStr)) {
                String[] userDataArray = userDataStr.split(";");
                for (int i = 0; i < userDataArray.length; i++) {
                    String oneUserData = userDataArray[i];
                    if (!StringUtils.isNullOrEmpty(oneUserData)) {
                        String oneUserDataTuple[] = oneUserData.split("=");
                        if (!StringUtils.isNullOrEmpty(oneUserDataTuple[0])
                                && !StringUtils.isNullOrEmpty(oneUserDataTuple[1])) {
                            execCmd.append("export ");
                            execCmd.append(oneUserDataTuple[0].toUpperCase());
                            execCmd.append("=");
                            execCmd.append(oneUserDataTuple[1]);
                            execCmd.append(";");
                        }
                    }
                }
            }

            if (!StringUtils.isNullOrEmpty(t.getUserDataUri())) {
                String myLinuxUserScript = t.getUserDataUri();
                validateAndAddFieldValue("string", myLinuxUserScript, "postScriptUri", null, tmp);
                execCmd.append("bash ");
                execCmd.append(myLinuxUserScript.substring(myLinuxUserScript.lastIndexOf('/') + 1));
                log.debug("postScriptCommand:" + execCmd.toString());
                validateAndAddFieldValue("string", execCmd.toString(), "postScriptCommand", null, tmp);
            } else {
                validateAndAddFieldValue("string", null, "postScriptUri", null, tmp);
                validateAndAddFieldValue("string", null, "postScriptCommand", null, tmp);
            }
            validateAndAddJsonNode("object", mapper.readTree(mapper.writeValueAsString(tags)), "tagValues", null, tmp);
            validateAndAddFieldValue("string", netSg.name(), "networkSecurityGroups", null, tmp);
            validateAndAddFieldValue("string", imageRefJson, "imageReference", null, tmp);
            validateAndAddFieldValue("string", t.getSubnetName(), "subnetName", null, tmp);
            validateAndAddFieldValue("string", t.getResourceGroup(), "virtualNetworkResourceGroup", null, tmp);
            validateAndAddFieldValue("string", t.getVirtualNetwork(), "virtualNetworkName", null, tmp);
            validateAndAddFieldValue("string", t.getStorageAccountType(), "storageAccountType", null, tmp);
            validateAndAddFieldValue("string", t.getVmType(), "virtualMachineSize", null, tmp);

            log.info("Deploment Begin:");
            AtomicInteger rstCount = new AtomicInteger(vmCount);
            for (int i = 0; i < vmCount; i++) {
                Thread deployThread = new DeployVMThread(t, tags, tmp, netSg, hasPublicIP, azurelMachines, rstCount);
                deployThread.start();
            }
            while (true) {
                if (rstCount.get() == 0)
                    break;
            }
            log.info("Deploment End.");
            log.info("The created machines: " + azurelMachines);
            return azurelMachines;
        } catch (Exception e) {
            log.error("Create instance error.", e);
        }
        return null;
    }

    /**
     * @Title: deleteVM
     * @Description: delete EC2 VM
     * @param
     * @param
     * instanceIds
     * @param
     * @return
     * @return List<InstanceStateChange>
     * @throws
     */
    public static List<AzureMachine>  deleteVM(List<AzureMachine> azureMachinesLst) {
        List<AzureMachine>  vmList = null;
        if (CollectionUtils.isEmpty(azureMachinesLst)) {
            log.error("Invalid instance Ids: " + azureMachinesLst);
            return null;
        }

        try {
            for(AzureMachine machine : azureMachinesLst) {
                deleteVM(machine);
            }
            vmList = listVMStatus(azureMachinesLst);
        } catch (Exception e) {
            log.error("Delete instance error.", e);
        }

        return vmList;
    }

    public static boolean deleteVM(AzureMachine azureMavhine) {
        if (log.isDebugEnabled()) {
            log.debug("Start in class AzureImpl in method deleteVM(), with parameter azureMavhine" + azureMavhine);
        }
        boolean rc = true;
        String vmRgName = null;
        try {
            Azure azure = getAzureClient();
            vmRgName = azureMavhine.getResourceGroup();
            if (StringUtils.isNullOrEmpty(vmRgName)) {
                VirtualMachine vm = azure.virtualMachines().getById(azureMavhine.getMachineId());
                if (vm == null) {
                    log.warn("Instance is not found: " + azureMavhine.getMachineId());
                    return rc;
                }
                vmRgName = vm.resourceGroupName();
            }
            ResourceGroup rg = azure.resourceGroups().getByName(vmRgName);
            String rgStatus = null;
            if (rg != null) {
                rgStatus = rg.provisioningState();
                log.debug("Resource group: " + vmRgName + " status " + rgStatus);
            }
            if(rg == null ||
                    (rg != null && rgStatus.equals("Deleting"))) {
                log.info("Resource group: " + vmRgName + " is already deleted or in deleting process.");
            } else {
                azure.resourceGroups().beginDeleteByName(vmRgName);
                log.info("Delete instance: " + azureMavhine.getMachineId() + ", resource group: " + vmRgName);
            }
        } catch (Exception e) {
            log.error("Failed to delete the instance resoure group " + vmRgName, e);
            rc = false;
        }
        return rc;
    }

    /**
     *
     * @Title: listVM
     * @Description: List all VM for current Azure subscription
     * @param
     * @return @return
     * List<Reservation> @throws
     */
    public static Map<String, VirtualMachine> listVM() {
        if (log.isDebugEnabled()) {
            log.debug("Start in class AzureImpl in method listVM(), list all VMs");
        }
        Map<String, VirtualMachine> vmMap = new HashMap<String, VirtualMachine>();

        Azure azure = getAzureClient();
        PagedList<VirtualMachine> pageListVMs = azure.virtualMachines().list();
        if (pageListVMs != null) {
            for (VirtualMachine vm : pageListVMs) {
                String vmRg = vm.resourceGroupName();
                // Resource group name may be upper case on Azure.
                if (vmRg.toLowerCase().startsWith("vmrg")) {
                    log.debug("listVM(): add the vm to map, id <" + vm.id() + "> in resource group <" + vmRg + ">");
                    vmMap.put(vm.id().toLowerCase(), vm);
                }
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("End in class AzureImpl in method listVM(), list all VMs. vmMap:" + vmMap);
        }

        return vmMap;
    }

    /**
     *
     * @Title: listVMStatus
     * @Description: query Instance status, it is only used by delteVM now.
     * @param
     * @param
     * azureMachineList
     * @param
     *  @return
     *  @return List<AzureMachine>
     *  @throws
     */
    public static List<AzureMachine>  listVMStatus(List<AzureMachine> azureMachineList) {
        List<AzureMachine> vmList = new ArrayList<AzureMachine>();
        if (CollectionUtils.isEmpty(azureMachineList)) {
            log.error("Empty Azure Machine List: " + azureMachineList);
            return null;
        }

        Azure azure = getAzureClient();
        for(AzureMachine instance: azureMachineList) {
            VirtualMachine vm = azure.virtualMachines().getById(instance.getMachineId());
            String ps = null;
            if(vm != null) {
                ps = vm.provisioningState();
            }

            if(vm == null) {
                instance.setStatus("Deleted");
                instance.setResult("succeed");
                log.warn("VM is not found. It may have been deleted. Vm: " + instance.toString());
            } else if(vm != null && ps != null) {
                instance.setStatus(ps);
                instance.setResult(ps);
            }
            vmList.add(instance);
        }

        return vmList;
    }

    /**
     *
     * @Title: listVM @Description: List VM by ID @param @param
     * instanceIds @param @return @return List<Reservation> @throws
     */
    public static Map<String, VirtualMachine> listVM(List<String> instanceIds) {
        if (CollectionUtils.isEmpty(instanceIds)) {
            log.debug("Input instance Ids list is null: " + instanceIds);
            return null;
        }

        Azure azure = getAzureClient();
        Map<String, VirtualMachine> vmMap = new HashMap<String, VirtualMachine>();
        for(String instanceId: instanceIds) {
            VirtualMachine vm = azure.virtualMachines().getById(instanceId);
            vmMap.put(instanceId, vm);
        }

        return vmMap;
    }


    /**
     * @Title: writeToFile @Description: write data to file @param @param
     * fileName @param @param data @return void @throws
     */
    public static synchronized void writeToFile(File f, String data) {
        BufferedWriter bw = null;
        try {
            // If file does not exists, then create it
            if (!f.exists()) {
                f.createNewFile();
            }

            // append == true, append data to file
            FileWriter fw = new FileWriter(f, false);
            bw = new BufferedWriter(fw);
            bw.write(data);

            log.info("Write data to file done: " + f.getName());
        } catch (IOException e) {
            log.error("Write data to file error.", e);
        } finally {
            try {
                if (null != bw) {
                    bw.close();
                    bw = null;
                }
            } catch (IOException ex) {
                log.error("Close IO error.", ex);
            }
        }
    }

    /**
     *
     * @Title: toJsonTxt @Description: Object to json text @param @param
     * obj @param @return @return String @throws
     */
    public static synchronized <T> String toJsonTxt(T obj) {
        String jsonTxt = "";
        ObjectMapper mapper = new ObjectMapper();

        try {
            jsonTxt = mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Change object to json text error.", e);
        }

        return jsonTxt;
    }

    /**
     *
     * @Title: toJsonFile @Description: Object to json file @param @param
     * obj @param @return @return String @throws
     */
    public static synchronized <T> void toJsonFile(T obj, String jfname) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.writeValue(new File(jfname), obj);
        } catch (Exception e) {
            log.error("Write object to json file error.", e);
        }
    }

    /**
     *
     *
     * @Title: toJsonFile @Description: Object to json file @param @param
     * obj @param @param jf @return void @throws
     */
    public static synchronized <T> void toJsonFile(T obj, File jf) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.writeValue(jf, obj);
        } catch (Exception e) {
            log.error("Write object to json file error.", e);
        }
    }

    /**
     *
     *
     * @Title: toObject @Description: Json file to Object @param @param
     * jsonFile @param @param type @param @return @return T @throws
     */
    public static <T> T toObject(File jsonFile, Class<T> type) {
        ObjectMapper mapper = new ObjectMapper();

        try {
            return (T) mapper.readValue(jsonFile, type);
        } catch (IOException e) {
            log.error("Change json file to object error.", e);
        }

        return null;
    }

    /**
     *
     *
     * @Title: toObject @Description: Json text to Object @param @param
     * jsonTxt @param @param valueType @param @return @return T @throws
     */
    public static <T> T toObject(String jsonTxt, Class<T> type) {
        ObjectMapper mapper = new ObjectMapper();

        try {
            return (T) mapper.readValue(jsonTxt, type);
        } catch (IOException e) {
            log.error("Change json text to object error.", e);
        }

        return null;
    }

    /**
     *
     * @Title: writeObject @Description: Write object to file @param @param
     * obj @param @return @return String @throws
     */
    public static <T> String writeObject(T obj) {
        ObjectOutputStream out = null;
        try {
            String fname = UUID.randomUUID().toString();
            out = new ObjectOutputStream(new FileOutputStream(new File(fname)));
            out.writeObject(obj);
            return fname;
        } catch (Exception e) {
            log.error("Write object to file error.", e);
        } finally {
            try {
                if (null != out) {
                    out.close();
                    out = null;
                }
            } catch (IOException e) {
                log.error("Close IO error.", e);
            }
        }

        return "";
    }

    /**
     *
     * @Title: readObject @Description: read object from file @param @param
     * file @param @return @return T @throws
     */
    @SuppressWarnings("unchecked")
    public static <T> T readObject(String fname) {
        ObjectInputStream in = null;
        try {
            in = new ObjectInputStream(new FileInputStream(new File(fname)));
            T obj = (T) in.readObject();
            return obj;
        } catch (Exception e) {
            log.error("Read object from file error.", e);
        } finally {
            try {
                if (null != in) {
                    in.close();
                    in = null;
                }
            } catch (IOException e) {
                log.error("Close IO error.", e);
            }
        }

        return null;
    }

    /**
     *
     * @Title: updateToFile @Description: update VM to local file @param @param
     * vmMap @return void @throws
     */
    public static void updateToFile(Map<String, AzureMachine> vmMap) {
        File jf = new File(workDir + "/" + provStatusFile);
        if (!jf.exists() || null == vmMap || vmMap.isEmpty()) {
            return;
        }

        AzureEntity ae = AzureUtil.toObject(jf, AzureEntity.class);
        if (CollectionUtils.isEmpty(ae.getReqs())) {
            return;
        }

        for (AzureRequest req : ae.getReqs()) {
            List<AzureMachine> mLst = req.getMachines();
            if (CollectionUtils.isEmpty(mLst)) {
                continue;
            }

            for (AzureMachine m : mLst) {
                if (vmMap.containsKey(m.getMachineId())) {
                    m.update(vmMap.get(m.getMachineId()));
                }
            }
        }

        AzureUtil.toJsonFile(ae, jf);
    }

    /**
     *
     * @Title: saveToFile @Description: save record to local file @param @param
     * req @return void @throws
     */
    public static void saveToFile(AzureRequest req) {
        File jf = new File(workDir + "/" + provStatusFile);

        File lsfWorkDir = new File(workDir);
        try {
            // Add new VM record
            if (!jf.exists()) {
                List<AzureRequest> reqLst = new ArrayList<AzureRequest>();
                reqLst.add(req);

                AzureEntity ae = new AzureEntity();
                ae.setReqs(reqLst);

                AzureUtil.toJsonFile(ae, jf);

                return;
            } else {
                log.info("Azure-db.json file exists ");
            }

            // Delete the VM record if the VM had been created before
            AzureEntity ae = AzureUtil.toObject(jf, AzureEntity.class);
            Iterator<AzureRequest> rqIt = ae.getReqs().iterator();
            while(rqIt.hasNext()) {
                AzureRequest rq = (AzureRequest) rqIt.next();
//				if(rq.getReqId().equals(req.getReqId())) {
//					rq.update(req);
//					AzureUtil.toJsonFile(ae, jf);
//					return;
//				}
                Iterator<AzureMachine> vmIt = rq.getMachines().iterator();
                while(vmIt.hasNext()) {
                    AzureMachine rqVm = (AzureMachine) vmIt.next();
                    for(AzureMachine vm : req.getMachines()) {
                        if(rqVm.getName().equals(vm.getName())) {
                            vmIt.remove();
                        }
                    }
                }
                if(rq.getMachines().isEmpty()) {
                    rqIt.remove();
                }
            }
//			for (AzureRequest rq : ae.getReqs()) {
//				// Update exist record
//				if (rq.getReqId().equals(req.getReqId())) {
//					rq.update(req);
//					AzureUtil.toJsonFile(ae, jf);
//					return;
//				}
//			}

            //Append VM record
            ae.getReqs().add(req);
            AzureUtil.toJsonFile(ae, jf);
        } catch (Exception e) {
            log.error("Error: ", e);
        }
    }

    /**
     *
     * @Title: getFromFile @Description: get record from local
     * file @param @param reqId @param @return @return AzureRequest @throws
     */
    public static AzureRequest getFromFile(String reqId) {
        File f = new File(workDir + "/" + provStatusFile);
        if (!f.exists()) {
            return null;
        }

        AzureEntity ae = AzureUtil.toObject(f, AzureEntity.class);
        List<AzureRequest> reqLst = ae.getReqs();
        if (CollectionUtils.isEmpty(reqLst)) {
            return null;
        }

        List<AzureMachine> retMLst = new ArrayList<AzureMachine>();
        for (AzureRequest req : reqLst) {
            // request VM case
            if (req.getReqId().equals(reqId)) {
                return req;
            }

            List<AzureMachine> mLst = req.getMachines();
            if (CollectionUtils.isEmpty(mLst)) {
                continue;
            }

            // return VM case
            for (AzureMachine m : mLst) {
                if (reqId.equals(m.getRetId())) {
                    retMLst.add(m);
                }
            }
        }

        // return VM case
        if (CollectionUtils.isEmpty(retMLst)) {
            return null;
        }

        AzureRequest ret = new AzureRequest();
        ret.setReqId(reqId);
        ret.setMachines(retMLst);
        return ret;
    }

    /**
     *
     * @Title: getFromFile @Description: get all request from
     * file @param @return @return List<AzureRequest> @throws
     */
    public static AzureEntity getFromFile() {
        File f = new File(workDir + "/" + provStatusFile);
        if (!f.exists()) {
            return null;
        }

        AzureEntity ae = AzureUtil.toObject(f, AzureEntity.class);
        return ae;
    }

    /**
     *
     *
     * @Title: saveToFile @Description: save AzureEntity object to json
     * file @param @param ae @return void @throws
     */
    public static void saveToFile(AzureEntity ae) {
        File jf = new File(workDir + "/" + provStatusFile);
        if (!jf.exists()) {
            return;
        }

        if (null == ae) {
            return;
        }

        AzureUtil.toJsonFile(ae, jf);
    }

    /**
     *
     *
     * @Title: getVMFromFile
     * @Description: get VM from local file
     * @param
     * @param	 * vmNames
     * @param
     * @return
     * @return Map<String,AzureMachine>
     * @throws
     */
    public static Map<String, AzureMachine> getVMFromFile(List<String> vmNames) {
        Map<String, AzureMachine> vmMap = new HashMap<String, AzureMachine>();
        File jf = new File(workDir + "/" + provStatusFile);
        if (!jf.exists()) {
            return vmMap;
        }

        List<String> vmShortNames = new ArrayList<String>();
        boolean flag = false;
        for(String vmName : vmNames) {
            int index = vmName.indexOf('.');
            if( index > 0) {
                vmName = vmName.substring(0, index);
                vmShortNames.add(vmName);
                flag = true;
            }
        }

        if(flag == true) {
            vmNames = vmShortNames;
        }



        AzureEntity ae = AzureUtil.toObject(jf, AzureEntity.class);
        List<AzureRequest> reqLst = ae.getReqs();
        if (CollectionUtils.isEmpty(reqLst)) {
            return vmMap;
        }

        for (AzureRequest req : reqLst) {
            List<AzureMachine> mLst = req.getMachines();
            if (CollectionUtils.isEmpty(mLst)) {
                continue;
            }

            for (AzureMachine m : mLst) {
                if (vmNames.contains(m.getName())) {
                    if (!("Terminated".equalsIgnoreCase(m.getStatus())) && StringUtils.isNullOrEmpty(m.getRetId())) {
                        vmMap.put(m.getMachineId(), m);
                    }
                }
            }
        }

        return vmMap;
    }

    /**
     *
     * @Title: deleteFromFile @Description: clear terminated VM records from
     * local file @param @return void @throws
     */
    public static int deleteFromFile() {
        int onlineNum = 0;
        File jf = new File(workDir + "/" + provStatusFile);
        if (!jf.exists()) {
            return onlineNum;
        }

        AzureEntity ae = AzureUtil.toObject(jf, AzureEntity.class);
        List<AzureRequest> reqLst = ae.getReqs();
        if (CollectionUtils.isEmpty(reqLst)) {
            return onlineNum;
        }

        boolean isUpdated = false;
        List<AzureMachine> delMLst = new ArrayList<AzureMachine>();
        List<AzureRequest> delRLst = new ArrayList<AzureRequest>();
        for (AzureRequest req : reqLst) {
            List<AzureMachine> mLst = req.getMachines();
            if (CollectionUtils.isEmpty(mLst)) {
                delRLst.add(req);
                isUpdated = true;
                continue;
            }

            delMLst.clear();
            for (AzureMachine m : mLst) {
                if ("Terminated".equalsIgnoreCase(m.getStatus()) && !StringUtils.isNullOrEmpty(m.getRetId())) {
                    delMLst.add(m);
                    continue;
                }

                onlineNum++;
            }

            if (!CollectionUtils.isEmpty(delMLst)) {
                mLst.removeAll(delMLst);
                isUpdated = true;
            }

            if (CollectionUtils.isEmpty(mLst)) {
                delRLst.add(req);
                isUpdated = true;
            }
        }

        if (isUpdated) {
            reqLst.removeAll(delRLst);
            AzureUtil.toJsonFile(ae, jf);
        }

        return onlineNum;
    }

    /**
     *
     * @Title: getTemplateFromFile @Description: get template from
     * file @param @param templateId @param @return @return
     * AzureTemplate @throws
     */
    public static AzureTemplate getTemplateFromFile(String templateId) {
        File jf = new File(confDir + "/conf/azureprov_templates.json");
        if (!jf.exists()) {
            log.error("Template file does not exist: " + jf.getPath());
            return null;
        }

        AzureEntity ae = AzureUtil.toObject(jf, AzureEntity.class);
        List<AzureTemplate> tLst = ae.getTemplates();
        for (AzureTemplate at : tLst) {
            if (at.getTemplateId().equals(templateId)) {
                return at;
            }
        }

        return null;
    }

    /**
     *
     * @Title: updateStatus
     * @Description: update status
     * @param @param fReq
     * @param @param inReq
     * @return void
     * @throws
      */
    public static void updateStatus(AzureRequest fReq, AzureRequest inReq) {
        Map<String, VirtualMachine> vmMap = null;
        Azure azure = getAzureClient();
        List<String> vmIdLst = new ArrayList<String>();
        List<AzureMachine> mLst = fReq.getMachines();
        String inReqId = inReq.getReqId();
        boolean statusUpdateForReturnMachine = (inReqId.startsWith(AzureConst.RETURN_REQUEST_PREFIX));
        boolean statusUpdateForCreateMachine = !statusUpdateForReturnMachine;

        String latestRequestStatus = AzureConst.EBROKERD_STATE_COMPLETE;
        String latestMachineStatus = AzureConst.EBROKERD_MACHINE_RESULT_FAIL;
        PowerState mstate = null;
        String provionStatus = null;
        Date currentDateTime = new Date();
        long currentDateSecond = currentDateTime.getTime()/1000;
        boolean requestStillRunning = false;
        long instanceCreationTimeOutSeconds = AzureConst.INSTANCE_CREATION_TIMEOUT_SECONDS;
        if (getConfig().getInstanceCreationTimeout() != null
                && getConfig().getInstanceCreationTimeout().intValue() > 0) {
            instanceCreationTimeOutSeconds = AzureUtil.getConfig().getInstanceCreationTimeout().intValue() * 60;
        }


        // If it is the first time to query new created VMs status
        // we need to retrieve their machineId because we didn't record it in createVM().
        List<VirtualMachine> newCreatedVmList = new ArrayList<VirtualMachine>();

        inReq.setReqId(fReq.getReqId());

        // 1) Azure has a Read API call limit of 15000 per hour.
        // 2) Azure creating VM is very slow. Normally 2~7 minutes for creating custom image VM.
        // 3) Sometimes, azure vm create fail but it take more than 40 minutes until azure indicates it fail.
        // To avoid hit the Read API limit:
        // 1) We decide to NOT query VM status at the first 2 minutes of VM creation.
        // 2) Delete the VM if it has been in creating status more than 15 minutes.
        // 3) For querying status of requestMachine request, do NOT query the virtual machine if it has been already marked as "succeed"
        if (statusUpdateForCreateMachine
                && fReq.getTime() != null && fReq.getTime() > 0
                && (currentDateSecond - fReq.getTime().longValue()/1000 < AzureConst.AZURE_QUERY_NEW_CREATED_VM_TIMEOUT)) {
            // running, complete or complete_with_error
            inReq.setMachines(mLst);
            inReq.setStatus(AzureConst.EBROKERD_STATE_RUNNING);
            inReq.setMsg("Azure is still creating. Will get the VM status 2 minutes later.");
            return;
        }

        for (AzureMachine m : mLst) {
            if (statusUpdateForCreateMachine && StringUtils.isNullOrEmpty(m.getMachineId())) {
                VirtualMachine vm = azure.virtualMachines().getByResourceGroup(m.getResourceGroup(), m.getName());
                if(vm != null) {
                    log.debug("updateStatus: Get new created VM:" + vm);
                    //set the machine id
                    m.setMachineId(vm.id());
                    log.debug("updateStatus: m:" + m);
                    newCreatedVmList.add(vm);
                } else {
                    // the VM is still not created, delete the resource group directly
                    log.info("updateStatus: the VM is still not created: " + m);
                    deleteVM(m);
                    m.setStatus("deployment_failed");
                    m.setResult(AzureConst.EBROKERD_MACHINE_RESULT_FAIL);
                    log.debug("updateStatus: Failed to get VM by m:" + m);
                }
            } else if(!StringUtils.isNullOrEmpty(m.getMachineId())) {
                // For querying requestMachines, do NOT query the virtual machine if it has been already marked as "succeed"
                if (statusUpdateForCreateMachine && m.getResult().equals(AzureConst.EBROKERD_MACHINE_RESULT_SUCCEED)) {
                    continue;
                } else {
                    vmIdLst.add(m.getMachineId());
                }
            } else {
                // should never go here
                log.error("updateStatus: machineId is null " + m);
            }
        }

        try {
            vmMap = AzureUtil.listVM(vmIdLst);
        } catch (Exception e) {
            log.error("Qeury list of VMs failed " + vmIdLst);
            inReq.setMachines(mLst);
            inReq.setStatus(AzureConst.EBROKERD_STATE_COMPLETE_WITH_ERROR);
            inReq.setMsg(e.getMessage());
            return;
        }

        if (vmMap == null) {
            vmMap = new HashMap<String, VirtualMachine>();
        }

        // add the new created VM to vmMap.
        if (!CollectionUtils.isEmpty(newCreatedVmList)) {
            for (VirtualMachine vm : newCreatedVmList) {
                vmMap.put(vm.id(), vm);
            }
        }


        for (AzureMachine m : mLst) {
            VirtualMachine i = vmMap.get(m.getMachineId());

            // For querying requestMachines, do NOT handle the machine if it has been already marked as "succeed"
            if (i == null && statusUpdateForCreateMachine
                    && m.getResult().equals(AzureConst.EBROKERD_MACHINE_RESULT_SUCCEED)) {
                continue;
            }

            /**
             * If we failed to get the VM on Azure
             * or the vm provision state is Deleting
             * We assume the vm will be deleted
             */
            if (statusUpdateForReturnMachine &&
                    (i == null || (i != null && i.provisioningState().equals("Deleting")))) {
                log.debug("Updating VM result to succeed as the VM is already Deleting.");
                m.setResult(AzureConst.EBROKERD_MACHINE_RESULT_SUCCEED);
                latestMachineStatus = "DEALLOCATED";
                m.setStatus(latestMachineStatus);
                m.setMsg("");
                //latestMachineStatus = AzureConst.EBROKERD_STATE_COMPLETE;
                continue;
            }

            if (statusUpdateForCreateMachine && i == null) {
                log.debug("The vm is not created." );
                m.setResult(AzureConst.EBROKERD_MACHINE_RESULT_FAIL);
                latestMachineStatus = "deployment_failed";
                m.setStatus(latestMachineStatus);
                latestMachineStatus = AzureConst.EBROKERD_STATE_COMPLETE_WITH_ERROR;
                continue;
            }

            /**
             * If creating VM timeout, delete the VM directly.
             */
            if (statusUpdateForCreateMachine
                    && m.getLaunchtime() != null
                    && m.getLaunchtime().longValue() > 0
                    && currentDateSecond - m.getLaunchtime().longValue() > instanceCreationTimeOutSeconds) {
                log.debug("Delete the creating timeout VM. currentDateSecond<" + currentDateSecond  + "> m.getLaunchtime<" +m.getLaunchtime()+ ">." );
                deleteVM(m);
                log.debug("Updating VM result to fail as the VM creation timeout.");
                m.setResult(AzureConst.EBROKERD_MACHINE_RESULT_FAIL);
                latestMachineStatus = "creating_timeout";
                m.setStatus(latestMachineStatus);
                latestRequestStatus = AzureConst.EBROKERD_STATE_COMPLETE_WITH_ERROR;
                continue;
            }

            provionStatus = i.provisioningState();
            mstate = i.powerState();

            /**
             * powerState may be null when vm is in provision or deallocating
             */
            if (null == mstate) {
                if(provionStatus.equalsIgnoreCase("Failed")
                        || provionStatus.equalsIgnoreCase("Canceled")) {
                    if(statusUpdateForCreateMachine) {
                        log.debug("Delete the failed VM. PowerState is null. VM provionStatus " + provionStatus );
                        deleteVM(m);
                    }
                    log.debug("Updating VM result to fail.  PowerState is null. VM provionStatus " + provionStatus );
                    m.setResult(AzureConst.EBROKERD_MACHINE_RESULT_FAIL);
                    m.setStatus(provionStatus);
                    m.setMsg("");
                    latestRequestStatus =  AzureConst.EBROKERD_STATE_COMPLETE_WITH_ERROR;
                } else {
                    log.debug("Updating VM result to executing. PowerState is null. VM provionStatus " + provionStatus );
                    latestMachineStatus = provionStatus;
                    m.setStatus(latestMachineStatus);
                    m.setResult(AzureConst.EBROKERD_MACHINE_RESULT_EXECUTING);
                    m.setMsg("");
                    latestRequestStatus = AzureConst.EBROKERD_STATE_RUNNING;
                    requestStillRunning = true;
                }
                continue;
            }


            /*
             * Status from Azure:
             * 1, provionStatus: Succeeded, Failed, Canceled,
             *    all others(Creating, Updating, Deleting...) indicate the operation is still running
             *    https://docs.microsoft.com/en-us/azure/azure-resource-manager/resource-manager-async-operations#provisioningstate-values
             * 2, PowerState: STARTING,  DEALLOCATING, RUNNING, STOPPED, DEALLOCATED
             *
             * Need to map status from LSF: executing, fail, succeed
             */
            if((mstate.equals(PowerState.RUNNING) && (provionStatus.equalsIgnoreCase("Succeeded")) && statusUpdateForCreateMachine)
                    || (mstate.equals(PowerState.STOPPED) && statusUpdateForReturnMachine)
                    || (mstate.equals(PowerState.DEALLOCATED) && statusUpdateForReturnMachine)	) {
                log.debug("Updating VM result to succeed. VM provionStatus " + provionStatus + " PowerState " + mstate.toString());
                m.setResult(AzureConst.EBROKERD_MACHINE_RESULT_SUCCEED);
            } else if ((mstate.equals(PowerState.RUNNING) && statusUpdateForReturnMachine)
                       || mstate.equals(PowerState.STARTING)
                       || mstate.equals(PowerState.DEALLOCATING)
                       || (!provionStatus.equalsIgnoreCase("Succeeded")
                           && !provionStatus.equalsIgnoreCase("Failed")
                           && !provionStatus.equalsIgnoreCase("Canceled"))) {
                log.debug("Updating VM result to executing. VM provionStatus " + provionStatus + " PowerState " + mstate.toString());
                m.setResult(AzureConst.EBROKERD_MACHINE_RESULT_EXECUTING);
                latestRequestStatus = AzureConst.EBROKERD_STATE_RUNNING;
                requestStillRunning = true;
            } else if (provionStatus.equalsIgnoreCase("Failed")
                       || provionStatus.equalsIgnoreCase("Canceled")) {
                log.debug("Updating VM result to fail as provionStatus is Failed or Canceded. VM provionStatus " + provionStatus + " PowerState " + mstate.toString());
                m.setResult(AzureConst.EBROKERD_MACHINE_RESULT_FAIL);
                latestRequestStatus = AzureConst.EBROKERD_STATE_COMPLETE_WITH_ERROR;
            } else {
                log.debug("Updating VM result to fail as unknown provionStatus. VM provionStatus " + provionStatus + " PowerState " + mstate.toString());
                m.setResult(AzureConst.EBROKERD_MACHINE_RESULT_FAIL);
                latestRequestStatus = AzureConst.EBROKERD_STATE_COMPLETE_WITH_ERROR;
            }

            m.setStatus(mstate.toString());
            m.setMsg("");
            if(statusUpdateForCreateMachine && m.getResult().equals(AzureConst.EBROKERD_MACHINE_RESULT_FAIL)) {
                log.debug("Delete the failed VM" + m.toString());
                deleteVM(m);
                continue;
            }

            if(i.getPrimaryPublicIPAddress() != null) {
                String publicIpStr = i.getPrimaryPublicIPAddress().ipAddress();
                String publicDnsName = null;
                if(!StringUtils.isNullOrEmpty(publicIpStr) ) {
                    m.setPublicIpAddress(publicIpStr);
                    //Set public dns name according to public ip address
                    publicDnsName = AzureConst.AZURE_HOSTNAME_PREFIX + publicIpStr.replace('.', '-');
                    m.setPublicDnsName(publicDnsName);
                    log.debug("Update VM public IP:" + m.toString());
                }
            } else {
                m.setPublicIpAddress("");
                m.setPublicDnsName("");
            }


            log.debug("Finish update VM status:" + m.toString());

        }

        if (requestStillRunning) {
            latestRequestStatus = AzureConst.EBROKERD_STATE_RUNNING;
        }
        // running, complete or complete_with_error
        inReq.setMachines(mLst);
        inReq.setStatus(latestRequestStatus);
        inReq.setMsg("");

        // update VM record
        updateVmStatus(fReq);
    }

    /**
     *
     *
     * @Title: updateVmStatus @Description: update VM status to json
     * file @param @param req @return void @throws
     */
    public static void updateVmStatus(AzureRequest req) {
        if (log.isDebugEnabled()) {
            log.debug("Start in class AzureUtil in method updateVmStatus with parameters: req: " + req);
        }

        if (null == req) {
            return;
        }

        if (CollectionUtils.isEmpty(req.getMachines())) {
            return;
        }

        Map<String, AzureMachine> vmMap = new HashMap<String, AzureMachine>();
        for (AzureMachine m : req.getMachines()) {
            // machineId may be null if it is first time to query new crated VMs
            // use hostname as the key of map
            vmMap.put(m.getName(), m);
        }

        AzureEntity ae = getFromFile();
        if (null == ae || CollectionUtils.isEmpty(ae.getReqs())) {
            return;
        }

        for (AzureRequest rq : ae.getReqs()) {
            if (CollectionUtils.isEmpty(rq.getMachines())) {
                continue;
            }

            for (AzureMachine m : rq.getMachines()) {
                if (!vmMap.containsKey(m.getName())) {
                    continue;
                }

                AzureMachine newM = vmMap.get(m.getName());
                m.update(newM);
                log.debug("Update VM:" + m.toString());
            }
        }

        saveToFile(ae);
    }

    private static String readFileToString(String filePath) {

        StringBuffer fileData = new StringBuffer(1000);
        char[] buf = new char[1024];

        int numRead = 0;
        try {
            BufferedReader reader = new BufferedReader(new FileReader(filePath));
            while ((numRead = reader.read(buf)) != -1) {
                String readData = String.valueOf(buf, 0, numRead);
                fileData.append(readData);
                buf = new char[1024];
            }

            reader.close();
        } catch (IOException e) {
            log.error("Close IO error.", e);
        }
        return fileData.toString();
    }

    public static void mapAllMachineIdsToErokerd(List<AzureMachine> machines) {
        if (machines != null && CollectionUtils.isNotEmpty(machines)) {
            for(AzureMachine am : machines) {
                am.mapMahinedIdToEbrokderd();
            }
        }
        return;
    }

}
