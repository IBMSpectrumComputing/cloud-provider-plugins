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


package com.ibm.spectrum.model;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

import net.sf.json.JSONObject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @ClassName: GcloudTemplate
 * @Description: The template of Google Cloud virtual machine
 * @author zcg
 * @date Sep 27, 2017 3:31:00 PM
 * @version 1.0
 */
public class GcloudTemplate {
    private static Logger log = LogManager.getLogger(GcloudTemplate.class);
    /**
     * Required and value type is string. Unique id that can identify this
     * template in the host provider
     */
    private String templateId;

    /**
     * Required and value type is number. The max number of machine with this
     * template configuration can be provisioned. means there is no limit to
     * provision machine with this template
     */
    private Integer maxNumber;

    /**
     * Required and type is string, represents the value type of this attribute,
     * it could be 'string' or 'numeric'","Required and type is string,
     * represents the actual value of this attribute
     */
    private HashMap<String, List<String>> attributes;

    @JsonInclude(Include.NON_NULL)
    private String region;

    @JsonInclude(Include.NON_NULL)
    private String zone;

    @JsonInclude(Include.NON_NULL)
    private String imageId;

    @JsonInclude(Include.NON_NULL)
    private String vpc;

    @JsonInclude(Include.NON_NULL)
    private String subnetId;

    @JsonInclude(Include.NON_NULL)
    private Boolean privateNetworkOnlyFlag;

    @JsonInclude(Include.NON_NULL)
    private String vmType;

    @JsonProperty("machineCount")
    @JsonInclude(Include.NON_NULL)
    private Integer vmNumber;

    @JsonInclude(Include.NON_NULL)
    private String gpuType;

    @JsonInclude(Include.NON_NULL)
    private Integer gpuNumber;

    @JsonInclude(Include.NON_NULL)
    private String minCpuPlatform;

    @JsonInclude(Include.NON_NULL)
    private Long ttl;

    @JsonInclude(Include.NON_NULL)
    private String keyName;

    @JsonInclude(Include.NON_NULL)
    private String userData;

    @JsonProperty("securityGroupIds")
    @JsonInclude(Include.NON_NULL)
    private List<String> sgIds;

    @JsonInclude(Include.NON_NULL)
    private GcloudUserData userDataObj;

    @JsonInclude(Include.NON_NULL)
    private String instanceTags;

    @JsonProperty("priority")
    @JsonInclude(Include.NON_NULL)
    private int priority;

    @JsonProperty("hostProject")
    @JsonInclude(Include.NON_NULL)
    private String hostProject;

    @JsonInclude(Include.NON_NULL)
    private String launchTemplateId;


    /**
     * <p>
     * Title:
     * </p>
     * <p>
     * Description:
     * </p>
     */
    public GcloudTemplate() {
    }

    /**
     * <p>
     * Title:
     * </p>
     * <p>
     * Description:
     * </p>
     *
     * @param t
     */
    public GcloudTemplate(GcloudTemplate t) {
        this.templateId = t.getTemplateId();
        this.imageId = t.getImageId();
        this.subnetId = t.getSubnetId();
        this.vmType = t.getVmType();
        this.vmNumber = t.getVmNumber();
        this.ttl = t.getTtl();
        this.keyName = t.getKeyName();
        this.maxNumber = t.getMaxNumber();
        this.sgIds = t.getSgIds();
        this.userData = t.getUserData();
        this.userDataObj = t.getUserDataObj();
        this.zone = t.getZone();
        this.region = t.getRegion();
        this.vpc = t.getVpc();
        this.privateNetworkOnlyFlag = t.getPrivateNetworkOnlyFlag();
        this.gpuType = t.getGpuType();
        this.gpuNumber = t.getGpuNumber();
        this.minCpuPlatform = t.getMinCpuPlatform();
        this.priority = t.getPriority();
        this.hostProject = t.getHostProject();
        this.launchTemplateId = t.getLaunchTemplateId();
    }

