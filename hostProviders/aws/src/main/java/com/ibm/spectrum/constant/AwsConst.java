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

import java.util.Arrays;
import java.util.List;

/**
* @ClassName: AwsConst
* @Description: Define constants
* @author xawangyd
* @date Jan 26, 2016 3:22:45 PM
* @version 1.0
*/
public class AwsConst {

    public final static String AWS_KEY_NAME = "lsf-key";

    public final static String AWS_USER_DATA_FILE = "/scripts/user_data.sh";

    public static final int REQUEST_VALIDITY_HOURS = 0;

    public static final int REQUEST_VALIDITY_MINUTES = 30;

    public static final int INSTANCE_CREATION_TIMEOUT_MINUTES = 10;

    public static final String EBROKERD_STATE_RUNNING = "running";

    public static final String EBROKERD_STATE_COMPLETE = "complete";

    public static final String EBROKERD_STATE_COMPLETE_WITH_ERROR = "complete_with_error";

    public static final String EBROKERD_STATE_WARNING = "warning";

    public static final String EBROKERD_STATE_ERROR = "error";

    public static final String SPOT_INSTANTCE_STATUS_MARKED_FOR_TERMINATION = "marked-for-termination";

    public static final String SPOT_INSTANCE_STATUS_TERMINATED_BY_PRICE = "instance-terminated-by-price";

    public static final String SPOT_INSTANTCE_STATUS_TERMINATION_NO_CAPACITY = "instance-terminated-no-capacity";

    public static final long SPOT_INSTANCE_TERMINATION_NOTICE_PERIOD_IN_SECONDS = 120;

    public static final String RESERVED_TAG_PREFIX = "aws:";
    public static final String SPOT_REQUEST_PREFIX = "sfr-";

    public static final String ON_DEMAND_REQUEST_PREFIX = "r-";

    public static final String RETURN_REQUEST_PREFIX = "ret-";


    public static final String EBROKERD_MACHINE_RESULT_SUCCEED = "succeed";

    public static final String EBROKERD_MACHINE_RESULT_FAIL = "fail";

    public static List<String> markedForTerminationStates = Arrays.asList(
                new String[] { "marked-for-termination", "instance-terminated-no-capacity", "instance-terminated-by-price",
                               "instance-terminated-by-schedule", "instance-terminated-by-service", "instance-terminated-by-experiment"
                             });

}
