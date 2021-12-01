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

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ibm.spectrum.cyclecloud.util.ProvUtil;

/**
* @Class Name : ProvNodeCreation
* @Description: Provider node creation
* @Author     : Yudong (Dom) Wang
* @Date       : 2019-6-18 16:02:50
* @Version    : V1.0
*/
public class ProvNodeCreation {
    private ProvTemplate template;

    @JsonProperty("rc_account")
    private String rcAccount;

    private Map<String, Object> userData;

    public ProvTemplate getTemplate() {
        return template;
    }

    public void setTemplate(ProvTemplate template) {
        this.template = template;
    }

    public String getRcAccount() {
        return rcAccount;
    }

    public void setRcAccount(String rcAccount) {
        this.rcAccount = rcAccount;
    }

    public Map<String, Object> getUserData() {
        return userData;
    }

    public void setUserData(Map<String, Object> userData) {
        this.userData = userData;
    }

    @Override
    public String toString() {
        return ProvUtil.toJsonStr(this);
    }
}
