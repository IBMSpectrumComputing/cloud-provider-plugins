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

package com.ibm.spectrum.oc.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.openapi.models.V1PodSpec;

import com.ibm.spectrum.oc.model.Config;
import com.ibm.spectrum.oc.model.Entity;
import com.ibm.spectrum.oc.model.Machine;
import com.ibm.spectrum.oc.model.Request;
import com.ibm.spectrum.oc.model.Template;
import com.ibm.spectrum.oc.client.Client;

public class Util {
    public final static String OPENSHIFT_API_VERSION = "v1";
    public final static String OPENSHIFT_API_KIND_POD = "Pod";
    public final static String OPENSHIFT_API_PROVIDER = "openshift";
    public final static String OPENSHIFT_NAME_SPACE_FILE_PATH = "/run/secrets/kubernetes.io/serviceaccount/namespace";
    public final static String OPENSHIFT_ACCESS_TOKEN_FILE_PATH = "/run/secrets/kubernetes.io/serviceaccount/token";
    public final static String OPENSHIFT_CERT_FILE_PATH = "/run/secrets/kubernetes.io/serviceaccount/ca.crt";
    public final static String OPENSHIFT_POD_CONTAINER_NAME = "lsf-rc";
    public final static String OPENSHIFT_POD_PREFIX = OPENSHIFT_POD_CONTAINER_NAME + "-";
    public final static String OPENSHIFT_MEM_LABEL = "memory";
    public final static String OPENSHIFT_CPU_LABEL = "cpu";
    public final static String OPENSHIFT_LSF_CLUSTER_LABEL = "lsfcluster";
    public final static String OPENSHIFT_LSFTYPE_LABEL = "lsftype";
    public final static String OPENSHIFT_LSF_ROLE = "role";
    public final static String OPENSHIFT_LSF_ROLE_DEFAULT_VALUE = "DynHost";
    public final static String OPENSHIFT_LSF_TYPE_DEFAULT_VALUE =  "ibm-spectrum-lsf";
    public final static String OPENSHIFT_LSF_CLUSTER_DEFAULT_VALUE =  "mylsf2";
    public final static String OPENSHIFT_LSF_SUBDOMAIN_DEFAULT_VALUE =  "lsf-domain";

    public final static String RC_ACCOUNT_LABEL = "rc_account";
    public final static String RC_POD_PREFIX_LABEL = "rc_pod_prefix";
    public final static String RC_POD_TMPLID_LABEL = "rc_pod_tmpl";
    public final static String RC_POD_RETID_LABEL = "rc_pod_retid";
    public final static String RC_REQID_LABEL = "rc_reqId";

    public final static String EBROKERD_PROV_CONFIG_JSON_FILE_NAME = "openshiftprov_config.json";
    public final static String EBROKERD_PROV_TEMPLATES_JSON_FILE_NAME = "openshiftprov_templates.json";

    public final static String EBROKERD_PROV_CONFIG_MEM = "mem";
    public final static String EBROKERD_PROV_CONFIG_CPU = "ncpus";
    public final static String EBROKERD_PROV_CONFIG_TYPE = "type";
    public final static String EBROKERD_PROV_CONFIG_POD_CMD = "pod_cmd";
    public final static String EBROKERD_PROV_CONFIG_POD_MEM = "pod_mem";
    public final static String EBROKERD_PROV_CONFIG_POD_CPU = "pod_cpu";

    public final static String EBROKERD_ENV_LSF_ENVDIR = "LSF_ENVDIR";
    public final static String EBROKERD_ENV_PROV_CONF_DIR = "PRO_CONF_DIR";
    public final static String EBROKERD_ENV_PROV_WORK_DIR = "PRO_DATA_DIR";
    public final static String EBROKERD_ENV_PROV_LSF_LOGDIR = "PRO_LSF_LOGDIR";
    public final static String EBROKERD_ENV_PROVIDIER_NAME = "PROVIDER_NAME";
    public final static String EBROKERD_PROPERTY_LSF_TOP_DIR = "LSF_TOP_DIR";
    public final static String EBROKERD_PROPERTY_LSF_HOSTS_FILE = "LSF_HOSTS_FILE";
    public final static String EBROKERD_PROPERTY_TEMP_HOSTS_FILE = "TEMP_HOSTS_FILE";
    public final static String EBROKERD_PROPERTY_ENABLE_EGO = "ENABLE_EGO";
    public final static String EBROKERD_PROPERTY_TEST_URL = "TEST_URL";
    public final static String EBROKERD_PROPERTY_WAIT_POD_IP = "WAIT_POD_IP";
    public final static String EBROKERD_PROPERTY_CLUSTER_NAME = "clusterName";

