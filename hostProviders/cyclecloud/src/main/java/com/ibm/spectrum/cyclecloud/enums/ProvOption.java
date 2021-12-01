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
* @Class Name : ProvOption
* @Description: Provider option
* @Author     : Yudong (Dom) Wang
* @Date       : 2019-6-10 20:14:49
* @Version    : V1.0
*/
public enum ProvOption {
    OPT_NODES("-n", "--nodes", "getAvailableNodes"),
    OPT_TEMPLATES("-t", "--templates", "getAvailableTemplates"),
    OPT_DROP("-d", "--dropped", "getReturnRequests"),
    OPT_CREATE("-c", "--create", "createNode"),
    OPT_RETURN("-r", "--return", "terminateNode"),
    OPT_STATUS("-s", "--status", "getRequestStatus"),
    OPT_USAGE("-h", "--help", "usage");

    /**
     * Short option name
     */
    private String shortOpt;

    /**
     * Long option name
     */
    private String longOpt;

    /**
     * Method name
     */
    private String methodName;

    /**
     * @Title : ProvOption
     * @Description: constructor
     * @Param : @param shortOpt
     * @Param : @param longOpt
     * @Param : @param methodName
     */
    ProvOption(String shortOpt, String longOpt, String methodName) {
        this.shortOpt = shortOpt;
        this.longOpt = longOpt;
        this.methodName = methodName;
    }

    public String getShortOpt() {
        return shortOpt;
    }

    public void setShortOpt(String shortOpt) {
        this.shortOpt = shortOpt;
    }

    public String getLongOpt() {
        return longOpt;
    }

    public void setLongOpt(String longOpt) {
        this.longOpt = longOpt;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    /**
    *
    * @Title      : method
    * @Description: Get method name
    * @Param      : @param optName
    * @Param      : @return
    * @Return     : String
    * @Throws     :
     */
    public static String method(String optName) {
        for (ProvOption option : values()) {
            if (option.getLongOpt().equals(optName) || option.getShortOpt().equals(optName)) {
                return option.getMethodName();
            }
        }
        return null;
    }
}
