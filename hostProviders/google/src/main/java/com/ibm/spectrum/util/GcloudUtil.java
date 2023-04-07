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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ibm.spectrum.constant.GcloudConst;
import com.ibm.spectrum.model.GcloudConfig;
import com.ibm.spectrum.model.GcloudEntity;
import com.ibm.spectrum.model.GcloudMachine;
import com.ibm.spectrum.model.GcloudRequest;
import com.ibm.spectrum.model.GcloudTemplate;
import com.ibm.spectrum.model.HostAllocationType;
import com.google.api.client.util.DateTime;
import com.google.api.services.compute.model.Instance;

/**
 * @ClassName: GcloudUtil
 * @Description: The common utilities
 * @author zcg
 * @date Sep 11, 2017 3:36:12 PM
 * @version 1.0
 */
public class GcloudUtil {
    private static Logger log = LogManager.getLogger(GcloudUtil.class);

    private static ObjectMapper mapper = new ObjectMapper();
    static {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

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
     * gcloud configuration
     */
    private static GcloudConfig config;

    /**
     * provider name
     */
    private static String providerName;

    /**
     * Gcloud db file
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
        if (null == GcloudUtil.homeDir) {
            GcloudUtil.homeDir = dir;
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
        if (null == GcloudUtil.confDir) {
            GcloudUtil.confDir = dir;
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
        if (null == GcloudUtil.logDir) {
            GcloudUtil.logDir = dir;
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
        if (null == GcloudUtil.workDir) {
            GcloudUtil.workDir = dir;
        }
    }

    /**
     * @return config
     */
    public static GcloudConfig getConfig() {
        return config;
    }

    /**
     * @param config
     *            the config to set
     */
    public static void setConfig(GcloudConfig config) {
        if (null == GcloudUtil.config) {
            GcloudUtil.config = config;
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
        if (null == GcloudUtil.providerName) {
            GcloudUtil.providerName = providerName;
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
        if (null == GcloudUtil.provStatusFile) {
            GcloudUtil.provStatusFile = provStatusFile;
        }
    }

    /**
     * @Title: writeToFile @Description: write data to file @param @param
     *         fileName @param @param data @return void @throws
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
     *         obj @param @return @return String @throws
     */
    public static synchronized <T> String toJsonTxt(T obj) {

        String jsonTxt = "";

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
     *         obj @param @return @return String @throws
     */
    public static synchronized <T> void toJsonFile(T obj, String jfname) {
        try {
            mapper.writeValue(new File(jfname), obj);
        } catch (Exception e) {
            log.error("Write object to json file error.", e);
        }
    }

    /**
     *
     *
     * @Title: toJsonFile @Description: Object to json file @param @param
     *         obj @param @param jf @return void @throws
     */
    public static synchronized <T> void toJsonFile(T obj, File jf) {
        try {
            mapper.writeValue(jf, obj);
        } catch (Exception e) {
            log.error("Write object to json file error.", e);
        }
    }

    /**
     *
     *
     * @Title: toObject @Description: Json file to Object @param @param
     *         jsonFile @param @param type @param @return @return T @throws
     */
    public static <T> T toObject(File jsonFile, Class<T> type) {
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
     *         jsonTxt @param @param valueType @param @return @return T @throws
     */
    public static <T> T toObject(String jsonTxt, Class<T> type) {
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
     *         obj @param @return @return String @throws
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
     *         file @param @return @return T @throws
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
     *         vmMap @return void @throws
     */
    public static void updateToFile(Map<String, GcloudMachine> vmMap) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class GcloudUtil in method updateToFile with parameters: vmMap: " + vmMap);
        }
        File jf = new File(workDir + "/" + provStatusFile);
        if (!jf.exists() || null == vmMap || vmMap.isEmpty()) {
            return;
        }

        GcloudEntity ae = GcloudUtil.toObject(jf, GcloudEntity.class);
        if (CollectionUtils.isEmpty(ae.getReqs())) {
            return;
        }

        for (GcloudRequest req : ae.getReqs()) {
            List<GcloudMachine> mLst = req.getMachines();
            if (CollectionUtils.isEmpty(mLst)) {
                continue;
            }

            for (GcloudMachine m : mLst) {
                if (vmMap.containsKey(m.getMachineId())) {
                    m.copyValues(vmMap.get(m.getMachineId()));
                }
            }
        }

        GcloudUtil.toJsonFile(ae, jf);
    }

    /**
     *
     * @Title: saveToFile @Description: save record to local file @param @param
     *         req @return void @throws
     */
    public static void saveToFile(GcloudRequest req) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class GcloudUtil in method saveToFile with parameters: req: " + req);
        }
        File jf = new File(workDir + "/" + provStatusFile);

        try {
            // Add new VM record
            if (!jf.exists()) {
                List<GcloudRequest> reqLst = new ArrayList<GcloudRequest>();
                reqLst.add(req);

                GcloudEntity ae = new GcloudEntity();
                ae.setReqs(reqLst);

                GcloudUtil.toJsonFile(ae, jf);

                return;
            } else {
                log.info("gcloud-db.json file exists ");
            }

            // Append VM record
            GcloudEntity ae = GcloudUtil.toObject(jf, GcloudEntity.class);
            for (GcloudRequest rq : ae.getReqs()) {
                // Update exist record
                if (rq.getReqId().equals(req.getReqId())) {
                    rq.update(req);
                    GcloudUtil.toJsonFile(ae, jf);
                    return;
                }
            }
            // append the request
            ae.getReqs().add(req);
            GcloudUtil.toJsonFile(ae, jf);

        } catch (Exception e) {
            log.error("Error: ", e);
        }

        if (log.isTraceEnabled()) {
            log.trace("End in class GcloudUtil in method saveToFile");
        }
    }

    /**
     *
     * @Title: getFromFile @Description: get record from local file @param reqId
     *
     * @return GcloudRequest @throws
     */
    public static GcloudRequest getFromFile(String reqId) {
        File f = new File(workDir + "/" + provStatusFile);
        if (!f.exists()) {
            return null;
        }

        GcloudEntity ae = GcloudUtil.toObject(f, GcloudEntity.class);
        List<GcloudRequest> reqLst = ae.getReqs();
        log.debug("Returning the requests: " + reqLst);
        if (CollectionUtils.isEmpty(reqLst)) {
            return null;
        }

        List<GcloudMachine> retMLst = new ArrayList<GcloudMachine>();
        for (GcloudRequest req : reqLst) {
            // request request object by request ID case
            if (req.getReqId().equals(reqId)) {
                if (log.isTraceEnabled()) {
                    log.trace("End in class GcloudUtil in method getFromFile with return: GcloudRequest: " + req);
                }
                return req;
            }

            List<GcloudMachine> mLst = req.getMachines();
            // return request object by machine return ID case
            for (GcloudMachine m : mLst) {
                if (reqId.equals(m.getRetId())) {
                    retMLst.add(m);
                }
            }
        }
        GcloudRequest returnGcloudRequest = null;
        if (!CollectionUtils.isEmpty(retMLst)) {
            returnGcloudRequest = new GcloudRequest();
            returnGcloudRequest.setReqId(reqId);
            returnGcloudRequest.setMachines(retMLst);
        }

        if (log.isTraceEnabled()) {
            log.trace(
                "End in class GcloudUtil in method getFromFile with return: GcloudRequest: " + returnGcloudRequest);
        }
        return returnGcloudRequest;
    }

    /**
     *
     * @Title: getFromFile @Description: get all request from
     *         file @param @return @return List<GcloudRequest> @throws
     */
    public static GcloudEntity getFromFile() {
        File f = new File(workDir + "/" + provStatusFile);
        if (!f.exists()) {
            return null;
        }

        GcloudEntity ae = GcloudUtil.toObject(f, GcloudEntity.class);
        return ae;
    }

    /**
     *
     *
     * @Title: saveToFile @Description: save GcloudEntity object to json
     *         file @param @param ae @return void @throws
     */
    public static void saveToFile(GcloudEntity ae) {
        File jf = new File(workDir + "/" + provStatusFile);
        if (!jf.exists()) {
            return;
        }

        if (null == ae) {
            return;
        }

        GcloudUtil.toJsonFile(ae, jf);
    }

    /**
     *
     *
     * @Title: getVMFromFile @Description: get VM from local file @param @param
     *         vmNames @param @return @return Map<String,GcloudMachine> @throws
     */
    public static Map<String, GcloudMachine> getVMFromFile(List<String> vmNames) {
        Map<String, GcloudMachine> vmMap = new HashMap<String, GcloudMachine>();
        File jf = new File(workDir + "/" + provStatusFile);
        if (!jf.exists()) {
            return vmMap;
        }

        GcloudEntity ae = GcloudUtil.toObject(jf, GcloudEntity.class);
        List<GcloudRequest> reqLst = ae.getReqs();
        if (CollectionUtils.isEmpty(reqLst)) {
            return vmMap;
        }

        for (GcloudRequest req : reqLst) {
            List<GcloudMachine> mLst = req.getMachines();
            if (CollectionUtils.isEmpty(mLst)) {
                continue;
            }

            for (GcloudMachine m : mLst) {
                if (vmNames.contains(m.getName())) {
                    if (!("Terminated".equalsIgnoreCase(m.getStatus())) && StringUtils.isEmpty(m.getRetId())) {
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
     *         local file @param @return void @throws
     */
    public static int deleteFromFile() {
        int onlineNum = 0;
        File jf = new File(workDir + "/" + provStatusFile);
        if (!jf.exists()) {
            return onlineNum;
        }

        GcloudEntity ae = GcloudUtil.toObject(jf, GcloudEntity.class);
        List<GcloudRequest> reqLst = ae.getReqs();
        if (CollectionUtils.isEmpty(reqLst)) {
            return onlineNum;
        }

        boolean isUpdated = false;
        List<GcloudMachine> delMLst = new ArrayList<GcloudMachine>();
        List<GcloudRequest> delRLst = new ArrayList<GcloudRequest>();
        for (GcloudRequest req : reqLst) {
            List<GcloudMachine> mLst = req.getMachines();
            if (CollectionUtils.isEmpty(mLst)) {
                delRLst.add(req);
                isUpdated = true;
                continue;
            }

            delMLst.clear();
            for (GcloudMachine m : mLst) {
                if ("Terminated".equalsIgnoreCase(m.getStatus()) && StringUtils.isNotEmpty(m.getRetId())) {
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
            GcloudUtil.toJsonFile(ae, jf);
        }

        return onlineNum;
    }

    /**
     *
     * @Title: getTemplateFromFile @Description: get template from
     *         file @param @param templateId @param @return @return
     *         GcloudTemplate @throws
     */
    public static GcloudTemplate getTemplateFromFile(String templateId) {
        File jf = new File(confDir + "/conf/" + GcloudConst.GOOGLEPROV_TEMPLATE_FILENAME);
        if (!jf.exists()) {
            log.error("Template file does not exist: " + jf.getPath());
            return null;
        }

        GcloudEntity ae = GcloudUtil.toObject(jf, GcloudEntity.class);
        List<GcloudTemplate> tLst = ae.getTemplates();
        for (GcloudTemplate at : tLst) {
            if (at.getTemplateId().equals(templateId)) {
                if (log.isTraceEnabled()) {
                    log.trace(
                        "End in class GcloudUtil in method getTemplateFromFile with return: GcloudTemplate: " + at);
                }
                return at;
            }
        }

        return null;
    }

    public static String readFileToString(String filePath) {

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


    /**
     * Retrieves user data
     *
     * @return
     */
    public static String getUserDataScriptContent(GcloudTemplate t, String tagValue, String bulkInsertId) {
        String exportCmd = "";
        String usrDatafileToStr = "";

        try {
            File userDataFile = new File(homeDir + GcloudConst.GCLOUD_USER_DATA_FILE);
            if (userDataFile.exists()) {
                usrDatafileToStr = readFileToString(userDataFile.getAbsolutePath());
                /*
                 * TODO Export only zone and templateName values from the
                 * userDataObj packages should be used for software installation
                 * on the new VM
                 */
                if (!StringUtils.isEmpty(t.getUserData())) {
                    exportCmd = "export " + t.getUserData().replaceAll(";", " ")  + ";";
                }
                if (!StringUtils.isEmpty(tagValue)) {
                    exportCmd = exportCmd + "export rc_account=" + tagValue + ";";
                }
                if (!StringUtils.isEmpty(t.getTemplateId())) {
                    exportCmd = exportCmd + "export template_id=" + t.getTemplateId() + ";";
                }
                String clusterName = System.getProperty("clusterName");
                if (!StringUtils.isEmpty(clusterName)) {
                    exportCmd = exportCmd + "export clustername=" + clusterName + ";";
                }
                String providerName = System.getenv("PROVIDER_NAME");
                if (!StringUtils.isEmpty(providerName)) {
                    exportCmd = exportCmd + "export providerName=" + providerName + ";";
                }
                if (StringUtils.isNotBlank(bulkInsertId)) {
                    exportCmd = exportCmd + "export bulkInsertId=" + bulkInsertId + ";";
                }
                usrDatafileToStr = usrDatafileToStr.replaceAll("%EXPORT_USER_DATA%", exportCmd);
                // encodedUserData = new String( Base64.encodeBase64(
                // usrDatafileToStr.getBytes( "UTF-8" )), "UTF-8" );
            }
        } catch (Exception e) {
            log.error("Error occured during encoding the user data", e);
        }
        return usrDatafileToStr;
    }

    /**
     * Maps an Instance retrieved from the Google Cloud API to the GcloudMachine
     * object passed in the parameters
     *
     * @param templateId
     *            The ID of the template used to request this instance
     * @param reqId
     *            The request Id of this instance
     * @param instance
     *            The instance to be mapped
     * @param instanceTagVal
     *            The tags to be added to that instance
     * @param Machine
     *            The GcloudMachine object that needs to have the values mapped
     *            to
     * @return The GcloudMachine object with the new values
     */
    public static GcloudMachine mapGcloudInstanceToGcloudMachine(String bulkRequestId, String templateId, Instance instance,
            String rcAccount, GcloudMachine machine) {

        if (machine == null) {
            machine = new GcloudMachine();
        }

        if (instance != null) {
            machine.setMachineId(instance.getId().toString());
            machine.setName(instance.getName());
            machine.setReqId(bulkRequestId);
            machine.setStatus(instance.getStatus());
            machine.setResult(instance.getStatus());
            // Get zone from url 'https://www.googleapis.com/compute/v1/projects/lsf-core-qa/zones/us-east1-c'
            String zoneSplit[] = instance.getZone().split("/");
            String zone = zoneSplit[zoneSplit.length - 1];
            machine.setZone(zone);
            // Here we do not get separate operation for each VM, just set bulk operation ID
            machine.setOperationId(bulkRequestId);
            machine.setTemplate(templateId);
            machine.setRcAccount(rcAccount);
            // Set launch time according to creationTimestamp
            // A problem here is: whether should we use local time (System.currentTimeMillis()) or UTC time?
            // They may be inconsistent.
            String launchTimestamp = instance.getCreationTimestamp();
            long launchTime = DateTime.parseRfc3339(launchTimestamp).getValue() / 1000;
            machine.setLaunchtime(launchTime);
            if (log.isTraceEnabled()) {
                log.info("Instance [" + instance.getName() + "] creation time: "+ instance.getCreationTimestamp() +
                         "launchTime: " + launchTime + "currentTime: " + System.currentTimeMillis() / 1000);
            }
        }
        return machine;
    }

    /**
     * Checks if the machine matches any of the machines in the list
     */
    public static GcloudMachine getMatchingMachineInList(GcloudMachine machine, List<GcloudMachine> machineList) {
        GcloudMachine matchingMachine = null;
        if (log.isTraceEnabled()) {
            log.trace("Start in class GcloudUtil in method isMachineInList with parameters: machine: " + machine
                      + ", machineList: " + machineList);
        }

        if (!CollectionUtils.isEmpty(machineList) && machine != null) {
            for (GcloudMachine tempMachine : machineList) {
                log.trace("Comparing machine: [" + machine + "] with machine: " + tempMachine);
                if (tempMachine.equals(machine)) {
                    log.trace("Machine : [" + machine + "] matched with machine: " + tempMachine);
                    matchingMachine = tempMachine;
                    break;
                }
            }
        }
        if (log.isTraceEnabled()) {
            log.trace("End in class GcloudUtil in method isMachineInList with return: machine: " + matchingMachine);
        }
        return matchingMachine;
    }


    /**
     * Remove the machine with the same IP address in the DB file
     */
    public static void removeSameIpMachineInDB(GcloudMachine tempMachineInDB) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class GcloudUtil in method removeDuplicateMachineInDB with parameters: req: " + tempMachineInDB);
        }
        File jf = new File(workDir + "/" + provStatusFile);

        if(StringUtils.isEmpty(tempMachineInDB.getPrivateIpAddress())) {
            log.debug("tempMachineInDB private ip is null" + tempMachineInDB.getName());
            return;
        }

        try {
            if (!jf.exists()) {
                return;
            } else {
                log.trace("gcloud-db.json file exists ");
            }

            // Delete the VM record if the VM with the same IP had been created before
            GcloudEntity ae = GcloudUtil.toObject(jf, GcloudEntity.class);
            Iterator<GcloudRequest> rqIt = ae.getReqs().iterator();
            while(rqIt.hasNext()) {
                GcloudRequest rq = (GcloudRequest) rqIt.next();
                Iterator<GcloudMachine> vmIt = rq.getMachines().iterator();
                while(vmIt.hasNext()) {
                    GcloudMachine rqVm = (GcloudMachine) vmIt.next();
                    if(StringUtils.isEmpty(rqVm.getPrivateIpAddress())) {
                        log.trace("The machine in db has not been assign private IP, rqVm:" + rqVm);
                        continue;
                    }
                    // skip the same host
                    if(rqVm.equals(tempMachineInDB)) {
                        continue;
                    }

                    if(rqVm.getPrivateIpAddress().equals(tempMachineInDB.getPrivateIpAddress())) {
                        vmIt.remove();
                    }
                }
                if(CollectionUtils.isEmpty(rq.getMachines())
                        && HostAllocationType.OnDemand.toString().equals(rq.getHostAllocationType())) {
                    log.debug("Request <" + rq.getReqId() + "> is empty, Remove it from the DB");
                    rqIt.remove();
                }
            }
            GcloudUtil.toJsonFile(ae, jf);
        } catch (Exception e) {
            log.error("Error: ", e);
        }

        if (log.isTraceEnabled()) {
            log.trace("End in class GcloudUtil in method tempMachineInDB");
        }
    }


    /**
     *
     * @Title: validateConfig
     * @Description: validate the basic configuration and determine the allocation type
     * @param t
     * @return if invalid configuration, return null, otherwise, return allocationType
     */
    public static HostAllocationType validateConfig(GcloudTemplate t) {
        HostAllocationType allocationType = null;
        boolean bulkDisabled = false;
        String projectId = GcloudUtil.getConfig().getProjectID();
        String zone = t.getZone();
        String region = (StringUtils.isNotEmpty(t.getRegion())) ? t.getRegion() : GcloudUtil.getConfig().getGcloudRegion();

        if (StringUtils.isEmpty(projectId)) {
            log.error("GCLOUD_PROJECT_ID must be set in " + GcloudConst.GOOGLEPROV_CONFIG_FILENAME);
            return null;
        }

        if (GcloudUtil.getConfig().getBulkInsertEnabled() != null
                && GcloudUtil.getConfig().getBulkInsertEnabled() == false) {
            bulkDisabled = true;
        }

        if (bulkDisabled) {
            // For on-demand request, zone must be set
            if (StringUtils.isEmpty(zone)) {
                log.error("zone must be set in " + GcloudConst.GOOGLEPROV_TEMPLATE_FILENAME);
                return null;
            }
            allocationType = HostAllocationType.OnDemand;
        } else {
            // For bulk request, zone or region must be set properly
            if (StringUtils.isEmpty(zone)
                    && StringUtils.isEmpty(region)) {
                log.error("zone or region must be set in " + GcloudConst.GOOGLEPROV_TEMPLATE_FILENAME +
                          ", or a default region GCLOUD_REGION must be set in " + GcloudConst.GOOGLEPROV_CONFIG_FILENAME);
                return null;
            }
            if (StringUtils.isNotEmpty(zone)) {
                allocationType = HostAllocationType.ZonalBulk;
            } else {
                allocationType = HostAllocationType.RegionalBulk;
            }
        }
        String subnetId = t.getSubnetId();
        if (StringUtils.isNotEmpty(subnetId)) {
            if (StringUtils.isEmpty(region)) {
                log.error("region must be set if you set subnetId in " + GcloudConst.GOOGLEPROV_TEMPLATE_FILENAME);
                return null;
            }
        }
        return allocationType ;
    }



    public static void dumpInstList(List<Instance> instList, String caller) {
        log.debug("Begin dumpInstList: " + caller);
        for (Instance inst : instList) {
            log.debug("id:<" + inst.getId().toString() + "> name:<" + inst.getName() + " status:<" + inst.getStatus() + ">.");
        }
        log.debug("End dumpInstList: " + caller);
    }

    public static void dumpInstMap(Map<String, Instance> vmMap, String caller) {
        log.debug("Begin dumpInstMap: " + caller);
        for (Map.Entry<String, Instance> entry : vmMap.entrySet()) {
            String instId =  entry.getKey() ;
            Instance inst =  entry.getValue();
            if (inst != null) {
                log.debug("key:<" + instId + "> id:<" + inst.getId().toString() + "> name:<" + inst.getName() + " status:<" + inst.getStatus() + ">.");
            } else {
                log.debug("key:<" + instId + "> instance: NULL");
            }
        }
        log.debug("End dumpInstMap: " + caller);
    }

    /**
     * @Title: isBulkRequest
     * @Description: Check whether the request is a bulk request
     * @param allocationType
     * @return
     */
    public static boolean isBulkRequest (String allocationType) {
        if (HostAllocationType.ZonalBulk.toString().equals(allocationType)
                || HostAllocationType.RegionalBulk.toString().equals(allocationType)) {
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * @Title: getHttpRequestConnectTimeout
     * @Description: get the google httpRequest connect timeout in seconds
     * @param null
     * @return httpRequest connect timeout
     */
    public static int getHttpRequestConnectTimeout() {
		int connectTimeout = GcloudConst.DEFAULT_HTTP_CONNECT_TIMEOUT;

    	Integer configConnectTimeout = GcloudUtil.getConfig().getHttpConnectTimeout();
		if (configConnectTimeout != null && configConnectTimeout.intValue() > 0) {
			connectTimeout = configConnectTimeout.intValue();
		} else if (configConnectTimeout != null) {
			log.warn("HTTP_CONNECT_TIMEOUT must be greater than 0 . Set default to " + connectTimeout);
		}
		
		return connectTimeout;
    }
    
    /**
     * @Title: getHttpRequestReadTimeout
     * @Description: get the google httpRequest read timeout in seconds
     * @param null
     * @return httpRequest read timeout
     */
    public static int getHttpRequestReadTimeout() {
		int readTimeout = GcloudConst.DEFAULT_HTTP_READ_TIMEOUT;
		
		Integer configReadTimeout = GcloudUtil.getConfig().getHttpReadTimeout();
		if (configReadTimeout != null && configReadTimeout.intValue() > 0) {
			readTimeout = configReadTimeout.intValue();
		} else if (configReadTimeout != null) {
			log.warn("HTTP_READ_TIMEOUT must be greater than 0. Set default to " + readTimeout);
		}

		return readTimeout;
    }

}
