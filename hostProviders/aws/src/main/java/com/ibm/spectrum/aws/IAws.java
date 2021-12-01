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

package com.ibm.spectrum.aws;

import com.ibm.spectrum.model.AwsEntity;

/**
* @ClassName: IAws
* @Description: The interface of AWS host provider
* @author xawangyd
* @date Jan 26, 2016 3:43:58 PM
* @version 1.0
*/
public interface IAws {
    /**
    *
    *
    * @Title: getAvailableTemplates
    * @Description: retrieve information about the available templates from the reosurce provider
    * @param req
    * @param @return
    * @return AwsEntity
    * @throws
     */
    AwsEntity getAvailableTemplates(AwsEntity req);

    /**
    *
    *
    * @Title: getAvailableMachines
    * @Description: Retrieve information about the available machines from the resource provider
    * @param @param req
    * @param @return
    * @return AwsEntity
    * @throws
     */
    AwsEntity getAvailableMachines(AwsEntity req);

    /**
    *
    *
    * @Title: getReturnRequests
    * @Description: Retrieve information if the resource provider wants any machines back.
    * @param @param req
    * @param @return
    * @return AwsEntity
    * @throws
     */
    AwsEntity getReturnRequests(AwsEntity req);

    /**
    *
    *
    * @Title: requestMachines
    * @Description: Raise a request to get machines from the resource provider
    * @param @param reqMachines
    * @param @return
    * @return req
    * @throws
     */
    AwsEntity requestMachines(AwsEntity req);

    /**
    *
    *
    * @Title: requestReturnMachines
    * @Description: Raise a request to return machines back to the resource provider
    * @param @param req
    * @param @return
    * @return AwsEntity
    * @throws
     */
    AwsEntity requestReturnMachines(AwsEntity req);

    /**
    *
    * @Title: getRequestStatus
    * @Description: Retrieve status of particular interface calling, etc. requestMachines and requestReturnMachines
    * @param @param req
    * @param @return
    * @return AwsEntity
    * @throws
     */
    AwsEntity getRequestStatus(AwsEntity req);
}
