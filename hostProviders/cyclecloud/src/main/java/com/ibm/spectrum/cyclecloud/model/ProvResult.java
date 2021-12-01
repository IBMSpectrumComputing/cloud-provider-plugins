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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ibm.spectrum.cyclecloud.enums.ProvCode;
import com.ibm.spectrum.cyclecloud.enums.ProvStatus;
import com.ibm.spectrum.cyclecloud.util.ProvUtil;

/**
* @Class Name : ProvResult
* @Description: Provider result
* @Author     : Yudong (Dom) Wang
* @Date       : 2019-5-31 9:59:18
* @Version    : V1.0
*/
public class ProvResult {
    @JsonIgnore
    private Integer code;

    @JsonProperty("message")
    private String msg;

    private String status;

    @JsonProperty("requestId")
    private String reqId;

    @JsonProperty("requests")
    private List<ProvReq> reqs;

    private List<ProvTemplate> templates;

    /**
    * @Title      : ProvResult
    * @Description: constructor
    * @Param      :
    */
    public ProvResult() {
    }

    /**
    * @Title      : ProvResult
    * @Description: constructor
    * @Param      : @param code
    * @Param      : @param msg
    */
    public ProvResult(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    /**
    *
    * @Title      : ProvResult
    * @Description: constructor
    * @Param      : @param pc
     */
    public ProvResult(ProvCode pc) {
        this.code = pc.value();
        this.msg = pc.message();
        this.status = pc.status().value();
    }

    /**
    *
    * @Title      : ProvResult
    * @Description: constructor
    * @Param      : @param pe
     */
    public ProvResult(ProvException pe) {
        this.code = pe.code();
        this.msg = pe.message();
        this.status = pe.status();
    }

    /**
    *
    * @Title      : ProvResult
    * @Description: constructor
    * @Param      : @param pc
    * @Param      : @param ps
    * @Param      : @param reqId
     */
    public ProvResult(ProvCode pc, ProvStatus ps, String reqId) {
        this.code = pc.value();
        this.msg = pc.message();
        this.status = ps.value();
        this.reqId = reqId;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReqId() {
        return reqId;
    }

    public void setReqId(String reqId) {
        this.reqId = reqId;
    }

    public List<ProvReq> getReqs() {
        return reqs;
    }

    public void setReqs(List<ProvReq> reqs) {
        this.reqs = reqs;
    }

    public List<ProvTemplate> getTemplates() {
        return templates;
    }

    public void setTemplates(List<ProvTemplate> templates) {
        this.templates = templates;
    }

    @Override
    public String toString() {
        return ProvUtil.toJsonStr(this);
    }
}
