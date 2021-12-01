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
* @ClassName: GcloudConst
* @Description: Define constants
* @author zcg
* @date Sep 11, 2017 3:22:45 PM
* @version 1.0
*/
public class GcloudConst {

    public final static String APPLICATION_NAME = "GcloudTools";
    public final static String GCLOUD_KEY_NAME = "lsf-key";

    public final static String GCLOUD_USER_DATA_FILE = "/scripts/user_data.sh";

    public static final int REQUEST_VALIDITY_HOURS = 0;

    public static final int REQUEST_VALIDITY_MINUTES = 30;

    // Create VM request timeout in seconds, default 5m.
    public final static long GOOGLE_CREATE_VM_REQUEST_TIMEOUT = 300;

    // The vm created by RC, but not join lsf cluster(keep closed_RC) in seconds, default 10m.
    public final static long GOOGLE_CLEAN_RC_CLOSED_VM_TIMEOUT = 600;

    public static final String EBROKERD_STATE_RUNNING = "running";

    public static final String EBROKERD_STATE_COMPLETE = "complete";

    public static final String EBROKERD_STATE_WARNING = "warning";

    public static final String EBROKERD_STATE_ERROR = "error";

    public static final String EBROKERD_STATE_COMPLETE_WITH_ERROR = "complete_with_error";

    public static final String RESERVED_TAG_PREFIX = "cloud:";

    public static final String ON_DEMAND_REQUEST_PREFIX = "r-";

    public static final String RETURN_REQUEST_PREFIX = "ret-";

    public static final String EBROKERD_MACHINE_RESULT_SUCCEED = "succeed";

    public static final String EBROKERD_MACHINE_RESULT_FAIL = "fail";

    //this status is used to indicate the beginning status when
    //the create instance operation is PENDING
    public static final String INSTANCE_STATUS_BEGIN = "BEGINNING";

    //google labels must be lower case
    public static final String INSTANCE_RC_ACCOUNT_KEY = "rc_account";

    public static final String GOOGLE_API_PREFIX = "https://www.googleapis.com/compute/v1/projects/";

    public static final String GOOGLEPROV_CONFIG_FILENAME = "googleprov_config.json";

    public static final String GOOGLEPROV_TEMPLATE_FILENAME = "googleprov_templates.json";

    public static final String PRODUCT_OFFERING = "IBM_Spectrum_LSF";

    public static final String BULK_INSERT_LABEL_KEY = "bulk_id";

    public static final String BULK_INSERT_ID_PREFIX = "bulk-";

    public static final int MAXIMUM_VM_IN_ONE_REQUEST = 1000;

}
