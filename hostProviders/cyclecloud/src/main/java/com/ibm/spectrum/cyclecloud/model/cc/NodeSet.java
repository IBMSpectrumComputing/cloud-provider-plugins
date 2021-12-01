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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ibm.spectrum.cyclecloud.util.ProvUtil;

/**
* @Class Name : NodeSet
* @Description: Node set
* @Author     : Yudong (Dom) Wang
* @Date       : 2019-5-30 14:27:22
* @Version    : V1.0
*/
public class NodeSet {
    /**
     * Required, the name of the nodearray to start nodes from
     * Example : "execute"
     */
    @JsonProperty("nodearray")
    private String nodeArray;

    /**
     * Required, the number of nodes to create
     * Example : 1
     */
    private Integer count;

    /**
     * Optional, the definition of the bucket to use. This is provided by the
     * cluster status API call. If some of the items given in the status call
     * are missing, or the entire bucket property is missing, the first bucket
     * that matches the given items is used.
     * Example : "object"
     */
    private NodeDefinition definition;

    /**
     * Optional, if given, nodes with the same value for groupId will all be
     * started in the same placement group.
     * Example : "string"
     */
    @JsonProperty("placementGroupId")
    private String pGrpId;

    /**
     * Optional, additional attributes to be set on each node from this set
     * Example : "[node](#node)"
     */
    @JsonProperty("nodeAttributes")
    private NodeAttribute attributes;

    /**
     * @Title : NodeSet
     * @Description: constructor
     * @Param :
     */
    public NodeSet() {
        this.count = 1;
    }

    public String getNodeArray() {
        return nodeArray;
    }

    public void setNodeArray(String nodeArray) {
        this.nodeArray = nodeArray;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public NodeDefinition getDefinition() {
        return definition;
    }

    public void setDefinition(NodeDefinition definition) {
        this.definition = definition;
    }

    public String getpGrpId() {
        return pGrpId;
    }

    public void setpGrpId(String pGrpId) {
        this.pGrpId = pGrpId;
    }

    public NodeAttribute getAttributes() {
        return attributes;
    }

    public void setAttributes(NodeAttribute attributes) {
        this.attributes = attributes;
    }

    @Override
    public String toString() {
        return ProvUtil.toJsonStr(this);
    }
}
