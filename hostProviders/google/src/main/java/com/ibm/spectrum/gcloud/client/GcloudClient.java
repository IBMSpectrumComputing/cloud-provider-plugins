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

package com.ibm.spectrum.gcloud.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;

import com.ibm.spectrum.constant.GcloudConst;
import com.ibm.spectrum.model.GcloudEntity;
import com.ibm.spectrum.model.GcloudMachine;
import com.ibm.spectrum.model.GcloudRequest;
import com.ibm.spectrum.model.GcloudTemplate;
import com.ibm.spectrum.model.HostAllocationType;
import com.ibm.spectrum.util.GcloudUtil;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeScopes;
import com.google.api.services.compute.model.AcceleratorConfig;
import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.AttachedDisk;
import com.google.api.services.compute.model.AttachedDiskInitializeParams;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceAggregatedList;
import com.google.api.services.compute.model.InstanceList;
import com.google.api.services.compute.model.InstanceProperties;
import com.google.api.services.compute.model.InstancesScopedList;
import com.google.api.services.compute.model.Metadata;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.compute.model.ServiceAccount;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.Scheduling;
import com.google.api.services.compute.model.BulkInsertInstanceResource;

/**
 * @ClassName: GcloudClient
 * @Description: The Client class for the Google Cloud API
 * @author zcg
 * @date Sep 11, 2017
 * @version 1.0
 */
public class GcloudClient {

    private static Logger log = LogManager.getLogger(GcloudClient.class);

    /** Global instance of the HTTP transport. */
    private static HttpTransport httpTransport;

    /** Global instance of the JSON factory. */
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    /**
     * Get the Google Cloud Compute
     */
    private static Compute gcloudCompute = getClient();

    /**
     * Get httpHeader
     */
    private static final HttpHeaders HTTP_HEADER = getHttpHeader();

    /**
     *
     * @Title: getClient @Description: Initialize Cloud Provider
     * client @param @return @return Compute @throws
     */
    public static synchronized Compute getClient() {
        if (log.isTraceEnabled()) {
            log.trace("Start in class GcloudClient in method getCloudProviderClient with parameters ");
        }

        if (null != gcloudCompute) {
            return gcloudCompute;
        }

        String credentialsFile = GcloudUtil.getConfig().getCredentialFile();
        // if GCLOUD_CREDENTIAL_FILE is not, then search it in conf dir
        if (StringUtils.isEmpty(credentialsFile)) {
            credentialsFile = GcloudUtil.getConfDir() + "/conf/credentials";
        }
        log.info(String.format("Obtaining static credentials from file %s", credentialsFile));

        GoogleCredentials credential = null;
        Compute compute = null;
        try {
            httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            File scJsonFile = new File(credentialsFile);
            if (scJsonFile.exists()) {
                InputStream jsonStream = new FileInputStream(credentialsFile);
                credential = GoogleCredentials.fromStream(jsonStream);
            } else {
                credential = GoogleCredentials.getApplicationDefault();
            }
            if (credential.createScopedRequired()) {
                List<String> scopes = new ArrayList<String>();
                // Set Google Cloud Storage scope to Full Control.
                scopes.add(ComputeScopes.DEVSTORAGE_FULL_CONTROL);
                // Set Google Compute Engine scope to Read-write.
                scopes.add(ComputeScopes.COMPUTE);
                credential = credential.createScoped(scopes);
            }

            HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credential);

            // Create Compute Engine object for listing instances.
            compute = new Compute.Builder(httpTransport, JSON_FACTORY, requestInitializer)
            .setApplicationName(GcloudConst.APPLICATION_NAME).build();
        } catch (Exception ex) {
            log.error("Failed to create credential and create compute client for cloud" + GcloudUtil.getProviderName()
                      + " credentials file.", ex);
        }

        if (log.isTraceEnabled()) {
            log.trace("End in class GcloudClient in method getClient with return: Compute: " + compute);
        }

