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
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ibm.spectrum.cyclecloud.model.ProvTemplate;
import com.ibm.spectrum.cyclecloud.util.ProvUtil;

/**
* @Class Name : NodeAttribute
* @Description: Node attribute
* @Author     : Yudong (Dom) Wang
* @Date       : 2019-6-21 15:44:22
* @Version    : V1.0
*/
public class NodeAttribute {
    @JsonProperty("SubnetId")
    private String subnetId;

    @JsonProperty("Region")
    private String region;

    @JsonProperty("KeypairLocation")
    private String keypairLocation;

    @JsonProperty("ImageId")
    private String imageId;

    @JsonProperty("ImageName")
    private String imageName;

    @JsonProperty("Tags")
    private Map<String, Object> tags;

    @JsonProperty("Configuration")
    private NodeConfig config;

    @JsonProperty("ClusterInitSpecs")
    private Map<String, Object> initSpecs;

    @JsonProperty("Interruptible")
    private Boolean interruptible;

    @JsonProperty("MaxPrice")
    private Double maxPrice;

    /**
    * @Title      : NodeAttribute
    * @Description: constructor
    * @Param      :
    */
    public NodeAttribute() {
    }

    /**
    * @Title      : NodeAttribute
    * @Description: constructor
    * @Param      : @param tpl
    */
    public NodeAttribute(ProvTemplate tpl) {
        this.subnetId = tpl.getSubnetId();
        this.keypairLocation = tpl.getKeyPairLocation();
        this.imageId = tpl.getImageId();
        this.imageName = tpl.getImageName();
        this.initSpecs = tpl.getInitSpecs();
        this.interruptible = tpl.getInterruptible();
        this.maxPrice = tpl.getMaxPrice();
    }

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

    public String getKeypairLocation() {
        return keypairLocation;
    }

    public void setKeypairLocation(String keypairLocation) {
        this.keypairLocation = keypairLocation;
    }

    public String getImageId() {
        return imageId;
    }

    public void setImageId(String imageId) {
        this.imageId = imageId;
    }

    public String getImageName() {
        return imageName;
    }

    public void setImageName(String imageName) {
        this.imageName = imageName;
    }

    public Map<String, Object> getTags() {
        return tags;
    }

    public void setTags(Map<String, Object> tags) {
        this.tags = tags;
    }

    public NodeConfig getConfig() {
        return config;
    }

    public void setConfig(NodeConfig config) {
        this.config = config;
    }

    public Map<String, Object> getInitSpecs() {
        return initSpecs;
    }

    public void setInitSpecs(Map<String, Object> initSpecs) {
        this.initSpecs = initSpecs;
    }

    public Boolean getInterruptible() {
        return this.interruptible;
    }

    public void setInterruptible(Boolean interruptible) {
        this.interruptible = Optional.ofNullable(interruptible).orElse(Boolean.FALSE);
    }

    public Double getMaxPrice() {
        return this.maxPrice;
    }

    public void setMaxPrice(Double maxPrice) {
        this.maxPrice = maxPrice;
    }

    @Override
    public String toString() {
        return ProvUtil.toJsonStr(this);
    }
}
