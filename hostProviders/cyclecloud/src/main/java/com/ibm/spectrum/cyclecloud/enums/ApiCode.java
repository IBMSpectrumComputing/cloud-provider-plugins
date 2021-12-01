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
* @Class Name : ApiCode
* @Description: Restful API code
* @Author     : Yudong (Dom) Wang
* @Date       : 2019-5-30 14:27:22
* @Version    : V1.0
*/
public enum ApiCode {
    NODE_CREATION(1),
    NODE_REMOVAL(2),
    NODE_TERMINATION(3),
    NODE_QUERY_BY_REQ_ID(4),
    CLUSTER_QUERY(5),
    NODE_QUERY_ALL(6);

    private int apiId;

    ApiCode(int apiId) {
        this.apiId = apiId;
    }

    public int value() {
        return apiId;
    }
}
