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
* @Class Name : CCBucket
* @Description: Azure CC bucket
* @Author     : Yudong (Dom) Wang
* @Date       : 2019-6-12 14:55:27
* @Version    : V1.0
*/
public class CCBucket {
    private String bucketId;

    private NodeDefinition definition;

    private Integer maxCount;

    private Integer maxCoreCount;

    private Integer activeCount;

    private Integer activeCoreCount;

    @JsonProperty("availableCount")
    private Integer availCount;

    @JsonProperty("availableCoreCount")
    private Integer availCoreCount;

    private Integer quotaCount;

    private Integer quotaCoreCount;

    private Integer consumedCoreCount;

    @JsonProperty("maxPlacementGroupSize")
    private Integer maxGrpSize;

    @JsonProperty("maxPlacementGroupCoreSize")
    private Integer maxGrpCoreSize;

    @JsonProperty("virtualMachine")
    private CCVirtMachine vm;

    public String getBucketId() {
        return bucketId;
    }

    public void setBucketId(String bucketId) {
        this.bucketId = bucketId;
    }

    public NodeDefinition getDefinition() {
        return definition;
    }

    public void setDefinition(NodeDefinition definition) {
        this.definition = definition;
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

    public Integer getActiveCount() {
        return activeCount;
    }

    public void setActiveCount(Integer activeCount) {
        this.activeCount = activeCount;
    }

    public Integer getActiveCoreCount() {
        return activeCoreCount;
    }

    public void setActiveCoreCount(Integer activeCoreCount) {
        this.activeCoreCount = activeCoreCount;
    }

    public Integer getAvailCount() {
        return availCount;
    }

    public void setAvailCount(Integer availCount) {
        this.availCount = availCount;
    }

    public Integer getAvailCoreCount() {
        return availCoreCount;
    }

    public void setAvailCoreCount(Integer availCoreCount) {
        this.availCoreCount = availCoreCount;
    }

    public Integer getQuotaCount() {
        return quotaCount;
    }

    public void setQuotaCount(Integer quotaCount) {
        this.quotaCount = quotaCount;
    }

    public Integer getQuotaCoreCount() {
        return quotaCoreCount;
    }

    public void setQuotaCoreCount(Integer quotaCoreCount) {
        this.quotaCoreCount = quotaCoreCount;
    }

    public Integer getConsumedCoreCount() {
        return consumedCoreCount;
    }

    public void setConsumedCoreCount(Integer consumedCoreCount) {
        this.consumedCoreCount = consumedCoreCount;
    }

    public Integer getMaxGrpSize() {
        return maxGrpSize;
    }

    public void setMaxGrpSize(Integer maxGrpSize) {
        this.maxGrpSize = maxGrpSize;
    }

    public Integer getMaxGrpCoreSize() {
        return maxGrpCoreSize;
    }

    public void setMaxGrpCoreSize(Integer maxGrpCoreSize) {
        this.maxGrpCoreSize = maxGrpCoreSize;
    }

    public CCVirtMachine getVm() {
        return vm;
    }

    public void setVm(CCVirtMachine vm) {
        this.vm = vm;
    }

    @Override
    public String toString() {
        return ProvUtil.toJsonStr(this);
    }
}
