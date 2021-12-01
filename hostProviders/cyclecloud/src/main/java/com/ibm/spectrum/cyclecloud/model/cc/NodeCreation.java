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


package com.ibm.spectrum.cyclecloud.model.cc;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ibm.spectrum.cyclecloud.constant.ProvConst;
import com.ibm.spectrum.cyclecloud.util.ProvUtil;

/**
* @Class Name : NodeCreation
* @Description: Specifies how to add nodes to a cluster
* @Author     : Yudong (Dom) Wang
* @Date       : 2019-5-30 14:27:22
* @Version    : V1.0
*/
public class NodeCreation {
    /**
     * Required, a list of node definitions to create. The request must contain
     * at least one set. Each set can specify a different set of properties.
     * Example : [ "object" ]
     */
    private List<NodeSet> sets;

    /**
     * Optional, user-supplied unique token to prevent duplicate operations
     * Example : "string"
     */
    @JsonProperty("requestId")
    private String reqId;

    /**
     * @Title : NodeRequest
     * @Description: constructor
     * @Param :
     */
    public NodeCreation() {
        this.reqId = ProvConst.REQ_ID_CREATE + ProvUtil.randomID();
    }

    /**
    * @Title      : NodeCreation
    * @Description: constructor
    * @Param      : @param sets
    */
    public NodeCreation(List<NodeSet> sets) {
        this.sets = sets;
        this.reqId = ProvConst.REQ_ID_CREATE + ProvUtil.randomID();
    }

    public String getReqId() {
        return reqId;
    }

    public void setReqId(String reqId) {
        this.reqId = reqId;
    }

    public List<NodeSet> getSets() {
        return sets;
    }

    public void setSets(List<NodeSet> sets) {
        this.sets = sets;
    }

    @Override
    public String toString() {
        return ProvUtil.toJsonStr(this);
    }
}
