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
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.amazonaws.services.ec2.model.AllocationStrategy;
import com.amazonaws.services.ec2.model.CreateFleetRequest;
import com.amazonaws.services.ec2.model.FleetLaunchTemplateConfigRequest;
import com.amazonaws.services.ec2.model.FleetLaunchTemplateOverridesRequest;
import com.amazonaws.services.ec2.model.FleetType;
import com.amazonaws.services.ec2.model.GroupIdentifier;
import com.amazonaws.services.ec2.model.IamInstanceProfileSpecification;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceLifecycleType;
import com.amazonaws.services.ec2.model.SpotFleetLaunchSpecification;
import com.amazonaws.services.ec2.model.SpotPlacement;
import com.amazonaws.services.ec2.model.SpotFleetTagSpecification;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TargetCapacitySpecificationRequest;
import com.amazonaws.services.ec2.model.ResourceType;
import com.amazonaws.services.ec2.model.InstanceNetworkInterfaceSpecification;
import com.amazonaws.services.ec2.model.InstanceNetworkInterface;
import com.amazonaws.services.ec2.model.LaunchTemplateIamInstanceProfileSpecificationRequest;
import com.amazonaws.services.ec2.model.LaunchTemplateTagSpecificationRequest;
import com.amazonaws.util.CollectionUtils;
import com.amazonaws.util.StringUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.spectrum.constant.AwsConst;
import com.ibm.spectrum.model.AwsConfig;
import com.ibm.spectrum.model.AwsEntity;
import com.ibm.spectrum.model.AwsMachine;
import com.ibm.spectrum.model.AwsRequest;
import com.ibm.spectrum.model.AwsTemplate;
import com.ibm.spectrum.model.HostAllocationType;

/**
* @ClassName: AwsUtil
* @Description: The common utilities
* @author xawangyd
* @date Jan 26, 2016 3:36:12 PM
* @version 1.0
*/
public class AwsUtil {
    private static Logger log = LogManager.getLogger(AwsUtil.class);

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
     * EC2 configuration
     */
    private static AwsConfig config;

    /**
     * provider name
     */
    private static String providerName;

    /**
     *  Aws db file
     */
    private static String provStatusFile;


    /**
     * @return homeDir
     */
    public static String getHomeDir() {
        return homeDir;
    }


    /**
     * @param homeDir the homeDir to set
     */
    public static void setHomeDir(String dir) {
        if (null == AwsUtil.homeDir) {
            AwsUtil.homeDir = dir;
        }
    }

    /**
     * @return confDir
     */
    public static String getConfDir() {
        return confDir;
    }


    /**
     * @param confDir the confDir to set
     */
    public static void setConfDir(String dir) {
        if (null == AwsUtil.confDir) {
            AwsUtil.confDir = dir;
        }
    }

    /**
     * @return logDir
     */
    public static String getLogDir() {
        return logDir;
    }


    /**
     * @param logDir the logDir to set
     */
    public static void setLogDir(String dir) {
        if (null == AwsUtil.logDir) {
            AwsUtil.logDir = dir;
        }
    }

    /**
     * @return workDir
     */
    public static String getWorkDir() {
        return workDir;
    }


    /**
     * @param workDir the workDir to set
     */
    public static void setWorkDir(String dir) {
        if (null == AwsUtil.workDir) {
            AwsUtil.workDir = dir;
        }
    }

    /**
     * @return config
     */
    public static AwsConfig getConfig() {
        return config;
    }

