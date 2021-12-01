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

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;

import com.ibm.spectrum.cyclecloud.model.ProvNode;
import com.ibm.spectrum.cyclecloud.util.ProvUtil;

/**
* @Class Name : CCNodes
* @Description: Azure CC nodes
* @Author     : Yudong (Dom) Wang
* @Date       : 2019-6-6 14:23:58
* @Version    : V1.0
*/
public class CCNodes {
    private List<CCNode> nodes;

    private CCOperation operation;

    public List<CCNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<CCNode> nodes) {
        this.nodes = nodes;
    }

    public CCOperation getOperation() {
        return operation;
    }

    public void setOperation(CCOperation operation) {
        this.operation = operation;
    }

    public List<ProvNode> toProvNodes() {
        List<ProvNode> pnodes = new ArrayList<ProvNode>();
        if (CollectionUtils.isEmpty(this.nodes)) {
            return pnodes;
        }
        for (CCNode cnode : this.nodes) {
            pnodes.add(new ProvNode(cnode));
        }
        return pnodes;
    }

    public List<ProvNode> toProvNodes(String rcAccount, String templateId) {
        List<ProvNode> pnodes = new ArrayList<ProvNode>();
        if (CollectionUtils.isEmpty(this.nodes)) {
            return pnodes;
        }
        for (CCNode cnode : this.nodes) {
            ProvNode pnode = new ProvNode(cnode);
            pnode.setRcAccount(rcAccount);
            pnode.setTemplate(templateId);
            pnodes.add(pnode);
        }
        return pnodes;
    }

    @Override
    public String toString() {
        return ProvUtil.toJsonStr(this);
    }
}