    public final static String EBROKERD_RC_BEGIN_HOSTS_LABEL = "# BEGIN_HOSTS_MANAGED_BY_RESOURCE_CONNECTOR";
    public final static String EBROKERD_RC_END_HOSTS_LABEL = "# END_HOSTS_MANAGED_BY_RESOURCE_CONNECTOR";

    public static final String OPENSHIFT_MACHINE_STATUS_PENDING = "Pending";
    public static final String OPENSHIFT_MACHINE_STATUS_RUNNING = "Running";
    public static final String OPENSHIFT_MACHINE_STATUS_SUCCEEDED = "Succeeded";
    public static final String OPENSHIFT_MACHINE_STATUS_FAILED = "Failed";
    public static final String OPENSHIFT_MACHINE_STATUS_UNKNOWN = "Unknown";

    public static final String EBROKERD_MACHINE_RESULT_EXECUTING = "executing";
    public static final String EBROKERD_MACHINE_RESULT_SUCCEED = "succeed";
    public static final String EBROKERD_MACHINE_RESULT_FAIL = "fail";

    public static final String EBROKERD_STATE_RUNNING = "running";
    public static final String EBROKERD_STATE_WARNING = "warning";
    public static final String EBROKERD_STATE_ERROR = "error";
    public static final String EBROKERD_STATE_COMPLETE = "complete";
    public static final String EBROKERD_STATE_COMPLETE_WITH_ERROR = "complete_with_error";

    public static final String REQUEST_PREFIX = "create-";
    public static final String RETURN_REQUEST_PREFIX = "return-";

    public static final int REQUEST_VALIDITY_MINUTES = 30;
    public static final int INSTANCE_CREATION_TIMEOUT_MINUTES = 10;
    public static final int MAX_TIMEOUT_SECONDS = 3600;
    public static final int LSF_MAX_TRY_ADD_HOST = 20;

    private static Logger log = LogManager.getLogger(Util.class);

    private static Client client = null;
    private static String homeDir = null;
    private static String lsfConfDir = null;

    private static String lsfTopDir = "/opt/ibm/lsfsuite/lsf";
    private static String lsf_hosts_file_path = "/opt/ibm/lsfsuite/lsf/conf/hosts";
    private static String temp_hosts_file_path = "/opt/ibm/lsfsuite/lsf/conf/hosts";   // somehow TEMP_HOSTS_FILE not specified, then use hosts file instead
    private static String enable_ego = "Y";
    /**
     * conf directory
     */
    private static String provConfDir = null;

    /**
     * log directory
     */
    private static String logDir = null;

    /**
     * work directory
     */
    private static String provWorkDir = null;

    private static Config config = null;

    private static List<Template> provTemplatesList = null;

    /**
     * provider name
     */
    private static String providerName = null;

    /**
     *   db file
     */
    private static String provStatusFile = null;

