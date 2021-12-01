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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.*;

/**
 * @ClassName: GcloudUserData
 * @Description: TODO
 * @author zcg
 * @date Sep 11, 2017 5:15:56 PM
 * @version 1.0
 */
public class GcloudUserData {
    @JsonInclude(Include.NON_NULL)
    private String volumes;

    @JsonInclude(Include.NON_NULL)
    private String packages;

    public GcloudUserData() {

    }

    public GcloudUserData(Properties p) {
        this.volumes = p.getProperty("volumes");
        this.packages = p.getProperty("packages");
    }

    /**
     * @return volumes
     */
    public String getVolumes() {
        return volumes;
    }

    /**
     * @param volumes
     *            the volumes to set
     */
    public void setVolumes(String volumes) {
        this.volumes = volumes;
    }

    /**
     * @return packages
     */
    public String getPackages() {
        return packages;
    }

    /**
     * @param packages
     *            the packages to set
     */
    public void setPackages(String packages) {
        this.packages = packages;
    }

    /**
     * (Non Javadoc)
     * <p>
     * Title: toString
     * </p>
     * <p>
     * Description:
     * </p>
     *
     * @return
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("GcloudUserData [volumes=");
        builder.append(volumes);
        builder.append(", packages=");
        builder.append(packages);
        builder.append("]");
        return builder.toString();
    }

}
