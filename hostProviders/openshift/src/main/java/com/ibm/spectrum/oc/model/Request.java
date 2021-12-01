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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

public class Request {
    @JsonProperty("requestId")
    @JsonInclude(Include.NON_NULL)
    private String reqId;


    @JsonProperty("templateId")
    @JsonInclude(Include.NON_NULL)
    private String templateId;

    /**
     * Any additional string message the caller should know
     */
    @JsonProperty("message")
    @JsonInclude(Include.NON_NULL)
    private String msg;

    @JsonInclude(Include.NON_NULL)
    private String status;

    /**
     * creating time
     */
    @JsonInclude(Include.NON_NULL)
    private Long time;

    /**
     * value type is string. Host name of the machine that needs to return
     */
    @JsonProperty("machine")
    @JsonInclude(Include.NON_NULL)
    private String vmName;

    /**
     * value type is string. Host machineId of the machine that needs to return
     */
    @JsonInclude(Include.NON_NULL)
    private String machineId;

    /**
     * Grace Period in seconds
     */
    @JsonInclude(Include.NON_NULL)
    private Long gracePeriod;

    /**
     * TTL in minutes
     */
    @JsonInclude(Include.NON_NULL)
    private Long ttl;

    @JsonInclude(Include.NON_NULL)
    private List<Machine> machines;

    @JsonProperty("rc_account")
    @JsonInclude(Include.NON_NULL)
    private String tagValue;

    /**
    * <p>Title: </p>
    * <p>Description: </p>
    * @param reqId
    * @param msg
    * @param status
    * @param machines
    */
    public void update(Request req) {
        this.reqId = req.getReqId();
        this.msg = req.getMsg();
        this.status = req.getStatus();
        this.ttl = req.getTtl();
        this.time = req.getTime();
        this.gracePeriod = req.getGracePeriod();
        this.vmName = req.getVmName();
        this.machineId = req.getMachineId();
        this.machines = req.getMachines();
        this.tagValue = req.getTagValue();
    }

    public void hide() {
        this.time = null;
        this.ttl = null;
        this.gracePeriod = null;
        this.vmName = null;
        this.machineId = null;
        this.tagValue = null;
    }

    /**
    * @return reqId
    */
    public String getReqId() {
        return reqId;
    }

    /**
     * @param reqId the reqId to set
     */
    public void setReqId(String reqId) {
        this.reqId = reqId;
    }

    /**
    * @return msg
    */
    public String getMsg() {
        return msg;
    }

    /**
     * @param msg the msg to set
     */
    public void setMsg(String msg) {
        this.msg = msg;
    }

    /**
    * @return status
    */
    public String getStatus() {
        return status;
    }

    /**
     * @param status the status to set
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
    * @return machines
    */
    public List<Machine> getMachines() {
        return machines;
    }

    /**
     * @param machines the machines to set
     */
    public void setMachines(List<Machine> machines) {
        this.machines = machines;
    }

    /**
    * @return time
    */
    public Long getTime() {
        return time;
    }

    /**
     * @param time the time to set
     */
    public void setTime(Long time) {
        this.time = time;
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
    * @return pod Name
    */
    public String getVmName() {
        return vmName;
    }

    /**
     * @param podName
     */
    public void setVmName(String podName) {
        this.vmName = podName;
    }

    /**
    * @return pod Id
    */
    public String getMachineId() {
        return machineId;
    }

    /**
     * @param pod Id
     */
    public void setMachineId(String machineId) {
        this.machineId = machineId;
    }

    /**
    * @return gracePeriod
    */
    public Long getGracePeriod() {
        return gracePeriod;
    }

    /**
     * @param gracePeriod the gracePeriod to set
     */
    public void setGracePeriod(Long gracePeriod) {
        this.gracePeriod = gracePeriod;
    }

    /**
     * @return tagValue
     */
    public String getTagValue() {
        return tagValue;
    }

    /**
     * @param tagValue the tagValue to set
     */
    public void setTagValue(String tagValue) {
        this.tagValue = tagValue;
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
        builder.append("Request [reqId=");
        builder.append(reqId);
        builder.append(", msg=");
        builder.append(msg);
        builder.append(", status=");
        builder.append(status);
        builder.append(", ttl=");
        builder.append(ttl);
        builder.append(", machines=");
        builder.append(machines);
        builder.append(", podName=");
        builder.append(vmName);
        builder.append(", machineId=");
        builder.append(machineId);
        builder.append(", gracePeriod=");
        builder.append(gracePeriod);
        builder.append(", rc_account=");
        builder.append(tagValue);
        builder.append("]");
        return builder.toString();
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

}
