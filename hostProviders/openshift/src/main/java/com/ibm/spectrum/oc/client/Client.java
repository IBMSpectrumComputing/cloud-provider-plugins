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

package com.ibm.spectrum.oc.client;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import okhttp3.Response;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1Capabilities;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimStatus;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimSpec;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimList;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapKeySelector;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1KeyToPath;
import io.kubernetes.client.openapi.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretVolumeSource;
import io.kubernetes.client.custom.V1Patch;

import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.custom.Quantity;

import com.ibm.spectrum.oc.model.Template;
import com.ibm.spectrum.oc.model.Entity;
import com.ibm.spectrum.oc.util.Util;

public class Client {
    private static Logger log = LogManager.getLogger(Client.class);
    private  CoreV1Api api = null;
    private String namespace = null;
    private String serviceAccount = null;
    boolean isDebugging = false;
    boolean isWaitPodIp = false;
    Map<String, V1PersistentVolumeClaimStatus> pvcsMap = null;
    Map<String, Map<String, String>> configsMap = null;
    Map<String, Map<String, byte[]>> secretsMap = null;
    public Client() {
        try {

            namespace = Util.getNamespace();
            serviceAccount = Util.getServiceAccount();
            isWaitPodIp = Util.getWaitPodIp();
            String accessToken = Util.getAccessToken();
            InputStream sslCaCert = Util.getCaCert();
            boolean hasCaCert = true;
            if (sslCaCert == null) {
                hasCaCert = false;
            }

            // 1) default in cluster
            // ApiClient client = ClientBuilder.cluster().build();
            // Configuration.setDefaultApiClient(client);

            // 2) with an external token
            String basePath = null;
            basePath = Util.getServerUrl();
            if (basePath == null) {
                ApiClient tmpClient = ClientBuilder.cluster().build();
                basePath = tmpClient.getBasePath();
            }

            ApiClient client = Config.fromToken(basePath, accessToken, hasCaCert);
            if (hasCaCert) {
                client.setSslCaCert(sslCaCert);
            }
            if (Util.getConfig().getLogLevel().equalsIgnoreCase("DEBUG")
                    ||Util.getConfig().getLogLevel().equalsIgnoreCase("TRACE") ) {
                client.setDebugging(true);
                isDebugging = true;
            }
            if (Util.getConfig().getTimeoutInSec() != null
                    && Util.getConfig().getTimeoutInSec().intValue() >= 0) {
                int timeout = Util.getConfig().getTimeoutInSec().intValue();
                if (timeout > Util.MAX_TIMEOUT_SECONDS) {
                    timeout = Util.MAX_TIMEOUT_SECONDS;
                }
                timeout *= 1000;  // in milliseconds

                client.setConnectTimeout(timeout);
                client.setReadTimeout(timeout);
                client.setWriteTimeout(timeout);
            }
            if (log.isTraceEnabled()) {
                log.trace("connTimeout: " + client.getConnectTimeout() + " readTimeout: " + client.getReadTimeout() + " writetimeout: " + client.getWriteTimeout());
            }
            Configuration.setDefaultApiClient(client);

            api = new CoreV1Api();
        } catch (Exception e) {
            log.error(e);
        }
    }

