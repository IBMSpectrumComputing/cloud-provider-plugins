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
* @Class Name : NodeMgtReq
* @Description: Specifies how to perform actions on nodes in a cluster.
*               There are multiple ways to specify nodes,
*               and if more than one way is included,
*               it is treated as a union.
* @Author     : Yudong (Dom) Wang
* @Date       : 2019-5-30 15:27:22
* @Version    : V1.0
*/
public class NodeMgtReq {
    /**
     * Optional, a list of node names to manage.
     * Example : [ "name1", "name2" ]
     */
    private List<String> names;

    /**
     * Optional, a list of node ids to manage.
     * Example : [ "id1", "id2" ]
     */
    private List<String> ids;

    /**
     * Optional, a filter expression that matches nodes. Note that strings must
     * be escaped properly.
     * Example : "State === \"Started\""
     */
    private String filter;

    /**
     * Optional, user-supplied unique token to prevent duplicate operations.
     * Example : "string"
     */
    @JsonProperty("requestId")
    private String reqId;

    /**
    * @Title      : NodeMgtRequest
    * @Description: constructor
    * @Param      :
    */
    public NodeMgtReq() {
        this.reqId = ProvConst.REQ_ID_DELETE + ProvUtil.randomID();
    }

    /**
    *
    * @Title      : NodeMgtReq
    * @Description: constructor
    * @Param      : @param names
    * @Param      : @param ids
     */
    public NodeMgtReq(List<String> ids) {
        this.reqId = ProvConst.REQ_ID_DELETE + ProvUtil.randomID();
        this.ids = ids;
    }

    public List<String> getNames() {
        return names;
    }

    public void setNames(List<String> names) {
        this.names = names;
    }

    public List<String> getIds() {
        return ids;
    }

    public void setIds(List<String> ids) {
        this.ids = ids;
    }

    public String getFilter() {
        return filter;
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

    public String getReqId() {
        return reqId;
    }

    public void setReqId(String reqId) {
        this.reqId = reqId;
    }

    @Override
    public String toString() {
        return ProvUtil.toJsonStr(this);
    }
}
