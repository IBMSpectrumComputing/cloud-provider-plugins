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

package com.ibm.spectrum.cyclecloud.model.cc;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ibm.spectrum.cyclecloud.util.ProvUtil;

/**
* @Class Name : Node
* @Description: Azure CC node
* @Author     : Yudong (Dom) Wang
* @Date       : 2019-6-6 11:22:58
* @Version    : V1.0
*/
public class CCNode {
    @JsonProperty("Template")
    private String tpl;

    @JsonProperty("TargetState")
    private String tstate;

    @JsonProperty("Name")
    private String name;

    @JsonProperty("MachineType")
    private String mtype;

    @JsonProperty("NodeId")
    private String nodeId;

    @JsonProperty("State")
    private String state;

    @JsonProperty("PublicIp")
    private String publicIp;

    @JsonProperty("PrivateIp")
    private String privateIp;

    @JsonProperty("Hostname")
    private String hostname;

    @JsonProperty("InstanceId")
    private String instanceId;

    @JsonProperty("ClusterName")
    private String cluster;

    @JsonProperty("PlacementGroupId")
    private String pGrpId;

    @JsonProperty("Configuration")
    private Map<String, Object> config;

    public String getTpl() {
        return tpl;
    }

    public void setTpl(String tpl) {
        this.tpl = tpl;
    }

    public String getTstate() {
        return tstate;
    }

    public void setTstate(String tstate) {
        this.tstate = tstate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMtype() {
        return mtype;
    }

    public void setMtype(String mtype) {
        this.mtype = mtype;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getPublicIp() {
        return publicIp;
    }

    public void setPublicIp(String publicIp) {
        this.publicIp = publicIp;
    }

    public String getPrivateIp() {
        return privateIp;
    }

    public void setPrivateIp(String privateIp) {
        this.privateIp = privateIp;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getCluster() {
        return cluster;
    }

    public void setCluster(String cluster) {
        this.cluster = cluster;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config;
    }

    public String getpGrpId() {
        return pGrpId;
    }

    public void setpGrpId(String pGrpId) {
        this.pGrpId = pGrpId;
    }

    @Override
    public String toString() {
        return ProvUtil.toJsonStr(this);
    }
}
