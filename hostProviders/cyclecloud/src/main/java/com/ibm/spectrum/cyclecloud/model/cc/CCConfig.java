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

import java.util.Map;

import com.ibm.spectrum.cyclecloud.util.ProvUtil;

/**
* @Class Name : NodeConfig
* @Description: Azure Cycle Cloud Configuration
* @Author     : Yudong (Dom) Wang
* @Date       : 2019-6-26 15:52:50
* @Version    : V1.0
*/
public class CCConfig {
    private LSFConfig lsf;

    private Map<String, Object> cyclecloud;

    public LSFConfig getLsf() {
        return lsf;
    }

    public void setLsf(LSFConfig lsf) {
        this.lsf = lsf;
    }

    public Map<String, Object> getCyclecloud() {
        return cyclecloud;
    }

    public void setCyclecloud(Map<String, Object> cyclecloud) {
        this.cyclecloud = cyclecloud;
    }

    @Override
    public String toString() {
        return ProvUtil.toJsonStr(this);
    }
}
