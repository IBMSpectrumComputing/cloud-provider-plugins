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

package com.ibm.spectrum.util;

public class StringUtils {
    public static boolean isEmptyString(String str) {
        return str == null || str.trim().isEmpty();
    }

    public static boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }

    public static boolean isEqual(String str1, String str2) {
        if (str1 == null) {
            if (str2 == null) {
                // both strings are null
                return true;
            }

            // only one string is null
            return false;
        }

        if (str2 == null) {
            // only str1 is not null
            return false;
        }

        return str1.equals(str2);
    }
}

