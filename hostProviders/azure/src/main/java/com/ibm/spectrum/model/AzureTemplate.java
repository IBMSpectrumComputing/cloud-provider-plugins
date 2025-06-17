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

import java.util.*;
import net.sf.json.JSONObject;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
* @ClassName: AzureTemplate
* @Description: The template of Azure EC2 virtual machine
* @author xawangyd
* @date Jan 26, 2016 3:31:00 PM
* @version 1.0
*/
public class AzureTemplate {
    private static Logger log = LogManager.getLogger(AzureTemplate.class);
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
    private String imageId;

    @JsonInclude(Include.NON_NULL)
    private String imageName;

    @JsonInclude(Include.NON_NULL)
    private String storageAccountType;

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


    @JsonProperty("customScriptUri")
    @JsonInclude(Include.NON_NULL)
    private String userDataUri;

    @JsonInclude(Include.NON_NULL)
    private String userDataExecCmd;

    @JsonProperty("securityGroup")
    @JsonInclude(Include.NON_NULL)
    private String sgId;

    @JsonInclude(Include.NON_NULL)
    private AzureUserData userDataObj;

    @JsonProperty("placementGroup")
    @JsonInclude(Include.NON_NULL)
    private String pGrpName;

    @JsonInclude(Include.NON_NULL)
    private String instanceTags;

    @JsonInclude(Include.NON_NULL)
    private String resourceGroup;

    @JsonInclude(Include.NON_NULL)
    private String virtualNetwork;

    @JsonProperty("subnet")
    @JsonInclude(Include.NON_NULL)
    private String subnetName;

    @JsonInclude(Include.NON_NULL)
    private String rootUserName;

    @JsonInclude(Include.NON_NULL)
    private String sshPubKeyFile;

    @JsonInclude(Include.NON_NULL)
    private Boolean withPublicIP;

    @JsonInclude(Include.NON_NULL)
    private String availabilitySet;

    @JsonProperty("priority")
    @JsonInclude(Include.NON_NULL)
    private int priority;

    public String getAvailabilitySet() {
        return availabilitySet;
    }

    public void setAvailabilitySet(String availabilitySet) {
        this.availabilitySet = availabilitySet;
    }

    public Boolean getWithPublicIP() {
        return withPublicIP;
    }

    public void setWithPublicIP(Boolean withPublicIP) {
        this.withPublicIP = withPublicIP;
    }


    public String getStorageAccountType() {
        return storageAccountType;
    }