    public void hide() {
        // Do not hide imageId vmType.
        // They are used to build EBD_HF_HostMenu->EBD_HF_HostClass
        //this.imageId = null;
        this.subnetId = null;
        //this.vmType = null;
        this.vmNumber = null;
        this.ttl = null;
        this.keyName = null;
        this.sgIds = null;
        //this.userData = null;
        this.region = null;
        this.zone = null;
        this.vpc = null;
        this.privateNetworkOnlyFlag = null;
        this.gpuType = null;
        this.gpuNumber = null;
        this.minCpuPlatform = null;
        this.hostProject = null;
        this.launchTemplateId = null;
    }

    /**
     * @return templateId
     */
    public String getTemplateId() {
        return templateId;
    }

    /**
     * @param templateId
     *            the templateId to set
     */
    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    /**
     * @return zone
     */
    public String getZone() {
        return zone;
    }

    /**
     * @param zone,
     *            instance zone to set
     */
    public void setZone(String zone) {
        this.zone = zone;
    }

    /**
     * @return the region
     */
    public String getRegion() {
        return region;
    }

    /**
     * @param region
     *            the region to set
     */
    public void setRegion(String region) {
        this.region = region;
    }

    /**
     * @return imageId
     */
    public String getImageId() {
        return imageId;
    }

    /**
     * @param imageId
     *            the imageId to set
     */
    public void setImageId(String imageId) {
        this.imageId = imageId;
    }

    /**
     * @return the vpc
     */
    public String getVpc() {
        return vpc;
    }

    /**
     * @param vpc
     *            the vpc to set
     */
    public void setVpc(String vpc) {
        this.vpc = vpc;
    }

    /**
     * @return subnetId
     */
    public String getSubnetId() {
        return subnetId;
    }

    /**
     * @return privateNetworkOnlyFlag
     */
    public Boolean getPrivateNetworkOnlyFlag() {
        return privateNetworkOnlyFlag;
    }

    /**
     * @param privateNetworkOnlyFlag
     *            the privateNetworkOnlyFlag to set
     */
    public void setPrivateNetworkOnlyFlag(Boolean privateNetworkOnlyFlag) {
        this.privateNetworkOnlyFlag = privateNetworkOnlyFlag;
    }

    /**
     * @param subnetId
     *            the subnetId to set
     */
    public void setSubnetId(String subnetId) {
        this.subnetId = subnetId;
    }

    /**
     * @return vmType
     */
    public String getVmType() {
        return vmType;
    }

    /**
     * @param vmType
     *            the vmType to set
     */
    public void setVmType(String vmType) {
        this.vmType = vmType;
    }

    /**
     * @return vmNumber
     */
    public Integer getVmNumber() {
        return vmNumber;
    }

    /**
     * @param vmNumber
     *            the vmNumber to set
     */
    public void setVmNumber(Integer vmNumber) {
        this.vmNumber = vmNumber;
    }

    /**
     * @return keyName
     */
    public String getKeyName() {
        return keyName;
    }

    /**
     * @param keyName
     *            the keyName to set
     */
    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    /**
     * @return maxNumber
     */
    public Integer getMaxNumber() {
        return maxNumber;
    }

    /**
     * @param maxNumber
     *            the maxNumber to set
     */
    public void setMaxNumber(Integer maxNumber) {
        this.maxNumber = maxNumber;
    }

    /**
     * @return sgIds
     */
    public List<String> getSgIds() {
        return sgIds;
    }

    /**
     * @param sgIds
     *            the sgIds to set
     */
    public void setSgIds(List<String> sgIds) {
        this.sgIds = sgIds;
    }

    /**
     * @return userData
     */
    public GcloudUserData getUserDataObj() {
        return userDataObj;
    }

    /**
     * @param userData
     *            the userData to set
     */
    public void setUserDataObj(GcloudUserData userDataObj) {
        this.userDataObj = userDataObj;
    }

    /**
     * @return userData
     */
    public String getUserData() {
        return userData;
    }

    /**
     * @param userData
     *            the userData to set
     */
    public void setUserData(String userData) {
        this.userData = userData;
    }

    /**
     * @return instanceTags
     */
    public String getInstanceTags() {
        return instanceTags;
    }

