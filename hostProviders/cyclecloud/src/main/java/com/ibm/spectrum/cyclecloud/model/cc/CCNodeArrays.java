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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ibm.spectrum.cyclecloud.util.ProvUtil;

/**
* @Class Name : CCNodeArray
* @Description: Azure CC node arrays
* @Author     : Yudong (Dom) Wang
* @Date       : 2019-6-12 14:48:53
* @Version    : V1.0
*/
public class CCNodeArrays {
    private String name;

    private Integer maxCount;

    private Integer maxCoreCount;

    @JsonProperty("nodearray")
    private CCNodeArray narray;

    private List<CCBucket> buckets;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getMaxCount() {
        return maxCount;
    }

    public void setMaxCount(Integer maxCount) {
        this.maxCount = maxCount;
    }

    public Integer getMaxCoreCount() {
        return maxCoreCount;
    }

    public void setMaxCoreCount(Integer maxCoreCount) {
        this.maxCoreCount = maxCoreCount;
    }

    public CCNodeArray getNarray() {
        return narray;
    }

    public void setNarray(CCNodeArray narray) {
        this.narray = narray;
    }

    public List<CCBucket> getBuckets() {
        return buckets;
    }

    public void setBuckets(List<CCBucket> buckets) {
        this.buckets = buckets;
    }

    @Override
    public String toString() {
        return ProvUtil.toJsonStr(this);
    }
}