    private void addPodLimit(Map<String, Quantity> limits, String key, List<String>attr) {
        if (limits == null || key == null || attr == null || attr.size() < 2) {
            return;
        }
        String val = attr.get(1);
        if (key.equals(Util.OPENSHIFT_MEM_LABEL)) {
            try {
                int iVal = Integer.parseInt(val);
                val = val + "Mi"; // MB
            } catch(Exception ei) {

            }
        }
        try {
            Quantity limit = Quantity.fromString(val);
            if (limit != null) {
                limits.put(key, limit);
            }
        } catch (Exception evalue) {
            log.warn(evalue);
        }
    }
    private String getCmdArgs(Map<String,List<String>> attributes, Template t, String rc_account ) {
        // pod cmd
        List<String> AttrPodCmd = attributes.get(Util.EBROKERD_PROV_CONFIG_POD_CMD);
        if (AttrPodCmd != null && AttrPodCmd.size() == 2) {
            String podCmd = 	AttrPodCmd.get(1);
            return podCmd;
        }
        // LSF dynamic host
        String binaryType =  "linux2.6-glibc2.3-x86_64";
        List<String> AttrType = attributes.get(Util.EBROKERD_PROV_CONFIG_TYPE);
        if (AttrType != null && AttrType.size() == 2) {
            String archType= 	AttrType.get(1);
            if (! archType.equalsIgnoreCase("X86_64")) {
                binaryType = "linux3.10-glibc2.17-ppc64le";
            }
        }
        String clusterName = System.getProperty(Util.EBROKERD_PROPERTY_CLUSTER_NAME);
        String templateId = t.getTemplateId();
        StringBuffer tmplAttrs = new StringBuffer();
        StringBuffer tmplResourceMap = new StringBuffer();
        List<String> baseAttrs = Arrays.asList(new String[] {"openshift", "rc_account", "clusterName", "templateID", "providerName"});
        List<String> builtInAttrs = Arrays.asList(new String[] {
                                        "cpu", "cpuf", "io", "logins", "ls", "idle", "maxmem", "maxswp", "maxtmp", "type", "model",
                                        "status", "it", "mem", "ncpus", "nprocs", "ncores", "nthreads",
                                        "define_ncpus_cores", "define_ncpus_procs", "define_ncpus_threads",
                                        "ndisks", "pg", "r15m", "r15s", "r1m", "swap", "swp", "tmp", "ut", "local",
                                        "dchost", "jobvm"
                                    });

        List<String> builtInTypes = Arrays.asList(new String[] {"Numeric", "String", "Boolean"});
        for (Map.Entry<String, List<String>> entry : attributes.entrySet()) {
            if (entry.getValue().size() != 2) {
                continue;
            }
            if (entry.getValue().get(0).isEmpty()
                    || entry.getValue().get(1).isEmpty()) {
                continue;
            }
            if (baseAttrs.contains( entry.getKey())) {
                continue;
            }
            if (builtInAttrs.contains( entry.getKey())) {
                continue;
            }
            if (! builtInTypes.contains(entry.getValue().get(0))) {
                continue;
            }
            tmplAttrs.append("if [ \"x`sed -n -e \'/Begin Resource/,/End Resource/ {/^[ \\t]*" + entry.getKey() + " [ \\t]\\+/p}\' ${lsf_shared_file}`\" = \"x\" ]; then ");
            tmplAttrs.append("sed -i -e \'/Begin Resource/,/End Resource/ { /End Resource/ { i\\");
            tmplAttrs.append(entry.getKey() +  "  " + entry.getValue().get(0) + "  ()       ()       (added by templateId " + templateId + ")\' -e \'}}\' ${lsf_shared_file};");
            tmplAttrs.append(" fi; ");

            if ( entry.getValue().get(0).equalsIgnoreCase("Boolean")) {
                tmplResourceMap.append(" [resource " + entry.getKey() + "]");
            } else {
                tmplResourceMap.append(" [resourcemap " +  entry.getValue().get(1) + "*" + entry.getKey() + "]");
            }
        }

        StringBuffer sb = new StringBuffer();
        sb.append("(");
        sb.append("LSF_TOP=\"");
        sb.append(Util.getLsfTopDir());
        sb.append( "\";");
        sb.append("LSF_CONFDIR=${LSF_TOP}/conf;");
        sb.append("rc_account=\"");
        sb.append(rc_account);
        sb.append( "\";");
        sb.append("clusterName=\"");
        sb.append(clusterName);
        sb.append( "\";");
        sb.append("templateId=\"");
        sb.append(templateId);
        sb.append( "\";");
        sb.append("providerName=\"");
        sb.append(Util.getProviderName());
        sb.append( "\";");
        sb.append("if [ ! -d ${LSF_TOP}/conf ]; then ");
        sb.append("mkdir -p ${LSF_TOP}/conf;");
        sb.append("cp  -r ${LSF_TOP}/.conf_tmpl/* ${LSF_TOP}/conf/;");
        sb.append("fi;");
        sb.append("if [ ! -f ${LSF_CONFDIR}/lsf.conf ]; then ");
        sb.append("echo \"$LSF_CONFDIR/lsf.conf not found\";");
        sb.append("exit 1;");
        sb.append("fi;");

        sb.append("echo \"");
        sb.append(Util.getHostsFileContent());
        sb.append("\" > " + "${LSF_CONFDIR}/hosts" + ";");
        sb.append("lsf_conf_file=${LSF_CONFDIR}/lsf.conf;");
        sb.append("lsf_shared_file=${LSF_CONFDIR}/lsf.shared;");
        sb.append("ego_conf_file=${LSF_CONFDIR}/ego/" + "myCluster" + "/kernel/ego.conf;");
        sb.append("MASTER_LIST=lsfmaster;");
        sb.append("if [ \"x`sed -n -e \'/Begin Resource/,/End Resource/ {/^[ \\t]*openshift[ \\t]\\+/p}\' ${lsf_shared_file}`\" = \"x\" ]; then ");
        sb.append("sed -i -e \'/Begin Resource/,/End Resource/ { /End Resource/ { i\\");
        sb.append("openshift Boolean    ()       ()       (instances from OpenShift)\' -e \'}}\' ${lsf_shared_file};");
        sb.append(" fi; ");
        sb.append("if [ \"x`sed -n -e \'/Begin Resource/,/End Resource/ {/^[ \\t]*rc_account[ \\t]\\+/p}\' ${lsf_shared_file}`\" = \"x\" ]; then ");
        sb.append("sed -i -e \'/Begin Resource/,/End Resource/ { /End Resource/ { i\\");
        sb.append("rc_account String     ()       ()       (account name for the external hosts)\' -e \'}}\' ${lsf_shared_file};");
        sb.append(" fi; ");
        sb.append("if [ \"x`sed -n -e \'/Begin Resource/,/End Resource/ {/^[ \\t]*clusterName[ \\t]\\+/p}\' ${lsf_shared_file}`\" = \"x\" ]; then ");
        sb.append("sed -i -e \'/Begin Resource/,/End Resource/ { /End Resource/ { i\\");
        sb.append("clusterName  String   ()       ()       (cluster name for the external hosts)\' -e \'}}\' ${lsf_shared_file};");
        sb.append(" fi; ");
        sb.append("if [ \"x`sed -n -e \'/Begin Resource/,/End Resource/ {/^[ \\t]*templateID[ \\t]\\+/p}\' ${lsf_shared_file}`\" = \"x\" ]; then ");
        sb.append("sed -i -e \'/Begin Resource/,/End Resource/ { /End Resource/ { i\\");
        sb.append("templateID   String   ()       ()       (template ID for the external hosts)\' -e \'}}\' ${lsf_shared_file};");
        sb.append(" fi; ");
        sb.append("if [ \"x`sed -n -e \'/Begin Resource/,/End Resource/ {/^[ \\t]*providerName[ \\t]\\+/p}\' ${lsf_shared_file}`\" = \"x\" ]; then ");
        sb.append("sed -i -e \'/Begin Resource/,/End Resource/ { /End Resource/ { i\\");
        sb.append("providerName String   ()       ()       (provider name for the external hosts)\' -e \'}}\' ${lsf_shared_file};");
        sb.append(" fi; ");
        sb.append(tmplAttrs.toString());
        sb.append("sed -i -e \'$ a LSF_GET_CONF=lim\' ${lsf_conf_file};");
        if (Util.getConfig().getMaxTryAddHost() != null
                && Util.getConfig().getMaxTryAddHost().intValue() >= 0) {
            int maxTryAddHost = Util.getConfig().getMaxTryAddHost().intValue();
            if (maxTryAddHost > Util.LSF_MAX_TRY_ADD_HOST) {
                sb.append("sed -i -e \'$ a LSF_MAX_TRY_ADD_HOST=" + maxTryAddHost + "' ${lsf_conf_file};");
            }
        }
        sb.append("sed -i -e \'$ a LSF_LOCAL_RESOURCES=\"[resource openshift]");
        sb.append(" [resourcemap \'\"${rc_account}\"\'*rc_account]");
        sb.append(" [resourcemap \'\"${clusterName}\"\'*clusterName]");
        sb.append(" [resourcemap \'\"${templateId}\"\'*templateID]");
        sb.append(" [resourcemap \'\"${providerName}\"\'*providerName]");
        sb.append(tmplResourceMap.toString());
        sb.append("\"\' ${lsf_conf_file};");
        sb.append("sed -i -e \'/^[ \\t]*LSF_MASTER_LIST=/ c LSF_MASTER_LIST=\"\'\"${MASTER_LIST}\"\'\"\' ${lsf_conf_file};");
        sb.append("sed -i -e \'/^[ \\t]*LSF_ENABLE_EGO=/ c LSF_ENABLE_EGO=" + Util.getEnable_ego()+ "\' ${lsf_conf_file};");
        sb.append("if [ \"x`sed -n -e \'/LSF_ENABLE_EGO=N/ p\' ${lsf_conf_file}`\" = \"x\" ]; then ");
        sb.append("sed -i -e \'$ a EGO_GET_CONF=lim\' ${ego_conf_file};");
        sb.append("sed -i -e \'$ a EGO_LOCAL_RESOURCES=\"[resource openshift]");
        sb.append(" [resourcemap \'\"${rc_account}\"\'*rc_account]");
        sb.append(" [resourcemap \'\"${clusterName}\"\'*clusterName]");
        sb.append(" [resourcemap \'\"${templateId}\"\'*templateID]");
        sb.append(" [resourcemap \'\"${providerName}\"\'*providerName]");
        sb.append(tmplResourceMap.toString());
        sb.append("\"\' ${ego_conf_file};");

        sb.append("sed -i -e \'/^[ \\t]*EGO_MASTER_LIST=/ c EGO_MASTER_LIST=\"\'\"${MASTER_LIST}\"\'\"\' ${ego_conf_file};");
        sb.append("fi;");
        sb.append(". ${LSF_CONFDIR}/profile.lsf;");
        sb.append("if [ -x /start_lsf.sh ]; then ");
        sb.append("/start_lsf.sh ");
        sb.append(Util.OPENSHIFT_LSF_ROLE_DEFAULT_VALUE + " ");
        sb.append("yes ");
        sb.append(Util.OPENSHIFT_LSF_TYPE_DEFAULT_VALUE + " ");
        sb.append("lsf ;");
        sb.append(" else ");
        sb.append("${LSF_SERVERDIR}" + "/lsf_daemons start ;");
        sb.append("while true; do sleep 10;done");
        sb.append("; fi;");
        sb.append(");");
        return sb.toString();
    }
    public V1Pod createPod(Template t, String rc_account, Entity rsp) {
        log.info("Start in class Client in method create a Pod with parameters: : " + t + ",  " + Util.RC_ACCOUNT_LABEL + ": "
                 + rc_account );
        V1Pod pod = null;
        try {
            String pretty = null;
            String dryRun = null;
            String fieldManager = null;
            String fieldValidation = null;

            V1ResourceRequirements res = new V1ResourceRequirements();
            Map<String, Quantity> limits = new HashMap<String, Quantity>();
            Map<String,List<String>> attributes = t.getAttributes();
            if (attributes != null) {
                // pod_mem, pod_cpu are precedent over mem, cpu respectively
                List<String> AttrPodCpu= attributes.get(Util.EBROKERD_PROV_CONFIG_POD_CPU);
                if (AttrPodCpu == null) {
                    AttrPodCpu= attributes.get(Util.EBROKERD_PROV_CONFIG_CPU);
                }
                if (AttrPodCpu == null) {
                    AttrPodCpu = Arrays.asList(new String[] {"Numeric", "1"});
                }
                addPodLimit(limits, Util.OPENSHIFT_CPU_LABEL, AttrPodCpu );

                List<String>AttrPodMem = attributes.get(Util.EBROKERD_PROV_CONFIG_POD_MEM);
                if (AttrPodMem == null) {
                    AttrPodMem = attributes.get(Util.EBROKERD_PROV_CONFIG_MEM);
                }
                if (AttrPodMem == null) {
                    AttrPodMem = Arrays.asList(new String[] {"Numeric", "512"});
                }
                addPodLimit(limits, Util.OPENSHIFT_MEM_LABEL, AttrPodMem );

                res.setLimits(limits);
                res.setRequests(limits);
            }

            V1Container container = new V1Container();
            V1SecurityContext securityContext = new V1SecurityContext();
            List<String> command = new ArrayList<String>();
            List<String> args = new ArrayList<String>();
            List<V1EnvVar> envs = null;
            Map<String, String> envVars = Util.getConfig().getEnvVars();
            if (log.isTraceEnabled()) {
                log.trace("ENV_VARS=" + envVars);
            }
            if (envVars != null
                    && ! envVars.isEmpty()) {
                envs = new ArrayList<V1EnvVar>();
                for (Map.Entry<String, String> entry : envVars.entrySet()) {
                    V1EnvVar var1 = new V1EnvVar();
                    var1.setName(entry.getKey());
                    var1.setValue(entry.getValue());
                    envs.add(var1);
                }
            }
            boolean isPrivileged = false;
            if (t.getPrivileged() != null) {
                isPrivileged = t.getPrivileged();
            }
            V1Capabilities capabilities = new V1Capabilities();
            List<String> capItemsAdd = Arrays.asList(new String[] {"KILL", "SETUID", "SETGID", "CHOWN", "SETPCAP", "NET_BIND_SERVICE", "DAC_OVERRIDE", "SYS_TTY_CONFIG", "SYS_RAWIO"});
            List<String> capItemsDrop = Arrays.asList(new String[] {"ALL"});
            capabilities.add(capItemsAdd);
            capabilities.drop(capItemsDrop);
            securityContext.setCapabilities(capabilities);
            securityContext.setPrivileged(isPrivileged);
            String cmdArgs = getCmdArgs(attributes, t, rc_account);
            if (!(cmdArgs == null
                    || cmdArgs.isEmpty())) {
                command.add("/bin/bash");
                args.add("-c");
                args.add(cmdArgs);
            }
            container.setName(Util.OPENSHIFT_POD_CONTAINER_NAME);
            container.setImage(t.getImageId());
            container.setSecurityContext(securityContext);
            container.setCommand(command);
            container.setArgs(args);
            if (envs != null) {
                container.setEnv(envs);
            }
            container.setResources(res);

            String name = Util.OPENSHIFT_POD_PREFIX;
            String hostname = Util.OPENSHIFT_POD_PREFIX + UUID.randomUUID().toString().substring(0, 11);
            if (hostname.endsWith("-")) {
                hostname = hostname.substring(0, hostname.length() - 1) + "a";
            }
            V1ObjectMeta meta = new V1ObjectMeta();

            String lsfOperatorType = Util.getConfig().getLsfOperatorType();
            if (lsfOperatorType == null) {
                lsfOperatorType = Util.OPENSHIFT_LSF_TYPE_DEFAULT_VALUE;
            }
            String lsfOperatorCluster = Util.getConfig().getLsfOperatorCluster();
            if (lsfOperatorCluster == null) {
                lsfOperatorCluster =  Util.OPENSHIFT_LSF_CLUSTER_DEFAULT_VALUE;
            }
            String lsfRole = Util.OPENSHIFT_LSF_ROLE_DEFAULT_VALUE;

            Map<String, String> labels = new HashMap<String, String>();
            labels.put(Util.RC_ACCOUNT_LABEL, rc_account);
            labels.put(Util.RC_POD_PREFIX_LABEL, Util.OPENSHIFT_POD_CONTAINER_NAME);
            labels.put(Util.RC_POD_TMPLID_LABEL, t.getTemplateId());
            labels.put(Util.RC_REQID_LABEL, rsp.getReqId());
            labels.put(Util.OPENSHIFT_LSF_CLUSTER_LABEL, lsfOperatorCluster);
            labels.put(Util.OPENSHIFT_LSFTYPE_LABEL, lsfOperatorType);
            labels.put(Util.OPENSHIFT_LSF_ROLE, lsfRole);

            /*
            Map<String, String> annotations = new HashMap<String, String>();
            annotations.put("key1", "val1");
            meta.setAnnotations(annotations);
             */
            //meta.generateName(name);
            meta.setName(hostname);
            meta.setNamespace(namespace);
            meta.setLabels(labels);

            V1PodSpec spec = new V1PodSpec();
            spec.setHostname(hostname);
            spec.setSubdomain(Util.OPENSHIFT_LSF_SUBDOMAIN_DEFAULT_VALUE);
            spec.containers(Arrays.asList(container));
            spec.setServiceAccount(serviceAccount);
            spec.setServiceAccountName(serviceAccount);
            spec.restartPolicy("Never");
            Map<String, String> nodeSelectors =t.getNodeSelectors();
            if (nodeSelectors != null) {
                spec.setNodeSelector(nodeSelectors);
            }
            Map<String, String> mountPaths = t.getMountPaths();
            Map<String, V1PersistentVolumeClaimStatus> pPvcsMap = null;
            Map<String, Map<String, String>> pConfigsMap = null;
            Map<String,  Map<String, byte[]>> pSecretsMap = null;
            List<V1Volume> volList = null;
            List<V1VolumeMount> volumeMounts = null;
            if (mountPaths != null
                    && ! mountPaths.isEmpty()) {
                pPvcsMap = getPVCs();
                pConfigsMap = getConfigMaps();
                pSecretsMap = getSecrets();
                if (pPvcsMap == null
                        || pPvcsMap.isEmpty()) {
                    if (log.isTraceEnabled()) {
                        log.trace("No PersistentVolumeClaims available");
                    }
                }
                if (pConfigsMap == null
                        || pConfigsMap.isEmpty()) {
                    if (log.isTraceEnabled()) {
                        log.trace("No ConfigMaps available");
                    }
                }
                if (pSecretsMap == null
                        || pSecretsMap.isEmpty()) {
                    if (log.isTraceEnabled()) {
                        log.trace("No Secrets available");
                    }
                }
                if ((pPvcsMap != null && !(pPvcsMap.isEmpty()))
                        || (pConfigsMap != null && !(pConfigsMap.isEmpty()))
                        || (pSecretsMap != null && !(pSecretsMap.isEmpty()))) {
                    volList = getVolumes(mountPaths);
                    if (volList != null
                            && volList.size() > 0) {
                        volumeMounts = getVolumeMounts(mountPaths);
                        if (volumeMounts != null
                                && volumeMounts.size() > 0) {
                            spec.setVolumes(volList);
                            container.setVolumeMounts(volumeMounts);
                        }
                    }
                }
            }
            V1Pod body = new V1Pod();
            body.setApiVersion(Util.OPENSHIFT_API_VERSION);
            body.setKind(Util.OPENSHIFT_API_KIND_POD);
            body.setMetadata(meta);
            body.setSpec(spec);
            pod = api.createNamespacedPod(namespace, body, pretty, dryRun, fieldManager, fieldValidation);
            if (pod != null) {
                log.info("End in class Client in method create a Pod: " + pod.getMetadata().getName());
                if (log.isTraceEnabled()) {
                    log.trace("Pod: " + pod );
                }
            }
        } catch (Exception e) {
            pod = null;
            log.error(e);
            e.printStackTrace();
            rsp.setStatus(Util.EBROKERD_STATE_ERROR);
            rsp.setRsp(1, "Failed to request pods on " + Util.getProviderName() + ". " + e.toString());
        }
        return pod;
    }

