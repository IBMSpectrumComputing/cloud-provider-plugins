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
* @ClassName: AwsTemplate
* @Description: The template of AWS EC2 virtual machine
* @author xawangyd
* @date Jan 26, 2016 3:31:00 PM
* @version 1.0
*/
public class AwsTemplate {
    private static Logger log = LogManager.getLogger(AwsTemplate.class);
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


    /**
     * [Required for Spot Instances]. Indicates the bid price for a spot instance
     *
     */
    @JsonInclude(Include.NON_NULL)
    private Float spotPrice;


    /**
     * [Optional] The duration needed for the Spot Block. The value expected
     * should range from 1-6
     */
    @JsonInclude(Include.NON_NULL)
    private Integer durationInHours;


    /**
     * [Required for Spot Instances with no instanceProfile]. Indicates the AWS IAM instance profile to be used
     * when requesting instances
     */
    @JsonInclude(Include.NON_NULL)
    private String fleetRole;



    /**
     * [Optional for Spot Instances]. Duration of Spot Instance request validity.
     * Format : HH:MM
     * Default value: {AwsConst.REQUEST_VALIDITY_HOURS}: {AwsConst.REQUEST_VALIDITY_MINUTES}
     */
    @JsonInclude(Include.NON_NULL)
    private String requestValidity;



    private Date requestValidityStartTime;

    private Date requestValidityEndTime;

    /**
     * [Optional for Spot Instances]. Indicates the Allocation strategy when
     * allocating from difference availability zones * Possible Values :
     * lowestPrice, diversified, capacityOptimized, Default Value: capacityOptimized
     */
    @JsonInclude(Include.NON_NULL)
    private String allocationStrategy;


    @JsonInclude(Include.NON_NULL)
    private String imageId;

    @JsonInclude(Include.NON_NULL)
    private String subnetId;

    @JsonInclude(Include.NON_NULL)
    private String vmType;

    @JsonProperty("machineCount")
    @JsonInclude(Include.NON_NULL)
    private Integer vmNumber;

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
    private AwsUserData userDataObj;

    @JsonProperty("placementGroupName")
    @JsonInclude(Include.NON_NULL)
    private String pGrpName;

    @JsonInclude(Include.NON_NULL)
    private String instanceTags;

    /**
     * Optional. Indicates the AWS IAM instance profile to be used
     * when requesting instances
     */
    @JsonInclude(Include.NON_NULL)
    private String instanceProfile;

    /**
     * Optional.
     */
    @JsonProperty("ebsOptimized")
    @JsonInclude(Include.NON_NULL)
    private boolean ebsOptimized;

    /**
     * Optional.
     */
    @JsonProperty("priority")
    @JsonInclude(Include.NON_NULL)
    private int priority;

    /**
     * Optional.
     */
    @JsonProperty("tenancy")
    @JsonInclude(Include.NON_NULL)
    private String tenancy;

    /**
     * Optional.
     */
    @JsonProperty("interfaceType")
    @JsonInclude(Include.NON_NULL)
    private String interfaceType;

    /**
     * Optional.
     */
    @JsonProperty("launchTemplateId")
    @JsonInclude(Include.NON_NULL)
    private String launchTemplateId;

    /**
     * Optional.
     */
    @JsonProperty("launchTemplateVersion")
    @JsonInclude(Include.NON_NULL)
    private String launchTemplateVersion;

    @JsonInclude(Include.NON_NULL)
    private Double marketSpotPrice;

    /**
     * <p>Title: </p>
     * <p>Description: </p>
     */
    public AwsTemplate() {
    }

    /**
    * <p>Title: </p>
    * <p>Description: </p>
    * @param t
    */
    public AwsTemplate(AwsTemplate t) {
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
        this.pGrpName = t.getPGrpName();
        this.instanceProfile = t.getInstanceProfile();
        this.ebsOptimized = t.getEbsOptimized();
        this.priority = t.getPriority();
        this.tenancy = t.getTenancy();
        this.interfaceType = t.getInterfaceType();
        this.launchTemplateId = t.getLaunchTemplateId();
        this.launchTemplateVersion = t.getLaunchTemplateVersion();
        this.marketSpotPrice = t.getMarketSpotPrice();
    }

