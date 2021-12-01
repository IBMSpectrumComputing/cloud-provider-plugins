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
* @Class Name : ProvMsgs
* @Description: Provider message
* @Author     : Yudong (Dom) Wang
* @Date       : 2019-6-11 9:47:33
* @Version    : V1.0
*/
public class ProvMsgs {
    @JsonProperty("messages")
    private Map<Integer, String> msgs;

    public Map<Integer, String> getMsgs() {
        return msgs;
    }

    public void setMsgs(Map<Integer, String> msgs) {
        this.msgs = msgs;
    }

    @Override
    public String toString() {
        return ProvUtil.toJsonStr(this);
    }
}
