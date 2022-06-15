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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
* @ClassName: AwsRequest
* @Description: The request from LSF host factory
* @author xawangyd
* @date Jan 26, 2016 3:29:08 PM
* @version 1.0
*/
public class AwsRequest {
    /**
     * Required and value type is string. Unique id to identify this request in
     * the host provider
     */
    @JsonProperty("requestId")
    @JsonInclude(Include.NON_NULL)
    private String reqId;


    /**
     * Value type is string. Template Id used for this request
     */
    @JsonProperty("templateId")
    @JsonInclude(Include.NON_NULL)
    private String templateId;

    /**
     * Any additional string message the caller should know
     */
    @JsonProperty("message")
    @JsonInclude(Include.NON_NULL)
    private String msg;

    /**
     * Required and value type is string. Status of this request and possible
     * value could be 'running', 'complete' or 'complete_with_error'. You should
     * check the machines info if there are any when it is 'complete' or
     * 'complete_with_error'
     */
    @JsonInclude(Include.NON_NULL)
    private String status;

    /**
     * value type is string. Host name of the machine that needs to return
     */
    @JsonProperty("machine")
    @JsonInclude(Include.NON_NULL)
    private String vmName;

    /**
     * value type is string. Host machineId of the  machine that needs to return
     */
    @JsonInclude(Include.NON_NULL)
    private String machineId;

    /**
     * value type is number. Indicator of how soon this machine should be returned,
     * and this is in seconds
     */
    @JsonInclude(Include.NON_NULL)
    private Long gracePeriod;

    /**
     * If an instance is in the cluster for the number of minutes, we will
     * terminate it.
     */
    @JsonInclude(Include.NON_NULL)
    private Long ttl;

    /**
     * creating time
     */
    @JsonInclude(Include.NON_NULL)
    private Long time;

    @JsonInclude(Include.NON_NULL)
    private List<AwsMachine> machines;

    @JsonProperty("rc_account")
    @JsonInclude(Include.NON_NULL)
    private String tagValue;



    /**
     * Type of host allocation: onDemand, spot
     * Default is onDemand
     */
    @JsonProperty("hostAllocationType")
    @JsonInclude(Include.NON_NULL)
    private String hostAllocationType;
    
    /**
     * Type of EC2 fleet request: instant, request
     */
    @JsonProperty("fleetType")
    @JsonInclude(Include.NON_NULL)
    private String fleetType;
    

    /**
    * <p>Title: </p>
    * <p>Description: </p>
    * @param reqId
    * @param msg
    * @param status
    * @param machines
    */
    public void update(AwsRequest req) {
        this.reqId = req.getReqId();
        this.msg = req.getMsg();
        this.status = req.getStatus();
        this.time = req.getTime();
        this.ttl = req.getTtl();
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
    public List<AwsMachine> getMachines() {
        return machines;
    }

    /**
     * @param machines the machines to set
     */
    public void setMachines(List<AwsMachine> machines) {
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
    * @return vmName
    */
    public String getVmName() {
        return vmName;
    }

    /**
     * @param vmName the vmName to set
     */
    public void setVmName(String vmName) {
        this.vmName = vmName;
    }

    /**
    * @return machineId
    */
    public String getMachineId() {
        return machineId;
    }

    /**
     * @param machineId the machineId to set
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
        builder.append("AwsRequest [reqId=");
        builder.append(reqId);
        builder.append(", msg=");
        builder.append(msg);
        builder.append(", status=");
        builder.append(status);
        builder.append(", ttl=");
        builder.append(ttl);
        builder.append(", time=");
        builder.append(time);
        builder.append(", machines=");
        builder.append(machines);
        builder.append(", vmName=");
        builder.append(vmName);
        builder.append(", machineId=");
        builder.append(machineId);
        builder.append(", gracePeriod=");
        builder.append(gracePeriod);
        builder.append(", rc_account=");
        builder.append(tagValue);
        builder.append(", allocationtype=");
        builder.append(hostAllocationType);
        builder.append(", fleetType=");
        builder.append(fleetType);
        builder.append("]");
        return builder.toString();
    }

    public String getHostAllocationType() {
        return hostAllocationType;
    }

    public void setHostAllocationType(String hostAllocationType) {
        this.hostAllocationType = hostAllocationType;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

	public String getFleetType() {
		return fleetType;
	}

	public void setFleetType(String fleetType) {
		this.fleetType = fleetType;
	}

}
