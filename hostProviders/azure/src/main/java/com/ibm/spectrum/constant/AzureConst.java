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

package com.ibm.spectrum.constant;

/**
* @ClassName: AzureConst
* @Description: Define constants
* @author xawangyd
* @date Jan 26, 2016 3:22:45 PM
* @version 1.0
*/
public class AzureConst {

    public final static String AZURE_KEY_NAME = "lsf-key";

    public final static String AZURE_USER_DATA_FILE = "/scripts/user_data.sh";

    public final static String AZURE_ROOT_USER_NAME = "lsfuser";

    public final static String AZURE_HOSTNAME_PREFIX = "host-";

    public final static String AZURE_RESOURCE_GROUP_PREFIX = "rsg-";

    public final static String AZURE_NET_INTERFACE_PREFIX = "nic";


    public final static String LINUX_CUSTOM_SCRIPT_EXTENSION_NAME = "CustomScriptForLinux";
    public final static String LINUX_CUSTOM_SCRIPT_PUBLISHER_NAME = "Microsoft.OSTCExtensions";
    public final static String LINUX_CUSTOM_SCRTPT_EXTENSION_TYPE_NAME = "CustomScriptForLinux";
    public final static String LINUX_CUSTOM_SCSIPT_EXENTIONS_VERSION_NAME = "1.4";

    public static final int INSTANCE_CREATION_TIMEOUT_MINUTES = 15;

    public static final int INSTANCE_CREATION_TIMEOUT_SECONDS = INSTANCE_CREATION_TIMEOUT_MINUTES * 60;

    // do not query the new created VM in seconds, default 2m.
    public final static long AZURE_QUERY_NEW_CREATED_VM_TIMEOUT = 120;



    public static final String EBROKERD_STATE_RUNNING = "running";

    public static final String EBROKERD_STATE_COMPLETE = "complete";

    public static final String EBROKERD_STATE_COMPLETE_WITH_ERROR = "complete_with_error";

    public static final String EBROKERD_STATE_WARNING = "warning";

    public static final String EBROKERD_STATE_ERROR = "error";

    public static final String EBROKERD_MACHINE_RESULT_SUCCEED = "succeed";

    public static final String EBROKERD_MACHINE_RESULT_FAIL = "fail";

    public static final String EBROKERD_MACHINE_RESULT_EXECUTING = "executing";


    public final static String RETURN_REQUEST_PREFIX = "ret-";

}
