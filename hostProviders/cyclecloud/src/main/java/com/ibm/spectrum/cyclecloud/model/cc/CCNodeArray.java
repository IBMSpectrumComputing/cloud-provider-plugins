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
* @Class Name : CCNodeArray
* @Description: Azure CC node array
* @Author     : Yudong (Dom) Wang
* @Date       : 2019-6-21 15:07:40
* @Version    : V1.0
*/
public class CCNodeArray {
    @JsonProperty("SubnetId")
    private String subnetId;

    @JsonProperty("Region")
    private String region;

    @JsonProperty("Status")
    private String status;

    @JsonProperty("State")
    private String state;

    @JsonProperty("Provider")
    private String provider;

    @JsonProperty("Azure")
    private Azure azure;

    @JsonProperty("MaxCoreCount")
    private Integer maxCoreCount;

    /**
     * It can be a string or an array list
     */
    @JsonProperty("MachineType")
    private Object machineType;

    @JsonProperty("Credentials")
    private String credentials;

    @JsonProperty("AccountName")
    private String accountName;

    @JsonProperty("Configuration")
    private CCConfig config;

    @JsonProperty("Interruptible")
    private Boolean interruptible;

    @JsonProperty("ClusterInitSpecs")
    private Map<String, Object> clusterInitSpecs;

    @JsonProperty("KeypairLocation")
    private String keypairLocation;

    @JsonProperty("ImageName")
    private String imageName;

    @JsonProperty("PhaseMap")
    private Map<String, Object> phaseMap;

    @JsonProperty("IsSpotInstance")
    private Boolean isSpotInstance;

    @JsonProperty("TargetState")
    private String targetState;

    @JsonProperty("Priority")
    private Object priority;

    public String getSubnetId() {
        return subnetId;
    }

    public void setSubnetId(String subnetId) {
        this.subnetId = subnetId;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Azure getAzure() {
        return azure;
    }

    public void setAzure(Azure azure) {
        this.azure = azure;
    }

    public Integer getMaxCoreCount() {
        return maxCoreCount;
    }

    public void setMaxCoreCount(Integer maxCoreCount) {
        this.maxCoreCount = maxCoreCount;
    }

    public Object getMachineType() {
        return machineType;
    }

    public void setMachineType(Object machineType) {
        this.machineType = machineType;
    }

    public String getCredentials() {
        return credentials;
    }

    public void setCredentials(String credentials) {
        this.credentials = credentials;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public CCConfig getConfig() {
        return config;
    }

    public void setConfig(CCConfig config) {
        this.config = config;
    }

    public Boolean getInterruptible() {
        return interruptible;
    }

    public void setInterruptible(Boolean interruptible) {
        this.interruptible = interruptible;
    }

    public Map<String, Object> getClusterInitSpecs() {
        return clusterInitSpecs;
    }

    public void setClusterInitSpecs(Map<String, Object> clusterInitSpecs) {
        this.clusterInitSpecs = clusterInitSpecs;
    }

    public String getKeypairLocation() {
        return keypairLocation;
    }

    public void setKeypairLocation(String keypairLocation) {
        this.keypairLocation = keypairLocation;
    }

    public String getImageName() {
        return imageName;
    }

    public void setImageName(String imageName) {
        this.imageName = imageName;
    }

    public Map<String, Object> getPhaseMap() {
        return phaseMap;
    }

    public void setPhaseMap(Map<String, Object> phaseMap) {
        this.phaseMap = phaseMap;
    }

    public Boolean getIsSpotInstance() {
        return isSpotInstance;
    }

    public void setIsSpotInstance(Boolean isSpotInstance) {
        this.isSpotInstance = isSpotInstance;
    }

    public String getTargetState() {
        return targetState;
    }

    public void setTargetState(String targetState) {
        this.targetState = targetState;
    }

    public Object getPriority() {
        return priority;
    }

    public void setPriority(Object priority) {
        this.priority = priority;
    }

    @Override
    public String toString() {
        return ProvUtil.toJsonStr(this);
    }
}
