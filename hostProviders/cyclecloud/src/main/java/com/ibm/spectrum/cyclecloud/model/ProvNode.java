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

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ibm.spectrum.cyclecloud.constant.ProvConst;
import com.ibm.spectrum.cyclecloud.enums.NodeResult;
import com.ibm.spectrum.cyclecloud.enums.NodeStatus;
import com.ibm.spectrum.cyclecloud.enums.ProvCode;
import com.ibm.spectrum.cyclecloud.model.cc.CCNode;
import com.ibm.spectrum.cyclecloud.model.cc.LSFConfig;
import com.ibm.spectrum.cyclecloud.util.ProvUtil;

/**
* @Class Name : ProvNode
* @Description: Provider host node
* @Author     : Yudong (Dom) Wang
* @Date       : 2019-5-31 10:24:14
* @Version    : V1.0
*/
public class ProvNode {
    @JsonProperty("machineId")
    private String nodeId;

    /**
     * Required and value type is string. Hostname of the machine
     */
    private String name;

    /**
     * Required and value type is string. The current status of the particular
     * request related to this machine. Possible value could be ‘executing’,
     * ‘fail’, ‘succeed’. For example, call requestMachines with templateId and
     * machineCount 3, and then call getRequestStatus to check the status of
     * this request, we should finally get 3 machines with ‘succeed’ result, if
     * any machine is missing or the status is not correct, that machine is not
     * useable.
     */
    private String result;

    private String status;

    /**
     * Required if the ‘result’ is ‘fail’. Any additional message for the
     * request status of this machine
     */
    @JsonProperty("message")
    private String msg;

    @JsonProperty("privateIpAddress")
    private String privateIpAddr;

    @JsonProperty("publicIpAddress")
    private String publicIpAddr;

    private String template;

    private String rcAccount;

    @JsonProperty("launchtime")
    private Long launchTime;

    /**
    * @Title      : ProvNode
    * @Description: constructor
    * @Param      :
    */
    public ProvNode() {
    }

    /**
    * @Title      : ProvNode
    * @Description: constructor
    * @Param      : @param node
    */
    public ProvNode(CCNode node) {
        this.nodeId = node.getNodeId();
        this.privateIpAddr = node.getPrivateIp();
        this.publicIpAddr = node.getPublicIp();

        // Cycle Cloud version >= 7.8.0, support to get the host name from API directly
        if (StringUtils.isNotBlank(node.getHostname())) {
            this.name = StringUtils.lowerCase(node.getHostname());
        } else { // Cycle Cloud version < 7.8.0, have to run command 'getent hosts <IP>' to get host name
            this.name = ProvUtil.hostName(node.getPrivateIp());
        }

        String lsfJson = ProvUtil.toJsonStr(node.getConfig().get("lsf"));
        if (StringUtils.isEmpty(lsfJson)) {
            return;
        }
        LSFConfig lsfCfg = ProvUtil.toObject(lsfJson, LSFConfig.class);
        if (null == lsfCfg || MapUtils.isEmpty(lsfCfg.getCustomEnv())) {
            return;
        }
        this.rcAccount = ProvUtil.strValue(lsfCfg.getCustomEnv().get(ProvConst.RC_ACCOUNT));
        this.template = ProvUtil.strValue(lsfCfg.getCustomEnv().get(ProvConst.TEMPLATE_ID));
    }

    public void setNode(NodeResult result, NodeStatus status, ProvCode pc) {
        this.result = result.value();
        this.status = status.value();
        this.msg = pc.message();
    }

    public void setNode(NodeResult result, NodeStatus status) {
        this.result = result.value();
        this.status = status.value();
    }

    public void setNode(ProvNode node) {
        if (null == node) {
            return;
        }
        if (StringUtils.isBlank(this.name)) {
            this.name = node.getName();
        }
        if (StringUtils.isBlank(this.nodeId)) {
            this.nodeId = node.getNodeId();
        }
        if (StringUtils.isBlank(this.privateIpAddr)) {
            this.privateIpAddr = node.getPrivateIpAddr();
        }
        if (StringUtils.isBlank(this.publicIpAddr)) {
            this.publicIpAddr = node.getPublicIpAddr();
        }
        if (StringUtils.isBlank(this.rcAccount)) {
            this.rcAccount = node.getRcAccount();
        }
        if (StringUtils.isBlank(this.template)) {
            this.template = node.getTemplate();
        }
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getPrivateIpAddr() {
        return privateIpAddr;
    }

    public void setPrivateIpAddr(String privateIpAddr) {
        this.privateIpAddr = privateIpAddr;
    }

    public String getPublicIpAddr() {
        return publicIpAddr;
    }

    public void setPublicIpAddr(String publicIpAddr) {
        this.publicIpAddr = publicIpAddr;
    }

    public String getRcAccount() {
        return rcAccount;
    }

    public void setRcAccount(String rcAccount) {
        this.rcAccount = rcAccount;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public Long getLaunchTime() {
        return launchTime;
    }

    public void setLaunchTime(Long launchTime) {
        this.launchTime = launchTime;
    }

    @Override
    public String toString() {
        return ProvUtil.toJsonStr(this);
    }
}