    public void hide() {
        //this.imageId = null;
        this.subnetId = null;
        //this.vmType = null;
        this.vmNumber = null;
        this.ttl = null;
        this.keyName = null;
        this.sgIds = null;
        //this.userData = null;
        this.pGrpName = null;
        this.instanceProfile = null;
        this.ebsOptimized = false;
        this.tenancy = "default";
    }

    /**
    * @return templateId
    */
    public String getTemplateId() {
        return templateId;
    }

    /**
     * @param templateId the templateId to set
     */
    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    /**
    * @return imageId
    */
    public String getImageId() {
        return imageId;
    }

    /**
     * @param imageId the imageId to set
     */
    public void setImageId(String imageId) {
        this.imageId = imageId;
    }

    /**
    * @return subnetId
    */
    public String getSubnetId() {
        return subnetId;
    }

    /**
     * @param subnetId the subnetId to set
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
     * @param vmType the vmType to set
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
     * @param vmNumber the vmNumber to set
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
     * @param keyName the keyName to set
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
     * @param maxNumber the maxNumber to set
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
     * @param sgIds the sgIds to set
     */
    public void setSgIds(List<String> sgIds) {
        this.sgIds = sgIds;
    }

    /**
     * @return userData
     */
    public AwsUserData getUserDataObj() {
        return userDataObj;
    }

    /**
     * @param userData the userData to set
     */
    public void setUserDataObj(AwsUserData userDataObj) {
        this.userDataObj = userDataObj;
    }

    /**
     * @return userData
     */
    public String getUserData() {
        return userData;
    }

    /**
     * @param userData the userData to set
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
     * @param instanceTags the instanceTags to set
     */
    public void setInstanceTags(String instanceTags) {
        this.instanceTags = instanceTags;
    }


    public void setEbsOptimized(boolean ebsOptimized) {
        this.ebsOptimized = ebsOptimized;
    }

