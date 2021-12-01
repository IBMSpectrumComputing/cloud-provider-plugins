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

package com.ibm.spectrum.cyclecloud.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ibm.spectrum.cyclecloud.constant.ProvConst;
import com.ibm.spectrum.cyclecloud.util.ProvUtil;

/**
* @Class Name : ProvConfig
* @Description: Cycle cloud provider configuration
* @Author     : Yudong (Dom) Wang
* @Date       : 2019-5-30 11:00:22
* @Version    : V1.0
*/
public class ProvConfig {
    @JsonProperty(ProvConst.CC_SERVER)
    private String server;

    @JsonProperty(ProvConst.CC_CLUSTER)
    private String cluster;

    @JsonProperty(ProvConst.CC_REGION)
    private String region;

    @JsonProperty(ProvConst.CC_USER)
    private String user;

    @JsonProperty(ProvConst.CC_PASSWD)
    private String password;

    @JsonProperty(ProvConst.LOG_LEVEL)
    private String logLevel;

    @JsonProperty(ProvConst.CC_VM_TERMINATION_TIMEOUT)
    private Long terminationTimeout;

    @JsonProperty(ProvConst.CC_VM_CREATION_TIMEOUT)
    private Long creationTimeout;

    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public String getCluster() {
        return cluster;
    }

    public void setCluster(String cluster) {
        this.cluster = cluster;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    public Long getTerminationTimeout() {
        if (null == this.terminationTimeout) {
            this.terminationTimeout = ProvConst.AZURE_CC_VM_TERMINATION_TIMEOUT;
        } else {
            this.terminationTimeout *= ProvConst.ONE_MINUTE;
        }
        return this.terminationTimeout;
    }

    public void setTerminationTimeout(Long timeout) {
        this.terminationTimeout = timeout;
    }

    public Long getCreationTimeout() {
        if (null == this.creationTimeout) {
            this.creationTimeout = ProvConst.AZURE_CC_VM_CREATION_TIMEOUT;
        } else {
            this.creationTimeout *= ProvConst.ONE_MINUTE;
        }
        return creationTimeout;
    }

    public void setCreationTimeout(Long timeout) {
        this.creationTimeout = timeout;
    }

    @Override
    public String toString() {
        return ProvUtil.toJsonStr(this);
    }

}
