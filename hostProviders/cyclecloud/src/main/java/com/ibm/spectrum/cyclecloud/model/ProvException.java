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

import com.ibm.spectrum.cyclecloud.enums.ProvCode;
import com.ibm.spectrum.cyclecloud.enums.ProvStatus;

/**
* @Class Name : ProvException
* @Description: Provider exception
* @Author     : Yudong (Dom) Wang
* @Date       : 2019-6-3 17:14:52
* @Version    : V1.0
*/
public class ProvException extends Exception {
    private static final long serialVersionUID = 2676619814640164672L;

    private Integer code;

    private String msg;

    private String status;

    /**
    *
    * @Title      : ProvException
    * @Description: constructor
    * @Param      : @param ps
    * @Param      : @param pc
     */
    public ProvException(ProvStatus ps, ProvCode pc) {
        this.code = pc.value();
        this.msg = pc.message();
        this.status = ps.value();
    }

    /**
    *
    * @Title      : ProvException
    * @Description: constructor
    * @Param      : @param pcode
    * @Param      : @param args
     */
    public ProvException(ProvCode pcode, Object... args) {
        this.code = pcode.value();
        this.msg = String.format(pcode.message(), args);
        this.status = pcode.status().value();
    }

    /**
    * @Title      : ProvException
    * @Description: constructor
    * @Param      : @param code
    * @Param      : @param msg
    * @Param      : @param status
    */
    public ProvException(Integer code, String msg, String status) {
        this.code = code;
        this.msg = msg;
        this.status = status;
    }

    public Integer code() {
        return this.code;
    }

    public String message() {
        return this.msg;
    }

    public String status() {
        return this.status;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ProvException:");
        builder.append("\n code: ");
        builder.append(code);
        builder.append("\n status: ");
        builder.append(status);
        builder.append("\n message: ");
        builder.append(msg);
        return builder.toString();
    }
}
