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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ibm.spectrum.util.StringUtils;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
* @ClassName: AzureMachine
* @Description: Virtual Machine
* @author xawangyd
* @date Jan 26, 2016 3:27:14 PM
* @version 1.0
*/
public class AzureMachine {
    @JsonInclude(Include.NON_NULL)
    private String machineId;

    /**
     * Required and value type is string. Hostname of the machine
     */
    @JsonInclude(Include.NON_NULL)
    private String name;

    @JsonProperty("requestId")
    @JsonInclude(Include.NON_NULL)
    private String reqId;

    @JsonProperty("returnId")
    @JsonInclude(Include.NON_NULL)
    private String retId;

    @JsonInclude(Include.NON_NULL)
    private String template;

    /**
     * Required and value type is string. The current status of the particular
     * request related to this machine. Possible value could be "executing",
     * "fail", "succeed". For example, call requestMachines with templateId and
     * machineCount 3, and then call getRequestStatus to check the status of
     * this request, we should finally get 3 machines with "succeed" result, if
     * any machine is missing or the status is not correct, that machine is not
     * useable.
     */
    @JsonInclude(Include.NON_NULL)
    private String result;

    /**
     * Required if the "result" is "fail". Any additional message for the
     * request status of this machine
     */
    @JsonProperty("message")
    @JsonInclude(Include.NON_NULL)
    private String msg;

    @JsonInclude(Include.NON_NULL)
    private String status;

    @JsonInclude(Include.NON_NULL)
    private String privateIpAddress;

    @JsonInclude(Include.NON_NULL)
    private String publicIpAddress;

    @JsonInclude(Include.NON_NULL)
    private String publicDnsName;

    @JsonInclude(Include.NON_NULL)
    private String rcAccount;

    @JsonProperty("launchtime")
    @JsonInclude(Include.NON_NULL)
    private Long launchtime;

    @JsonInclude(Include.NON_NULL)
    private String resourceGroup;

    public void hide() {
        this.reqId = null;
        this.retId = null;
        this.template = null;
    }

    /**
    * <p>Title: </p>
    * <p>Description: </p>
    * @param m
    */
    public void update(AzureMachine m) {
        this.machineId = m.getMachineId();
        this.name = m.getName();
        this.reqId = m.getReqId();
        this.retId = m.getRetId();
        this.template = m.getTemplate();
        this.result = m.getResult();
        this.msg = m.getMsg();
        this.status = m.getStatus();
        this.privateIpAddress = m.getPrivateIpAddress();
        this.publicIpAddress = m.getPublicIpAddress();
        this.publicDnsName = m.getPublicDnsName();
        this.rcAccount = m.getRcAccount();
        this.launchtime = m.getLaunchtime();
        this.resourceGroup = m.getResourceGroup();
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
     * Azure machineId example:
     * /subscriptions/a77c17c3-5122-4984-81d7-24a49acf5280/resourceGroups/vmrg7213626197aa63/providers/Microsoft.Compute/virtualMachines/host-10-0-2-4
     *
     * Map Azure machineId to the machineId in ebrokderd
     * Because bhosts -rc use mosquitto, when mosquitto_publish
     * the machineId cannot start with /
     */
    public String mapMahinedIdToEbrokderd() {
        if (!StringUtils.isNullOrEmpty(machineId)) {
            machineId = machineId.replace('/', '_');
        }
        return machineId;
    }

    public String mapEbrokerdBackToMahinedId() {
        if (!StringUtils.isNullOrEmpty(machineId)) {
            if (machineId.startsWith("_")) {
                machineId = machineId.replace('_', '/');
            }
        }
        return machineId;
    }


    /**
    * @return name
    */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
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
    * @return retId
    */
    public String getRetId() {
        return retId;
    }

    /**
     * @param retId the retId to set
     */
    public void setRetId(String retId) {
        this.retId = retId;
    }

    /**
    * @return template
    */
    public String getTemplate() {
        return template;
    }

    /**
     * @param template the template to set
     */
    public void setTemplate(String template) {
        this.template = template;
    }

    /**
    * @return result
    */
    public String getResult() {
        return result;
    }

    /**
     * @param result, result to set
     */
    public void setResult(String result) {
        this.result = result;
    }

    /**
    * @return msg
    */
    public String getMsg() {
        return msg;
    }

    /**
     * @param msg,  msg to set
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
     * @param status, status to set
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * @return privateIpAddress
     */
    public String getPrivateIpAddress() {
        return privateIpAddress;
    }

    /**
     * @param privateIpAddress, privateIpAddress to set
     */
    public void setPrivateIpAddress(String privateIpAddress) {
        this.privateIpAddress = privateIpAddress;
    }

    /**
     * @return publicIpAddress
    */
    public String getPublicIpAddress() {
        return publicIpAddress;
    }

    /**
     * @param publicIpAddress, publicIpAddress to set
     */
    public void setPublicIpAddress(String publicIpAddress) {
        this.publicIpAddress = publicIpAddress;
    }

    /**
    * @return the publicDnsName
    */
    public String getPublicDnsName() {
        return publicDnsName;
    }

    /**
     * @param publicDnsName the publicDnsName to set
     */
    public void setPublicDnsName(String publicDnsName) {
        this.publicDnsName = publicDnsName;
    }

    /**
      * @return rcAccount
     */
    public String getRcAccount() {
        return rcAccount;
    }

    /**
     * @param rcAccount the rcAccount to set
     */
    public void setRcAccount(String rcAccount) {
        this.rcAccount = rcAccount;
    }

    /**
     * @return launchtime
    */
    public Long getLaunchtime() {
        return launchtime;
    }

    /**
     * @param result the lauchtime is set
    */
    public void setLaunchtime(Long launchtime) {
        this.launchtime = launchtime;
    }


    /**
     * @return the resouceGroup
     */
    public String getResourceGroup() {
        return resourceGroup;
    }

    /**
     * @param resouceGroup the resouceGroup to set
     */
    public void setResourceGroup(String resouceGroup) {
        this.resourceGroup = resouceGroup;
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
        builder.append("AzureMachine [machineId=");
        builder.append(machineId);
        builder.append(", name=");
        builder.append(name);
        builder.append(", privateIpAddress=");
        builder.append(privateIpAddress);
        builder.append(", publicIpAddress=");
        builder.append(publicIpAddress);
        builder.append(", publicDnsName=");
        builder.append(publicDnsName);
        builder.append(", rc_account=");
        builder.append(rcAccount);
        builder.append(", reqId=");
        builder.append(reqId);
        builder.append(", retId=");
        builder.append(retId);
        builder.append(", template=");
        builder.append(template);
        builder.append(", launchtime=");
        builder.append(launchtime);
        builder.append(", resourceGroup=");
        builder.append(resourceGroup);
        builder.append(", result=");
        builder.append(result);
        builder.append(", msg=");
        builder.append(msg);
        builder.append(", status=");
        builder.append(status);
        builder.append("]");
        return builder.toString();
    }

}
