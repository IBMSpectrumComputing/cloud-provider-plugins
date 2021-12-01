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

import java.util.List;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * @ClassName: AzureConfig
 * @Description: The configuration of Azure EC2 virtual machine
 * @author prachi
 * @date July 8, 2016 11:11:00 AM
 * @version 1.0
 */
public class AzureConfig {
    /**
     * Required and value type is string. specify the log level
     */
    @JsonProperty("LogLevel")
    private String logLevel;

    /**
     * Required and type is string, specify the Path of the credentials file
     * which has the access key and secret key information
     */
    @JsonProperty("AZURE_CREDENTIAL_FILE")
    private String AzureCredentialFile;

    /**
     * Required and type is string, specify the region information (in which
     * location the machines should be created)
     */
    @JsonProperty("AZURE_REGION")
    private String AzureRegion;

    /**
     * Required and type is string, specify the Path of the Key file used to ssh
     * into the newly created VMs
     */
    @JsonProperty("AZURE_KEY_FILE")
    private String AzureKeyFile;

    /**
     * Optional and type is integer. Timeout for an instance creation from
     * beginning to finish (succeed or fail). Time in minutes. Default: 10
     * minutes.
     */
    @JsonProperty("INSTANCE_CREATION_TIMEOUT")
    @JsonInclude(Include.NON_NULL)
    private Integer instanceCreationTimeout;

    /**
     * <p>
     * Title:
     * </p>
     * <p>
     * Description:
     * </p>
     */
    public AzureConfig() {
    }

    /**
     * <p>
     * Title:
     * </p>
     * <p>
     * Description:
     * </p>
     *
     * @param t
     */
    public AzureConfig(AzureConfig c) {
        this.logLevel = c.getLogLevel();
        this.AzureCredentialFile = c.getAzureCredentialFile();
        this.AzureRegion = c.getAzureRegion();
        this.AzureKeyFile = c.getAzureKeyFile();
    }

    /**
     * @return logLevel
     */
    public String getLogLevel() {
        return logLevel;
    }

    /**
     * @param logLevel
     *            the logLevel to set
     */
    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    /**
     * @return AzureCredentialFile
     */
    public String getAzureCredentialFile() {
        return AzureCredentialFile;
    }

    /**
     * @param AzureCredentialFile
     *            the AzureCredentialFile to set
     */
    public void setAzureCredentialFile(String AzureCredentialFile) {
        this.AzureCredentialFile = AzureCredentialFile;
    }

    /**
     * @return AzureRegion
     */
    public String getAzureRegion() {
        return AzureRegion;
    }

    /**
     * @param AzureRegion
     *            the AzureRegion to set
     */
    public void setAzureRegion(String AzureRegion) {
        this.AzureRegion = AzureRegion;
    }

    /**
     * @return AzureKeyFile
     */
    public String getAzureKeyFile() {
        return AzureKeyFile;
    }

    /**
     * @param AzureKeyFile
     *            the AzureKeyFile to set
     */
    public void setAzureKeyFile(String AzureKeyFile) {
        this.AzureKeyFile = AzureKeyFile;
    }

    /**
     * @return the instanceCreationTimeout
     */
    public Integer getInstanceCreationTimeout() {
        return instanceCreationTimeout;
    }

    /**
     * @param instanceCreationTimeout
     *            the instanceCreationTimeout to set
     */
    public void setInstanceCreationTimeout(Integer instanceCreationTimeout) {
        this.instanceCreationTimeout = instanceCreationTimeout;
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
        builder.append("AzureConfig [logLevel=");
        builder.append(logLevel);
        builder.append(", AzureCredentialFile=");
        builder.append(AzureCredentialFile);
        builder.append(", AzureRegion=");
        builder.append(AzureRegion);
        builder.append(", AzureKeyFile=");
        builder.append(AzureKeyFile);
        builder.append(", instanceCreationTimeout=");
        builder.append(instanceCreationTimeout);
        builder.append("]");
        return builder.toString();
    }

}