    /**
     * @param config
     *            the config to set
     */
    public static void setConfig(AwsConfig config) {
        if (null == AwsUtil.config) {
            AwsUtil.config = config;
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
        if (null == AwsUtil.providerName) {
            AwsUtil.providerName = providerName;
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
        if (null == AwsUtil.provStatusFile) {
            AwsUtil.provStatusFile = provStatusFile;
        }
    }


    /**
     * @Title: writeToFile
     * @Description: write data to file
     * @param @param fileName
     * @param @param data
     * @return void
     * @throws
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
        } catch(IOException e) {
            log.error("Write data to file error.", e);
        } finally {
            try {
                if (null != bw) {
                    bw.close();
                    bw = null;
                }
            } catch(IOException ex) {
                log.error("Close IO error.", ex);
            }
        }
    }

    /**
    *
    * @Title: toJsonTxt
    * @Description: Object to json text
    * @param @param obj
    * @param @return
    * @return String
    * @throws
     */
    public static synchronized  <T> String toJsonTxt(T obj) {
        String jsonTxt = "";
        ObjectMapper mapper = new ObjectMapper();

        try {
            jsonTxt = mapper.writeValueAsString(obj);
        } catch(JsonProcessingException e) {
            log.error("Change object to json text error.", e);
        }

        return jsonTxt;
    }

    /**
    *
    * @Title: toJsonFile
    * @Description: Object to json file
    * @param @param obj
    * @param @return
    * @return String
    * @throws
     */
    public static synchronized  <T> void toJsonFile(T obj, String jfname) {   
    	File jf = new File(jfname);
    	String fileNameBkp = jfname + ".bkp";
    	File fileBkp = new File(fileNameBkp);
        try {
            if (jf.exists()) {
                if (fileBkp.exists()) {
                    fileBkp.delete();
                }
                if (!jf.renameTo(fileBkp)) {
                    log.error("Backup json file <" + jfname + "> error.");
                } else {
                    log.debug("Backup json file <" + jfname + "> success.");
                }
            }
            ObjectMapper mapper = new ObjectMapper();
            mapper.writerWithDefaultPrettyPrinter().writeValue(jf, obj);
            
            if (fileBkp.exists()) {
                fileBkp.delete();
            }
        } catch(Exception e) {
            log.error("Write object to json file <" + jfname + "> error.", e);
            if (fileBkp.exists()) {
            	if (!fileBkp.renameTo(jf)) {
            		log.error("Rollback json file <" + jfname + "> error.");
            	} else {
            		log.debug("Rollback json file <" + jfname + "> success.");
            	}
            }
        }   
    }

    /**
    *
    *
    * @Title: toJsonFile
    * @Description: Object to json file
    * @param @param obj
    * @param @param jf
    * @return void
    * @throws
     */
    public static synchronized <T> void toJsonFile(T obj, File jf) {
        String fileNameBkp = jf.getAbsolutePath() + ".bkp";
        File fileBkp = new File(fileNameBkp);
        try {
            if (jf.exists()) {
                if (fileBkp.exists()) {
                    fileBkp.delete();
                }
                if (!jf.renameTo(fileBkp)) {
                    log.error("Backup json file <" + jf.getAbsolutePath() + "> error.");
                } else {
                    log.debug("Backup json file <" + jf.getAbsolutePath() + "> success.");
                }
            }
            ObjectMapper mapper = new ObjectMapper();
            mapper.writerWithDefaultPrettyPrinter().writeValue(jf, obj);
            
            if (fileBkp.exists()) {
                fileBkp.delete();
            }
        } catch(Exception e) {
            log.error("Write object to json file <" + jf.getAbsolutePath() + "> error.", e);
            if (fileBkp.exists()) {
                if (!fileBkp.renameTo(jf)) {
                    log.error("Rollback json file <" + jf.getAbsolutePath() + "> error.");
                } else {
                    log.debug("Rollback json file <" + jf.getAbsolutePath() + "> success.");
                }
            }
        }
    }

    /**
    *
    *
    * @Title: toObject
    * @Description: Json file to Object
    * @param @param jsonFile
    * @param @param type
    * @param @return
    * @return T
    * @throws
     */
    public static <T> T toObject(File jsonFile, Class<T> type) {
        ObjectMapper mapper = new ObjectMapper();

        try {
            return (T) mapper.readValue(jsonFile, type);
        } catch(IOException e) {
            log.error("Change json file to object error.", e);
            if (jsonFile.length() == 0) {
                log.error("The file <" + jsonFile.getAbsolutePath() + "> is empty which cannot be parsed to json object, remove it.");
                jsonFile.delete();
            }
        }
        
        return null;
    }
    

    public static <T> T toObjectCaseInsensitive(String content, Class<T> type) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);

        try {
            return (T) mapper.readValue(content, type);
        } catch(IOException e) {
            log.error("Change json file to object error.", e);
        }
        

        return null;
    }

    /**
    *
    *
    * @Title: toObject
    * @Description: Json text to Object
    * @param @param jsonTxt
    * @param @param valueType
    * @param @return
    * @return T
    * @throws
     */
    public static <T> T toObject(String jsonTxt, Class<T> type) {
        ObjectMapper mapper = new ObjectMapper();

        try {
            return (T)mapper.readValue(jsonTxt, type);
        } catch(IOException e) {
            log.error("Change json text to object error.", e);
        }

        return null;
    }

    /**
    *
    * @Title: writeObject
    * @Description: Write object to file
    * @param @param obj
    * @param @return
    * @return String
    * @throws
     */
    public static <T> String writeObject(T obj) {
        ObjectOutputStream out = null;
        try {
            String fname = UUID.randomUUID().toString();
            out = new ObjectOutputStream(new FileOutputStream(new File(fname)));
            out.writeObject(obj);
            return fname;
        } catch(Exception e) {
            log.error("Write object to file error.", e);
        } finally {
            try {
                if (null != out) {
                    out.close();
                    out = null;
                }
            } catch(IOException e) {
                log.error("Close IO error.", e);
            }
        }

        return "";
    }

    /**
    *
    * @Title: readObject
    * @Description: read object from file
    * @param @param file
    * @param @return
    * @return T
    * @throws
     */
    @SuppressWarnings("unchecked")
    public static <T> T readObject(String fname) {
        ObjectInputStream in = null;
        try {
            in = new ObjectInputStream(new FileInputStream(new File(fname)));
            T obj = (T) in.readObject();
            return obj;
        } catch(Exception e) {
            log.error("Read object from file error.", e);
        } finally {
            try {
                if (null != in) {
                    in.close();
                    in = null;
                }
            } catch(IOException e) {
                log.error("Close IO error.", e);
            }
        }

        return null;
    }

