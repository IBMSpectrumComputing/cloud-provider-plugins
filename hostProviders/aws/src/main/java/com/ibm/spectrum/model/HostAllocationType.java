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

package com.ibm.spectrum.model;


/**
*
* @Description: Define values of Host Allocation Types: OnDemand Instances or Spot Instances
* @author omara
* @date March 16, 2017
* @version 1.0
*/
public enum HostAllocationType {


    OnDemand("onDemand"),
    Spot("spot");

    private String value;
    /**
    *
    */
    private HostAllocationType(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return this.value;
    }

    /**
    * Use this in place of valueOf.
    *
    * @param value
    *        real value
    * @return AllocationStrategy corresponding to the value
    */
    public static HostAllocationType fromValue(String value) {
        if (value == null || "".equals(value)) {
            throw new IllegalArgumentException("Value cannot be null or empty!");
        }

        for (HostAllocationType enumEntry : HostAllocationType.values()) {
            if (enumEntry.toString().equalsIgnoreCase(value)) {
                return enumEntry;
            }
        }

        throw new IllegalArgumentException("Cannot create enum from " + value + " value!");
    }

}