    /**
     * @param instanceTags
     *            the instanceTags to set
     */
    public void setInstanceTags(String instanceTags) {
        this.instanceTags = instanceTags;
    }

    /**
     * @param attributes
     *            the attributes to set
     */
    public HashMap<String, List<String>> getAttributes() {
        return attributes;
    }

    /**
     * @param attributes
     *            the attributes to set
     */
    public void setAttributes(JSONObject attributes) {
        try {
            this.attributes = new ObjectMapper().readValue(attributes.toString(), HashMap.class);
        } catch (Exception e) {
            log.error("Call service setAttributes method error: ", e);
        }
    }

    /**
     * @return ttl
     */
    public Long getTtl() {
        return ttl;
    }

    /**
     * @param ttl
     *            the ttl to set
     */
    public void setTtl(Long ttl) {
        this.ttl = ttl;
    }

    /**
     * @return the gpuType
     */
    public String getGpuType() {
        return gpuType;
    }

    /**
     * @param gpuType the gpuType to set
     */
    public void setGpuType(String gpuType) {
        this.gpuType = gpuType;
    }

    /**
     * @return the gpuNumber
     */
    public Integer getGpuNumber() {
        return gpuNumber;
    }

    /**
     * @param gpuNumber the gpuNumber to set
     */
    public void setGpuNumber(Integer gpuNumber) {
        this.gpuNumber = gpuNumber;
    }

    /**
     * @return the minCpuPlatform
     */
    public String getMinCpuPlatform() {
        return minCpuPlatform;
    }

    /**
     * @param minCpuPlatform the minCpuPlatform to set
     */
    public void setMinCpuPlatform(String minCpuPlatform) {
        this.minCpuPlatform = minCpuPlatform;
    }

    /**
     * @return the priority
     */
    public int getPriority() {
        return priority;
    }

    /**
     * @param priority the priority to set
     */
    public void setPriority(int priority) {
        this.priority = priority;
    }

    /**
     * @return the hostProject
     */
    public String getHostProject() {
        return hostProject;
    }

    /**
     * @param priority the priority to set
     */
    public void setHostProject(String hostProject) {
        this.hostProject = hostProject;
    }

    /**
     * @return the launchTemplateId
     */
    public String getLaunchTemplateId() {
        return launchTemplateId;
    }

    /**
     * @param launchTemplateId
     *              the launchTemplateId to set
     */
    public void setLaunchTemplateId(String launchTemplateId) {
        this.launchTemplateId = launchTemplateId;
    }

    /**
     * (Non Javadoc)
     * <p>
     * Title: toString
     * </p>
     * <p>
     * Description:
     * </p>
     *
     * @return
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("GcloudTemplate [templateId=");
        builder.append(templateId);
        builder.append(", maxNumber=");
        builder.append(maxNumber);
        builder.append(", attributes=");
        builder.append(attributes);
        builder.append(", imageId=");
        builder.append(imageId);
        builder.append(", region=");
        builder.append(this.region);
        builder.append(", zone=");
        builder.append(this.zone);
        builder.append(", vpc=");
        builder.append(this.vpc);
        builder.append(", subnetId=");
        builder.append(subnetId);
        builder.append(", vmType=");
        builder.append(vmType);
        builder.append(", vmNumber=");
        builder.append(vmNumber);
        builder.append(", gpuType=");
        builder.append(gpuType);
        builder.append(", gpuNumber=");
        builder.append(gpuNumber);
        builder.append(", minCpuPlatform=");
        builder.append(minCpuPlatform);
        builder.append(", ttl=");
        builder.append(ttl);
        builder.append(", keyName=");
        builder.append(keyName);
        builder.append(", sgIds=");
        builder.append(sgIds);
        builder.append(", userData=");
        builder.append(userData);
        builder.append(", userDataObj=");
        builder.append(userDataObj);
        builder.append(", instanceTags=");
        builder.append(instanceTags);
        builder.append(", priority=");
        builder.append(this.priority);
        builder.append(", hostProject=");
        builder.append(this.hostProject);
        builder.append(", launchTemplateId=");
        builder.append(this.launchTemplateId);

        builder.append("]");
        return builder.toString();
    }

}
