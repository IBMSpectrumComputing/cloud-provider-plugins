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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ibm.spectrum.cyclecloud.model.ProvTemplate;
import com.ibm.spectrum.cyclecloud.util.ProvUtil;

/**
* @Class Name : LSFConfig
* @Description: Azure Cycle Cloud LSF Configuration
* @Author     : Yudong (Dom) Wang
* @Date       : 2019-6-26 15:25:59
* @Version    : V1.0
*/
public class LSFConfig {
    @JsonProperty("custom_env_names")
    private String customEnvNames;

    @JsonProperty("custom_env")
    private Map<String, Object> customEnv;

    @JsonProperty("custom_script_uri")
    private String scriptUri;

    @JsonProperty("skip_modify_local_resources")
    private Boolean skipModify;

    @JsonProperty("autoscale")
    private Boolean autoScale;

    private Boolean headless;

    @JsonProperty("local_etc")
    private String localEtc;

    @JsonProperty("lsf_top")
    private String lsfTop;

    @JsonProperty("num_placement_groups")
    private Integer numPGroups;

    /**
    * @Title      : LSFConfig
    * @Description: constructor
    * @Param      :
    */
    public LSFConfig() {
    }

    /**
    * @Title      : LSFConfig
    * @Description: constructor
    * @Param      : @param tpl
    */
    public LSFConfig(ProvTemplate tpl) {
        this.scriptUri = tpl.getCustomScriptUri();
        this.skipModify = tpl.getSkipModify();
        this.autoScale = tpl.getAutoScale();
        this.headless = tpl.getHeadless();
        this.localEtc = tpl.getLsfEnvDir();
        this.lsfTop = tpl.getLsfTop();
        this.numPGroups = tpl.getNumPGroups();
    }

    public String getCustomEnvNames() {
        return customEnvNames;
    }

    public void setCustomEnvNames(String customEnvNames) {
        this.customEnvNames = customEnvNames;
    }

    public Map<String, Object> getCustomEnv() {
        return customEnv;
    }

    public void setCustomEnv(Map<String, Object> customEnv) {
        this.customEnv = customEnv;
    }

    public String getScriptUri() {
        return scriptUri;
    }

    public void setScriptUri(String scriptUri) {
        this.scriptUri = scriptUri;
    }

    public Boolean getSkipModify() {
        return skipModify;
    }

    public void setSkipModify(Boolean skipModify) {
        this.skipModify = skipModify;
    }

    public Boolean getAutoScale() {
        return autoScale;
    }

    public void setAutoScale(Boolean autoScale) {
        this.autoScale = autoScale;
    }

    public Boolean getHeadless() {
        return headless;
    }

    public void setHeadless(Boolean headless) {
        this.headless = headless;
    }

    public String getLocalEtc() {
        return localEtc;
    }

    public void setLocalEtc(String localEtc) {
        this.localEtc = localEtc;
    }

    public String getLsfTop() {
        return lsfTop;
    }

    public void setLsfTop(String lsfTop) {
        this.lsfTop = lsfTop;
    }

    public Integer getNumPGroups() {
        return numPGroups;
    }

    public void setNumPGroups(Integer numPGroups) {
        this.numPGroups = numPGroups;
    }

    @Override
    public String toString() {
        return ProvUtil.toJsonStr(this);
    }
}