    public static Client getClient() {
        if (client == null) {
            client = new Client();
        }
        return client;
    }
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
        if (null == Util.homeDir) {
            Util.homeDir = dir;
        }
    }

    public static String getLsfConfDir() {
        return lsfConfDir;
    }
    public static void setLsfConfDir(String lsfConfDir) {
        Util.lsfConfDir = lsfConfDir;
    }
    /**
     * @return provConfDir
     */
    public static String getProvConfDir() {
        return provConfDir;
    }


    /**
     * @param provConfDir the provConfDir to set
     */
    public static int setProvConfDir(String dir)	{
        if (null == Util.provConfDir)	{
            Util.provConfDir = dir;
        }
        // valid openshiftprov_config.json
        String prov_conf_json = provConfDir + "/" + Util.EBROKERD_PROV_CONFIG_JSON_FILE_NAME;
        File prov_conf_jsonFile = new File(prov_conf_json);
        if (! prov_conf_jsonFile.exists()) {
            log.error(prov_conf_json + " does not exist.");
            return -1;
        }
        Config cfg = Util.toObject(prov_conf_jsonFile, Config.class);
        if (cfg == null) {
            log.error(prov_conf_json + " is not a valid JSON format file.");
            return -1;
        }
        // openshiftprov_temples.json
        String prov_tmpl_json = provConfDir + "/" + Util.EBROKERD_PROV_TEMPLATES_JSON_FILE_NAME;
        File prov_tmpl_jsonFile = new File(prov_tmpl_json);
        if (! prov_tmpl_jsonFile.exists()) {
            log.error(prov_tmpl_json + "does not exist.");
            return -1;
        }
        Entity entity = Util.toObject(prov_tmpl_jsonFile, Entity.class);
        if (entity == null) {
            log.error(prov_tmpl_jsonFile + " is not a valid JSON format file.");
            return -1;
        }
        provTemplatesList = entity.getTemplates();
        if (provTemplatesList == null
                || provTemplatesList.isEmpty()) {
            log.error("No templates are found in " + prov_tmpl_jsonFile);
            return -1;
        }
        log.debug("Configuration " + cfg.toString());

        setConfig(cfg);
        return 0;
    }

    public static List<Template> getProvTmplatesList() {
        return provTemplatesList;
    }
    public static void setProvTmplatesList(List<Template> provTmplatesList) {
        Util.provTemplatesList = provTmplatesList;
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
        if (null == Util.logDir) {
            Util.logDir = dir;
        }
    }

    /**
     * @return provWorkDir
     */
    public static String getProvWorkDir() {
        return provWorkDir;
    }


    /**
     * @param provWorkDir the provWorkDir to set
     */
    public static void setProvWorkDir(String dir) {
        if (null == Util.provWorkDir) {
            Util.provWorkDir = dir;
        }
    }

    /**
     * @return config
     */
    public static Config getConfig() {
        return config;
    }

    /**
     * @param config
     *            the config to set
     */
    public static void setConfig(Config config) {
        if (null == Util.config) {
            Util.config = config;
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
        if (null == Util.providerName) {
            Util.providerName = providerName;
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
        if (null == Util.provStatusFile) {
            Util.provStatusFile = provStatusFile;
        }
    }

    public static String getNamespace() {
        String nameSpace = null;
        if (config != null) {
            nameSpace = config.getNameSpace();
        }
        return nameSpace;
    }
    public static String getAccessToken() {
        String token = null;
        if (config != null) {
            token = config.getToken();
        }
        return token;
    }
    public static String getServiceAccount() {
        String serviceAccount = null;
        if (config != null) {
            serviceAccount = config.getServiceAccount();
        }
        return serviceAccount;
    }

    public static String getServerUrl() {
        String serverUrl = null;
        if (config != null) {
            serverUrl = config.getServerUrl();
        }
        return serverUrl;
    }
    public static boolean getWaitPodIp() {
        boolean waitPodIp = false;
        if (config != null) {
            String strWaitPodIp = config.getWaitPodIp();
            if (strWaitPodIp != null
                    && strWaitPodIp.equalsIgnoreCase("Y")) {
                waitPodIp = true;
            }
        }
        return waitPodIp;
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
            log.error("Change object to JSON text error.", e);
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
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.writeValue(new File(jfname), obj);
        } catch(Exception e) {
            log.error("Write object to JSON file error.", e);
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
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.writeValue(jf, obj);
        } catch(Exception e) {
            log.error("Write object to JSON file error.", e);
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
            log.error("Change JSON file to object error.", e);
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
            log.error("Change JSON text to object error.", e);
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
     * @Description: update Pod to local file
     * @param @param vmMap
     * @return void
     * @throws
     */
    public static void updateToFile(Map<String, Machine> vmMap) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class Util in method updateToFile with parameters: vmMap: "
                      + vmMap );
        }
        File jf = new File(provWorkDir + "/" + provStatusFile);
        if (!jf.exists() || null == vmMap || vmMap.isEmpty()) {
            return;
        }
        Entity ae = Util.toObject(jf, Entity.class);
        if (ae == null) {
            return;
        }
        List<Request> rList = ae.getReqs();
        if (rList == null || rList.isEmpty()) {
            return;
        }

        for (Request req : rList) {
            List<Machine> mLst = req.getMachines();
            if (mLst == null || mLst.isEmpty()) {
                continue;
            }

            for (Machine m : mLst) {
                if (vmMap.containsKey(m.getMachineId())) {
                    m.copyValues(vmMap.get(m.getMachineId()));
                }
            }
        }

        Util.toJsonFile(ae, jf);
    }

    /**
     *
     * @Title: saveToFile
     * @Description: save record to local file
     * @param @param req
     * @return void
     * @throws
     */
    public static void saveToFile(Request req) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class Util in method saveToFile with parameters: req: " + req);
        }
        File jf = new File(provWorkDir + "/" + provStatusFile);

        File lsfWorkDir = new File(provWorkDir);
        try {
            // Add new Pod record
            if (!jf.exists()) {
                List<Request> reqLst = new ArrayList<Request>();
                reqLst.add(req);

                Entity ae = new Entity();
                ae.setReqs(reqLst);

                Util.toJsonFile(ae, jf);

                return;
            } else {
                log.info("-db.json file exists ");
            }

            // Append a record
            Entity ae = Util.toObject(jf, Entity.class);
            for (Request rq : ae.getReqs()) {
                if (rq.getReqId().equals(req.getReqId())) {
                    rq.update(req);
                    Util.toJsonFile(ae, jf);
                    return;
                }
            }

            ae.getReqs().add(req);
            Util.toJsonFile(ae, jf);
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
     * @return Request
     * @throws
     */
    public static Request getFromFile(String reqId) {
        File f = new File(provWorkDir + "/" + provStatusFile);
        if (!f.exists()) {
            return null;
        }

        Entity ae = Util.toObject(f, Entity.class);
        List<Request> reqLst = ae.getReqs();
        log.debug("Returning the requests: " + reqLst);
        if (reqLst == null || reqLst.isEmpty()) {
            return null;
        }

        List<Machine> retMLst = new ArrayList<Machine>();
        for (Request req : reqLst) {
            // request request object by request ID case
            if (req.getReqId().equals(reqId)) {
                if (log.isTraceEnabled()) {
                    log.trace("End in class Util in method getFromFile with return: Request: "
                              + req);
                }
                return req;
            }

            List<Machine> mLst = req.getMachines();
            // return request object by machine return ID case
            for (Machine m : mLst) {
                if (reqId.equals(m.getRetId())) {
                    retMLst.add(m);
                }
            }
        }
        if(retMLst == null || retMLst.isEmpty()) {
            return null;
        }

        Request returnRequest =  new Request();
        returnRequest.setReqId(reqId);
        returnRequest.setMachines(retMLst);

        if (log.isTraceEnabled()) {
            log.trace("End in class Util in method getFromFile with return: Request: " + returnRequest);
        }
        return returnRequest;
    }

    /**
     *
     * @Title: getFromFile
     * @Description: get all request from file
     * @param @return
     * @return List<Request>
     * @throws
     */
    public static Entity getFromFile() {
        File f = new File(provWorkDir + "/" + provStatusFile);
        if (!f.exists()) {
            return null;
        }

        Entity ae = Util.toObject(f, Entity.class);
        return ae;
    }

    /**
     *
     *
     * @Title: saveToFile
     * @Description: save Entity object to json file
     * @param @param ae
     * @return void
     * @throws
     */
    public static void saveToFile(Entity ae) {
        File jf = new File(provWorkDir + "/" + provStatusFile);
        if (!jf.exists()) {
            return;
        }

        if (null == ae) {
            return;
        }

        Util.toJsonFile(ae, jf);
    }

    /**
     *
     *
     * @Title: getPodFromFile
     * @Description: get Pod from local file
     * @param @param vmNames
     * @param @return
     * @return Map<String,Machine>
     * @throws
     */
    public static Map<String, Machine> getPodFromFile(List<String> podNames) {
        Map<String, Machine> mMap = new HashMap<String, Machine>();
        File jf = new File(provWorkDir + "/" +  provStatusFile);
        if (!jf.exists()) {
            return mMap;
        }

        Entity ae = Util.toObject(jf, Entity.class);
        List<Request> reqLst = ae.getReqs();
        if (reqLst == null || reqLst.isEmpty()) {
            return mMap;
        }

        for (Request req : reqLst) {
            List<Machine> mLst = req.getMachines();
            if (mLst == null || mLst.isEmpty()) {
                continue;
            }

            for (Machine m : mLst) {
                if (podNames.contains(m.getName())) {
                    String id = m.getRetId();
                    if (!("Terminated".equalsIgnoreCase(m.getStatus()))
                            &&  (id == null || id.isEmpty())) {
                        mMap.put(m.getMachineId(), m);
                    }
                }
            }
        }

        return mMap;
    }

    /**
     *
     * @Title: deleteFromFile
     * @Description: clear terminated Pod records from local file
     * @param
     * @return void
     * @throws
     */
    public static int deleteFromFile() {
        int onlineNum = 0;
        File jf = new File(provWorkDir + "/" + provStatusFile);
        if (!jf.exists()) {
            return onlineNum;
        }

        Entity ae = Util.toObject(jf, Entity.class);
        List<Request> reqLst = ae.getReqs();
        if (reqLst == null || reqLst.isEmpty()) {
            return onlineNum;
        }

        boolean isUpdated = false;
        List<Machine> delMLst = new ArrayList<Machine>();
        List<Request> delRLst = new ArrayList<Request>();
        for (Request req : reqLst) {
            List<Machine> mLst = req.getMachines();
            if (mLst == null || mLst.isEmpty()) {
                delRLst.add(req);
                isUpdated = true;
                continue;
            }

            delMLst.clear();
            for (Machine m : mLst) {
                String id = m.getRetId();
                if ("Terminated".equalsIgnoreCase(m.getStatus())
                        &&  ! (id == null || id.isEmpty())) {
                    delMLst.add(m);
                    continue;
                }

                onlineNum ++;
            }

            if (!(delMLst == null || delMLst.isEmpty())) {
                mLst.removeAll(delMLst);
                isUpdated = true;
            }

            if (mLst == null || mLst.isEmpty()) {
                delRLst.add(req);
                isUpdated = true;
            }
        }

        if (isUpdated) {
            reqLst.removeAll(delRLst);
            Util.toJsonFile(ae, jf);
        }

        return onlineNum;
    }

    /**
     *
     * @Title: getTemplateFromFile
     * @Description: get template from file
     * @param @param templateId
     * @param @return
     * @return Template
     * @throws
     */
    public static Template getTemplateFromFile(String templateId) {
        if (provTemplatesList == null) {
            return null;
        }
        for (Template tmpl : provTemplatesList) {
            if (tmpl.getTemplateId().equals(templateId)) {
                if (log.isTraceEnabled()) {
                    log.trace("End in class Util in method getTemplateFromFile with return: Template: " + tmpl);
                }
                return tmpl;
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
    /*  TLS certificate for a Kubernetes service
     *
     */
    public static InputStream getCaCert() {
        InputStream caCert = null;
        File caCertFile = null;
        String caCertPath = getConfig().getCaCert();
        if (caCertPath == null) {
            caCertPath = OPENSHIFT_CERT_FILE_PATH;
        }
        caCertFile = new File(caCertPath);
        try {
            caCert = new FileInputStream(caCertFile);
        } catch(Exception e) {
            log.error(e);
        }
        return caCert;
    }

    private static String getLines(String filePath) {
        BufferedReader br = null;
        FileReader fr = null;
        StringBuffer sb = new StringBuffer();
        try {
            fr = new FileReader (filePath);
            br = new BufferedReader(fr);
            while (true) {
                String line = br.readLine();
                if (line == null) {
                    break;
                }
                int end = line.indexOf('#');
                if (end < 0) {
                    sb.append(line);
                } else {
                    sb.append(line.substring(0, end));
                }
                sb.append("\n");
            }
        } catch (Exception e) {
            log.error(e);
        } finally {
            try {
                if (fr != null) {
                    fr.close();
                }
                if (br != null) {
                    br.close();
                }
            } catch (Exception e) {
                log.error(e);
            }
        }
        return sb.toString();
    }

    public static String getHostsFileContent() {
        String filepath = Util.getTemp_hosts_file_path();
        if (filepath == null) {
            filepath = Util.getLsf_hosts_file_path();
        }
        return getLines(filepath);
    }
    private static int moveFile(String srcPath, String destPath) {
        File srcFile = new File (srcPath);
        File destFile = new File(destPath);
        try {
            if (srcFile != null && srcFile.exists()) {
                if (destFile != null && destFile.exists()) {
                    destFile.delete();
                }
                Files.move(Paths.get(srcPath), Paths.get(destPath), StandardCopyOption.REPLACE_EXISTING );
            }
        } catch (Exception e) {
            log.error(e);
            return -1;
        }
        return 0;
    }
    public static int appendLineToHostsFile(String aNewline) {
        boolean isReadOk = false, isWriteOk = false;
        BufferedReader br = null;
        FileReader fr = null;
        List<String> lines = new ArrayList<String>();

        try {
            boolean isAdded = false;
            fr = new FileReader (Util.getLsf_hosts_file_path());
            br = new BufferedReader(fr);
            while (true) {
                String line = br.readLine();
                if (line == null) {
                    break;
                }
                if (line.indexOf(Util.EBROKERD_RC_END_HOSTS_LABEL) >= 0) {
                    lines.add(aNewline);
                    isAdded = true;
                }
                lines.add(line);
            }
            if (! isAdded) {
                lines.add(Util.EBROKERD_RC_BEGIN_HOSTS_LABEL);
                lines.add(aNewline);
                lines.add(Util.EBROKERD_RC_END_HOSTS_LABEL);
            }
            isReadOk = true;
        } catch (Exception e) {
            log.error(e);
        } finally {
            try {
                if (fr != null) {
                    fr.close();
                }
                if (br != null) {
                    br.close();
                }
            } catch (Exception e) {
                log.error(e);
            }
        }
        if (! isReadOk) {
            return -1;
        }
        // write back
        String tempFilePath= Util.getLsfConfDir() + "/.temp_host_file_by_rc";
        PrintWriter pw = null;
        BufferedWriter bw = null;
        FileWriter fw = null;
        try {
            fw = new FileWriter(tempFilePath, false);
            bw = new BufferedWriter(fw);
            pw = new PrintWriter(bw);
            for (String line: lines) {
                pw.println(line);
            }
            isWriteOk = true;
        } catch (Exception e) {
            log.error(e);
        } finally {
            try {
                if (bw != null) {
                    bw.close();
                }
                if(fw != null) {
                    fw.close();
                }
                if (pw != null) {
                    pw.close();
                }
            } catch(Exception e) {
                log.error(e);
            }
        }
        if (! isWriteOk) {
            return -1;
        }
        if (moveFile(tempFilePath, Util.getLsf_hosts_file_path()) < 0) {
            log.warn("failed to move " + tempFilePath + "to" + Util.getLsf_hosts_file_path());
            return -1;
        }
        return 0;
    }

    public static String getLsfTopDir() {
        return lsfTopDir;
    }
    public static void setLsfTopDir(String lsfTopDir) {
        Util.lsfTopDir = lsfTopDir;
    }
    public static String getLsf_hosts_file_path() {
        return lsf_hosts_file_path;
    }
    public static void setLsf_hosts_file_path(String lsf_hosts_file_path) {
        Util.lsf_hosts_file_path = lsf_hosts_file_path;
    }

    public static String getTemp_hosts_file_path() {
        return temp_hosts_file_path;
    }
    public static void setTemp_hosts_file_path(String temp_hosts_file_path) {
        Util.temp_hosts_file_path = temp_hosts_file_path;
    }

    public static String getEnable_ego() {
        return enable_ego;
    }
    public static void setEnable_ego(String enable_ego) {
        if (enable_ego == null
                || enable_ego.isEmpty()) {
            return;
        }
        if (enable_ego.indexOf("N") >= 0
                || enable_ego.indexOf("n") >= 0) {
            Util.enable_ego = "N";
        } else {
            Util.enable_ego = "Y";
        }
    }
    private static String genMachineResultByStatus( String machineStatus, boolean returnInProgress) {
        String machineResult = Util.EBROKERD_MACHINE_RESULT_EXECUTING;
        if (returnInProgress) {
            if (machineStatus.equalsIgnoreCase( Util.OPENSHIFT_MACHINE_STATUS_PENDING)
                    || machineStatus.equalsIgnoreCase( Util.OPENSHIFT_MACHINE_STATUS_RUNNING)
                    || machineStatus.equalsIgnoreCase( Util.OPENSHIFT_MACHINE_STATUS_UNKNOWN)) {
                machineResult = Util.EBROKERD_MACHINE_RESULT_EXECUTING;
            } else if (machineStatus.equalsIgnoreCase( Util.OPENSHIFT_MACHINE_STATUS_SUCCEEDED)) {
                machineResult = Util.EBROKERD_MACHINE_RESULT_SUCCEED;
            } else {
                machineResult = Util.EBROKERD_MACHINE_RESULT_FAIL;
            }
        } else {
            if (machineStatus.equalsIgnoreCase( Util.OPENSHIFT_MACHINE_STATUS_PENDING)
                    || machineStatus.equalsIgnoreCase( Util.OPENSHIFT_MACHINE_STATUS_UNKNOWN)) {
                machineResult = Util.EBROKERD_MACHINE_RESULT_EXECUTING;
            } else if (machineStatus.equalsIgnoreCase( Util.OPENSHIFT_MACHINE_STATUS_RUNNING)) {
                machineResult = Util.EBROKERD_MACHINE_RESULT_SUCCEED;
            } else {
                machineResult = Util.EBROKERD_MACHINE_RESULT_FAIL;
            }
        }
        return machineResult;
    }
    /**
     * Maps an V1Pod retrieved from the  API to the Machine object
     * passed in the parameters
     *
     * @param pod
     *            V1Pod object
     * @param pMachine
     *            The Machine object that needs to have the values mapped to
     * @return The Machine object with the new values
     */
    private static Machine mapV1PodToMachine(V1Pod pod, Machine pMachine) {
        if (pod == null) {
            return null;
        }
        Machine aMachine = null;
        boolean returnInProgress = false; // return machine is in progress
        if (pMachine == null) {
            aMachine = new Machine();
        } else {
            aMachine = pMachine;
        }
        // PodSpec is a description of a pod.
        V1PodSpec spec = pod.getSpec();
        // ObjectMeta is metadata that all persisted resources must have, which includes all objects users must create.
        V1ObjectMeta metaData = pod.getMetadata();
        // PodStatus represents information about the status of a pod.
        // Status may trail the actual state of a system, especially if the node that hosts the pod cannot contact the control plane.
        V1PodStatus statusData = pod.getStatus();

        String podName = null;
        String podIp = null; // IP address allocated to the pod. Routable at least within the cluster. Empty if not yet allocated.
        String hostIp = null; // IP address of the host to which the pod is assigned. Empty if not yet scheduled.
        // no conversion is required; not used in Ebrokerd - only used in MQ
        // refers to the phase - The phase of a Pod is a simple, high-level summary of where the Pod is in its lifecycle.
        //  There are five possible phase values: Pending, Running, Succeeded, Failed, Unknown
        String machineStatus = null;
        String reqId = null, retId = null;
        String rc_account = null, tmplId = null;
        long creationTime = 0;
        String msg = null;  // A human readable message indicating details about why the pod is in this condition.

        if (metaData != null) {
            podName = metaData.getName();
            Map <String, String> labels = metaData.getLabels();
            if (labels != null) {
                rc_account = labels.get(RC_ACCOUNT_LABEL);
                tmplId = labels.get(RC_POD_TMPLID_LABEL);
                reqId = labels.get(Util.RC_REQID_LABEL);
                retId = labels.get(Util.RC_POD_RETID_LABEL);
                if (retId != null) {
                    returnInProgress = true;
                }
            }
        }
        if (statusData != null) {
            podIp = statusData.getPodIP();
            hostIp = statusData.getHostIP();
            machineStatus = statusData.getPhase();
            msg = statusData.getMessage();
            if (statusData.getStartTime() != null) {
                creationTime = statusData.getStartTime().toEpochSecond();
            }
        }

        aMachine.setResult(genMachineResultByStatus(machineStatus, returnInProgress));
        aMachine.setMachineId(podName);
        aMachine.setStatus(machineStatus);
        aMachine.setPrivateIpAddress(podIp);
        aMachine.setPublicIpAddress(hostIp);
        aMachine.setName(podName);
        aMachine.setLaunchtime(creationTime);
        aMachine.setMsg(msg);
        aMachine.setReqId(reqId);
        aMachine.setRetId(retId);
        aMachine.setRcAccount(rc_account);
        aMachine.setTemplate(tmplId);

        if (log.isTraceEnabled()) {
            log.trace("End in class Util in method mapV1PodToMachine with return Machine:" + aMachine);
        }
        return aMachine;
    }

    public static List<Machine> createMachines( Entity req, Template tmpl, Entity rsp) {
        if (req == null
                || tmpl == null) {
            return null;
        }
        String rc_account = req.getTagValue();
        String templateId = tmpl.getTemplateId();
        StringBuffer newHostslines = new StringBuffer();
        List<V1Pod> pList =	getClient().createPods(req, tmpl, rc_account, rsp);
        List<Machine> mList = new ArrayList<Machine>();
        if (pList != null) {
            for (V1Pod pod: pList) {
                Machine aMachine = mapV1PodToMachine(pod, null) ;
                if (aMachine != null) {
                    mList.add(aMachine);
                    // appending IP address/pod name is done by LSF Operator (overwritten in any way)
                    //if (aMachine.getPrivateIpAddress() != null) {
                    //	newHostslines.append(aMachine.getPrivateIpAddress() + " " + aMachine.getName() + "\n");
                    //}
                }
            }
            if (log.isTraceEnabled()) {
                log.trace("newHostslines: " + newHostslines.toString());
            }

            if (newHostslines.length() > 0) {
                appendLineToHostsFile(newHostslines.toString());
            }
        }
        return mList;
    }

    public static int deleteMachines(Entity req, List<Machine> mList, Entity rsp) {
        int ret = 0;
        if (mList == null ) {
            return -1;
        }
        Entity p = null;
        if (rsp == null) {
            Entity tmpRsp = new Entity();
            String tmpRetId = Util.RETURN_REQUEST_PREFIX + UUID.randomUUID().toString();
            tmpRsp.setRetId(tmpRetId);
            p = tmpRsp;
        } else {
            p = rsp;
        }

        String machineStatus = null;
        for (Machine m : mList) {
            if (getClient().deletePod(m.getMachineId(), p) < 0) {
                machineStatus = OPENSHIFT_MACHINE_STATUS_FAILED;
                ret = -1;
            } else {
                machineStatus = OPENSHIFT_MACHINE_STATUS_SUCCEEDED;
            }
            m.setStatus(machineStatus);
            m.setResult(genMachineResultByStatus(machineStatus, true));
        }
        return ret;
    }

    /**
     * Checks if the machine matches any of the machines in the list
     */
    public static Machine getMatchingMachineInList(Machine Machine,
            List<Machine> MachineList) {
        Machine matchingMachine = null;
        if (log.isTraceEnabled()) {
            log.trace("Start in class Util in method isMachineInList with parameters: Machine: "
                      + Machine
                      + ", MachineList: "
                      + MachineList );
        }

        if(!(MachineList == null || MachineList.isEmpty())) {
            for(Machine tempMachine : MachineList) {
                log.trace("Comparing machine: [" + Machine + "] with machine: " + tempMachine);
                if(tempMachine.equals(Machine)) {
                    log.trace("Machine : [" + Machine + "] matched with machine: " + tempMachine);
                    matchingMachine = tempMachine;
                    break;
                }
            }
        }
        if (log.isTraceEnabled()) {
            log.trace("End in class Util in method isMachineInList with return: Machine: " + matchingMachine);
        }
        return matchingMachine;

    }
    public static Map<String, Machine> retrieveMachines(Map<String, V1Pod> pAllPodsMap, String reqId, List<Machine> mList ) {
        Map<String, Machine>mMap = null;
        List<String> pList = new ArrayList<String>();
        for (Machine m : mList) {
            pList.add(m.getMachineId());
        }

        Map<String, V1Pod> pMap = null;
        if (pAllPodsMap == null) {
            pAllPodsMap = getClient().listPods(pList);
        }
        pMap = pAllPodsMap;
        if (pMap != null) {
            mMap = new HashMap<String, Machine>();
            for (Machine m : mList) {
                V1Pod pod =  pMap.get(m.getMachineId());
                if (pod != null) {
                    mapV1PodToMachine(pod, m) ;
                    mMap.put(m.getMachineId(), m);
                }
            }
        }
        return mMap;
    }

    // list machines
    public static List<Machine> listMachines() {
        List<Machine>mList = null;
        List<V1Pod> pList = null;
        pList = getClient().listPods();
        if (pList == null) {
            return null;
        }
        mList = new ArrayList<Machine>();
        for (V1Pod pod : pList) {
            Machine m = mapV1PodToMachine(pod, null) ;
            mList.add(m);
        }
        return mList;
    }
    public static Map<String, Machine> retrieveAllMachines() {
        Map<String, Machine>mMap = new HashMap<String, Machine>();
        List<Machine>mList = listMachines();
        if (mList != null) {
            for (Machine m: mList) {
                mMap.put(m.getMachineId(), m);
            }
        }
        return mMap;
    }

}