    private List<V1Pod> waitPodIPs(List<V1Pod>list, Entity rsp) {
        List<V1Pod> pList = null;
        List<String> podNames = new ArrayList<String>();
        for (V1Pod pod: list) {
            podNames.add(pod.getMetadata().getName());
        }
        pList = list;
        long begin = new Date().getTime()/1000;
        while (true) {
            boolean hasPodIPs = true;
            for (V1Pod pod: pList) {
                if (pod.getStatus().getPodIP() == null) {
                    hasPodIPs = false;
                    break;
                }
            }
            if (hasPodIPs) {
                return pList;
            }
            // initially there is no IP assigned
            long now = new Date().getTime()/1000;
            try {
                Thread.sleep(5000);
            } catch (Exception et) {
                log.error(et);
            }
            if (now - begin > 60) { // longer than 1 minute
                return null;
            }
            try {
                StringBuffer sb = new StringBuffer();
                sb.append(Util.RC_ACCOUNT_LABEL + ", " + Util.RC_POD_PREFIX_LABEL + " = " + Util.OPENSHIFT_POD_CONTAINER_NAME);
                sb.append("," + Util.RC_REQID_LABEL + "=" + rsp.getReqId());
                String pretty = null;
                Boolean allowWatchBookmarks = null;
                String _continue = null;
                String fieldSelector = null;
                String labelSelector = sb.toString();
                Integer limit = null;
                Integer timeoutSeconds = null;
                Boolean watch = null;
                String resourceVersion = null; // Util.OPENSHIFT_API_VERSION;
                String resourceVersionMatch = null;
                V1PodList podlist = api.listNamespacedPod(namespace, pretty, allowWatchBookmarks, _continue, fieldSelector, labelSelector, limit, resourceVersion, resourceVersionMatch, timeoutSeconds, watch);
                 if (podlist != null) {
                    pList = podlist.getItems();
                }
            } catch (Exception e) {
                log.error(e);
            }
        }
    }
    public List<V1Pod> createPods(Entity req, Template t, String rc_account, Entity rsp) {
        log.info("Start in class Client in method createPods with parameters: t: " + t + ", rc_account: "
                 + rc_account );
        List<V1Pod> list = new ArrayList<V1Pod>();
        try {
            int numPods = (t.getNumPods() != null)? t.getNumPods().intValue(): 1;
            for (int i = 0; i < numPods; i ++) {
                V1Pod pod = createPod(t, rc_account, rsp);
                if (pod == null) {
                    // delete pods created here
                    for (V1Pod temp: list) {
                        deletePod(temp.getMetadata().getName(), null);
                    }
                    list = null;
                    return list;
                }
                list.add(pod);
            }
            // start_lsf.sh on master pod monitors pod list
            // LSF hosts file gets updated accordingly - so no need to wait until pod IP becomes available
            boolean needsWaitPodIp = isWaitPodIp;
            String waitPodIp = System.getProperty(Util.EBROKERD_PROPERTY_WAIT_POD_IP);
            if (waitPodIp != null && waitPodIp.equalsIgnoreCase("Y")) {
                needsWaitPodIp = true;
            }
            if (needsWaitPodIp) {
                List<V1Pod> pList = waitPodIPs(list, rsp);
                if (pList == null) {
                    log.error("No pod IP available");
                    for (V1Pod temp: list) {
                        deletePod(temp.getMetadata().getName(), null);
                    }
                    list = null;
                } else {
                    list = pList;
                }
            }

        } catch (Exception e) {
            log.error(e);
        }
        return list;
    }