    /**
    *
    * @Title: updateToFile
    * @Description: update VM to local file
    * @param @param vmMap
    * @return void
    * @throws
     */
    public static void updateToFile(Map<String, AwsMachine> vmMap) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class AwsUtil in method updateToFile with parameters: vmMap: "
                      + vmMap );
        }
        File jf = new File(workDir + "/" + provStatusFile);
        if (!jf.exists() || null == vmMap || vmMap.isEmpty()) {
            return;
        }

        AwsEntity ae = AwsUtil.toObject(jf, AwsEntity.class);
        if (ae == null) {
            return;
        }

        if (CollectionUtils.isNullOrEmpty(ae.getReqs())) {
            return;
        }

        for (AwsRequest req : ae.getReqs()) {
            List<AwsMachine> mLst = req.getMachines();
            if (CollectionUtils.isNullOrEmpty(mLst)) {
                continue;
            }

            for (AwsMachine m : mLst) {
                if (vmMap.containsKey(m.getMachineId())) {
                    m.copyValues(vmMap.get(m.getMachineId()));
                }
            }
        }

        AwsUtil.toJsonFile(ae, jf);
    }

    /**
    *
    * @Title: saveToFile
    * @Description: save record to local file
    * @param @param req
    * @return void
    * @throws
     */
    public static void saveToFile(AwsRequest req) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class AwsUtil in method saveToFile with parameters: req: " + req);
        }
        File jf = new File(workDir + "/" + provStatusFile);

        File lsfWorkDir = new File(workDir);
        try {
            // Add new VM record
            if (!jf.exists()) {
                List<AwsRequest> reqLst = new ArrayList<AwsRequest>();
                reqLst.add(req);

                AwsEntity ae = new AwsEntity();
                ae.setReqs(reqLst);

                AwsUtil.toJsonFile(ae, jf);

                return;
            } else {
                log.info("aws-db.json file exists ");
            }

            // Append VM record
            AwsEntity ae = AwsUtil.toObject(jf, AwsEntity.class);
            if (ae == null) {
                return;
            }

            for (AwsRequest rq : ae.getReqs()) {
                // Update exist record
                if (rq.getReqId().equals(req.getReqId())) {
                    rq.update(req);
                    AwsUtil.toJsonFile(ae, jf);
                    return;
                }
            }

            ae.getReqs().add(req);
            AwsUtil.toJsonFile(ae, jf);
        } catch (Exception e) {
            log.error("Error: ", e);
        }
    }

    /**
     *
     * @Title: getFromFile
     * @Description: get record from local file
     * @param @param reqId
     * @param @return
     * @return AwsRequest
     * @throws
     */
    public static AwsRequest getFromFile(String reqId) {
        File f = new File(workDir + "/" + provStatusFile);
        if (!f.exists()) {
            return null;
        }

        AwsEntity ae = AwsUtil.toObject(f, AwsEntity.class);

        if (ae == null) {
            return null;
        }

        List<AwsRequest> reqLst = ae.getReqs();
        log.debug("Returning the requests: " + reqLst);
        if (CollectionUtils.isNullOrEmpty(reqLst)) {
            return null;
        }

        List<AwsMachine> retMLst = new ArrayList<AwsMachine>();
        for (AwsRequest req : reqLst) {
            // request request object by request ID case
            if (req.getReqId().equals(reqId)) {
                if (log.isTraceEnabled()) {
                    log.trace("End in class AwsUtil in method getFromFile with return: AwsRequest: "
                              + req);
                }
                return req;
            }

            List<AwsMachine> mLst = req.getMachines();
            // return request object by machine return ID case
            for (AwsMachine m : mLst) {
                if (reqId.equals(m.getRetId())) {
                    retMLst.add(m);
                }
            }
        }
        AwsRequest returnAwsRequest = null;
        if(!CollectionUtils.isNullOrEmpty(retMLst)) {
            returnAwsRequest = new AwsRequest();
            returnAwsRequest.setReqId(reqId);
            returnAwsRequest.setMachines(retMLst);
        }

        if (log.isTraceEnabled()) {
            log.trace("End in class AwsUtil in method getFromFile with return: AwsRequest: " + returnAwsRequest);
        }
        return returnAwsRequest;
    }

    /**
    *
    * @Title: getFromFile
    * @Description: get all request from file
    * @param @return
    * @return List<AwsRequest>
    * @throws
     */
    public static AwsEntity getFromFile() {
        File f = new File(workDir + "/" + provStatusFile);
        if (!f.exists()) {
            log.debug("The file <"+f.getAbsolutePath()+"> does not exist");
            return null;
        }

        AwsEntity ae = AwsUtil.toObject(f, AwsEntity.class);

        return ae;
    }

    /**
    *
    *
    * @Title: saveToFile
    * @Description: save AwsEntity object to json file
    * @param @param ae
    * @return void
    * @throws
     */
    public static void saveToFile(AwsEntity ae) {
        File jf = new File(workDir + "/" + provStatusFile);
        if (!jf.exists()) {
            return;
        }

        if (null == ae) {
            return;
        }

        AwsUtil.toJsonFile(ae, jf);
    }

    /**
    *
    *
    * @Title: getVMFromFile
    * @Description: get VM from local file
    * @param @param vmNames
    * @param @return
    * @return Map<String,AwsMachine>
    * @throws
     */
    public static Map<String, AwsMachine> getVMFromFile(List<String> vmNames) {
        Map<String, AwsMachine> vmMap = new HashMap<String, AwsMachine>();
        File jf = new File(workDir + "/" +  provStatusFile);
        if (!jf.exists()) {
            return vmMap;
        }

        AwsEntity ae = AwsUtil.toObject(jf, AwsEntity.class);
        if (ae == null) {
            return vmMap;
        }

        List<AwsRequest> reqLst = ae.getReqs();
        if (CollectionUtils.isNullOrEmpty(reqLst)) {
            return vmMap;
        }

        for (AwsRequest req : reqLst) {
            List<AwsMachine> mLst = req.getMachines();
            if (CollectionUtils.isNullOrEmpty(mLst)) {
                continue;
            }

            for (AwsMachine m : mLst) {
                if (vmNames.contains(m.getName())) {
                    if (!("Terminated".equalsIgnoreCase(m.getStatus())) &&
                            StringUtils.isNullOrEmpty(m.getRetId())) {
                        vmMap.put(m.getMachineId(), m);
                    }
                }
            }
        }

        return vmMap;
    }

    /**
    *
    * @Title: deleteFromFile
    * @Description: clear terminated VM records from local file
    * @param
    * @return void
    * @throws
     */
    public static int deleteFromFile() {
        int onlineNum = 0;
        File jf = new File(workDir + "/" + provStatusFile);
        if (!jf.exists()) {
            return onlineNum;
        }

        AwsEntity ae = AwsUtil.toObject(jf, AwsEntity.class);
        if (ae == null) {
            return onlineNum;
        }
        List<AwsRequest> reqLst = ae.getReqs();
        if (CollectionUtils.isNullOrEmpty(reqLst)) {
            return onlineNum;
        }

        boolean isUpdated = false;
        List<AwsMachine> delMLst = new ArrayList<AwsMachine>();
        List<AwsRequest> delRLst = new ArrayList<AwsRequest>();
        for (AwsRequest req : reqLst) {
            List<AwsMachine> mLst = req.getMachines();
            if (CollectionUtils.isNullOrEmpty(mLst)) {
                delRLst.add(req);
                isUpdated = true;
                continue;
            }

            delMLst.clear();
            for (AwsMachine m : mLst) {
                if ("Terminated".equalsIgnoreCase(m.getStatus()) &&
                        !StringUtils.isNullOrEmpty(m.getRetId())) {
                    delMLst.add(m);
                    continue;
                }

                onlineNum ++;
            }

            if (!CollectionUtils.isNullOrEmpty(delMLst)) {
                mLst.removeAll(delMLst);
                isUpdated = true;
            }

            if (CollectionUtils.isNullOrEmpty(mLst)) {
                delRLst.add(req);
                isUpdated = true;
            }
        }

        if (isUpdated) {
            reqLst.removeAll(delRLst);
            AwsUtil.toJsonFile(ae, jf);
        }

        return onlineNum;
    }

    /**
    *
    * @Title: getTemplateFromFile
    * @Description: get template from file
    * @param @param templateId
    * @param @return
    * @return AwsTemplate
    * @throws
     */
    public static AwsTemplate getTemplateFromFile(String templateId) {
        File jf = new File(confDir + "/conf/awsprov_templates.json");
        if (!jf.exists()) {
            log.error("Template file does not exist: " + jf.getPath());
            return null;
        }

        AwsEntity ae = AwsUtil.toObject(jf, AwsEntity.class);
        if (ae == null) {
            return null;
        }
        List<AwsTemplate> tLst = ae.getTemplates();
        for (AwsTemplate at : tLst) {
            if (at.getTemplateId().equals(templateId)) {
                if (log.isTraceEnabled()) {
                    log.trace("End in class AwsUtil in method getTemplateFromFile with return: AwsTemplate: " + at);
                }
                return at;
            }
        }

        return null;
    }



    public static String readFileToString(String filePath) {

        StringBuffer fileData = new StringBuffer(1000);
        char[] buf = new char[1024];

        int numRead=0;
        try {
            BufferedReader reader = new BufferedReader(
                new FileReader(filePath));
            while((numRead=reader.read(buf)) != -1) {
                String readData = String.valueOf(buf, 0, numRead);
                fileData.append(readData);
                buf = new char[1024];
            }

            reader.close();
        } catch(IOException e ) {
            log.error("Close IO error.", e);
        }
        return fileData.toString();
    }



    /**
     * Use EC2 instance profile if specified by the template (RTC 127032)
     *
     * @param instanceProfile
     * @return
     */
    public static IamInstanceProfileSpecification getIamInstanceProfile(
        String instanceProfile) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class AwsUtil in method getIamInstanceProfile with parameters: instanceProfile: "
                      + instanceProfile );
        }
        IamInstanceProfileSpecification iamProfile = null;
        if (!StringUtils.isNullOrEmpty(instanceProfile)) {
            iamProfile = new IamInstanceProfileSpecification();
            // check if it is a name or ARN
            if (instanceProfile.startsWith("arn:")) {
                iamProfile.setArn(instanceProfile);
            } else {
                iamProfile.setName(instanceProfile);
            }
        }
        if (log.isTraceEnabled()) {
            log.trace("End in class AwsUtil in method getIamInstanceProfile with return: IamInstanceProfileSpecification: " + iamProfile);
        }
        return iamProfile;
    }

    /**
     * Use EC2 template instance profile if specified by the template
     *
     * @param instanceProfile
     * @return
     */
    public static LaunchTemplateIamInstanceProfileSpecificationRequest getLaunchTemplateIamInstanceProfile(
        String instanceProfile) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class AwsUtil in method getLaunchTemplateIamInstanceProfile with parameters: instanceProfile: "
                      + instanceProfile );
        }
        LaunchTemplateIamInstanceProfileSpecificationRequest iamProfile = null;
        if (!StringUtils.isNullOrEmpty(instanceProfile)) {
            iamProfile = new LaunchTemplateIamInstanceProfileSpecificationRequest();
            if (instanceProfile.startsWith("arn:")) {
                iamProfile.setArn(instanceProfile);
            } else {
                iamProfile.setName(instanceProfile);
            }
        }
        if (log.isTraceEnabled()) {
            log.trace("End in class AwsUtil in method getLaunchTemplateIamInstanceProfile with return: LaunchTemplateIamInstanceProfileSpecificationRequest: " + iamProfile);
        }
        return iamProfile;
    }


    /**
     * @param awsTemplate
     */
    public static void applyDefaultValuesForSpotInstanceTemplate(AwsTemplate awsTemplate) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class AwsUtil in method applyDefaultValuesForSpotInstanceTemplate with parameters: awsTemplate: " + awsTemplate);
        }

        // Mapping the allocation strategy
        // SUP_BY_LSF issue#259 support CapacityOptimized
        // and change the default allocation strategy to CapacityOptimized if empty or invalid
        if (StringUtils.isNullOrEmpty(awsTemplate.getAllocationStrategy())) {
            awsTemplate.setAllocationStrategy(AllocationStrategy.CapacityOptimized.toString());
        } else if (awsTemplate.getAllocationStrategy().equalsIgnoreCase(
                       AllocationStrategy.CapacityOptimized.toString())) {
            awsTemplate.setAllocationStrategy(AllocationStrategy.CapacityOptimized.toString());
        } else if (awsTemplate.getAllocationStrategy().equalsIgnoreCase(
                       AllocationStrategy.LowestPrice.toString())) {
            awsTemplate.setAllocationStrategy(AllocationStrategy.LowestPrice.toString());
        } else if (awsTemplate.getAllocationStrategy().equalsIgnoreCase(
                       AllocationStrategy.Diversified.toString())) {
            awsTemplate.setAllocationStrategy(AllocationStrategy.Diversified.toString());
        } else {
            // In case AWS SDK support new allocation strategy, no need code change in future
            // What we need to do it update aws sdk jar library
            try {
                AllocationStrategy allocStategy = AllocationStrategy.fromValue(awsTemplate.getAllocationStrategy());
                if (allocStategy != null) {
                    awsTemplate.setAllocationStrategy(allocStategy.toString());
                } else {
                    awsTemplate.setAllocationStrategy(AllocationStrategy.CapacityOptimized.toString());
                }
            } catch (IllegalArgumentException illArgExp) {
                log.warn("Invalid allocation strategy specified: [" + awsTemplate.getAllocationStrategy() + "]. Using the default allocation strategy: " + AllocationStrategy.CapacityOptimized.toString());
                awsTemplate.setAllocationStrategy(AllocationStrategy.CapacityOptimized.toString());
            }
        }


        //Setting the validity of the request
        int requestValidityHours=0,requestValidityMinutes=0;
        boolean validRequestValidityPeriod = false;
        if(!StringUtils.isNullOrEmpty(awsTemplate.getRequestValidity())) {
            String[] validityPeriod = awsTemplate.getRequestValidity().split(":");
            if(validityPeriod.length == 2) {
                try {
                    requestValidityHours = Integer.parseInt(validityPeriod[0]);
                    requestValidityMinutes = Integer.parseInt(validityPeriod[1]);
                    validRequestValidityPeriod = true;
                } catch (NumberFormatException e) {
                    log.error("Invalid request validity period: " + awsTemplate.getRequestValidity(),e);
                }
            }
        }
        if(StringUtils.isNullOrEmpty(awsTemplate.getRequestValidity()) || !validRequestValidityPeriod) {
            requestValidityHours = AwsConst.REQUEST_VALIDITY_HOURS;
            requestValidityMinutes = AwsConst.REQUEST_VALIDITY_MINUTES;
        }
        log.debug("The hour validity: " + requestValidityHours);
        log.debug("The minutes validity: " + requestValidityMinutes);
        Calendar startTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        log.debug("Start time: " + startTime);
        Calendar endTime = (Calendar)startTime.clone();
        endTime.add(Calendar.HOUR, requestValidityHours);
        endTime.add(Calendar.MINUTE, requestValidityMinutes);
        log.debug("End time: " + endTime);
        awsTemplate.setRequestValidityStartTime(startTime.getTime());
        awsTemplate.setRequestValidityEndTime(endTime.getTime());
        if (log.isTraceEnabled()) {
            log.trace("End in class AwsUtil in method applyDefaultValuesForSpotInstanceTemplate with return: void: " + awsTemplate);
        }

    }





    /**
     * Validate all required parameters are provided
     *
     * @param awsTemplate
     * @return
     */
    public static boolean validateSpotInstanceRequest(AwsTemplate awsTemplate) {

        if(awsTemplate.getSpotPrice() == null || awsTemplate.getSpotPrice() == 0f) {
            log.error("Missing required parameter spot price");
            return false;
        }

        if(awsTemplate.getVmNumber() == null || awsTemplate.getVmNumber() <=0) {
            log.error("Missing required parameter machine number");
            return false;
        }

        if (StringUtils.isNullOrEmpty(awsTemplate.getLaunchTemplateId())) {
            if (StringUtils.isNullOrEmpty(awsTemplate.getVmType())) {
                log.error("Missing required parameter machine type");
                return false;
            }
        }

        if(StringUtils.isNullOrEmpty(awsTemplate.getFleetRole())) {
            log.error("Missing required parameter fleet role");
            return false;
        }

        return true;
    }
    
    /**
     * 
     * @Title: replaceTargetCapacitySpecification
     * @Description: replace fixed pattern in targetCapacitySpecification accordingly
     * @param awsTemplate
     * @return
     */
    public static String replaceTargetCapacitySpecification(AwsTemplate awsTemplate) {
    	String configPath = awsTemplate.getEc2FleetConfig();
    	String newContent = "";
    	if (!StringUtils.isNullOrEmpty(configPath)) {
    		String content = readFileToString(configPath);
    		Integer onDemandTargetCapacity = 0;
    		Integer spotDemandTargetCapacity = 0;
    		if (awsTemplate.getOnDemandTargetCapacityRatio() != null) {
    			onDemandTargetCapacity = (int) Math.ceil(awsTemplate.getVmNumber() * awsTemplate.getOnDemandTargetCapacityRatio());
    			spotDemandTargetCapacity = awsTemplate.getVmNumber() - onDemandTargetCapacity;
    		}
    		
    		newContent = content.replaceAll("\\$LSF_TOTAL_TARGET_CAPACITY", awsTemplate.getVmNumber().toString())
    				            .replaceAll("\\$LSF_ONDEMAND_TARGET_CAPACITY", onDemandTargetCapacity.toString())
    				            .replaceAll("\\$LSF_SPOT_TARGET_CAPACITY", spotDemandTargetCapacity.toString());
    	}
    	return newContent;
    }
    
    /**
     * Validate all required parameters are provided
     *
     * @param awsTemplate
     * @return
     */
    public static boolean validateEC2FleetRequest(AwsTemplate awsTemplate, AwsEntity rsp) {
    	String configFilePath = null;
    	if (!StringUtils.isNullOrEmpty(awsTemplate.getEc2FleetConfig())) {
    		if (awsTemplate.getEc2FleetConfig().startsWith("/")) {
    			configFilePath = awsTemplate.getEc2FleetConfig();	
    		} else {
    			configFilePath = AwsUtil.getConfDir() + File.separator + "conf"
    	        		+ File.separator + awsTemplate.getEc2FleetConfig();
    		}

    		File fleetConfigFile = new File(configFilePath);
    		if(!fleetConfigFile.exists()) {
    			rsp.setMsg("The specified fleet configuration file <" + configFilePath + "> does not exist");
    			return false;
    		}
    		
    		if (awsTemplate.getOnDemandTargetCapacityRatio() != null
    				&& (awsTemplate.getOnDemandTargetCapacityRatio() < 0.0
    						|| awsTemplate.getOnDemandTargetCapacityRatio() > 1.0)) {
    			rsp.setMsg("The specified OnDemandTargetCapacityRatio is invalid, please specify a value between 0.0 and 1.0");
    			return false;
    		}
    		
    		awsTemplate.setEc2FleetConfig(configFilePath);
    		
    		String fileContent = replaceTargetCapacitySpecification(awsTemplate);
    		CreateFleetRequest request = AwsUtil.toObjectCaseInsensitive(fileContent, CreateFleetRequest.class);
    		if (request == null) {
    			rsp.setMsg("Error parsing fleet configuration file <" + configFilePath + ">");
    			return false;
    		}
    		
    		//Maintain type not supported
    		if (StringUtils.isNullOrEmpty(request.getType()) 
    				|| FleetType.Maintain.toString().equalsIgnoreCase(request.getType())) {
    			rsp.setMsg("Type not defined or defined to unsupported type 'Maintain', only 'Instant' or 'Request' type is supported");
    			return false;
    		}
    		
    		//TargetCapacityUnitType not supported
    		TargetCapacitySpecificationRequest targetCapacitySpecification = request.getTargetCapacitySpecification();
    		if (targetCapacitySpecification != null
    				&& targetCapacitySpecification.getTargetCapacityUnitType() != null) {
    			rsp.setMsg("TargetCapacityUnitType is not supported");
    			return false;
    		}
    		
    		//InstanceRequirements not supported
    		List<FleetLaunchTemplateConfigRequest> fleetLaunchTemplateConfigList = request.getLaunchTemplateConfigs();
    		if (!CollectionUtils.isNullOrEmpty(fleetLaunchTemplateConfigList)) {
    			for (FleetLaunchTemplateConfigRequest config : fleetLaunchTemplateConfigList) {
    				List<FleetLaunchTemplateOverridesRequest> fleetLaunchTemplateOverridesList  = config.getOverrides();
    				if (!CollectionUtils.isNullOrEmpty(fleetLaunchTemplateOverridesList)){
    					for (FleetLaunchTemplateOverridesRequest override : fleetLaunchTemplateOverridesList) {
    						if (override.getInstanceRequirements() != null) {
    							rsp.setMsg("InstanceRequirements is not supported");
    							return false;
    						}
    					}
    				}
    			}
    		}
    		
       		awsTemplate.setFleetType(request.getType());
    	}

        return true;
    }
    
    


    /**
     * Maps an AWSTemplate to a Spot Fleet Launch Specification
     * @param t
     * @return
     */
    public static List<SpotFleetLaunchSpecification> mapTemplateToLaunchSpecificationList(AwsTemplate t, String tagValue, String keyName) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class AwsUtil in method mapTemplateToLaunchSpecificationList with parameters: t: " + t + ", tagValue: "
                      +  tagValue );
        }
        List<SpotFleetLaunchSpecification> spotFleetLaunchSpecificationList = new ArrayList<SpotFleetLaunchSpecification>();
        String encodedUserData = getEncodedUserData(t, tagValue);
        List<GroupIdentifier> groupIdentifier = mapSecurityGrpIdsToGroupIdentifierList(t.getSgIds());

        String instanceTypes = t.getVmType();
        String[]instanceTypesArray = instanceTypes.split(",");

        for(String instanceType : instanceTypesArray) {

            //If the current instanceType is not a valid string, ignore this instance type
            if(StringUtils.isNullOrEmpty(instanceType.trim())) {
                continue;
            }
            log.debug("Creating a launch specification for the instance type: " + instanceType.trim());
            SpotFleetLaunchSpecification spotFleetLaunchSpecification = new SpotFleetLaunchSpecification();
            spotFleetLaunchSpecification.withInstanceType(instanceType.trim())
            .withSubnetId(t.getSubnetId())
            .withSecurityGroups(groupIdentifier)
            .withUserData(encodedUserData).withImageId(t.getImageId());

            if(!StringUtils.isNullOrEmpty(t.getInstanceProfile())) {
                spotFleetLaunchSpecification.setIamInstanceProfile(getIamInstanceProfile(t.getInstanceProfile()));
            }

            if(!StringUtils.isNullOrEmpty(keyName)) {
                spotFleetLaunchSpecification.setKeyName(keyName);
            }

            //TODO This is not needed if the subnetId is provided(TBD)
            if (!StringUtils.isNullOrEmpty(t.getPGrpName())) {
                SpotPlacement placementGrp = new SpotPlacement();
                placementGrp.setGroupName(t.getPGrpName());
                if (!StringUtils.isNullOrEmpty(t.getTenancy())) {
                    placementGrp.setTenancy(t.getTenancy());
                }
                spotFleetLaunchSpecification.setPlacement(placementGrp);
            } else if (!StringUtils.isNullOrEmpty(t.getTenancy())) {
                SpotPlacement placementGrp = new SpotPlacement();
                placementGrp.setTenancy(t.getTenancy());
                spotFleetLaunchSpecification.setPlacement(placementGrp);
            }

            boolean ebsOptimized = t.getEbsOptimized();
            if (ebsOptimized == true) {
                spotFleetLaunchSpecification.setEbsOptimized(ebsOptimized);
            }

            // add user specified tags
            String userTagString = "RC_ACCOUNT=" + tagValue + ";" + t.getInstanceTags();
            if (!StringUtils.isNullOrEmpty(userTagString)) {
                List<SpotFleetTagSpecification> tagSpecifications = createSpotFleetTagSpecification(userTagString);
                if (!tagSpecifications.isEmpty()) {
                    spotFleetLaunchSpecification.setTagSpecifications(tagSpecifications);
                }
            }

            spotFleetLaunchSpecificationList.add(spotFleetLaunchSpecification);

        }
        if (log.isTraceEnabled()) {
            log.trace("End in class AwsUtil in method mapTemplateToLaunchSpecificationList with return: List<SpotFleetLaunchSpecification>: " + spotFleetLaunchSpecificationList);
        }
        return spotFleetLaunchSpecificationList;
    }


    /**
     * Maps the list of Security Group Ids to a list of GroupIdentifier object
     *
     * @param sgIds
     * @return
     */
    private static List<GroupIdentifier> mapSecurityGrpIdsToGroupIdentifierList(
        List<String> sgIds) {
        List<GroupIdentifier> groupIdentifierList = null;
        if (sgIds != null && !sgIds.isEmpty()) {
            GroupIdentifier tempGroupIdentifier = null;
            groupIdentifierList = new ArrayList<GroupIdentifier>();
            for (String sgId : sgIds) {
                tempGroupIdentifier = new GroupIdentifier();
                tempGroupIdentifier.setGroupId(sgId);
                groupIdentifierList.add(tempGroupIdentifier);
            }
        }
        return groupIdentifierList;
    }


    private static List<SpotFleetTagSpecification> createSpotFleetTagSpecification(String userTagString) {

        List<SpotFleetTagSpecification> tagSpecifications = new ArrayList<SpotFleetTagSpecification>();

        if (!StringUtils.isNullOrEmpty(userTagString)) {
            List<Tag> tagsToInstance = new ArrayList<Tag>();
            Tag tag;
            String[] tagStr = userTagString.split(";");
            for (String inst : tagStr) {
                String[] instSubStr = inst.split("=", 2);
                if (instSubStr.length == 2
                        && !StringUtils.isNullOrEmpty(instSubStr[0])
                        && !StringUtils.isNullOrEmpty(instSubStr[1])) {
                    if (instSubStr[0].toLowerCase().
                            startsWith(AwsConst.RESERVED_TAG_PREFIX.toLowerCase())) {
                        log.error("User tags cannot start with [" +
                                  AwsConst.RESERVED_TAG_PREFIX
                                  + "]. This prefix is reserved for internal AWS tags. Ignoring the tag: "
                                  + inst);
                    } else {
                        tag = new Tag(instSubStr[0], instSubStr[1]);
                        tagsToInstance.add(tag);
                    }
                }
            }

            SpotFleetTagSpecification tagSpec = new SpotFleetTagSpecification();
            tagSpec.setResourceType(ResourceType.Instance);
            tagSpec.setTags(tagsToInstance);
            tagSpecifications.add(tagSpec);

        }
        return tagSpecifications;

    }

    public static List<LaunchTemplateTagSpecificationRequest> createLaunchTemplateTagSpecification(String userTagString) {

        List<LaunchTemplateTagSpecificationRequest> tagSpecifications = new ArrayList<LaunchTemplateTagSpecificationRequest>();

        if (!StringUtils.isNullOrEmpty(userTagString)) {
            List<Tag> tagsToInstance = new ArrayList<Tag>();
            Tag tag;
            String[] tagStr = userTagString.split(";");
            for (String inst : tagStr) {
                String[] instSubStr = inst.split("=", 2);
                if (instSubStr.length == 2
                        && !StringUtils.isNullOrEmpty(instSubStr[0])
                        && !StringUtils.isNullOrEmpty(instSubStr[1])) {
                    if (instSubStr[0].toLowerCase().
                            startsWith(AwsConst.RESERVED_TAG_PREFIX.toLowerCase())) {
                        log.error("User tags cannot start with [" +
                                  AwsConst.RESERVED_TAG_PREFIX
                                  + "]. This prefix is reserved for internal AWS tags. Ignoring the tag: "
                                  + inst);
                    } else {
                        tag = new Tag(instSubStr[0], instSubStr[1]);
                        tagsToInstance.add(tag);
                    }
                }
            }

            LaunchTemplateTagSpecificationRequest tagSpec = new LaunchTemplateTagSpecificationRequest();
            tagSpec.withResourceType(ResourceType.Instance);
            tagSpec.setTags(tagsToInstance);
            tagSpecifications.add(tagSpec);

        }
        return tagSpecifications;
    }

    /**
        * Retrieves the encoded user data
    	* @return
    	*/
    public static String getEncodedUserData(AwsTemplate t, String tagValue) {
        String exportCmd="";
        String encodedUserData = "";

        try {
            File userDataFile = new File(homeDir + AwsConst.AWS_USER_DATA_FILE);
            if(userDataFile.exists()) {
                String usrDatafileToStr = readFileToString(userDataFile.getAbsolutePath());
                /*TODO Export only zone and templateName values from the userDataObj
                 * Volume should be used for attaching EBS Volume to AMI instance
                 * packages should be used for software installation on the new VM
                 */
                if (!StringUtils.isNullOrEmpty( t.getUserData())) {
                    exportCmd = "export " +  t.getUserData().replaceAll(";", " ") + ";";
                }
                if (!StringUtils.isNullOrEmpty( tagValue)) {
                    exportCmd = exportCmd + "export rc_account=" +  tagValue + ";";
                }
                if (!StringUtils.isNullOrEmpty(t.getTemplateId().toLowerCase())) {
                    exportCmd = exportCmd + "export template_id=" + t.getTemplateId() + ";";
                }
                String clusterName = System.getProperty("clusterName");
                if (!StringUtils.isNullOrEmpty(clusterName)) {
                    exportCmd = exportCmd + "export clustername=" + clusterName + ";";
                }
                String providerName = System.getenv("PROVIDER_NAME");
                if (!StringUtils.isNullOrEmpty(providerName)) {
                    exportCmd = exportCmd + "export providerName=" + providerName + ";";
                }
                usrDatafileToStr = usrDatafileToStr.replaceAll("%EXPORT_USER_DATA%", exportCmd);
                encodedUserData = new String( Base64.encodeBase64( usrDatafileToStr.getBytes( "UTF-8" )), "UTF-8" );
            }
        } catch (UnsupportedEncodingException e) {
            log.error("Error occured during encoding the user data", e);
        }
        return encodedUserData;
    }

    /**
     * Maps an Instance retrieved from the AWS API to an AwsMachine
     *
     * @param templateId
     *            The ID of the template used to request this instance
     * @param reqId
     *            The request Id of this instance
     * @param instance
     *            The instance to be mapped
     * @return An AwsMachine object with the mapped values
     */
    public static AwsMachine mapAwsInstanceToAwsMachine(String templateId,
            String reqId, Instance instance, String instanceTagVal) {
        return mapAwsInstanceToAwsMachine(templateId, reqId, instance,
                                          instanceTagVal, new AwsMachine());
    }

    /**
     * Maps an Instance retrieved from the AWS API to the AwsMachine object
     * passed in the parameters
     *
     * @param templateId
     *            The ID of the template used to request this instance
     * @param reqId
     *            The request Id of this instance
     * @param instance
     *            The instance to be mapped
     * @param instanceTagVal
     *            The tags to be added to that instance
     * @param awsMachine
     *            The AwsMachine object that needs to have the values mapped to
     * @return The AwsMachine object with the new values
     */
    public static AwsMachine mapAwsInstanceToAwsMachine(String templateId,
            String reqId, Instance instance, String instanceTagVal, AwsMachine awsMachine) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class AwsUtil in method mapAwsInstanceToAwsMachine with parameters: templateId:"
                      + templateId + " reqId:" + reqId + " instance:" + instance +  " instanceTagVal:" + instanceTagVal + " awsMachine:" + awsMachine);
        }
        if (awsMachine == null) {
            awsMachine = new AwsMachine();
        }

        awsMachine.setMachineId(instance.getInstanceId());
        awsMachine.setStatus(instance.getState().getName());
        awsMachine.setResult(instance.getState().getName());
        awsMachine.setPrivateIpAddress(instance.getPrivateIpAddress());
        awsMachine.setPublicIpAddress(instance.getPublicIpAddress());
        // If this is a spot instance, set the machine's request ID with the
        // spot instance request ID not the spot fleet request ID
        
        if(InstanceLifecycleType.Spot.toString().equals(instance.getInstanceLifecycle())) {
        	awsMachine.setLifeCycleType(HostAllocationType.Spot);
        	awsMachine.setReqId(instance.getSpotInstanceRequestId());
        } else {
        	awsMachine.setReqId(reqId);
        	awsMachine.setLifeCycleType(HostAllocationType.OnDemand);
        }
        awsMachine.setTemplate(templateId);
        awsMachine.setName(instance.getPrivateDnsName());
        awsMachine.setRcAccount(instanceTagVal);
        // get the launch time of this machine
        Date mlaunchdate = instance.getLaunchTime();
        // convert Date from dd-MMM-yyyy format to UTC millisec
        Long mlaunchtime = mlaunchdate.getTime();
        // convert to seconds
        if (mlaunchtime > 0)
            mlaunchtime = mlaunchtime / 1000;
        else
            mlaunchtime = 0L;
        awsMachine.setLaunchtime(mlaunchtime);
        
        //Set ncores and nthreads of this machine
        Integer ncores = instance.getCpuOptions().getCoreCount();
        Integer nthreads = ncores * instance.getCpuOptions().getThreadsPerCore();
        awsMachine.setNcores(ncores);
        awsMachine.setNthreads(nthreads);
        
        log.debug("Instance type: " + instance.getInstanceType() + ", ncores: " + ncores + ", nthreads: " + nthreads + ", templateId: " + templateId);
        
        if (log.isTraceEnabled()) {
            log.trace("End in class AwsUtil in method mapAwsInstanceToAwsMachine with return awsMachine:" + awsMachine);
        }
        return awsMachine;
    }


    /**
     * Checks if the machine matches any of the machines in the list
     */
    public static AwsMachine getMatchingMachineInList(AwsMachine awsMachine,
            List<AwsMachine> awsMachineList) {
        AwsMachine matchingMachine = null;
//        if (log.isTraceEnabled()) {
//            log.trace("Start in class AwsUtil in method isMachineInList with parameters: awsMachine: "
//                      + awsMachine
//                      + ", awsMachineList: "
//                      + awsMachineList );
//        }

        if(!CollectionUtils.isNullOrEmpty(awsMachineList) && awsMachine != null) {
            for(AwsMachine tempAwsMachine : awsMachineList) {
//                log.trace("Comparing machine: [" + awsMachine + "] with machine: " + tempAwsMachine);
                if(tempAwsMachine.equals(awsMachine)) {
                    log.trace("Machine : [" + awsMachine + "] matched with machine: " + tempAwsMachine);
                    matchingMachine = tempAwsMachine;
                    break;
                }
            }
        }
//        if (log.isTraceEnabled()) {
//            log.trace("End in class AwsUtil in method isMachineInList with return: AwsMachine: " + matchingMachine);
//        }
        return matchingMachine;
    }


}