    public void setStorageAccountType(String storageAccountType) {
        this.storageAccountType = storageAccountType;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public int getPriority() {
        return priority;
    }

    /*
     * TODO remove storageAccount
     * use managed disk to create VM will not need to specify storageAccount
     */
    @JsonInclude(Include.NON_NULL)
    private String storageAccount;

    public String getStorageAccount() {
        return storageAccount;
    }

    public void setStorageAccount(String storageAccount) {
        this.storageAccount = storageAccount;
    }

    /**
    * <p>Title: </p>
    * <p>Description: </p>
    */
    public AzureTemplate() {
    }

    /**
    * <p>Title: </p>
    * <p>Description: </p>
    * @param t
    */
    public AzureTemplate(AzureTemplate t) {
        this.templateId = t.getTemplateId();
        this.imageId = t.getImageId();
        this.imageName = t.getImageName();
        this.storageAccountType = t.storageAccountType;
        this.vmType = t.getVmType();
        this.vmNumber = t.getVmNumber();
        this.ttl = t.getTtl();
        this.keyName = t.getKeyName();
        this.maxNumber = t.getMaxNumber();
        this.sgId = t.getSgId();
        this.userData =  t.getUserData();
        this.userDataUri = t.getUserDataUri();
        this.userDataExecCmd = t.getUserDataExecCmd();
        this.userDataObj = t.getUserDataObj();
        this.pGrpName = t.getPGrpName();
        this.resourceGroup = t.getResourceGroup();
        this.virtualNetwork =  t.getVirtualNetwork();
        this.subnetName = t.getSubnetName();
        this.rootUserName =  t.getRootUserName();
        this.sshPubKeyFile = t.getSshPubKeyFile();
        this.storageAccount =  t.getStorageAccount();
        this.withPublicIP = t.getWithPublicIP();
        this.availabilitySet = t.getAvailabilitySet();
        this.priority = t.getPriority();
    }

    public void hide() {
        //this.imageId = null;
        this.storageAccountType = null;
        //this.vmType = null;
        this.vmNumber = null;
        this.ttl = null;
        this.keyName = null;
        this.sgId = null;
        this.userData = null;
        this.userDataUri = null;
        this.userDataExecCmd = null;
        this.pGrpName = null;
        this.resourceGroup =  null;
        this.virtualNetwork =  null;
        this.subnetName = null;
        this.rootUserName = null;
        this.sshPubKeyFile = null;
        this.storageAccount = null;
        this.withPublicIP = null;
        this.availabilitySet = null;
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
    * @return imageName
    */
    public String getImageName() {
        return imageName;
    }

    /**
     * @param imageName the imageName to set
     */
    public void setImageName(String imageName) {
        this.imageName = imageName;
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
    public String getSgId() {
        return sgId;
    }

    /**
     * @param sgIds the sgIds to set
     */
    public void setSgId(String sgId) {
        this.sgId = sgId;
    }

    /**
     * @return userData
     */
    public AzureUserData getUserDataObj() {
        return userDataObj;
    }

    /**
     * @param userData the userData to set
     */
    public void setUserDataObj(AzureUserData userDataObj) {
        this.userDataObj = userDataObj;
    }

    public String getUserData() {
        return userData;
    }

    public void setUserData(String userData) {
        this.userData = userData;
    }


    /**
     * @return userDataUri
     */
    public String getUserDataUri() {
        return userDataUri;
    }

    /**
     * @param userDataUri the userDataUri to set
     */
    public void setUserDataUri(String userDataUri) {
        this.userDataUri = userDataUri;
    }

    /**
     * @return userData
     */
    public String getUserDataExecCmd() {
        return userDataExecCmd;
    }

    /**
     * @param userData the userData to set
     */
    public void setUserDataExecCmd(String userDataExecCmd) {
        this.userDataExecCmd = userDataExecCmd;
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

    public String getResourceGroup() {
        return resourceGroup;
    }

    public void setResourceGroup(String resourcegroup) {
        this.resourceGroup = resourcegroup;
    }

    public String getVirtualNetwork() {
        return virtualNetwork;
    }

    public void setVirtualNetwork(String virtualNetwork) {
        this.virtualNetwork = virtualNetwork;
    }


    public String getSubnetName() {
        return subnetName;
    }

    public void setSubnetName(String subnetName) {
        this.subnetName = subnetName;
    }

    public String getRootUserName() {
        return rootUserName;
    }

    public void setRootUserName(String rootUserName) {
        this.rootUserName = rootUserName;
    }

    public String getSshPubKeyFile() {
        return sshPubKeyFile;
    }

    public void setSshPubKeyFile(String sshPublicKey) {
        this.sshPubKeyFile = sshPublicKey;
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
        builder.append("AzureTemplate [templateId=");
        builder.append(templateId);
        builder.append(", maxNumber=");
        builder.append(maxNumber);
        builder.append(", attributes=");
        builder.append(attributes);
        builder.append(", imageId=");
        builder.append(imageId);
        builder.append(", imageName=");
        builder.append(imageName);
        builder.append(", storageAccountType=");
        builder.append(storageAccountType);
        builder.append(", vmType=");
        builder.append(vmType);
        builder.append(", vmNumber=");
        builder.append(vmNumber);
        builder.append(", ttl=");
        builder.append(ttl);
        builder.append(", keyName=");
        builder.append(keyName);
        builder.append(", sgId=");
        builder.append(sgId);
        builder.append(", placementGroupName=");
        builder.append(pGrpName);
        builder.append(", resourcegroup=");
        builder.append(resourceGroup);
        builder.append(", storageAccount=");
        builder.append(storageAccount);
        builder.append(", withPublicIP=");
        builder.append(withPublicIP);
        builder.append(", virtualNetwork=");
        builder.append(virtualNetwork);
        builder.append(", subnetName=");
        builder.append(subnetName);
        builder.append(", availabilitySet=");
        builder.append(availabilitySet);
        builder.append(", rootUserName=");
        builder.append(rootUserName);
        builder.append(", sshPubKeyFile=");
        builder.append(sshPubKeyFile);
        builder.append(", userData=");
        builder.append(userData);
        builder.append(", userDataUri=");
        builder.append(userDataUri);
        builder.append(", userDataExecCmd=");
        builder.append(userDataExecCmd);
        builder.append(", userDataObj=");
        builder.append(userDataObj);
        builder.append(", instanceTags=");
        builder.append(instanceTags);
        builder.append(", priority=");
        builder.append(this.priority);
        builder.append("]");
        return builder.toString();
    }

}