    private void addLabelToPod(String podName, String labelKey, String labelVal) {
        // set retId
        String pretty = null;
        String dryRun = null;
        String fieldManager = null;
        String fieldValidation = null;
        Boolean force = null;
        String content = String.format("[{\"op\": \"add\", \"path\": \"/metadata/labels/%s\", \"value\": \"%s\"}]", labelKey, labelVal);
        V1Patch body = new V1Patch(content);
        try {
            api.patchNamespacedPod(podName, namespace, body, pretty, dryRun, fieldManager, fieldValidation, force);
        } catch (Exception e) {
            log.error(e);
            e.printStackTrace();

        }

    }
    //private V1Status cleanupPod(String podName, boolean setErrMessage) {
    //	V1Status status = null;
    public int deletePod(String podName, Entity rsp) {
        String errMsg = null;
        int ret = -1;
        try {
            String pretty = null;
            String dryRun = null;
            String propagationPolicy = null;
            Integer gracePeriodSeconds = null;
            Boolean orphanDependents = null;
            V1DeleteOptions deleteOptions = null;
            if (rsp != null) {
                addLabelToPod(podName,  Util.RC_POD_RETID_LABEL, rsp.getRetId());
            }
            // deleteNamespacedPod() always raises ApiException ... cannot be used.
            //status = api.deleteNamespacedPod(podName, namespace, pretty, dryRun, gracePeriodSeconds, orphanDependents, propagationPolicy, deleteOptions);
            Response response = api.deleteNamespacedPodCall(podName, namespace, pretty, dryRun, gracePeriodSeconds, orphanDependents, propagationPolicy, deleteOptions, null).execute();
            // 404 can be treated as success (most likely removed externally)
            if (response.code() == 404) {
                log.warn("Pod <" + podName + "> not found. Response code: " + response.code() + "  " + response.message());
                ret = 0;
            } else if (!response.isSuccessful()) {
                errMsg = "Response code: " + response.code() + " " + response.message();
            } else {
                ret = 0;
            }
        } catch (Exception e) {
            log.error(e);
            e.printStackTrace();
            errMsg = e.toString();
        }
        if (ret < 0
                && rsp != null) {
            rsp.setStatus(Util.EBROKERD_STATE_ERROR);
            rsp.setRsp(1, "Failed to delete pods on " + Util.getProviderName() + ". " + errMsg);
        }
        return ret;
    }

