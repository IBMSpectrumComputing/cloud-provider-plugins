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

import com.ibm.spectrum.cyclecloud.model.ProvMsg;
import com.ibm.spectrum.cyclecloud.util.ProvUtil;

/**
* @Class Name : ProvCode
* @Description: Provider error code
* @Author     : Yudong (Dom) Wang
* @Date       : 2019-5-30 16:47:16
* @Version    : V1.0
*/
public enum ProvCode {
    OK(0, ProvStatus.COMPLETE),
    ERR_INNER(1, ProvStatus.ERR),
    EMPTY(2, ProvStatus.WARN),
    NOT_EXIST_FILE(3, ProvStatus.ERR),
    BAD_JSON_FILE(4, ProvStatus.ERR),
    UNSUPPORTED_API(5, ProvStatus.ERR),
    BAD_TEMPLATE(6, ProvStatus.WARN),
    NOT_EXIST_TEMPLATE(7, ProvStatus.WARN),
    BAD_NODE_COUNT(8, ProvStatus.WARN),
    ERR_CC_RESPONSE(9, ProvStatus.WARN),
    UNRECOGNIZED_CC_RESPONSE(10, ProvStatus.WARN),
    ERR_CC_REQUEST(11, ProvStatus.WARN),
    UNSUPPORTED_REQ(12, ProvStatus.COMPLETE_WITH_ERR),
    ERR_CC_NODE(13, ProvStatus.COMPLETE_WITH_ERR),
    ERR_NODE_CREATION(14, ProvStatus.COMPLETE_WITH_ERR),
    ERR_NODE_TERMINATION(15, ProvStatus.COMPLETE_WITH_ERR),
    BAD_OPTION(16, ProvStatus.ERR),
    UNRECOGNIZED_JSON(17, ProvStatus.WARN),
    ERR_READ_FILE(18, ProvStatus.WARN),
    ERR_EXEC_API(19, ProvStatus.WARN),
    NULL_DATA_DIR(20, ProvStatus.WARN),
    NULL_CONF_DIR(21, ProvStatus.WARN),
    ERR_CREATE_FILE(22, ProvStatus.WARN),
    NOT_FOUND_API(23, ProvStatus.WARN),
    CREATION_TIMEOUT(24, ProvStatus.COMPLETE_WITH_ERR),
    TERMINATION_TIMEOUT(25, ProvStatus.COMPLETE_WITH_ERR),
    NOT_EXIST_NODE(26, ProvStatus.COMPLETE_WITH_ERR);

    private int code;

    private ProvStatus status;

    ProvCode(int code, ProvStatus status) {
        this.code = code;
        this.status = status;
    }

    public int value() {
        return this.code;
    }

    public ProvStatus status() {
        return this.status;
    }

    public String message() {
        ProvMsg msg = ProvUtil.getMsg(code);
        return msg.getMsg();
    }
}
