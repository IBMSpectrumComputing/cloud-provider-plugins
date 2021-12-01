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

import com.ibm.spectrum.cyclecloud.util.ProvUtil;

/**
* @Class Name : NodeDefinition
* @Description: Node definition
* @Author     : Yudong (Dom) Wang
* @Date       : 2019-5-30 14:27:22
* @Version    : V1.0
*/
public class NodeDefinition {
    /**
     * Optional
     * Example : "A2"
     */
    private String machineType;

    /**
    * @Title      : NodeDefinition
    * @Description: constructor
    * @Param      :
    */
    public NodeDefinition() {
    }

    /**
    * @Title      : NodeDefinition
    * @Description: constructor
    * @Param      : @param machineType
    */
    public NodeDefinition(String machineType) {
        this.machineType = machineType;
    }

    public String getMachineType() {
        return machineType;
    }

    public void setMachineType(String machineType) {
        this.machineType = machineType;
    }

    @Override
    public String toString() {
        return ProvUtil.toJsonStr(this);
    }
}