    /**
     *
     * @Title: list Pods
     * @Description: query Pods
     * @param @return
     * @return List<Reservation>
     * @throws
     */
    public  List<V1Pod> listPods() {
        if (log.isTraceEnabled()) {
            log.trace("Start in class Client in method listPods");
        }
        List<V1Pod> pods = null;
        try {
            String pretty = null;
            Boolean allowWatchBookmarks = null;
            String _continue = null;
            String fieldSelector = null;
            String labelSelector = Util.RC_ACCOUNT_LABEL + ", " + Util.RC_POD_PREFIX_LABEL + " = " + Util.OPENSHIFT_POD_CONTAINER_NAME;
            Integer limit = null;
            Integer timeoutSeconds = null;
            Boolean watch = null;
            String resourceVersion = null; // Util.OPENSHIFT_API_VERSION;
            String resourceVersionMatch = null;
            V1PodList list = api.listNamespacedPod(namespace, pretty, allowWatchBookmarks, _continue, fieldSelector, labelSelector, limit, resourceVersion, resourceVersionMatch, timeoutSeconds, watch);

            if (list != null) {
                pods = 	list.getItems();
                if (isDebugging) {
                    for (V1Pod item : list.getItems()) {
                        log.info("pod: "+ item);
                    }
                }
            }
        } catch (Exception e) {
            log.error(e);
        }
        return pods;
    }

