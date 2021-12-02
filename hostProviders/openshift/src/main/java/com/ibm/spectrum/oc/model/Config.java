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


package com.ibm.spectrum.oc.model;

import java.util.HashMap;
import net.sf.json.JSONObject;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Config {
    /**
     * Required and value type is string. specify the log level
     */
    @JsonProperty("LogLevel")
    private String logLevel;

    @JsonProperty("INSTANCE_CREATION_TIMEOUT")
    @JsonInclude(Include.NON_NULL)
    private Integer instanceCreationTimeout;

    @JsonProperty("SERVICE_ACCOUNT")
    @JsonInclude(Include.NON_NULL)
    private String serviceAccount;

    @JsonProperty("SERVICE_ACCOUNT_TOKEN")
    @JsonInclude(Include.NON_NULL)
    private String token;

    @JsonProperty("CA_CERT")
    @JsonInclude(Include.NON_NULL)
    private String caCert;

    @JsonProperty("NAME_SPACE")
    @JsonInclude(Include.NON_NULL)
    private String nameSpace;

    @JsonProperty("SERVER_URL")
    @JsonInclude(Include.NON_NULL)
    private String serverUrl;

    @JsonProperty("LSF_OPERATOR_CLUSTER")
    @JsonInclude(Include.NON_NULL)
    private String lsfOperatorCluster;

    @JsonProperty("LSF_OPERATOR_TYPE")
    @JsonInclude(Include.NON_NULL)
    private String lsfOperatorType;

    @JsonProperty("WAIT_POD_IP")
    @JsonInclude(Include.NON_NULL)
    private String waitPodIp;

    @JsonProperty("TIMEOUT_IN_SEC")
    @JsonInclude(Include.NON_NULL)
    private Integer timeoutInSec;

    @JsonProperty("ENV_VARS")
    @JsonInclude(Include.NON_NULL)
    private HashMap<String, String> envVars;

    @JsonProperty("LSF_MAX_TRY_ADD_HOST")
    @JsonInclude(Include.NON_NULL)
    private Integer maxTryAddHost;
    /**
    * <p>Title: </p>
    * <p>Description: </p>
    */
    public Config() {
    }

    /**
    * <p>Title: </p>
    * <p>Description: </p>
    * @param t
    */
    public Config(Config c) {
        this.logLevel = c.getLogLevel();
    }

    /**
    * @return logLevel
    */
    public String getLogLevel() {
        return logLevel;
    }

    /**
     * @param logLevel the logLevel to set
     */
    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }


    public Integer getInstanceCreationTimeout() {
        return instanceCreationTimeout;
    }

    public void setInstanceCreationTimeout(Integer instanceCreationTimeout) {
        this.instanceCreationTimeout = instanceCreationTimeout;
    }

    public String getServiceAccount() {
        return serviceAccount;
    }

    public void setServiceAccount(String serviceAccount) {
        this.serviceAccount = serviceAccount;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getNameSpace() {
        return nameSpace;
    }

    public void setNameSpace(String nameSpace) {
        this.nameSpace = nameSpace;
    }


    public String getCaCert() {
        return caCert;
    }

    public void setCaCert(String caCert) {
        this.caCert = caCert;
    }

    public String getLsfOperatorCluster() {
        return lsfOperatorCluster;
    }

    public void setLsfOperatorCluster(String lsfOperatorCluster) {
        this.lsfOperatorCluster = lsfOperatorCluster;
    }

    public String getLsfOperatorType() {
        return lsfOperatorType;
    }

    public void setLsfOperatorType(String lsfOperatorType) {
        this.lsfOperatorType = lsfOperatorType;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getWaitPodIp() {
        return waitPodIp;
    }

    public void setWaitPodIp(String waitPodIp) {
        this.waitPodIp = waitPodIp;
    }

    public Integer getTimeoutInSec() {
        return timeoutInSec;
    }

    public void setTimeoutInSec(Integer timeoutInSec) {
        this.timeoutInSec = timeoutInSec;
    }

    public HashMap<String, String> getEnvVars() {
        return envVars;
    }

    public void setEnvVars(JSONObject envVars) {
        try {
            this.envVars = new ObjectMapper().readValue(envVars.toString(), HashMap.class);
        } catch(Exception e) {

        }
    }

    public Integer getMaxTryAddHost() {
        return maxTryAddHost;
    }

    public void setMaxTryAddHost(Integer maxTryAddHost) {
        this.maxTryAddHost = maxTryAddHost;
    }

    /** (Non Javadoc)
    * <p>Title: toString</p>
    * <p>Description: </p>
    * @return
    * @see java.lang.Object#toString()
    */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Config [logLevel=");
        builder.append(logLevel);
        builder.append(", instanceCreationTimeout=");
        builder.append(instanceCreationTimeout);
        builder.append(", nameSpace=");
        builder.append(nameSpace);
        builder.append(", serviceAccount=");
        builder.append(serviceAccount);
        builder.append(", token=");
        builder.append(token);
        builder.append(", caCert=");
        builder.append(caCert);
        builder.append(", lsfOperatorCluster=");
        builder.append(lsfOperatorCluster);
        builder.append(", lsfOperatorType=");
        builder.append(lsfOperatorType);
        builder.append(", serverUrl=");
        builder.append(serverUrl);
        builder.append(", waitPodIp=");
        builder.append(waitPodIp);
        builder.append(", timeoutInSec=");
        builder.append(timeoutInSec);
        builder.append(", envVars=");
        builder.append(envVars);
        builder.append(", maxTryAddHost=");
        builder.append(maxTryAddHost);
        builder.append("]");
        return builder.toString();
    }
}
