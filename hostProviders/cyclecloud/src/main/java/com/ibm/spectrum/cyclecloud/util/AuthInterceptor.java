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


package com.ibm.spectrum.cyclecloud.util;

import java.io.IOException;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ibm.spectrum.cyclecloud.constant.ProvConst;
import com.ibm.spectrum.cyclecloud.model.ProvConfig;

import okhttp3.Credentials;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
* @Class Name : AuthInterceptor
* @Description: Basic authentication interceptor
* @Author     : Yudong (Dom) Wang
* @Date       : 2019-5-30 10:05:22
* @Version    : V1.0
*/
public class AuthInterceptor implements Interceptor {
    private static Logger log = LogManager.getLogger(AuthInterceptor.class);

    /**
     * Basic authentication credentials
     */
    private String credentials = StringUtils.EMPTY;

    /**
    *
    * @Title      : BasicAuthInterceptor
    * @Description: constructor
    * @Param      : @param user
    * @Param      : @param password
     */
    public AuthInterceptor() {
        if (StringUtils.isNotBlank(credentials)) {
            log.warn("Empty credentials.");
            return;
        }
        ProvConfig config = ProvUtil.getConfig();
        if (null == config) {
            log.error("Failed to get cycle cloud credentials.");
            return;
        }
        this.credentials = Credentials.basic(config.getUser(), config.getPassword());
    }

    public Response intercept(Chain chain) throws IOException {
        Request authReq = chain.request().newBuilder().header(ProvConst.AUTH, credentials).build();
        return chain.proceed(authReq);
    }
}