    public boolean getEbsOptimized() {
        return ebsOptimized;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public int getPriority() {
        return priority;
    }

    public void setTenancy(String tenancy) {
        this.tenancy = tenancy;
    }

    public String getTenancy() {
        return tenancy;
    }

    /**
     * @param attributes the attributes to set
     */
    public HashMap<String, List<String>> getAttributes() {
        return attributes;
    }

    /**
     * @param attributes the attributes to set
     */
    public void setAttributes(JSONObject attributes) {
        try {
            this.attributes = new ObjectMapper().readValue(attributes.toString(), HashMap.class);
        } catch(Exception e) {
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
     * @param ttl the ttl to set
     */
    public void setTtl(Long ttl) {
        this.ttl = ttl;
    }

    /**
     * @return pGrpName
     */
    public String getPGrpName() {
        return pGrpName;
    }

    /**
     * @param pGrpName the pGrpName to set
     */
    public void setPpGrpName(String pGrpName) {
        this.pGrpName = pGrpName;
    }

    /**
    * @return instanceProfile
    */
    public String getInstanceProfile() {
        return instanceProfile;
    }

    /**
     * @param instanceProfile the instanceProfile to set
     */
    public void setInstanceProfile(String instanceProfile) {
        this.instanceProfile = instanceProfile;
    }

    /**
     * @return interfaceType
     */
    public String getInterfaceType() {
        return interfaceType;
    }

    /**
     * @param interfaceType the interfaceType to set
     */
    public void setInterfaceType(String interfaceType) {
        this.interfaceType = interfaceType;
    }

    /**
     * @return launchTemplateId
     */
    public String getLaunchTemplateId() {
        return launchTemplateId;
    }

    /**
     * @param launchTemplateId the launchTemplateId to set
     */
    public void setLaunchTemplateId(String launchTemplateId) {
        this.launchTemplateId = launchTemplateId;
    }

    /**
     * @return launchTemplateVersion
     */
    public String getLaunchTemplateVersion() {
        return launchTemplateVersion;
    }

    /**
     * @param launchTemplateVersion the launchTemplateVersion to set
     */
    public void setLaunchTemplateVersion(String launchTemplateVersion) {
        this.launchTemplateVersion = launchTemplateVersion;
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
        builder.append("AwsTemplate [templateId=");
        builder.append(templateId);
        builder.append(", maxNumber=");
        builder.append(maxNumber);
        builder.append(", attributes=");
        builder.append(attributes);
        builder.append(", imageId=");
        builder.append(imageId);
        builder.append(", subnetId=");
        builder.append(subnetId);
        builder.append(", vmType=");
        builder.append(vmType);
        builder.append(", vmNumber=");
        builder.append(vmNumber);
        builder.append(", ttl=");
        builder.append(ttl);
        builder.append(", keyName=");
        builder.append(keyName);
        builder.append(", sgIds=");
        builder.append(sgIds);
        builder.append(", placementGroupName=");
        builder.append(pGrpName);
        builder.append(", userData=");
        builder.append(userData);
        builder.append(", userDataObj=");
        builder.append(userDataObj);
        builder.append(", instanceTags=");
        builder.append(instanceTags);
        builder.append(", instanceProfile=");
        builder.append(instanceProfile);
        builder.append(", fleetRole=");
        builder.append(this.fleetRole);
        builder.append(", requestValidityEndTime=");
        builder.append(this.requestValidityEndTime);
        builder.append(", requestValidityStartTime=");
        builder.append(this.requestValidityStartTime);
        builder.append(", spotPrice=");
        builder.append(this.spotPrice);
        builder.append(", ebsOptimized=");
        builder.append(this.ebsOptimized);
        builder.append(", priority=");
        builder.append(this.priority);
        builder.append(", tenancy=");
        builder.append(this.tenancy);
        builder.append(", interfaceType=");
        builder.append(this.interfaceType);
        builder.append(", launchTemplateId=");
        builder.append(this.launchTemplateId);
        builder.append(", launchTemplateVersion=");
        builder.append(this.launchTemplateVersion);
        builder.append(", marketSpotPrice=");
        builder.append(this.marketSpotPrice);
        builder.append("]");
        return builder.toString();
    }

    public Integer getDurationInHours() {
        return durationInHours;
    }

    public void setDurationInHours(Integer durationInHours) {
        this.durationInHours = durationInHours;
    }

    public String getFleetRole() {
        return fleetRole;
    }

    public void setFleetRole(String fleetRole) {
        this.fleetRole = fleetRole;
    }

    public Float getSpotPrice() {
        return spotPrice;
    }

    public void setSpotPrice(Float spotPrice) {
        this.spotPrice = spotPrice;
    }

    public String getRequestValidity() {
        return requestValidity;
    }

    public void setRequestValidity(String requestValidity) {
        this.requestValidity = requestValidity;
    }

    public String getAllocationStrategy() {
        return allocationStrategy;
    }

    public void setAllocationStrategy(String allocationStrategy) {
        this.allocationStrategy = allocationStrategy;
    }

    public Date getRequestValidityStartTime() {
        return requestValidityStartTime;
    }

    public void setRequestValidityStartTime(Date requestValidityStartTime) {
        this.requestValidityStartTime = requestValidityStartTime;
    }

    public Date getRequestValidityEndTime() {
        return requestValidityEndTime;
    }

    public void setRequestValidityEndTime(Date requestValidityEndTime) {
        this.requestValidityEndTime = requestValidityEndTime;
    }

    public Double getMarketSpotPrice() {
        return marketSpotPrice;
    }

    public void setMarketSpotPrice(Double marketSpotPrice) {
        this.marketSpotPrice = marketSpotPrice;
    }

}