        return compute;
    }

    /**
     *
     * @Title: getHttpHeader
     * @Description: get the http header where userAgent set to IBM_spectrum_LSF_$project_id
     * @return HttpHeaders
     */
    public static HttpHeaders getHttpHeader() {
        HttpHeaders httpHeader = new HttpHeaders();
        String userAgent = GcloudConst.PRODUCT_OFFERING + "_" + GcloudUtil.getConfig().getProjectID();
        httpHeader.setUserAgent(userAgent);
        return httpHeader;
    }

    /**
     *
     * @Title: createVM @Description: create google cloud
     * instances @param @return @return List<GcloudMachine> @throws
     */
    public static List<GcloudMachine> createVM(GcloudTemplate t, String tagValue, String requestId) {
        /**
         * create google cloud instances, need to save the returned operation
         * Id(name) into GcloudMachine
         */
        if (log.isTraceEnabled()) {
            log.info("Start in class GcloudClient in method createVM with parameters: t: " + t + ", tagValue: " + tagValue);
        }

        List<GcloudMachine> gcloudMachineList = new ArrayList<GcloudMachine>();
        String hostname = "compute-" + RandomStringUtils.randomAlphanumeric(5).toLowerCase();
        String projectId = GcloudUtil.getConfig().getProjectID();
        String zone = t.getZone();

        Instance instance = getInstanceContent(t, projectId, tagValue);

        try {

            for (int i = 0; i < t.getVmNumber(); i++) {
                // Create VM Instance object with the required properties.
                String instanceName = hostname + String.valueOf(i);
                instance.setName(instanceName);

                if (log.isTraceEnabled()) {
                    log.trace("Begining to create instance:" + instance.toPrettyString());
                }
                Compute.Instances.Insert insert = gcloudCompute.instances().insert(projectId, zone, instance);

                // Launch instance from a template if launchTemplateId defined
                if (StringUtils.isNotBlank(t.getLaunchTemplateId())) {
                    String templateNameStr = "global/instanceTemplates/" + t.getLaunchTemplateId();
                    insert.setSourceInstanceTemplate(templateNameStr);
                    if (log.isTraceEnabled()) {
                        log.trace("Launch instance template is set to: " + templateNameStr);
                    }
                }

                Operation op = insert.setRequestHeaders(HTTP_HEADER).execute();

                if (log.isTraceEnabled()) {
                    log.trace("Http header userAgent: " + insert.getRequestHeaders().getUserAgent());
                }

                GcloudMachine gcloudMachine = new GcloudMachine();
                gcloudMachine.setName(instanceName);
                gcloudMachine.setMachineId(op.getTargetId().toString());
                gcloudMachine.setReqId(requestId);
                gcloudMachine.setStatus(GcloudConst.INSTANCE_STATUS_BEGIN);
                gcloudMachine.setResult(GcloudConst.INSTANCE_STATUS_BEGIN);
                gcloudMachine.setZone(zone);
                gcloudMachine.setOperationId(op.getName());
                gcloudMachine.setTemplate(t.getTemplateId());
                gcloudMachine.setRcAccount(tagValue);
                gcloudMachine.setLaunchtime(System.currentTimeMillis()/1000);

                gcloudMachineList.add(gcloudMachine);
            } // end of for

        } catch (Exception e) {
            log.error("Create instances error.", e);
        }
        if (log.isTraceEnabled()) {
            log.info("End in class GcloudClient in method createVM with parameters: t: " + t + ", tagValue: " + tagValue);
        }
        return gcloudMachineList;
    }



    /**
     *
     * @Title: getMachineType
     * @Description: Get machine type
     * @param t
     * @param projectId
     * @param isBulk: Whether this is used for generate InstanceProperties
     * @return
     */
    public static String getMachineType(GcloudTemplate t, String projectId, Boolean isBulk) {
        String glcoudMachineTypeStr = null;

        // For machineType in instanceProperties of bulk, only need resource name, not URL
        if (isBulk) {
            glcoudMachineTypeStr = t.getVmType();
        } else {
            StringBuilder gcloudMachineType = new StringBuilder();
            gcloudMachineType.append(GcloudConst.GOOGLE_API_PREFIX);
            gcloudMachineType.append(projectId);
            gcloudMachineType.append("/zones/");
            gcloudMachineType.append(t.getZone());
            gcloudMachineType.append("/machineTypes/");
            gcloudMachineType.append(t.getVmType());
            glcoudMachineTypeStr = gcloudMachineType.toString();
        }

        return glcoudMachineTypeStr;
    }


    /**
     * @Title: getNetworkInterface
     * @Description: Get network settings
     * @param t
     * @param projectId
     * @param ifc
     * @return
     */
    public static boolean getNetworkInterface(GcloudTemplate t, String projectId, NetworkInterface ifc) {
        if (ifc == null) {
            ifc = new NetworkInterface();
        }

        Boolean needSetNetwork = false;
        Boolean launchTemplateNotDefined = StringUtils.isBlank(t.getLaunchTemplateId());
        String hostProject = StringUtils.isEmpty(t.getHostProject()) ? projectId : t.getHostProject();

        // Set network if defined
        StringBuilder gcloudNetwork = new StringBuilder();
        gcloudNetwork.append(GcloudConst.GOOGLE_API_PREFIX);
        gcloudNetwork.append(hostProject);
        gcloudNetwork.append("/global/networks/");
        if (StringUtils.isNotBlank(t.getVpc())) {
            gcloudNetwork.append(t.getVpc());
            needSetNetwork = true;
        } else if (launchTemplateNotDefined) {
            gcloudNetwork.append("default");
            needSetNetwork = true;
        }

        if (needSetNetwork) {
            String gcloudNetworkStr = gcloudNetwork.toString();
            log.debug("network: " + gcloudNetworkStr);
            ifc.setNetwork(gcloudNetworkStr);
        }

        // Set subnet if defined
        StringBuilder gcloudSubnetwork = new StringBuilder();
        if(StringUtils.isNotBlank(t.getSubnetId())) {
            gcloudSubnetwork.append(GcloudConst.GOOGLE_API_PREFIX);
            gcloudSubnetwork.append(hostProject);
            gcloudSubnetwork.append("/regions/");
            gcloudSubnetwork.append(t.getRegion());
            gcloudSubnetwork.append("/subnetworks/");
            gcloudSubnetwork.append(t.getSubnetId());
            String gcloudSubnetworkStr = gcloudSubnetwork.toString();
            log.debug("subnet: " + gcloudSubnetworkStr);
            ifc.setSubnetwork(gcloudSubnetworkStr);
            needSetNetwork = true;
        }

        // If launchTemplateId not defined, default no public ip, set public ip only when privateNetwortOnlyFlag = false
        // If launchTemplateId defined and privateNetwortOnlyFlag not defined, we should honor what defined in template
        // If launchTemplateId defined and privateNetwortOnlyFlag = true or false, we should override what defined in template
        if (launchTemplateNotDefined && t.getPrivateNetworkOnlyFlag() == null) {
            t.setPrivateNetworkOnlyFlag(Boolean.TRUE);
        }
        if (t.getPrivateNetworkOnlyFlag() != null) {
            if (t.getPrivateNetworkOnlyFlag().equals(Boolean.FALSE)) {
                List<AccessConfig> configs = new ArrayList<AccessConfig>();
                AccessConfig config = new AccessConfig();
                config.setType("ONE_TO_ONE_NAT");
                config.setName("External NAT");
                configs.add(config);
                ifc.setAccessConfigs(configs);
                needSetNetwork = true;
            } else if (!launchTemplateNotDefined) {
                needSetNetwork = true;
            }
        }

        return needSetNetwork;

    }


    /**
     * @Title: getDisk
     * @Description: Get disks settings
     * @param t
     * @param projectId
     * @param isBulk: Whether this is used for generate InstanceProperties
     * @return
     */
    public static AttachedDisk getDisk(GcloudTemplate t, String projectId, Boolean isBulk) {
        AttachedDisk disk = new AttachedDisk();

        StringBuilder gcloudImage = new StringBuilder();
        gcloudImage.append(GcloudConst.GOOGLE_API_PREFIX);
        gcloudImage.append(projectId);
        gcloudImage.append("/global/images/");
        gcloudImage.append(t.getImageId());
        String gcloudImageStr = gcloudImage.toString();

        // For diskType in instanceProperties of bulk, only need resource name, not URL
        String gcloudDiskTypeStr = null;
        if (isBulk) {
            gcloudDiskTypeStr = "pd-standard";
        } else {
            // Now only to support Standard Persistent Disk
            StringBuilder gcloudDiskType = new StringBuilder();
            gcloudDiskType.append(GcloudConst.GOOGLE_API_PREFIX);
            gcloudDiskType.append(projectId);
            gcloudDiskType.append("/zones/");
            gcloudDiskType.append(t.getZone());
            gcloudDiskType.append("/diskTypes/pd-standard");
            gcloudDiskTypeStr = gcloudDiskType.toString();
        }
        // Add attached Persistent Disk to be used by VM Instance.
        disk.setBoot(true);
        disk.setAutoDelete(true);
        disk.setType("PERSISTENT");
        AttachedDiskInitializeParams params = new AttachedDiskInitializeParams();
        // Assign the Persistent Disk the same name as the VM Instance.
        // params.setDiskName(instanceName);
        // Specify the source operating system machine image to be used
        // by the VM Instance.
        params.setSourceImage(gcloudImageStr);
        // Specify the disk type as Standard Persistent Disk
        params.setDiskType(gcloudDiskTypeStr);
        disk.setInitializeParams(params);

        return disk;

    }

    /**
     *
     * @Title: getGuestAccelerators
     * @Description: Get GPU settings
     * @param t
     * @param projectId
     * @param isBulk: Whether this is used for generate InstanceProperties
     * @return
     */
    public static List<AcceleratorConfig> getGuestAccelerators(GcloudTemplate t, String projectId, Boolean isBulk) {

        StringBuilder gcloudGpuType = new StringBuilder();
        String gcloudGpuTypeStr = null;
        // For gpu acceleratorTypes in instanceProperties of bulk, only need resource name, not URL
        if (isBulk) {
            gcloudGpuTypeStr = t.getGpuType();
        } else {
            gcloudGpuType.append(GcloudConst.GOOGLE_API_PREFIX);
            gcloudGpuType.append(projectId);
            gcloudGpuType.append("/zones/");
            gcloudGpuType.append(t.getZone());
            gcloudGpuType.append("/acceleratorTypes/");
            gcloudGpuType.append(t.getGpuType());
            gcloudGpuTypeStr = gcloudGpuType.toString();
        }
        AcceleratorConfig gpuConfig = new AcceleratorConfig();
        List<AcceleratorConfig> gpuConfigList = new ArrayList<AcceleratorConfig>();
        gpuConfig.setAcceleratorType(gcloudGpuTypeStr);
        gpuConfig.setAcceleratorCount(t.getGpuNumber());
        gpuConfigList.add(gpuConfig);

        return gpuConfigList;
    }


    /**
     * @Title: getSchedulingForGpu
     * @Description: Get scheduler setting for GPU
     * @return
     */
    public static Scheduling getSchedulingForGpu() {
        Scheduling scheduling = new Scheduling();
        scheduling.setOnHostMaintenance("TERMINATE");
        scheduling.setAutomaticRestart(true);
        scheduling.setPreemptible(false);

        return scheduling;
    }

    /**
     *
     * @Title: getServiceAccount
     * @Description: Get service account setting
     * @param t
     * @param projectId
     * @return
     */
    public static ServiceAccount getServiceAccount(GcloudTemplate t, String projectId) {
        // Initialize the service account to be used by the VM Instance
        // and set the API access scopes.
        ServiceAccount account = new ServiceAccount();
        account.setEmail("default");
        List<String> scopes = new ArrayList<String>();
        scopes.add("https://www.googleapis.com/auth/devstorage.full_control");
        scopes.add("https://www.googleapis.com/auth/compute");
        account.setScopes(scopes);

        return account;
    }


    /**
     *
     * @Title: getMetadata
     * @Description: Get metadata setting
     * @param projectId
     * @param tagValue
     * @param bulkInsertId
     * @return
     */
    public static Metadata getMetadata(GcloudTemplate t, String projectId, String tagValue, String bulkInsertId) {
        // Optional - Add a startup script to be used by the VM
        // Instance.
    	
    	String userData = GcloudUtil.getUserDataScriptContent(t, tagValue, bulkInsertId);

    	if (StringUtils.isNotBlank(userData)) {
    		Metadata meta = new Metadata();
    		Metadata.Items item = new Metadata.Items();
    		item.setKey("startup-script");
    		item.setValue(userData);
    		meta.setItems(Collections.singletonList(item));
    		return meta;
    	} else {
    		return null;
    	}
    }

    /**
     *
     * @Title: getInstanceContent
     * @Description: get instance settings according to googleprv_template.json
     * @param t
     * @param projectId
     * @param tagValue
     * @return
     */
    public static Instance getInstanceContent(GcloudTemplate t, String projectId, String tagValue) {
        Instance instance = new Instance();

        Boolean launchTemplateNotDefined = StringUtils.isBlank(t.getLaunchTemplateId());

        // Set Machine Type if defined
        if (launchTemplateNotDefined || StringUtils.isNotBlank(t.getVmType())) {
            String glcoudMachineTypeStr = getMachineType(t, projectId, false);
            instance.setMachineType(glcoudMachineTypeStr);
        }

        // Set Network Interface to be used by VM Instance.
        NetworkInterface ifc = new NetworkInterface();
        Boolean needSetNetwork = getNetworkInterface(t, projectId, ifc);
        if (needSetNetwork) {
            instance.setNetworkInterfaces(Collections.singletonList(ifc));
        }

        // Set disks
        if (launchTemplateNotDefined || StringUtils.isNotBlank(t.getImageId())) {
            AttachedDisk disk = getDisk(t, projectId, false);
            instance.setDisks(Collections.singletonList(disk));
        }

        // Set GPU
        if (StringUtils.isNotBlank(t.getGpuType()) && t.getGpuNumber() != null) {
            List<AcceleratorConfig> gpuConfigList = getGuestAccelerators(t, projectId, false);
            instance.setGuestAccelerators(gpuConfigList);
            // set instance Scheduling, otherwise, will got error
            //   "Instances with guest accelerators do not support live migration"
            // see link: https://groups.google.com/forum/#!topic/gce-discussion/e9K3h3fQuJk
            Scheduling gcloudSch = getSchedulingForGpu();
            instance.setScheduling(gcloudSch);
        }

        // Set minCpuPlatform
        if (StringUtils.isNotBlank(t.getMinCpuPlatform())) {
            instance.setMinCpuPlatform(t.getMinCpuPlatform());
        }

        // Initialize the service account to be used by the VM Instance
        // and set the API access scopes.
        ServiceAccount account = getServiceAccount(t, projectId);
        instance.setServiceAccounts(Collections.singletonList(account));

        // Optional - Add a startup script to be used by the VM
        // Instance.
        Metadata meta = getMetadata(t, projectId, tagValue, null);
        instance.setMetadata(meta);

        // It seem tags and lables are same for google cloud
        // so, apply labels from instanceTags
        Map<String, String> labelMap = createInstanceTagMap(tagValue, t.getInstanceTags(), null);
        instance.setLabels(labelMap);

        return instance;

    }

    /**
     *
     * @Title: getInstanceProperties
     * @Description: get InstanceProperties settings according to googleprv_template.json
     * @param t
     * @param projectId
     * @param tagValue
     * @param bulkInsertId
     * @return
     */
    public static InstanceProperties getInstanceProperties(GcloudTemplate t, String projectId, String tagValue, String bulkInsertId) {
        InstanceProperties instanceProperties = new InstanceProperties();

        Boolean launchTemplateNotDefined = StringUtils.isBlank(t.getLaunchTemplateId());

        // Set Machine Type if defined
        if (launchTemplateNotDefined || StringUtils.isNotBlank(t.getVmType())) {
            String glcoudMachineTypeStr = getMachineType(t, projectId, true);
            instanceProperties.setMachineType(glcoudMachineTypeStr);
            instanceProperties.setMachineType(t.getVmType());
        }

        // Set Network Interface to be used by VM Instance.
        NetworkInterface ifc = new NetworkInterface();
        Boolean needSetNetwork = getNetworkInterface(t, projectId, ifc);
        if (needSetNetwork) {
            instanceProperties.setNetworkInterfaces(Collections.singletonList(ifc));
        }

        // Set disks
        if (launchTemplateNotDefined || StringUtils.isNotBlank(t.getImageId())) {
            AttachedDisk disk = getDisk(t, projectId, true);
            instanceProperties.setDisks(Collections.singletonList(disk));
        }

        // Set GPU
        if (StringUtils.isNotBlank(t.getGpuType()) && t.getGpuNumber() != null) {
            List<AcceleratorConfig> gpuConfigList = getGuestAccelerators(t, projectId, true);
            instanceProperties.setGuestAccelerators(gpuConfigList);
            // set instance Scheduling, otherwise, will got error
            //   "Instances with guest accelerators do not support live migration"
            // see link: https://groups.google.com/forum/#!topic/gce-discussion/e9K3h3fQuJk
            Scheduling gpuScheduler = getSchedulingForGpu();
            instanceProperties.setScheduling(gpuScheduler);
        }

        // Set minCpuPlatform
        if (StringUtils.isNotBlank(t.getMinCpuPlatform())) {
            instanceProperties.setMinCpuPlatform(t.getMinCpuPlatform());
        }

        // Initialize the service account to be used by the VM Instance
        // and set the API access scopes.
        ServiceAccount account = getServiceAccount(t, projectId);
        instanceProperties.setServiceAccounts(Collections.singletonList(account));

        // Optional - Add a startup script to be used by the VM
        // Instance.
        Metadata meta = getMetadata(t, projectId, tagValue, bulkInsertId);
        instanceProperties.setMetadata(meta);

        // It seem tags and lables are same for google cloud
        // so, apply labels from instanceTags
        Map<String, String> labelMap = createInstanceTagMap(tagValue, t.getInstanceTags(), bulkInsertId);
        instanceProperties.setLabels(labelMap);

        return instanceProperties;

    }

    /**
     * @Title: deleteVM
     * @Description: delete VMs on cloud
     * @param retMachineList machines to be deleted with new status
     * @return List<InstanceStateChange> @throws
     */
    public static List<GcloudMachine> deleteVM(List<GcloudMachine> retMachineList, GcloudEntity rsp) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class GcloudClient in method deleteVM with parameters: retMachineList: " + retMachineList);
        }

        List<GcloudMachine> vmList = null;
        if (CollectionUtils.isEmpty(retMachineList)) {
            log.error("Invalid machine list to return: " + retMachineList);
            return null;
        }

        for (GcloudMachine machine : retMachineList) {
            String zone = machine.getZone();
            deleteCloudVM(machine.getName(), zone);
        }

        vmList = listVMStatus(retMachineList);

        if (log.isTraceEnabled()) {
            log.trace("End in class GcloudClient in method deleteVM with return vmList: " + vmList);
        }
        return vmList;
    }

    /**
     * @Title: deleteVMListFromCloud
     * @Description: delete VMs on cloud
     * @param retMachineList machines to be deleted
     * @return List<InstanceStateChange> @throws
     */
    public static void deleteVMListFromCloud(List<GcloudMachine> retMachineList) {

        if (log.isTraceEnabled()) {
            log.trace("Start in class GcloudClient in method deleteVMListFromCloud with parameters: retMachineList: " + retMachineList);
        }

        if (CollectionUtils.isEmpty(retMachineList)) {
            log.error("Invalid machine list to return: " + retMachineList);
            return;
        }

        // Get filter string:
        boolean isFirstItem = true;
        StringBuilder filter = new StringBuilder();
        for (GcloudMachine machine : retMachineList) {
            if (isFirstItem) {
                isFirstItem = false;
            } else {
                filter.append(" OR ");
            }
            filter.append("(name = " + machine.getName() + ")");
        }
        String filterStr = filter.toString();
        log.debug("deleteVMListFromCloud filterStr: " + filterStr);
        String projectId = GcloudUtil.getConfig().getProjectID();

        // Get instance list from cloud
        Map<String, Instance> vmMap = new HashMap<String, Instance>();
        List<Instance> instList = null;
        try {
            instList = getInstanceList(projectId, filterStr);
        } catch (IOException e) {
            log.error("Get instance list failed. " + e);
        }
        if (instList != null) {
            for (Instance inst: instList) {
                vmMap.put(inst.getName(), inst);
            }
        }

        // Delete VM from cloud
        for (GcloudMachine machine : retMachineList) {
            Instance inst = vmMap.get(machine.getName());
            if (inst != null) {
                // Get zone from url 'https://www.googleapis.com/compute/v1/projects/lsf-core-qa/zones/us-east1-c'
                String zoneSplit [] = inst.getZone().split("/");
                String zone = zoneSplit[zoneSplit.length - 1];
                deleteCloudVM(machine.getName(), zone);
            } else {
                log.info("Instance is not found. It may already been deleted. " + machine.getName());
            }
        }

        if (log.isTraceEnabled()) {
            log.trace("End in class GcloudClient in method deleteVMListFromCloud");
        }
        return;
    }


    /**
     * @Title: deleteCloudVM
     * @Description: delete VMs on cloud
     * @param projectId
     * @param machineName
     * @param zone
     * @return
     */
    public static int deleteCloudVM(String machineName, String zone) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class GcloudClient in method deleteCloudVM with parameters machineName:" + machineName
                      + "zone:" + zone);
        }
        if (machineName == null || zone == null) {
            log.error("Input parameter error");
            return 1;
        }

        Compute compute = getClient();
        String projectId = GcloudUtil.getConfig().getProjectID();

        try {
            Compute.Instances.Delete delete = compute.instances().delete(projectId, zone, machineName);
            delete.setRequestHeaders(HTTP_HEADER).execute();
            log.info("Delete instance successful: " + machineName);
            if (log.isTraceEnabled()) {
                log.trace("Http header userAgent: " + delete.getRequestHeaders().getUserAgent());
            }
        } catch (IOException e) {
            log.error("Delete instance failed: " + machineName + e.toString());
            return 1;
        }

        if (log.isTraceEnabled()) {
            log.trace("End in class GcloudClient in method deleteCloudVM.");
        }

        return 0;
    }

    /**
     *
     * @Title: listVMStatus @Description: query Instance status, it is only used
     * by delteVM now. @param @param retMachineList @param @return @return
     * List<GcloudMachine> @throws
     */
    public static List<GcloudMachine> listVMStatus(List<GcloudMachine> retMachineList) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class GcloudClient in method listVMStatus with retMachineList: " + retMachineList);
        }
        List<GcloudMachine> vmList = new ArrayList<GcloudMachine>();
        if (CollectionUtils.isEmpty(retMachineList)) {
            log.error("Empty Google Cloud Machine List: " + retMachineList);
            return null;
        }

        for (GcloudMachine temRetMachine : retMachineList) {
            Instance retInstance = getCloudVM(temRetMachine.getName(), temRetMachine.getZone());
            if (retInstance != null) {
                temRetMachine.setStatus(retInstance.getStatus());
                temRetMachine.setResult(retInstance.getStatus());
            } else {
                temRetMachine.setStatus("DELETED");
                temRetMachine.setResult("succeed");
                log.warn("Failed to get google cloud instance. Set it to be DELETED. VM name: "
                         + temRetMachine.getName());
            }
            vmList.add(temRetMachine);
        }

        if (log.isTraceEnabled()) {
            log.trace("End in class GcloudClient in method listVMStatus with return vmList: " + vmList);
        }

        return vmList;
    }

    /**
     *
     * @Title: listVM
     * @Description: List VM by ID
     * @param machineList, machines list in db file
     * @return Map<String, Instance>, a list of instances
     * @throws
     */
    public static Map<String, Instance> listVM(List<GcloudMachine> machineList) {

        if (log.isTraceEnabled()) {
            log.trace("Start in class GcloudClient in method listVM with parameters machineList: " + machineList);
        }

        Map<String, Instance> vmMap = new HashMap<String, Instance>();

        for (GcloudMachine tempMachine : machineList) {
            Instance instance = getCloudVM(tempMachine.getName(), tempMachine.getZone());

            vmMap.put(tempMachine.getMachineId(), instance);
            if (instance == null) {
                log.warn("Failed to get instance. It may be has been already deleted. tempMachine:" + tempMachine);
            }
        }


        if (log.isTraceEnabled()) {
            GcloudUtil.dumpInstMap(vmMap, "listVM");
            log.trace("End in class GcloudClient in method listVM");
        }
        return vmMap;

    }



    /**
     * TODO, leave here for bulk instance
     *
     * @param requestsToBeChecked
     */
    public static List<GcloudRequest> retrieveInstancesMarkedForTermination(List<GcloudRequest> requestsList) {
        if (log.isTraceEnabled()) {
            log.trace(
                "[getReturnRequest]Start in class GcloudClient in method getInstancesStatus with parameters: requestsList: "
                + requestsList);
        }
        List<GcloudRequest> requestList = new ArrayList<GcloudRequest>();

        if (log.isTraceEnabled()) {
            log.trace("[getReturnRequest]End in class GcloudClient in method getInstancesStatus with return: void: "
                      + null);
        }
        return requestList;
    }

    /**
     * Get one instance from one Zone
     *
     * @param instance
     * @return
     */
    public static Instance getCloudVM(String machineNamne, String zone) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class GcloudClient in method getCloudVM with parameter machineNamne: " + machineNamne
                      + " zone: " + zone);
        }

        if (machineNamne == null || zone == null) {
            log.error("Invalid input parameter machineNamne: " + machineNamne + " zone: " + zone);
        }

        Compute compute = getClient();
        String project = GcloudUtil.getConfig().getProjectID();
        Instance retInstance = null;
        try {
            Compute.Instances.Get get = compute.instances().get(project, zone, machineNamne);
            retInstance = get.setRequestHeaders(HTTP_HEADER).execute();
            if (log.isTraceEnabled()) {
                log.trace("Http header userAgent: " + get.getRequestHeaders().getUserAgent());
            }

        } catch (IOException e) {
            log.warn("Failed to get google cloud instance. machineNamne: " + machineNamne + " zone: " + zone);
            log.debug(e);
        }

        if (log.isTraceEnabled()) {
            log.trace("End in class GcloudClient in method listVMStatus with return retInstance: ");
            if (retInstance != null) {
                log.debug("Instance: id:<" + retInstance.getId().toString() + "> name:<" + retInstance.getName() + " status:<" + retInstance.getStatus() +     ">.");
            } else {
                log.debug("Instance: NULL");
            }
        }
        return retInstance;

    }

    /**
     * Try to get an instance's private IP address
     *
     * @param instance
     * @return
     */
    public static String getInstancePrivateIP(Instance instance) {
        String privateIP = "";
        try {
            privateIP = instance.getNetworkInterfaces().get(0).getNetworkIP();
        } catch (Exception e) {
            privateIP = "";
        }
        return privateIP;

    }

    /**
     * Try to get an instance's public IP address
     *
     * @param instance
     * @return
     */
    public static String getInstancePublicIP(Instance instance) {
        String publicIP = "";
        try {
            publicIP = instance.getNetworkInterfaces().get(0).getAccessConfigs().get(0).getNatIP();
        } catch (Exception e) {
            log.trace("The specified instance has not gotten public IP address" + instance.toString());
            publicIP = "";
        }
        return publicIP;

    }

    /**
     * Try to get an instance's public IP address
     *
     * @param instance
     * @return
     */
    public static Map<String, String> createInstanceTagMap(String accountTagValue, String additionalInstanceTags, String bulkInsertId) {
        Map<String, String> labelMap = new HashMap<String, String>();

        String[] instanceTagStr = null;

        if (!StringUtils.isEmpty(accountTagValue)) {
            labelMap.put(GcloudConst.INSTANCE_RC_ACCOUNT_KEY, accountTagValue.toLowerCase());
        }
        if (!StringUtils.isEmpty(additionalInstanceTags)) {
            instanceTagStr = additionalInstanceTags.split(";");
            for (String inst : instanceTagStr) {
                String[] instSubStr = inst.split("=", 2);
                if (instSubStr.length == 2 && !StringUtils.isEmpty(instSubStr[0])
                        && !StringUtils.isEmpty(instSubStr[1])) {
                    labelMap.put(instSubStr[0].toLowerCase(), instSubStr[1].toLowerCase());
                }
            }

        }
        if (StringUtils.isNotBlank(bulkInsertId)) {  // If bulkInsertId is null or empty, skip
            labelMap.put(GcloudConst.BULK_INSERT_LABEL_KEY, bulkInsertId.toLowerCase());
        }
        return labelMap;

    }

    /**
     *
     * @Title: getBulkInsertInstanceResource
     * @Description: fill BulkInsertInstanceResource class
     * @param t
     * @param projectId
     * @param tagValue
     * @param bulkInsertId
     * @return
     */

    public static BulkInsertInstanceResource getBulkInsertInstanceResource(GcloudTemplate t, String projectId, String tagValue, String bulkInsertId) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class GcloudClient in method getBulkInsertInstanceResource with parameters: t: " + t + ", tagValue: "
                      + tagValue + ", bulkInsertId: " + bulkInsertId);
        }
        long minCount = 1;
        String namePattern = "compute-" + RandomStringUtils.randomAlphanumeric(5).toLowerCase() + "####";
        BulkInsertInstanceResource bulkResource = new BulkInsertInstanceResource();
        bulkResource.setCount(t.getVmNumber().longValue());
        bulkResource.setMinCount(minCount);
        bulkResource.setNamePattern(namePattern);

        if (StringUtils.isNotBlank(t.getLaunchTemplateId())) {
            String templateNameStr = "global/instanceTemplates/" + t.getLaunchTemplateId();
            bulkResource.setSourceInstanceTemplate(templateNameStr);
        }

        InstanceProperties instanceProperties = getInstanceProperties (t, projectId, tagValue, bulkInsertId);
        bulkResource.setInstanceProperties(instanceProperties);

        try {
            if (log.isTraceEnabled()) {
                log.trace("BulkInsertInstanceResource properties: " + bulkResource.toPrettyString());
            }
        } catch (IOException e) {
            log.warn("bulkResource.toPrettyString() execution failed. " + e);
        }

        if (log.isTraceEnabled()) {
            log.trace("End in class GcloudClient in method getBulkInsertInstanceResource.");
        }

        return bulkResource;
    }


    /**
     *
     * @Title: createBulkVMs
     * @Description: create VMs by bulkInsert API call
     * @param t
     * @param tagValue
     * @param bulkInsertId
     * @return
     */
    public static String createBulkVMs(GcloudTemplate t, String tagValue, String bulkInsertId, HostAllocationType allocType, GcloudEntity rsp) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class GcloudClient in method createBulkVMs with parameters: t: " + t + ", tagValue: "
                      + tagValue + ", bulkInsertId: " + bulkInsertId);
        }

        String projectId = GcloudUtil.getConfig().getProjectID();
        String zone = t.getZone();
        String region = (StringUtils.isNotEmpty(t.getRegion())) ? t.getRegion() : GcloudUtil.getConfig().getGcloudRegion();

        BulkInsertInstanceResource bulkResource = getBulkInsertInstanceResource(t, projectId, tagValue, bulkInsertId);

        String bulkOperationId = null;
        try {
            if (allocType.equals(HostAllocationType.ZonalBulk)) {
                Compute.Instances.BulkInsert bulkInsert = gcloudCompute.instances().bulkInsert(projectId, zone, bulkResource);
                Operation bulkOp = bulkInsert.setRequestHeaders(HTTP_HEADER).execute();
                bulkOperationId = bulkOp.getName();
            } else if (allocType.equals(HostAllocationType.RegionalBulk)) {
                Compute.RegionInstances.BulkInsert bulkInsert = gcloudCompute.regionInstances().bulkInsert(projectId, region, bulkResource);
                Operation bulkOp = bulkInsert.setRequestHeaders(HTTP_HEADER).execute();
                bulkOperationId = bulkOp.getName();
            }
        } catch (GoogleJsonResponseException e1) {
            if (e1.getDetails() != null
                    && CollectionUtils.isNotEmpty(e1.getDetails().getErrors())
                    && e1.getDetails().getErrors().get(0) != null ) {
                String errorReason = e1.getDetails().getErrors().get(0).getReason();
                // ***** NOTICE - THIS IS VERY IMPORTANT *****
                // Need follow "Error Code: errorReason" format for error handling in ebrokerd. Refer chkAllocateFail.
                rsp.setRsp(1, "Create bulk VMs on " + GcloudUtil.getProviderName() + " failed. Error Code: " + errorReason);
            } else {
                rsp.setRsp(1, "Create bulk VMs on " + GcloudUtil.getProviderName() + " failed.");
            }
            rsp.setStatus(GcloudConst.EBROKERD_STATE_WARNING);
            bulkOperationId = "";
            log.error("Create instance error. " + e1);
        } catch (Exception e) {
            bulkOperationId = "";
            rsp.setStatus(GcloudConst.EBROKERD_STATE_WARNING);
            rsp.setRsp(1, "Create bulk VMs on " + GcloudUtil.getProviderName() + " failed.");
            log.error("Create instance error. " + e);
        }

        if (log.isTraceEnabled()) {
            log.trace("End in class GcloudClient in method createBulkVMs.");
        }

        return bulkOperationId;

    }


    /**
     * @Title: updateBulkRequestStatus
     * @Description: update bulk request status by query bulk operation status
     * @param req
     */
    public static void updateBulkRequestStatus(GcloudRequest req, GcloudEntity rsp) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class GcloudClient in method updateBulkRequestStatus with parameters: GcloudRequest: " + req);
        }
        String projectId = GcloudUtil.getConfig().getProjectID();
        GcloudTemplate at = GcloudUtil.getTemplateFromFile(req.getTemplateId());
        Compute compute = getClient();
        String bulkOperationId = req.getReqId();
        String opStatus = null;
        String ebrokerdRequestStatus = null;

        try {
            // Query zonal or regional operation API according to allocation type
            if (HostAllocationType.ZonalBulk.toString().equals(req.getHostAllocationType())) {
                String zone = at.getZone();
                Compute.ZoneOperations.Get get = compute.zoneOperations().get(projectId, zone, bulkOperationId);
                Operation op = get.setRequestHeaders(HTTP_HEADER).execute();
                opStatus = op.getStatus();
            } else if (HostAllocationType.RegionalBulk.toString().equals(req.getHostAllocationType())) {
                String region = (StringUtils.isNotEmpty(at.getRegion())) ? at.getRegion() : GcloudUtil.getConfig().getGcloudRegion();
                Compute.RegionOperations.Get get = compute.regionOperations().get(projectId, region, bulkOperationId);
                Operation op = get.setRequestHeaders(HTTP_HEADER).execute();
                opStatus = op.getStatus();
            }

        } catch (HttpResponseException e1) {
            // Retry for HTTP errors 429 (RESOURCE_EXHAUSTED) and 503 (UNAVAILABLE)
            // Refer https://cloud.google.com/apis/design/errors
            // According to my test, QUOTA exceeded would report 403 Forbidden errors, so add it here too

            if (e1.getStatusCode() != 429
                    && e1.getStatusCode() != 503
                    && e1.getStatusCode() != 403) {
                ebrokerdRequestStatus = GcloudConst.EBROKERD_STATE_COMPLETE_WITH_ERROR;
                req.setStatus(ebrokerdRequestStatus);
                log.debug("Set bulk request [" + bulkOperationId + "] status to compute_with_error.");
            }
            rsp.setStatus(GcloudConst.EBROKERD_STATE_WARNING);
            rsp.setRsp(1, "Get bulk operation status failed for request [" + bulkOperationId + "].");
            log.error("Failed to get bulkInsert operation [" + bulkOperationId + "] status." + e1);
            return;
        } catch (IOException e) {
            rsp.setStatus(GcloudConst.EBROKERD_STATE_WARNING);
            rsp.setRsp(1, "Get bulk operation status failed for request [" + bulkOperationId + "].");
            log.error("Failed to get bulkInsert operation [" + bulkOperationId + "] status." + e);
            return;
        }

        // Setting the status of the request
        if("PENDING".equals(opStatus) || "RUNNING".equals(opStatus)) {
            ebrokerdRequestStatus = GcloudConst.EBROKERD_STATE_RUNNING;
        } else if ("DONE".equals(opStatus)) {
            ebrokerdRequestStatus = GcloudConst.EBROKERD_STATE_COMPLETE;
        } else { // Should not happen
            ebrokerdRequestStatus = GcloudConst.EBROKERD_STATE_COMPLETE_WITH_ERROR;
        }

        req.setStatus(ebrokerdRequestStatus);

        if (log.isDebugEnabled()) {
            log.debug("BulkInsert operation [" + bulkOperationId + "] status <" +
                      opStatus + ">, set bulk request status to <" + ebrokerdRequestStatus + ">.");
        }

        if (log.isTraceEnabled()) {
            log.trace("End in class GcloudClient in method updateBulkRequestStatus, ebrokerdRequestStatus: " + ebrokerdRequestStatus);
        }

    }


    /**
     *
     * @Title: updateBulkVMList
     * @Description: update VM list created by a bulk request
     * @param req
     * @return
     */
    public static Map<String, Instance> updateBulkVMList(GcloudRequest req, GcloudEntity rsp) {
        Map<String, Instance> vmMap = new HashMap<String, Instance>();
        List<GcloudMachine> newMachinesList = new ArrayList<GcloudMachine>();
        GcloudTemplate at = GcloudUtil.getTemplateFromFile(req.getTemplateId());
        String templateId = at.getTemplateId();
        String bulkRequestId = req.getReqId();
        String bulkInsertId = req.getBulkInsertId();
        String rcAccount = req.getTagValue();
        Map<String, Instance> gcloudVMMap = new HashMap<String, Instance>();

        if (log.isDebugEnabled()) {
            log.debug("Start in class GcloudClient in method updateBulkVMList with parameters: GcloudRequest: " + req);
        }

        // Update bulkInsert Operation status
        updateBulkRequestStatus(req, rsp);

        // Checking if there are new instances created
        List<Instance> instances = new ArrayList<Instance>();

        // Using label bulk_id = bulkInsertId to filter instances created by this bulk request
        String filterStr = "labels." + GcloudConst.BULK_INSERT_LABEL_KEY + "=" + bulkInsertId ;
        log.debug("Bulk filter string: " + filterStr);

        // Get instances created by this bulk request
        if (HostAllocationType.ZonalBulk.toString().equals(req.getHostAllocationType())) {
            instances = getZonalBulkInstances(req, rsp);
        } else if (HostAllocationType.RegionalBulk.toString().equals(req.getHostAllocationType())) {
            instances = getRegionalBulkInstances(req, rsp);
        }

        for (Instance instance : instances) {
            GcloudMachine tempGcloudMachine = new GcloudMachine();
            tempGcloudMachine.setMachineId(instance.getId().toString());
            if (!req.getMachines().contains(tempGcloudMachine)) {
                if (log.isDebugEnabled()) {
                    log.debug("This is a newly created machine: " + instance);
                }
                newMachinesList.add(tempGcloudMachine);
            }
            gcloudVMMap.put(instance.getId().toString(), instance);
        }

        if (!newMachinesList.isEmpty()) {
            for (GcloudMachine newMachine : newMachinesList) {
                Instance instance = gcloudVMMap.get(newMachine.getMachineId());
                newMachine = GcloudUtil.mapGcloudInstanceToGcloudMachine(bulkRequestId, templateId, instance, rcAccount, newMachine);
            }

            // Add the new machines to the actual machines list
            int oldMachineCount = req.getMachines().size();
            req.getMachines().addAll(newMachinesList);
            if (log.isDebugEnabled()) {
                log.debug("The count of machines before adding new machines: " + oldMachineCount);
                log.debug("The count of machines after adding new machines: "
                          + req.getMachines().size() + ", newMachinesList: " + newMachinesList);
            }
        }

        for (GcloudMachine tempMachine : req.getMachines()) {
            Instance instance = gcloudVMMap.get(tempMachine.getMachineId());
            vmMap.put(tempMachine.getMachineId(), instance);
            if (instance == null) {
                log.warn("Failed to get instance from cloud. It may have already been deleted. tempMachine:" + tempMachine);
            }
        }

        if (log.isTraceEnabled()) {
            GcloudUtil.dumpInstMap(vmMap, "updateBulkVMList");
            log.trace("End in class GcloudClient in method updateBulkVMList");
        }
        return vmMap;

    }

    /**
     *
     * @Title: getInstanceList
     * @Description: get instance List by the specified filter
     * @param projectId
     * @param filterStr
     * @return
     * @throws IOException
     */
    public static List<Instance> getInstanceList(String projectId, String filterStr) throws IOException {
        if (log.isTraceEnabled()) {
            log.trace("Start in class GcloudClient in method getInstanceList");
        }

        Compute compute = getClient();
        List<Instance> instances = new ArrayList<Instance>();
        InstanceAggregatedList response = null;
        Compute.Instances.AggregatedList request = compute.instances().aggregatedList(projectId);
        if (StringUtils.isNotBlank(filterStr)) {
            request.setFilter(filterStr);
        }
        do {
            response = request.setRequestHeaders(HTTP_HEADER).execute();
            request.setPageToken(response.getNextPageToken());
            if (response.getItems() == null) {
                continue;
            }
            for (Map.Entry<String, InstancesScopedList> item : response.getItems().entrySet()) {
                if (item != null
                        && item.getValue() != null
                        && CollectionUtils.isNotEmpty(item.getValue().getInstances())) {
                    instances.addAll(item.getValue().getInstances());
                }
            }
        } while (response.getNextPageToken() != null);

        if (log.isTraceEnabled()) {
            GcloudUtil.dumpInstList(instances, "getInstanceList");
            log.trace("End in class GcloudClient in method getInstanceList");
        }

        return instances;
    }

    /**
     *
     * @Title: getZonalBulkInstances
     * @Description: get instance List for a zonal bulk request
     * @param req
     * @param rsp
     * @return
     */
    public static List<Instance> getZonalBulkInstances(GcloudRequest req, GcloudEntity rsp) {
        Compute compute = getClient();
        String projectId = GcloudUtil.getConfig().getProjectID();
        GcloudTemplate at = GcloudUtil.getTemplateFromFile(req.getTemplateId());
        String zone = at.getZone();
        String bulkRequestId = req.getReqId();
        String bulkInsertId = req.getBulkInsertId();
        List<Instance> instances = new ArrayList<Instance>();
        InstanceList response = null;

        if (log.isTraceEnabled()) {
            log.trace("Start in class GcloudClient in method getZonalBulkInstances, zone: " + zone );
        }

        // Using label bulk_id = bulkInsertId to filter instances created by this bulk request
        String filterStr = "labels." + GcloudConst.BULK_INSERT_LABEL_KEY + "=" + bulkInsertId ;
        log.debug("Bulk filter string: " + filterStr);

        try {
            Compute.Instances.List request = compute.instances().list(projectId, zone);
            request.setFilter(filterStr);
            do {
                response = request.setRequestHeaders(HTTP_HEADER).execute();
                request.setPageToken(response.getNextPageToken());
                if (response.getItems() == null) {
                    continue;
                }
                instances.addAll(response.getItems());
            } while (response.getNextPageToken() != null);
        } catch (Exception e) {
            log.error("Get instance list failed. " + e);
            rsp.setStatus(GcloudConst.EBROKERD_STATE_WARNING);
            rsp.setRsp(1, "Get zonal bulk instance list failed for request [" + bulkRequestId + "].");
        }

        if (log.isTraceEnabled()) {
            GcloudUtil.dumpInstList(instances, "getZonalBulkInstances-" + zone);
            log.trace("End in class GcloudClient in method getZonalBulkInstances.");
        }
        return instances;
    }


    /**
     *
     * @Title: getRegionalBulkInstances
     * @Description: get instance List for a regional bulk request
     * @param req
     * @param rsp
     * @return
     */
    public static List<Instance> getRegionalBulkInstances (GcloudRequest req, GcloudEntity rsp) {
        String projectId = GcloudUtil.getConfig().getProjectID();
        GcloudTemplate at = GcloudUtil.getTemplateFromFile(req.getTemplateId());
        String bulkRequestId = req.getReqId();
        String bulkInsertId = req.getBulkInsertId();
        String region = (StringUtils.isNotEmpty(at.getRegion())) ? at.getRegion() : GcloudUtil.getConfig().getGcloudRegion();
        List<Instance> instances = null;

        if (log.isTraceEnabled()) {
            log.trace("Start in class GcloudClient in method getRegionalBulkInstances, region: " + region);
        }

        // Using label bulk_id = bulkInsertId to filter instances created by this bulk request
        String filterStr = "labels." + GcloudConst.BULK_INSERT_LABEL_KEY + "=" + bulkInsertId ;
        log.debug("Bulk filter string: " + filterStr);

        try {
            instances = getInstanceList(projectId, filterStr);
        } catch (IOException e) {
            rsp.setStatus(GcloudConst.EBROKERD_STATE_WARNING);
            rsp.setRsp(1, "Get regional bulk instance list failed for request [" + bulkRequestId + "].");
            log.error("Get instance list failed. " + e);
        }

        if (log.isTraceEnabled()) {
            GcloudUtil.dumpInstList(instances, "getRegionalBulkInstances-" + region);
            log.trace("End in class GcloudClient in method getRegionalBulkInstances.");
        }

        return instances;
    }


    /**
     *
     * @Title: getCloudVMMap
     * @Description: get all VMs on cloud for user project
     * @param
     * @return Map<String, Instance>, a list of instances
     * @throws
     */
    public static Map<String, Instance> getCloudVMMap() {

        if (log.isTraceEnabled()) {
            log.trace("Start in class GcloudClient in method getCloudVMMap()");
        }

        Map<String, Instance> vmMap = new HashMap<String, Instance>();
        String projectId = GcloudUtil.getConfig().getProjectID();

        // Get instance list on cloud
        List<Instance> instList = null;
        try {
            instList = getInstanceList(projectId, null);
        } catch (IOException e) {
            log.error("Get instance list failed. " + e);
        }

        // Add these instances to vmMap
        if (CollectionUtils.isNotEmpty(instList)) {
            for (Instance inst : instList) {
                vmMap.put(inst.getId().toString(), inst);
            }
        }

        if (log.isTraceEnabled()) {
            GcloudUtil.dumpInstMap(vmMap, "getCloudVMMap");
            log.trace("End in class GcloudClient in method getCloudVMMap()");
        }
        return vmMap;

    }
}
