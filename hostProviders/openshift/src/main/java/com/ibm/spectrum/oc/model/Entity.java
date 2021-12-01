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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

public class Entity {
    @JsonIgnore
    private Integer code;

    @JsonInclude(Include.NON_NULL)
    @JsonProperty("message")
    private String msg;

    @JsonInclude(Include.NON_NULL)
    @JsonProperty("requestId")
    private String reqId;

    @JsonInclude(Include.NON_NULL)
    @JsonProperty("returnId")
    private String retId;

    @JsonInclude(Include.NON_NULL)
    private String status;

    @JsonInclude(Include.NON_NULL)
    private Template template;

    @JsonInclude(Include.NON_NULL)
    private Machine machine;

    @JsonInclude(Include.NON_NULL)
    private Request request;

    @JsonInclude(Include.NON_NULL)
    private List<Template> templates;

    @JsonInclude(Include.NON_NULL)
    private List<Machine> machines;

    @JsonInclude(Include.NON_NULL)
    @JsonProperty("requests")
    private List<Request> reqs;

    @JsonProperty("rc_account")
    @JsonInclude(Include.NON_NULL)
    private String tagValue;

    @JsonInclude(Include.NON_NULL)
    private UserData userData;


    public Entity() {
        this.code = 0;
    }

    /**
     * @return code
     */
    public Integer getCode() {
        return code;
    }

    /**
     * @param code
     *            the code to set
     */
    public void setCode(Integer code) {
        this.code = code;
    }

    /**
     * @return msg
     */
    public String getMsg() {
        return msg;
    }

    /**
     * @param msg
     *            the msg to set
     */
    public void setMsg(String msg) {
        this.msg = msg;
    }

    /**
     * @return reqId
     */
    public String getReqId() {
        return reqId;
    }

    /**
     * @param reqId
     *            the reqId to set
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
     * @param retId
     *            the retId to set
     */
    public void setRetId(String retId) {
        this.retId = retId;
    }

    /**
     * @return status
     */
    public String getStatus() {
        return status;
    }

    /**
     * @param status
     *            the status to set
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * @return template
     */
    public Template getTemplate() {
        return template;
    }

    /**
     * @param template
     *            the template to set
     */
    public void setTemplate(Template template) {
        this.template = template;
    }

    /**
     * @return machine
     */
    public Machine getMachine() {
        return machine;
    }

    /**
     * @param machine
     *            the machine to set
     */
    public void setMachine(Machine machine) {
        this.machine = machine;
    }

    /**
     * @return request
     */
    public Request getRequest() {
        return request;
    }

    /**
     * @param request
     *            the request to set
     */
    public void setRequest(Request request) {
        this.request = request;
    }

    /**
     * @return templates
     */
    public List<Template> getTemplates() {
        return templates;
    }

    /**
     * @param templates
     *            the templates to set
     */
    public void setTemplates(List<Template> templates) {
        this.templates = templates;
    }

    /**
     * @return machines
     */
    public List<Machine> getMachines() {
        return machines;
    }

    /**
     * @param machines
     *            the machines to set
     */
    public void setMachines(List<Machine> machines) {
        this.machines = machines;
    }

    /**
     * @return reqs
     */
    public List<Request> getReqs() {
        return reqs;
    }

    /**
     * @param reqs
     *            the reqs to set
     */
    public void setReqs(List<Request> reqs) {
        this.reqs = reqs;
    }

    /**
     * @return tagValue
     */
    public String getTagValue() {
        return tagValue;
    }

    /**
     * @param tagValue
     *            the tagValue to set
     */
    public void setTagValue(String tagValue) {
        this.tagValue = tagValue;
    }

    public void setRsp(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public UserData getUserData() {
        return userData;
    }

    public void setUserData(UserData userData) {
        this.userData = userData;
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
        builder.append("Entity [code=");
        builder.append(code);
        builder.append(", msg=");
        builder.append(msg);
        builder.append(", reqId=");
        builder.append(reqId);
        builder.append(", retId=");
        builder.append(retId);
        builder.append(", status=");
        builder.append(status);
        builder.append(", template=");
        builder.append(template);
        builder.append(", machine=");
        builder.append(machine);
        builder.append(", request=");
        builder.append(request);
        builder.append(", templates=");
        builder.append(templates);
        builder.append(", machines=");
        builder.append(machines);
        builder.append(", reqs=");
        builder.append(reqs);
        builder.append(", rc_account=");
        builder.append(tagValue);
        builder.append(", userData=");
        builder.append(userData);
        builder.append("]");
        return builder.toString();
    }

}
