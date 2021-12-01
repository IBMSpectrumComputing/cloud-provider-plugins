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
* @Class Name : NodeStatus
* @Description: Node Status
* @Author     : Yudong (Dom) Wang
* @Date       : 2019-6-6 19:11:11
* @Version    : V1.0
*/
public enum CCNodeState {
    UNAVAILABLE("Unavailable"),
    FAILED("Failed"),
    TERMINATED("Terminated"),
    DEALLOCATED("Deallocated"),
    STARTED("Started");

    private String state;

    /**
    * @Title      : NodeState
    * @Description: constructor
    * @Param      : @param state
    */
    private CCNodeState(String state) {
        this.state = state;
    }

    public String value() {
        return this.state;
    }

    /**
    *
    * @Title      : isStarted
    * @Description: Check if it is started
    * @Param      : @param value
    * @Param      : @return
    * @Return     : boolean
    * @Throws     :
     */
    public static boolean isStarted(String value) {
        if (STARTED.value().equals(value)) {
            return true;
        }
        return false;
    }

    /**
    *
    * @Title      : isTerminated
    * @Description: TODO
    * @Param      : @param value
    * @Param      : @return
    * @Return     : boolean
    * @Throws     :
     */
    public static boolean isTerminated(String value) {
        if (TERMINATED.value().equals(value)) {
            return true;
        }
        return false;
    }

    /**
    *
    * @Title      : isFailed
    * @Description: Check if it is failed
    * @Param      : @param value
    * @Param      : @return
    * @Return     : boolean
    * @Throws     :
     */
    public static boolean isFailed(String value) {
        if (UNAVAILABLE.value().equals(value)) {
            return true;
        }

        if (FAILED.value().equals(value)) {
            return true;
        }
        return false;
    }
}
