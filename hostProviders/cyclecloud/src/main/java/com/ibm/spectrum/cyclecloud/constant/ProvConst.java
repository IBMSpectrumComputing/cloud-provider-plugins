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

package com.ibm.spectrum.cyclecloud.constant;

import okhttp3.MediaType;

/**
* @Class Name : ProvConst
* @Description: Cycle Cloud Provider Constants
* @Author     : Yudong (Dom) Wang
* @Date       : 2019-5-29 11:27:22
* @Version    : V1.0
*/
public final class ProvConst {
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public static final String AUTH = "Authorization";

    public static final String LOG_LEVEL = "LogLevel";

    public static final String CC_VM_TERMINATION_TIMEOUT = "AZURE_CC_VM_TERMINATION_TIMEOUT";

    public static final String CC_VM_CREATION_TIMEOUT = "AZURE_CC_VM_CREATION_TIMEOUT";

    public static final String CC_SERVER = "AZURE_CC_SERVER";

    public static final String CC_CLUSTER = "AZURE_CC_CLUSTER";

    public static final String CC_REGION = "AZURE_CC_REGION";

    public static final String CC_USER = "AZURE_CC_USER";

    public static final String CC_PASSWD = "AZURE_CC_PASSWORD";

    public static final String CC_LOG_PATH = "com.ibm.spectrum";

    public static final String JSON_CC_CONF = "cyclecloudprov_config.json";

    public static final String JSON_CC_TPL = "cyclecloudprov_templates.json";

    public static final String JSON_CC_API = "prov_api.json";

    public static final String JSON_CC_DATA = "-db.json";

    public static final String JSON_CC_MSG = "prov_msg.json";

    public static final String PROV_USAGE = "prov-usage.txt";

    public static final String UTF_8 = "UTF-8";

    public static final long ONE_SECOND = 1000L;

    public static final long THIRTY_SECONDS = 30 * ONE_SECOND;

    public static final String PATH_CLUSTER = "{cluster}";

    public static final String PATH_ID = "{id}";

    public static final String REQ_ID_CREATE = "IDCREATE";

    public static final String REQ_ID_DELETE = "IDDELETE";

    public static final String TEMPLATE = "template";

    public static final String DATA_DIR = "PRO_DATA_DIR";

    public static final String CONF_DIR = "PRO_CONF_DIR";

    public static final String PROV_NAME = "PROVIDER_NAME";

    public static final long ONE_MINUTE = 60 * 1000;

    public static final long ONE_HOUR = 60 * ONE_MINUTE;

    public static final long ONE_DAY = 24 * ONE_HOUR;

    public static final long AZURE_CC_VM_TERMINATION_TIMEOUT = 60 * ONE_MINUTE;

    public static final long AZURE_CC_VM_CREATION_TIMEOUT = 35 * ONE_MINUTE;

    public static final int PRIORITY_BASE = 10;

    public static final String NUMERIC = "Numeric";

    public static final String SCRIPT_URI = "custom_script_uri";

    public static final String ZONE = "zone";

    public static final String CONF = "conf";

    public static final String CMD_GETENT = "getent hosts ";

    public static final String RC_ACCOUNT = "rc_account";

    public static final String TEMPLATE_ID = "template_id";
}
