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


package com.ibm.spectrum.oc.action;

import com.ibm.spectrum.oc.model.Entity;

public interface IAction {
    Entity getAvailableTemplates(Entity req);

    Entity getAvailableMachines(Entity req);

    Entity getReturnRequests(Entity req);

    Entity requestMachines(Entity req);

    Entity requestReturnMachines(Entity req);

    Entity getRequestStatus(Entity req);
}
