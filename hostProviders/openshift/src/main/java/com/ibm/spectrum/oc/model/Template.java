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
import java.util.List;

import net.sf.json.JSONObject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Template {
    private static Logger log = LogManager.getLogger(Template.class);
    private String templateId;

    private Integer maxNumber;

    private HashMap<String, List<String>> attributes;

    @JsonInclude(Include.NON_NULL)
    private String imageId;

    @JsonProperty("machineCount")
    @JsonInclude(Include.NON_NULL)
    private Integer numPods;

    @JsonProperty("vmType")
    @JsonInclude(Include.NON_NULL)
    private String podType;

    @JsonInclude(Include.NON_NULL)
    private Long ttl;

    @JsonInclude(Include.NON_NULL)
    private String keyName;


    @JsonInclude(Include.NON_NULL)
    private String userData;

    @JsonInclude(Include.NON_NULL)
    private String instanceTags;
    /**
     * Optional.
     */
    @JsonProperty("priority")
    @JsonInclude(Include.NON_NULL)
    private int priority;
    private HashMap<String, String> mountPaths;
    private HashMap<String, String> nodeSelectors;

    /**
     * <p>Title: </p>
     * <p>Description: </p>
     */
    public Template() {
    }

    /**
    * <p>Title: </p>
    * <p>Description: </p>
    * @param t
    */
    public Template(Template t) {
        this.templateId = t.getTemplateId();
        this.imageId = t.getImageId();
        this.numPods = t.getNumPods();
        this.podType = t.getPodType();
        this.ttl = t.getTtl();
        this.maxNumber = t.getMaxNumber();
        this.keyName = t.getKeyName();
        this.userData = t.getUserData();
        this.priority = t.getPriority();
    }

    public void hide() {
        this.numPods = null;
        this.ttl = null;
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
    * @return numPods
    */
    public Integer getNumPods() {
        return numPods;
    }


    /**
     * @param numPods the numPods to set
     */
    public void setNumPods(Integer numPods) {
        this.numPods = numPods;
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

    public String getPodType() {
        return podType;
    }

    public void setPodType(String podType) {
        this.podType = podType;
    }

    public String getKeyName() {
        return keyName;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    public String getUserData() {
        return userData;
    }

    public void setUserData(String userData) {
        this.userData = userData;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public HashMap<String, String> getMountPaths() {
        return mountPaths;
    }

    public void setMountPaths(JSONObject mountPaths) {
        try {
            this.mountPaths = new ObjectMapper().readValue(mountPaths.toString(), HashMap.class);
        } catch(Exception e) {
            log.error("Call service setMountPaths method error: ", e);
        }
    }

    public HashMap<String, String> getNodeSelectors() {
        return nodeSelectors;
    }

    public void setNodeSelectors(JSONObject nodeSelectors) {
        try {
            this.nodeSelectors =  new ObjectMapper().readValue(nodeSelectors.toString(), HashMap.class);
        } catch(Exception e) {
            log.error("Call service setNodeSelector method error: ", e);
        }
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
        builder.append("Template [templateId=");
        builder.append(templateId);
        builder.append(", maxNumber=");
        builder.append(maxNumber);
        builder.append(", attributes=");
        builder.append(attributes);
        builder.append(", imageId=");
        builder.append(imageId);
        builder.append(", numPods=");
        builder.append(numPods);
        builder.append(", userData=");
        builder.append(userData);
        builder.append(", priority=");
        builder.append(priority);
        builder.append(", keyName=");
        builder.append(keyName);
        builder.append(", ttl=");
        builder.append(ttl);
        builder.append(", podType=");
        builder.append(podType);
        builder.append(", instanceTags=");
        builder.append(instanceTags);
        builder.append(", mountPaths=");
        builder.append(mountPaths);
        builder.append(", nodeSelectors=");
        builder.append(nodeSelectors);
        builder.append("]");
        return builder.toString();
    }

}
