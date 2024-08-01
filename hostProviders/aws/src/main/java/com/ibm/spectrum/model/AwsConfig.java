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
* @ClassName: AwsConfig
* @Description: The configuration of AWS EC2 virtual machine
* @author prachi
* @date July 8, 2016 11:11:00 AM
* @version 1.0
*/
public class AwsConfig {
    /**
     * Required and value type is string. specify the log level
     */
    @JsonProperty("LogLevel")
    private String logLevel;

    /**
     * Required and type is string, specify the Path of the credentials file
     * which has the access key and secret key information
     */
    @JsonProperty("AWS_CREDENTIAL_FILE")
    private String awsCredentialFile;

    /**
     * Required and type is string, specify
     * the region information (in which location the machines
     * should be created)
     */
    @JsonProperty("AWS_REGION")
    private String awsRegion;

    /**
     * Required and type is string, specify the Path of the Key file
     * used to ssh into the newly created VMs
     */
    @JsonProperty("AWS_KEY_FILE")
    private String awsKeyFile;

    /**
     * Required and type is string, specify the Path of the script file
     * used to create temperary STS for supporting Federated accounts
     * The parameter can either have a script path or empty value.
     */
    @JsonProperty("AWS_CREDENTIAL_SCRIPT")
    private String awsCredentialScript;

    /**
     * Required and type is string, specify the duration until when
     * the STScredentials are valid and need to renew it
     * The parameter can either have a value in seconds or empty value.
     */
    @JsonProperty("AWS_CREDENTIAL_RENEW_MARGIN")
    private String awsCredentialMargin;

    /**
     * Optional and type is Boolean (true/false). Default: false. For spot instances, if true,
     * proactively terminate instances that are scheduled for termination by AWS. By default
     * let AWS terminate such instances on its own time, which is cheaper (partial hours are
     * not billed in this case).
     */
    @JsonProperty("AWS_SPOT_TERMINATE_ON_RECLAIM")
    @JsonInclude(Include.NON_NULL)
    private Boolean spotTerminateOnReclaim = new Boolean(false);

    /**
     * Optional and type is integer. Timeout for an instance creation from beginning
     * to finish (succeed or fail). Time in minutes. Default: 10 minutes.
     */
    @JsonProperty("INSTANCE_CREATION_TIMEOUT")
    @JsonInclude(Include.NON_NULL)
    private Integer instanceCreationTimeout;

    /**
     * Optional and type is Boolean (true/false). Default: false.
     * if true, AWS plugin will add the tag of "InstanceID" to new created instances on
     *          both the instance and its ebs volumes.
     * if false, AWS plugin will not add "InstanceID" to new created instances.
     */
    @JsonProperty("AWS_TAG_InstanceID")
    @JsonInclude(Include.NON_NULL)
    private Boolean tagInstanceID = new Boolean(false);

    /**
     * Optional and type is string, specify
     * the service endpoint either with or without the
     * protocol (e.g. https://sns.us-west-1.amazonaws.com:443
     * or sns.us-west-1.amazonaws.com)
     */
	@JsonProperty("AWS_ENDPOINT_URL")
    private String awsEndpointUrl;


	/**
    * <p>Title: </p>
    * <p>Description: </p>
    */
    public AwsConfig() {
    }

    /**
    * <p>Title: </p>
    * <p>Description: </p>
    * @param t
    */
    public AwsConfig(AwsConfig c) {
        this.logLevel = c.getLogLevel();
        this.awsCredentialFile = c.getAwsCredentialFile();
        this.awsRegion = c.getAwsRegion();
        this.awsEndpointUrl = c.getAwsEndpointUrl();
        this.awsKeyFile = c.getAwsKeyFile();
        this.awsCredentialScript = c.getAwsCredentialScript();
        this.awsCredentialMargin = c.getAwsCredentialMargin();
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
    * @return awsCredentialFile
    */
    public String getAwsCredentialFile() {
        return awsCredentialFile;
    }

    /**
     * @param awsCredentialFile the awsCredentialFile to set
     */
    public void setAwsCredentialFile(String awsCredentialFile) {
        this.awsCredentialFile = awsCredentialFile;
    }

    /**
    * @return awsRegion
    */
    public String getAwsRegion() {
        return awsRegion;
    }

    /**
     * @param awsRegion the awsRegion to set
     */
    public void setAwsRegion(String awsRegion) {
        this.awsRegion = awsRegion;
    }

    /**
    * @return awsKeyFile
    */
    public String getAwsKeyFile() {
        return awsKeyFile;
    }

    /**
     * @param awsKeyFile the awsKeyFile to set
     */
    public void setAwsKeyFile(String awsKeyFile) {
        this.awsKeyFile = awsKeyFile;
    }

    /**
    * @return awsCredentialScript
    */
    public String getAwsCredentialScript() {
        return awsCredentialScript;
    }

    /**
     * @param awsCredentialScript the awsCredentialScript to set
     */
    public void setAwsCredentialScript(String awsCredentialScript) {
        this.awsCredentialScript = awsCredentialScript;
    }

    /**
    * @return awsCredentialMargin
    */
    public String getAwsCredentialMargin() {
        return awsCredentialMargin;
    }

    /**
     * @param awsCredentialMargin the awsCredentialMargin to set
     */
    public void setAwsCredentialMargin(String awsCredentialMargin) {
        this.awsCredentialMargin = awsCredentialMargin;
    }

    /**
    * @return spotTerminateOnReclaim Boolean
    */
    public Boolean isSpotTerminateOnReclaim() {
        return spotTerminateOnReclaim;
    }

    /**
     * @param spotTerminateOnReclaim Boolean value to set
     */
    public void setSpotTerminateOnReclaim(Boolean spotTerminateOnReclaim) {
        this.spotTerminateOnReclaim = spotTerminateOnReclaim;
    }

    /**
     * @return the instanceCreationTimeout
     */
    public Integer getInstanceCreationTimeout() {
        return instanceCreationTimeout;
    }

    /**
     * @param instanceCreationTimeout the instanceCreationTimeout to set
     */
    public void setInstanceCreationTimeout(Integer instanceCreationTimeout) {
        this.instanceCreationTimeout = instanceCreationTimeout;
    }

    public Boolean getTagInstanceID() {
        return tagInstanceID;
    }

    public void setTagInstanceID(Boolean tagInstanceID) {
        this.tagInstanceID = tagInstanceID;
    }


    /**
    * @return awsEndpointUrl
    */
    public String getAwsEndpointUrl()
    {
        return awsEndpointUrl;
    }

    /**
     * @param awsEndpointUrl the endpoint to set
     */
    public void setAwsEndpointUrl(String awsEndpointUrl)
    {
        this.awsEndpointUrl = awsEndpointUrl;
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
        builder.append("AwsConfig [logLevel=");
        builder.append(logLevel);
        builder.append(", awsCredentialFile=");
        builder.append(awsCredentialFile);
        builder.append(", awsRegion=");
        builder.append(awsRegion);
        builder.append(", awsEndpointUrl=");
        builder.append(awsEndpointUrl);
        builder.append(", awsKeyFile=");
        builder.append(awsKeyFile);
        builder.append(", awsCredentialScript=");
        builder.append(awsCredentialScript);
        builder.append(", awsCredentialMargin=");
        builder.append(awsCredentialMargin);
        builder.append(", spotTerminateOnReclaim=");
        builder.append(spotTerminateOnReclaim);
        builder.append(", instanceCreationTimeout=");
        builder.append(instanceCreationTimeout);
        builder.append(", tagInstanceID=");
        builder.append(tagInstanceID);
        builder.append("]");
        return builder.toString();
    }

}
