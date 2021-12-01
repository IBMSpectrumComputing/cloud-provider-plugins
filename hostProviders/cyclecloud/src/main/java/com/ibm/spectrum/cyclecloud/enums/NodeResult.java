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

package com.ibm.spectrum.cyclecloud.enums;

/**
* @Class Name : NodeResult
* @Description: Node result
* @Author     : Yudong (Dom) Wang
* @Date       : 2019-6-6 14:15:52
* @Version    : V1.0
*/
public enum NodeResult {
    SUCCEED("succeed"),
    EXECUTING("executing"),
    FAILED("fail");

    private String result;

    /**
    * @Title      : NodeResult
    * @Description: constructor
    * @Param      : @param result
    */
    private NodeResult(String result) {
        this.result = result;
    }

    public String value() {
        return this.result;
    }
}
