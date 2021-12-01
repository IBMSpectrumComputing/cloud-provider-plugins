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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
* @ClassName: AwsEntity
* @Description: The entity used for the request and response of the method
* @author xawangyd
* @date Jan 26, 2016 3:24:07 PM
* @version 1.0
*/
public class AwsEntity {
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
    private AwsUserData userData;

    @JsonInclude(Include.NON_NULL)
    private AwsTemplate template;

    @JsonInclude(Include.NON_NULL)
    private AwsMachine machine;

    @JsonInclude(Include.NON_NULL)
    private AwsRequest request;

    @JsonInclude(Include.NON_NULL)
    private List<AwsTemplate> templates;

    @JsonInclude(Include.NON_NULL)
    private List<AwsMachine> machines;

    @JsonInclude(Include.NON_NULL)
    @JsonProperty("requests")
    private List<AwsRequest> reqs;

    @JsonProperty("rc_account")
    @JsonInclude(Include.NON_NULL)
    private String tagValue;

    /**
    * <p>Title: </p>
    * <p>Description: </p>
    */
    public AwsEntity() {
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
    public AwsTemplate getTemplate() {
        return template;
    }

    /**
     * @param template
     *            the template to set
     */
    public void setTemplate(AwsTemplate template) {
        this.template = template;
    }

    /**
     * @return machine
     */
    public AwsMachine getMachine() {
        return machine;
    }

    /**
     * @param machine
     *            the machine to set
     */
    public void setMachine(AwsMachine machine) {
        this.machine = machine;
    }

    /**
     * @return request
     */
    public AwsRequest getRequest() {
        return request;
    }

    /**
     * @param request
     *            the request to set
     */
    public void setRequest(AwsRequest request) {
        this.request = request;
    }

    /**
     * @return templates
     */
    public List<AwsTemplate> getTemplates() {
        return templates;
    }

    /**
     * @param templates
     *            the templates to set
     */
    public void setTemplates(List<AwsTemplate> templates) {
        this.templates = templates;
    }

    /**
     * @return machines
     */
    public List<AwsMachine> getMachines() {
        return machines;
    }

    /**
     * @param machines
     *            the machines to set
     */
    public void setMachines(List<AwsMachine> machines) {
        this.machines = machines;
    }

    /**
     * @return reqs
     */
    public List<AwsRequest> getReqs() {
        return reqs;
    }

    /**
     * @param reqs
     *            the reqs to set
     */
    public void setReqs(List<AwsRequest> reqs) {
        this.reqs = reqs;
    }

    /**
    * @return userData
    */
    public AwsUserData getUserData() {
        return userData;
    }

    /**
     * @param userData the userData to set
     */
    public void setUserData(AwsUserData userData) {
        this.userData = userData;
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

    /** (Non Javadoc)
    * <p>Title: toString</p>
    * <p>Description: </p>
    * @return
    * @see java.lang.Object#toString()
    */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("AwsEntity [code=");
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
