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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ibm.spectrum.cyclecloud.util.ProvUtil;

/**
* @Class Name : ProvTemplate
* @Description: Provider template
* @Author     : Yudong (Dom) Wang
* @Date       : 2019-5-31 9:46:29
* @Version    : V1.0
*/
public class ProvTemplate {
    /**
     * Required and value type is string. Unique id that can identify this
     * template in the host provider
     */
    @JsonProperty("templateId")
    private String tplId;

    /**
     * Required and value type is number. The max number of machine with this
     * template configuration can be provisioned. means there is no limit to
     * provision machine with this template
     */
    @JsonProperty("maxNumber")
    private Integer maxNum;

    /**
     * Required, dynamic available number of hosts
     */
    @JsonProperty("availableNumber")
    private Integer availableNum;

    /**
     * Required and type is string, represents the value type of this attribute,
     * it could be ‘string’ or ‘numeric’ , Required and type is string,
     * represents the actual value of this attribute
     */
    private HashMap<String, List<String>> attributes;

    private String nodeArray;

    @JsonProperty("vmType")
    private String mtype;

    @JsonProperty("machineCount")
    private Integer mcount;

    @JsonProperty("placementGroupName")
    private String pGrpName;

    @JsonProperty("instanceTags")
    private String tags;

    private String keyPairLocation;

    private String imageId;

    private String imageName;

    private String subnetId;

    private Integer priority;

    private String customScriptUri;

    private String userData;

    @JsonProperty("clusterInitSpecs")
    private Map<String, Object> initSpecs;

    @JsonProperty("skipModifyLocalResources")
    private Boolean skipModify;

    private Boolean autoScale;

    private Boolean headless;

    private String lsfEnvDir;

    private String lsfTop;

    @JsonProperty("numPlacementGroups")
    private Integer numPGroups;

    @JsonProperty("interruptible")
    private Boolean interruptible;

    @JsonProperty("maxPrice")
    private Double maxPrice;

    /**
    * @Title      : ProvTemplate
    * @Description: constructor
    * @Param      :
    */
    public ProvTemplate() {
    }

    /**
    * @Title      : ProvTemplate
    * @Description: constructor
    * @Param      : @param tpl
    */
    public ProvTemplate(ProvTemplate tpl) {
        this.tplId = tpl.getTplId();
        this.maxNum = tpl.getMaxNum();
        this.availableNum = tpl.getAvailableNum();
        this.attributes = tpl.getAttributes();
        this.nodeArray = tpl.getNodeArray();
        this.mtype = tpl.getMtype();
        this.mcount = tpl.getMcount();
        this.pGrpName = tpl.getpGrpName();
        this.tags = tpl.getTags();
        this.keyPairLocation = tpl.getKeyPairLocation();
        this.imageId = tpl.getImageId();
        this.imageName = (String) Optional.ofNullable(tpl.getImageName()).map(String::trim).orElse(null);
        this.subnetId = tpl.getSubnetId();
        this.priority = tpl.getPriority();
        this.customScriptUri = tpl.getCustomScriptUri();
        this.initSpecs = tpl.getInitSpecs();
        this.skipModify = tpl.getSkipModify();
        this.autoScale = tpl.getAutoScale();
        this.headless = tpl.getHeadless();
        this.lsfEnvDir  = tpl.getLsfEnvDir();
        this.lsfTop = tpl.getLsfTop();
        this.numPGroups = tpl.getNumPGroups();
        this.userData = tpl.getUserData();
        this.interruptible = tpl.getInterruptible();
        this.maxPrice = tpl.getMaxPrice();
    }

    public void hide() {
        this.initSpecs = null;
        this.skipModify = null;
        this.autoScale = null;
        this.headless = null;
        this.lsfEnvDir  = null;
        this.lsfTop = null;
        this.numPGroups = null;
    }

    public String getTplId() {
        return tplId;
    }

    public void setTplId(String tplId) {
        if (StringUtils.isBlank(this.tplId)) {
            this.tplId = tplId;
        }
    }

    public Integer getMaxNum() {
        return maxNum;
    }

    public void setMaxNum(Integer maxNum) {
        if (null == this.maxNum) {
            this.maxNum = maxNum;
        }
    }

    public Integer getAvailableNum() {
        return availableNum;
    }

    public void setAvailableNum(Integer availableNum) {
        if (null == this.availableNum) {
            this.availableNum = availableNum;
        }
    }

    public HashMap<String, List<String>> getAttributes() {
        return attributes;
    }

    public void setAttributes(HashMap<String, List<String>> attributes) {
        if (MapUtils.isEmpty(this.attributes)) {
            this.attributes = attributes;
        }
    }