    public  Map<String,  V1Pod> listPods(List<String> podNames) {
        try {
            List<V1Pod>pods = listPods();
            Map<String, Boolean> nMap = new HashMap<String, Boolean>();
            Map<String, V1Pod> podMap = new HashMap<String, V1Pod>();
            for (String name: podNames) {
                nMap.put(name, new Boolean(true));
            }
            for (V1Pod pod  : pods) {
                String podName =pod.getMetadata().getName();
                if (nMap.get(podName) != null) {
                    podMap.put(podName, pod);
                }
            }
            return podMap;
        } catch (Exception e) {
            log.error(e);
            return null;
        }
    }
    private Map<String, V1PersistentVolumeClaimStatus> getPVCs() {
        if (this.pvcsMap != null) {
            return this.pvcsMap;
        }
        V1PersistentVolumeClaimList list = null;
        Map <String, V1PersistentVolumeClaimStatus> m = new HashMap<String, V1PersistentVolumeClaimStatus>();
        StringBuffer sb = new StringBuffer();
        String pretty = null;
        Boolean allowWatchBookmarks = null;
        String _continue = null;
        String fieldSelector = null;
        String labelSelector = null;
        Integer limit = null;
        Integer timeoutSeconds= null;
        Boolean watch = null;
        String resourceVersion = null; // Util.OPENSHIFT_API_VERSION;
        String resourceVersionMatch = null;
        try {
            if (sb.length() > 0) {
                labelSelector = sb.toString();
            }
            list = api.listNamespacedPersistentVolumeClaim(
                       namespace, pretty, allowWatchBookmarks, _continue,
                       fieldSelector, labelSelector, limit, resourceVersion, resourceVersionMatch,
                       timeoutSeconds, watch);
            if (log.isTraceEnabled()) {
                log.trace("PersistentVolumeClaims " + list);
            }
            if (list != null) {
                for (V1PersistentVolumeClaim pvc : list.getItems()) {
                    String pvcName = pvc.getMetadata().getName();
                    if (pvcName != null) {
                        m.put(pvcName, pvc.getStatus());
                    }
                }
            }
            this.pvcsMap = m;
            return this.pvcsMap;
        } catch (Exception e) {
            log.error(e);
            return null;
        }
    }
    private Map<String, Map<String, byte[]>> getSecrets() {
        if (this.secretsMap != null) {
            return this.secretsMap;
        }
        V1SecretList list = null;
        Map <String, Map<String, byte[]>> m = new HashMap<String, Map<String, byte[]>>();
        StringBuffer sb = new StringBuffer();
        String pretty = null;
        Boolean allowWatchBookmarks = null;
        String _continue = null;
        String fieldSelector = null;
        String labelSelector = null;
        Integer limit = null;
        Integer timeoutSeconds= null;
        Boolean watch = null;
        String resourceVersion = null; // Util.OPENSHIFT_API_VERSION;
        String resourceVersionMatch = null;
        try {
            if (sb.length() > 0) {
                labelSelector = sb.toString();
            }
            list = api.listNamespacedSecret(namespace, pretty, allowWatchBookmarks, _continue, fieldSelector, labelSelector, limit, resourceVersion, resourceVersionMatch, timeoutSeconds, watch);
            if (log.isTraceEnabled()) {
                log.trace("secrets " + list);
            }
            if (list != null) {
                for (V1Secret secret : list.getItems()) {
                    String secretName = secret.getMetadata().getName();
                    if (secretName != null) {
                        m.put(secretName, secret.getData());
                    }
                }
            }
            this.secretsMap = m;
            return this.secretsMap;
        } catch (Exception e) {
            log.error(e);
            return null;
        }
    }
    private Map<String, Map<String, String>> getConfigMaps() {
        if (this.configsMap != null) {
            return this.configsMap;
        }
        V1ConfigMapList list = null;
        Map <String, Map<String,String>> m = new HashMap<String, Map<String, String>>();
        StringBuffer sb = new StringBuffer();
        String pretty = null;
        Boolean allowWatchBookmarks = null;
        String _continue = null;
        String fieldSelector = null;
        String labelSelector = null;
        Integer limit = null;
        Integer timeoutSeconds= null;
        Boolean watch = null;
        String resourceVersion = null; // Util.OPENSHIFT_API_VERSION;
        String resourceVersionMatch= null;
        try {
            if (sb.length() > 0) {
                labelSelector = sb.toString();
            }
            list = api.listNamespacedConfigMap(namespace, pretty, allowWatchBookmarks, _continue, fieldSelector, labelSelector, limit, resourceVersion, resourceVersionMatch, timeoutSeconds, watch);
            if (log.isTraceEnabled()) {
                log.trace("ConfigMaps " + list);
            }
            if (list != null) {
                for (V1ConfigMap configMap : list.getItems()) {
                    String configMapName = configMap.getMetadata().getName();
                    if (configMapName != null) {
                        m.put(configMapName, configMap.getData());
                    }
                }
            }
            this.configsMap = m;
            return this.configsMap;
        } catch (Exception e) {
            log.error(e);
            return null;
        }
    }

