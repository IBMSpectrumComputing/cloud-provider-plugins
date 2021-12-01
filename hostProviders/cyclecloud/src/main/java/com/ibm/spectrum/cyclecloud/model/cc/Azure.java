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
* @Class Name : Azure
* @Description: Azure
* @Author     : Yudong (Dom) Wang
* @Date       : 2019-6-21 15:12:46
* @Version    : V1.0
*/
public class Azure {
    @JsonProperty("MaxScalesetSize")
    private Integer maxScalesetSize;

    public Integer getMaxScalesetSize() {
        return maxScalesetSize;
    }

    public void setMaxScalesetSize(Integer maxScalesetSize) {
        this.maxScalesetSize = maxScalesetSize;
    }

    @Override
    public String toString() {
        return ProvUtil.toJsonStr(this);
    }
}
