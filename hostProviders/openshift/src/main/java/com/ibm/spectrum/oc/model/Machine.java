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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

public class Machine {
    @JsonInclude(Include.NON_NULL)
    private String machineId;

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

    @JsonInclude(Include.NON_NULL)
    private String result;

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
    private String rcAccount;

    @JsonProperty("launchtime")
    @JsonInclude(Include.NON_NULL)
    private Long launchtime;

    @JsonInclude(Include.NON_NULL)
    private String publicDnsName;

    public void hide() {
        this.reqId = null;
        this.retId = null;
        this.template = null;
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
     * @param result the launchTime is set
    */
    public void setLaunchtime(Long launchtime) {
        this.launchtime = launchtime;
    }

    /**
     * @return Public DNS name
    */
    public String getPublicDnsName() {
        return publicDnsName;
    }

    /**
     * @param Machine public DNS name
    */
    public void setPublicDnsName(String name) {
        this.publicDnsName = name;
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
        builder.append("Machine [machineId=");
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
        builder.append(", result=");
        builder.append(result);
        builder.append(", msg=");
        builder.append(msg);
        builder.append(", status=");
        builder.append(status);
        builder.append("]");
        return builder.toString();
    }

    /**
     * @param machineWithNewValues
     */
    public void copyValues(Machine machineWithNewValues) {
        this.setMachineId(machineWithNewValues.getMachineId());
        this.setName(machineWithNewValues.getName());
        this.setPrivateIpAddress(machineWithNewValues.getPrivateIpAddress());
        this.setPublicIpAddress(machineWithNewValues.getPublicIpAddress());
        this.setPublicDnsName(machineWithNewValues.getPublicDnsName());
        this.setRcAccount(machineWithNewValues.getRcAccount());
        this.setReqId(machineWithNewValues.getReqId());
        this.setRetId(machineWithNewValues.getRetId());
        this.setTemplate(machineWithNewValues.getTemplate());
        this.setLaunchtime(machineWithNewValues.getLaunchtime());
        this.setResult(machineWithNewValues.getResult());
        this.setMsg(machineWithNewValues.getMsg());
        this.setStatus(machineWithNewValues.getStatus());
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                 + ((machineId == null) ? 0 : machineId.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    /**
     * Checks if the current object matches the object sent as a parameter.
     * <br> This method checks the attributes in the machine sent as a parameter:
     * <br> 1. If the machine has the following attributes as non-empty(machineId & name). The method will use both attributes in the matching.
     * <br> 2. If only the machineId attribute is set, the method will only use the machineId attribute in the matching
     * <br> 3. If only the machineName attribute is set, the method will only use the name attribute in the matching
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Machine other = (Machine) obj;
        boolean compareID = (this.compareMachineId());
        boolean compareName = (this.compareMachineName() );
        boolean compareIdANDName = (compareID && compareName);
        if (compareIdANDName) {
            if (other.compareMachineId() && other.compareMachineName()) {
                if (machineId.equals(other.getMachineId())
                        && name.equals(other.getName())) {
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } else if (compareID) {
            if (other.compareMachineId()) {
                if (machineId.equals(other.getMachineId())) {
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } else if (compareName) {
            if (other.compareMachineName()) {
                if (name.equals(other.getName())) {
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }
        return false;
    }
    /**
     * Is this machine eligible to use the machineId in the matching
     */
    private boolean compareMachineId() {
        if (machineId != null && !machineId.trim().equals("")) {
            return true;
        }
        return false;
    }

    /**
     * Is the machine eligible to use the name in the matching
     */
    private boolean compareMachineName() {
        if (name != null && !name.trim().equals("")) {
            return true;
        }
        return false;
    }


}