    public String getNodeArray() {
        return nodeArray;
    }

    public void setNodeArray(String nodeArray) {
        if (StringUtils.isEmpty(this.nodeArray)) {
            this.nodeArray = nodeArray;
        }
    }

    public String getMtype() {
        return mtype;
    }

    public void setMtype(String mtype) {
        if (StringUtils.isEmpty(this.mtype)) {
            this.mtype = mtype;
        }
    }

    public Integer getMcount() {
        return mcount;
    }

    public void setMcount(Integer mcount) {
        if (null == this.mcount) {
            this.mcount = mcount;
        }
    }

    public String getpGrpName() {
        return pGrpName;
    }

    public void setpGrpName(String pGrpName) {
        if (StringUtils.isEmpty(this.pGrpName)) {
            this.pGrpName = pGrpName;
        }
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        if (StringUtils.isEmpty(this.tags)) {
            this.tags = tags;
        }
    }

    public String getKeyPairLocation() {
        return keyPairLocation;
    }

    public void setKeyPairLocation(String keyPairLocation) {
        if (StringUtils.isEmpty(this.keyPairLocation)) {
            this.keyPairLocation = keyPairLocation;
        }
    }

    public String getImageId() {
        return imageId;
    }

    public void setImageId(String imageId) {
        if (StringUtils.isEmpty(this.imageId)) {
            this.imageId = imageId;
        }
    }

    public String getImageName() {
        return (String) Optional.ofNullable(imageName).map(String::trim).orElse(null);
    }

    public void setImageName(String imageName) {
        if (StringUtils.isEmpty(this.imageName)) {
            this.imageName = imageName.trim();
        }
    }

    public String getSubnetId() {
        return subnetId;
    }

    public void setSubnetId(String subnetId) {
        if (StringUtils.isEmpty(this.subnetId)) {
            this.subnetId = subnetId;
        }
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        if (null == this.priority) {
            this.priority = priority;
        }
    }

    public String getCustomScriptUri() {
        return customScriptUri;
    }

    public void setCustomScriptUri(String customScriptUri) {
        if (StringUtils.isEmpty(this.customScriptUri)) {
            this.customScriptUri = customScriptUri;
        }
    }

    public Map<String, Object> getInitSpecs() {
        return initSpecs;
    }

    public void setInitSpecs(Map<String, Object> initSpecs) {
        if (MapUtils.isEmpty(this.initSpecs)) {
            this.initSpecs = initSpecs;
        }
    }

    public Boolean getSkipModify() {
        return skipModify;
    }

    public void setSkipModify(Boolean skipModify) {
        if (null == this.skipModify) {
            this.skipModify = skipModify;
        }
    }

    public Boolean getAutoScale() {
        return autoScale;
    }

    public void setAutoScale(Boolean autoScale) {
        if (null == this.autoScale) {
            this.autoScale = autoScale;
        }
    }

    public Boolean getHeadless() {
        return headless;
    }

    public void setHeadless(Boolean headless) {
        if (null == this.headless) {
            this.headless = headless;
        }
    }

    public String getLsfEnvDir() {
        return lsfEnvDir;
    }

    public void setLsfEnvDir(String lsfEnvDir) {
        if (StringUtils.isEmpty(this.lsfEnvDir)) {
            this.lsfEnvDir = lsfEnvDir;
        }
    }

    public String getLsfTop() {
        return lsfTop;
    }

    public void setLsfTop(String lsfTop) {
        if (StringUtils.isEmpty(this.lsfTop)) {
            this.lsfTop = lsfTop;
        }
    }

    public Integer getNumPGroups() {
        return numPGroups;
    }

    public void setNumPGroups(Integer numPGroups) {
        if (null == this.numPGroups) {
            this.numPGroups = numPGroups;
        }
    }

    public String getUserData() {
        return userData;
    }

    public void setUserData(String userData) {
        if (StringUtils.isEmpty(this.userData)) {
            this.userData = userData;
        }
    }

    public Boolean getInterruptible() {
        return Optional.ofNullable(interruptible).orElse(Boolean.FALSE);
    }

    public void setInterruptible(Boolean interruptible) {
        if (null == this.interruptible) {
            this.interruptible = interruptible;
        }
    }
    public Double getMaxPrice() {
        return Optional.ofNullable(maxPrice).orElse(-1.0);
    }

    public void setMaxPrice(Double maxPrice) {
        if (null == this.maxPrice) {
            this.maxPrice = maxPrice;
        }
    }

    @Override
    public String toString() {
        return ProvUtil.toJsonStr(this);
    }
}
