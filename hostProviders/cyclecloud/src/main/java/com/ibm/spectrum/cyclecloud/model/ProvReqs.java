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

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ibm.spectrum.cyclecloud.util.ProvUtil;

/**
* @Class Name : ProvReqs
* @Description: Provider requests
* @Author     : Yudong (Dom) Wang
* @Date       : 2019-6-6 9:55:14
* @Version    : V1.0
*/
public class ProvReqs {
    @JsonProperty("requests")
    private List<ProvReq> reqs;

    /**
    *
    * @Title      : ProvReqs
    * @Description: constructor
    * @Param      :
     */
    public ProvReqs() {
        this.reqs = new ArrayList<ProvReq>();
    }

    public List<ProvReq> getReqs() {
        return reqs;
    }

    public void setReqs(List<ProvReq> reqs) {
        this.reqs = reqs;
    }

    @Override
    public String toString() {
        return ProvUtil.toJsonStr(this);
    }
}