    private List<V1Volume> getVolumes(Map <String, String>mountPaths) {
        List<V1Volume> volList = new ArrayList<V1Volume>();
        List<String> pvcList = new ArrayList<String>(mountPaths.keySet());
        String mountPath;
        Map<String, V1PersistentVolumeClaimStatus> pPvcsMap = null;
        Map<String, Map<String, String>> pConfigsMap = null;
        Map<String,  Map<String, byte[]>> pSecretsMap = null;

        if (mountPaths == null
                || mountPaths.isEmpty()) {
            return null;
        }
        if (this.pvcsMap != null && ! this.pvcsMap.isEmpty()) {
            pPvcsMap = this.pvcsMap;
        }
        if  (this.configsMap != null && ! this.configsMap.isEmpty()) {
            pConfigsMap = this.configsMap;
        }

        if  (this.secretsMap != null && ! this.secretsMap.isEmpty()) {
            pSecretsMap = this.secretsMap;
        }
        if (pPvcsMap == null && pConfigsMap == null && pSecretsMap == null) {
            return null;
        }
        for (String pvc: pvcList) {
            try {
                V1PersistentVolumeClaimStatus volStatus = null;
                Map<String, String> configMap = null;
                Map<String, byte[]> secret = null;
                if (pPvcsMap != null) {
                    volStatus = this.pvcsMap.get(pvc);
                    if (volStatus == null) {
                        if (log.isTraceEnabled()) {
                            log.trace("PersistentVolumeClaim " + pvc + " not found");
                        }
                    }
                }
                if (pConfigsMap != null) {
                    configMap = this.configsMap.get(pvc);
                    if (configMap == null) {
                        if (log.isTraceEnabled()) {
                            log.trace("ConfigMap " + pvc + " not found");
                        }
                    }
                }
                if (pSecretsMap != null) {
                    secret = this.secretsMap.get(pvc);
                    if (secret == null) {
                        if (log.isTraceEnabled()) {
                            log.trace("Secret " + pvc + " not found");
                        }
                    }
                }
                if (volStatus == null && configMap == null && secret == null) {
                    continue;
                }
                mountPath = mountPaths.get(pvc);
                if (mountPath == null) {
                    if (log.isTraceEnabled()) {
                        log.trace("mountPath not configured for " + pvc);
                    }
                    continue;
                }
                if (log.isTraceEnabled()) {
                    log.trace("mountPath configured for " + pvc + ": " + mountPath);
                }
                V1Volume vol = new V1Volume();
                if (volStatus != null) {
                    V1PersistentVolumeClaimVolumeSource pvcSource = new
                    V1PersistentVolumeClaimVolumeSource();
                    pvcSource.setClaimName(pvc);
                    vol.setPersistentVolumeClaim(pvcSource);
                } else if (configMap != null) {
                    V1ConfigMapVolumeSource configSource = new V1ConfigMapVolumeSource();
                    Integer defaultMode = 384;
                    //List<V1KeyToPath> items = new ArrayList<V1KeyToPath>();
                    //V1KeyToPath aKey = new V1KeyToPath();
                    //aKey.setKey(pvc);
                    //aKey.setMode(defaultMode);
                    //aKey.setPath(mountPath);
                    ///items.add(aKey);
                    //configSource.setItems(items);
                    configSource.setName(pvc);
                    configSource.setDefaultMode(defaultMode);
                    vol.setConfigMap(configSource);
                } else if (secret != null) {
                    V1SecretVolumeSource secretSource = new V1SecretVolumeSource();
                    Integer defaultMode = 384;
                    //List<V1KeyToPath> items = new ArrayList<V1KeyToPath>();
                    //V1KeyToPath aKey = new V1KeyToPath();
                    //aKey.setKey(pvc);
                    //aKey.setMode(defaultMode);
                    //aKey.setPath(mountPath);
                    //items.add(aKey);
                    //secretSource.setItems(items);
                    secretSource.setSecretName(pvc);
                    secretSource.setDefaultMode(defaultMode);
                    vol.setSecret(secretSource);
                } else {
                    continue;
                }
                vol.setName(pvc);
                volList.add(vol);
            } catch (Exception e) {
                log.error(e);
                return null;
            }
        } // for mountVolumeList
        return volList;
    }
    private List<V1VolumeMount> getVolumeMounts(Map <String, String>mountPaths) {
        List<V1VolumeMount> mountList = new ArrayList<V1VolumeMount>();
        List<String> pvcList = new ArrayList<String>(mountPaths.keySet());
        String mountPath;
        if (mountPaths == null
                || mountPaths.isEmpty()) {
            return null;
        }
        for (String pvc: pvcList) {
            try {
                V1PersistentVolumeClaimStatus volStatus = null;
                Map<String, String> configMap = null;
                Map<String, byte[]> secret = null;
                if (this.pvcsMap != null && ! this.pvcsMap.isEmpty()) {
                    volStatus = this.pvcsMap.get(pvc);
                }
                if (this.configsMap != null && ! this.configsMap.isEmpty()) {
                    configMap = this.configsMap.get(pvc);
                }
                if (this.secretsMap != null && ! this.secretsMap.isEmpty()) {
                    secret = this.secretsMap.get(pvc);
                }
                if (volStatus == null && configMap == null && secret == null ) {
                    if (log.isTraceEnabled()) {
                        log.trace(pvc + " not found in PersistentVolumeClaims,ConfigMaps, and secrets.");
                    }
                    continue;
                }
                mountPath = mountPaths.get(pvc);
                if (mountPath == null) {
                    if (log.isTraceEnabled()) {
                        log.trace("mountPath not configured for " + pvc);
                    }
                    continue;
                }
                if (log.isTraceEnabled()) {
                    log.trace("mountPath configured for " + pvc + ": " + mountPath);
                }
                V1VolumeMount aMount = new V1VolumeMount();
                // volumeMount
                aMount.setName(pvc);
                aMount.setMountPath(mountPath);
                mountList.add(aMount);
            } catch (Exception e) {
                log.error(e);
                return null;
            }
        } // for mountVolumeList
        return mountList;
    }
}
