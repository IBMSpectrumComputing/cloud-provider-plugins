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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
* @ClassName: GcloudConfig
* @Description: The configuration of Google Cloud  virtual machine
* @author zcg
* @date Sep 11, 2017 11:11:00 AM
* @version 1.0
*/
public class GcloudConfig {
    /**
     * Required and value type is string. specify the log level
     */
    @JsonProperty("LogLevel")
    private String logLevel;

    /**
     * Required and type is string, specify the Path of the credentials file
     * which has the access key and secret key information
     */
    @JsonProperty("GCLOUD_CREDENTIAL_FILE")
    private String credentialFile;

    /**
     * Required and type is string, specify the Google Cloud Project ID
     */
    @JsonProperty("GCLOUD_PROJECT_ID")
    private String projectID;

    /**
     * Optional and type is boolean, specify the flag to true if want to disable bulkInsert function
     */
    @JsonProperty("GCLOUD_BULK_INSERT")
    @JsonInclude(Include.NON_NULL)
    private Boolean bulkInsertEnabled;

    /**
     * Set this as default region when you want to using regional bulk API
     */
    @JsonProperty("GCLOUD_REGION")
    private String gcloudRegion;
    
    /**
     * Optional and type is int, specify the http connect timeout (in seconds)
     */
    @JsonProperty("HTTP_CONNECT_TIMEOUT")
    @JsonInclude(Include.NON_NULL)
    private Integer httpConnectTimeout;

	/**
     * Optional and type is int, specify the http read timeout (in seconds)
     */
    @JsonProperty("HTTP_READ_TIMEOUT")
    @JsonInclude(Include.NON_NULL)
    private Integer httpReadTimeout;
 
	/**
    * <p>Title: </p>
    * <p>Description: </p>
    */
    public GcloudConfig() {
    }

    /**
    * <p>Title: </p>
    * <p>Description: </p>
    * @param t
    */
    public GcloudConfig(GcloudConfig c) {
        this.logLevel = c.getLogLevel();
        this.credentialFile = c.getCredentialFile();
        this.projectID = c.getProjectID();
        this.bulkInsertEnabled = c.getBulkInsertEnabled();
        this.gcloudRegion = c.getGcloudRegion();
        this.httpConnectTimeout = c.getHttpConnectTimeout();
        this.httpReadTimeout = c.getHttpReadTimeout();
    }

    /**
    * @return logLevel
    */
    public String getLogLevel() {
        return logLevel;
    }

    /**
     * @param logLevel the logLevel to set
     */
    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    /**
    * @return gcloudCredentialFile
    */
    public String getCredentialFile() {
        return credentialFile;
    }

    /**
     * @param gcloudCredentialFile the gcloudCredentialFile to set
     */
    public void setCredentialFile(String credentialFile) {
        this.credentialFile = credentialFile;
    }

    /**
     * @return projectID
     */
    public String getProjectID() {
        return projectID;
    }

    /**
     * @param projectID the Google cloud projct ID to set
     */
    public void setProjectID(String projectID) {
        this.projectID = projectID;
    }

    /**
     *
     * @return bulkInsertEnabled
     */
    public Boolean getBulkInsertEnabled () {
        return bulkInsertEnabled;
    }

    /**
     *
     * @param bulkInsertEnabled the flag to enable or disable bulkInsert function
     */
    public void setbulkInsertEnabled(Boolean bulkInsertEnabled) {
        this.bulkInsertEnabled = bulkInsertEnabled;
    }

    /**
     *
     * @return gcloudRegion
     */
    public String getGcloudRegion () {
        return gcloudRegion;
    }

    /**
     *
     * @param gcloudRegion the default region to set
     */
    public void setGcloudRegion(String gcloudRegion) {
        this.gcloudRegion = gcloudRegion;
    }

    
    /**
    * @return httpConnectTimeout
    */
    public Integer getHttpConnectTimeout() {
		return httpConnectTimeout;
	}

    /**
     * @param httpConnectTimeout to set (in seconds)
     */
	public void setHttpConnectTimeout(Integer httpConnectTimeout) {
		this.httpConnectTimeout = httpConnectTimeout;
	}

    
    /**
    * @return httpReadTimeout
    */
    public Integer getHttpReadTimeout() {
		return httpReadTimeout;
	}

    /**
     * @param httpReadTimeout to set (in seconds)
     */
	public void setHttpReadTimeout(Integer httpReadTimeout) {
		this.httpReadTimeout = httpReadTimeout;
	}


    /** (Non Javadoc)
    * <p>Title: toString</p>
    * <p>Description: </p>
    * @return
    * @see java.lang.Object#toString()
    */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("GcloudConfig [logLevel=");
        builder.append(logLevel);
        builder.append(", credentialFile=");
        builder.append(credentialFile);
        builder.append(", projectID=");
        builder.append(projectID);
        builder.append(", gcloudRegion=");
        builder.append(gcloudRegion);
        builder.append(", httpConnectTimeout=");
        builder.append(httpConnectTimeout);
        builder.append(", httpReadTimeout=");
        builder.append(httpReadTimeout);
        if (bulkInsertEnabled != null) {
            builder.append(", bulkInsertEnabled=");
            builder.append(bulkInsertEnabled);
        }
        builder.append("]");
        return builder.toString();
    }

}
