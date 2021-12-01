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
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ibm.spectrum.cyclecloud.enums.ProvCode;
import com.ibm.spectrum.cyclecloud.enums.ProvStatus;
import com.ibm.spectrum.cyclecloud.util.ProvUtil;

/**
* @Class Name : ProvReq
* @Description: Provider request
* @Author     : Yudong (Dom) Wang
* @Date       : 2019-5-31 9:59:18
* @Version    : V1.0
*/
public class ProvReq {
    /**
     * Required and value type is string. Unique id to identify this request in
     * the host provider
     */
    @JsonProperty("requestId")
    private String reqId;

    /**
     * Any additional string message the caller should know
     */
    @JsonProperty("message")
    private String msg;

    /**
     * Required and value type is string. Status of this request and possible
     * value could be ‘running’, ‘complete’ or ‘complete_with_error’. You should
     * check the machines info if there are any when it is ‘complete’ or
     * ‘complete_with_error’
     */
    private String status;

    /**
     * value type is string. Node id of the machine that needs to return
     */
    @JsonProperty("machineId")
    private String nodeId;

    /**
     * value type is string. Node name of the machine that needs to return
     */
    @JsonProperty("machine")
    private String nodeName;

    /**
     * value type is number. Indicator of how soon this machine should be returned,
     * and this is in seconds
     */
    private Long gracePeriod;

    @JsonProperty("machines")
    private List<ProvNode> nodes;

    @JsonProperty("rc_account")
    private String rcAccount;

    /**
    * @Title      : ProvReq
    * @Description: constructor
    * @Param      :
    */
    public ProvReq() {
    }

    /**
    *
    * @Title      : ProvReq
    * @Description: constructor
    * @Param      : @param reqId
    * @Param      : @param nodes
     */
    public ProvReq(String reqId, List<ProvNode> nodes) {
        this.reqId = reqId;
        this.nodes = nodes;
    }

    /**
    *
    * @Title      : ProvReq
    * @Description: constructor
    * @Param      : @param reqId
    * @Param      : @param pc
     */
    public ProvReq(String reqId, ProvCode pc) {
        this.reqId = reqId;
        this.msg = pc.message();
        this.status = pc.status().value();
    }

    /**
    * @Title      : ProvReq
    * @Description: constructor
    * @Param      : @param reqId
    * @Param      : @param msg
    * @Param      : @param status
    */
    public ProvReq(String reqId, String msg, String status) {
        this.reqId = reqId;
        this.msg = msg;
        this.status = status;
    }

    public void setRequest(ProvCode pc) {
        this.msg = String.format(pc.message(), this.reqId);
        this.status = pc.status().value();
    }

    public void setRequest(ProvCode pc, ProvStatus status) {
        this.msg = pc.message();
        this.status = status.value();
    }

    public void updateRequest(ProvReq req) {
        this.reqId = req.getReqId();
        this.msg = req.getMsg();
        this.status = req.getStatus();
        this.nodeId = req.getNodeId();
        this.nodeName = req.getNodeName();
        this.gracePeriod = req.getGracePeriod();
        this.nodes = req.getNodes();
        if (StringUtils.isNotBlank(req.getRcAccount())) {
            this.rcAccount = req.getRcAccount();
        }
    }

    public void updateNodes(Map<String, ProvNode> pnodes) {
        if (MapUtils.isEmpty(pnodes)) {
            return;
        }
        if (CollectionUtils.isEmpty(this.nodes)) {
            return;
        }

        for (ProvNode pnode : this.nodes) {
            ProvNode node = pnodes.get(pnode.getNodeId());
            pnode.setNode(node);
        }
    }

    public String getReqId() {
        return reqId;
    }

    public void setReqId(String reqId) {
        this.reqId = reqId;
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

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public Long getGracePeriod() {
        return gracePeriod;
    }

    public void setGracePeriod(Long gracePeriod) {
        this.gracePeriod = gracePeriod;
    }

    public List<ProvNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<ProvNode> nodes) {
        this.nodes = nodes;
    }

    public String getRcAccount() {
        return rcAccount;
    }

    public void setRcAccount(String rcAccount) {
        this.rcAccount = rcAccount;
    }

    @Override
    public String toString() {
        return ProvUtil.toJsonStr(this);
    }
}
