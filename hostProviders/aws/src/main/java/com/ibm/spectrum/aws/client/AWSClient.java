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

package com.ibm.spectrum.aws.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.ActiveInstance;
import com.amazonaws.services.ec2.model.ActivityStatus;
import com.amazonaws.services.ec2.model.BatchState;
import com.amazonaws.services.ec2.model.CreateFleetRequest;
import com.amazonaws.services.ec2.model.CreateFleetResult;
import com.amazonaws.services.ec2.model.CreateKeyPairRequest;
import com.amazonaws.services.ec2.model.CreateKeyPairResult;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.CreateTagsResult;
import com.amazonaws.services.ec2.model.DefaultTargetCapacityType;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeKeyPairsRequest;
import com.amazonaws.services.ec2.model.DescribeKeyPairsResult;
import com.amazonaws.services.ec2.model.DescribeSpotFleetInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeSpotFleetInstancesResult;
import com.amazonaws.services.ec2.model.DescribeSpotFleetRequestsRequest;
import com.amazonaws.services.ec2.model.DescribeSpotFleetRequestsResult;
import com.amazonaws.services.ec2.model.DescribeSpotInstanceRequestsRequest;
import com.amazonaws.services.ec2.model.DescribeSpotInstanceRequestsResult;
import com.amazonaws.services.ec2.model.EbsInstanceBlockDevice;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.FleetActivityStatus;
import com.amazonaws.services.ec2.model.FleetData;
import com.amazonaws.services.ec2.model.FleetLaunchTemplateConfig;
import com.amazonaws.services.ec2.model.FleetLaunchTemplateConfigRequest;
import com.amazonaws.services.ec2.model.FleetLaunchTemplateOverridesRequest;
import com.amazonaws.services.ec2.model.FleetType;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceBlockDeviceMapping;
import com.amazonaws.services.ec2.model.InstanceLifecycleType;
import com.amazonaws.services.ec2.model.InstanceState;
import com.amazonaws.services.ec2.model.InstanceStateChange;
import com.amazonaws.services.ec2.model.InstanceStatus;
import com.amazonaws.services.ec2.model.KeyPair;
import com.amazonaws.services.ec2.model.KeyPairInfo;
import com.amazonaws.services.ec2.model.Placement;
import com.amazonaws.services.ec2.model.RequestSpotFleetRequest;
import com.amazonaws.services.ec2.model.RequestSpotFleetResult;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.SpotFleetLaunchSpecification;
import com.amazonaws.services.ec2.model.SpotFleetRequestConfig;
import com.amazonaws.services.ec2.model.SpotFleetRequestConfigData;
import com.amazonaws.services.ec2.model.SpotInstanceRequest;
import com.amazonaws.services.ec2.model.SpotInstanceState;
import com.amazonaws.services.ec2.model.SpotInstanceStatus;
import com.amazonaws.services.ec2.model.SpotOptionsRequest;
import com.amazonaws.services.ec2.model.TagSpecification;
import com.amazonaws.services.ec2.model.TargetCapacitySpecificationRequest;
import com.amazonaws.services.ec2.model.TargetCapacityUnitType;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.ResourceType;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesResult;
import com.amazonaws.services.ec2.model.InstanceNetworkInterfaceSpecification;
import com.amazonaws.services.ec2.model.InstanceNetworkInterface;
import com.amazonaws.services.ec2.model.LaunchTemplateConfig;
import com.amazonaws.services.ec2.model.DeleteLaunchTemplateRequest;
import com.amazonaws.services.ec2.model.DeleteLaunchTemplateResult;
import com.amazonaws.services.ec2.model.CreateLaunchTemplateRequest;
import com.amazonaws.services.ec2.model.RequestLaunchTemplateData;
import com.amazonaws.services.ec2.model.CreateLaunchTemplateResult;
import com.amazonaws.services.ec2.model.FleetLaunchTemplateSpecification;
import com.amazonaws.services.ec2.model.FleetLaunchTemplateSpecificationRequest;
import com.amazonaws.services.ec2.model.FleetStateCode;
import com.amazonaws.services.ec2.model.LaunchTemplateConfig;
import com.amazonaws.services.ec2.model.LaunchTemplateInstanceNetworkInterfaceSpecificationRequest;
import com.amazonaws.services.ec2.model.LaunchTemplatePlacementRequest;
import com.amazonaws.services.ec2.model.LaunchTemplateIamInstanceProfileSpecificationRequest;
import com.amazonaws.services.ec2.model.LaunchTemplateTagSpecificationRequest;
import com.amazonaws.services.ec2.model.OnDemandOptionsRequest;
import com.amazonaws.services.ec2.model.LaunchTemplateSpecification;
import com.amazonaws.services.ec2.model.LaunchTemplateOverrides;
import com.amazonaws.services.ec2.model.CreateLaunchTemplateVersionResult;
import com.amazonaws.services.ec2.model.CreateLaunchTemplateVersionRequest;
import com.amazonaws.services.ec2.model.DeleteLaunchTemplateVersionsRequest;
import com.amazonaws.services.ec2.model.DeleteLaunchTemplateVersionsResult;
import com.amazonaws.services.ec2.model.DescribeFleetInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeFleetInstancesResult;
import com.amazonaws.services.ec2.model.DescribeFleetsInstances;
import com.amazonaws.services.ec2.model.DescribeFleetsRequest;
import com.amazonaws.services.ec2.model.DescribeFleetsResult;
import com.amazonaws.util.CollectionUtils;
import com.amazonaws.util.StringUtils;
import com.ibm.spectrum.constant.AwsConst;
import com.ibm.spectrum.model.AwsMachine;
import com.ibm.spectrum.model.AwsRequest;
import com.ibm.spectrum.model.AwsTemplate;
import com.ibm.spectrum.model.HostAllocationType;
import com.ibm.spectrum.util.AwsUtil;
import com.ibm.spectrum.model.AwsEntity;
import com.amazonaws.services.ec2.model.DescribeSpotPriceHistoryRequest;
import com.amazonaws.services.ec2.model.DescribeSpotPriceHistoryResult;
import com.amazonaws.services.ec2.model.SpotPrice;
import com.amazonaws.services.ec2.model.AllocationStrategy;
import com.amazonaws.services.ec2.model.DescribeSubnetsRequest;
import com.amazonaws.services.ec2.model.DescribeSubnetsResult;
import com.amazonaws.services.ec2.model.Subnet;
/**
 * @ClassName: AWSClient
 * @Description: The Client class for the AWS API
 * @author omara
 * @date March 13, 2017
 * @version 1.0
 */
public class AWSClient {

    private static Logger log = LogManager.getLogger(AWSClient.class);

    /**
     * EC2 client
     */
    private static AmazonEC2 ec2 = getEC2Client();

    private final static String AWS_TEMP_FILE = "aws_federatedUser_credentials";

    /**
     *
     * @Title: getEC2Client
     * @Description: Initialize EC2 client
     * @param @return
     * @return AmazonEC2
     * @throws
     */
    public static synchronized AmazonEC2 getEC2Client() throws AmazonClientException {
        if (log.isTraceEnabled()) {
            log.trace("Start in class AWSClient in method getEC2Client with parameters ");
        }

        if (null != ec2) {
            return ec2;
        }

        /*
         * The ProfileCredentialsProvider will return your [default] credential
         * profile by reading from the credentials file located at
         * (./credentials).
         */
        AWSCredentials credentials = null;
        String credentialsFile = AwsUtil.getConfig().getAwsCredentialFile();
        String regionName = "";
        String scriptFile = AwsUtil.getConfig().getAwsCredentialScript();      // script path being retreived from the json file
        String margin = AwsUtil.getConfig().getAwsCredentialMargin(); // margin being read from json file. The value is expected in seconds
        AWSCredentialsProvider credsProvider = null;

        if (!StringUtils.isNullOrEmpty(scriptFile)) {
            File file = new File(scriptFile);
            if (!file.exists()) {
                log.warn("The file <" + scriptFile + "> defined in AWS_CREDENTIAL_SCRIPT parameters does not exit.");
                scriptFile = null;
            }
        }

        if ((scriptFile != "" && scriptFile != null) && (credentialsFile != "" && credentialsFile != null)) {
            log.warn("Both AWS_CREDENTIAL_FILE and AWS_CREDENTIAL_SCRIPT parameters are defined in awsprov_config.json. The system will ignore AWS_CREDENTIAL_FILE and use AWS_CREDENTIAL_SCRIPT. If you want to use AWS IAM credentials, ensure AWS_CREDENTIAL_SCRIPT is not defined in awsprov_config.json.");
        }

        long marginInSeconds = 1770; // by default, after 29 minutes 30 sec the new temporary credentials shall be generated.
        if (margin != "" && margin != null) {
            marginInSeconds = Integer.parseInt(margin);
            if (marginInSeconds < 0) {
                log.error("A negative value is specified for AWS_CREDENTIAL_RENEW_MARGIN parameter in awsprov_config.json.");
                return null;
            }
        }
        if (scriptFile != "" && scriptFile != null) {  	// If script file is specified, use STS federated user credentials
            log.info(String.format("Obtaining temporary credentials from script %s", scriptFile));
            boolean isfileCreatedNow = false;

            try {
                File file = new File(AwsUtil.getWorkDir()+ "/" + AWS_TEMP_FILE);
                //If temporary credentials file exists, if not set isFileCreatedNow to true.
                if (!file.exists()) {
                    if (file.createNewFile()) {
                        isfileCreatedNow = true;
                        log.info("Credential file for federated user is created in " + AwsUtil.getWorkDir());
                    } else {
                        log.error("Cannot create credential file for federated user in work directory.");
                        return null;
                    }
                }
                if (isfileCreatedNow == true || (System.currentTimeMillis() > (file.lastModified() + marginInSeconds * 1000))) {
                    String s = null;
                    Process p = Runtime.getRuntime().exec(scriptFile);

                    // read output from the script file
                    BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));

                    // read the output from the command
                    BufferedWriter bw = null;
                    FileWriter fw = null;
                    fw = new FileWriter(AwsUtil.getWorkDir()+ "/" + AWS_TEMP_FILE);
                    bw = new BufferedWriter(fw);
                    while ((s = stdInput.readLine()) != null) {
                        bw.write(s + "\n");
                    }
                    if (bw != null) {
                        bw.close();
                    }
                    if (fw != null) {
                        fw.close();
                    }

                    // read the error from the command
                    while ((s = stdError.readLine()) != null) {
                        log.debug(s);
                    }

                }
            } catch(Exception e) {
                log.error("Failed to create temporary "+AwsUtil.getProviderName()+" credentials file.", e);
                throw new AmazonClientException( e);
            }

            try {
                // read the temp credentials file
                credentialsFile = AwsUtil.getWorkDir()+ "/" + AWS_TEMP_FILE;
                credsProvider = new ProfileCredentialsProvider(credentialsFile, "default");

            } catch(Exception e) {
                log.error("Failed to load the temporary "+AwsUtil.getProviderName()+" credentials file.", e);
                throw new AmazonClientException(e);
            }

        } // end if credential script
        else if (credentialsFile != null) {
            // if script parameter is not specified in the json config,
            // use the permanent credentials available in credentials file

            if (credentialsFile == "") {
                credentialsFile = AwsUtil.getConfDir() + "/conf/credentials";
            }
            log.info(String.format("Obtaining static credentials from file %s", credentialsFile));

            try {
                credsProvider = new ProfileCredentialsProvider(credentialsFile, "default");
            } catch(Exception e) {
                log.error("Failed to load the "+AwsUtil.getProviderName()+" credentials file.", e);

                StringBuilder b = new StringBuilder();
                b.append("Cannot load the credentials from the credential profiles file. ")
                .append("Make sure that your credentials file is at the correct location (")
                .append(AwsUtil.getConfDir())
                .append("), and is in valid format.");

                throw new AmazonClientException(b.toString(), e);
            }

        } // end if credentials file
        else { // credentials not configured; assume EC2 instance profile credentials
            log.info(String.format("Obtaining credentials from EC2 instance profile"));
            credsProvider = InstanceProfileCredentialsProvider.getInstance();
        }

        // Create the AmazonEC2Client object so we can call various APIs.

        regionName = AwsUtil.getConfig().getAwsRegion();
        if (StringUtils.isNullOrEmpty(regionName)) {
            regionName = "us-east-1";
            AwsUtil.getConfig().setAwsRegion(regionName);
            log.warn("AWS_REGION is not defined in awsprov_config.json. Using default region: "
                     + regionName);
        }
        log.debug(String.format("Client connecting to region: %s", regionName));

        // Create the AmazonEC2Client object so we can call various APIs.
        ec2 = AmazonEC2ClientBuilder.standard()
              .withCredentials(credsProvider)
              .withRegion(regionName).build();

        if (log.isTraceEnabled()) {
            log.trace("End in class AWSClient in method getEC2Client with return: AmazonEC2: " + ec2);
        }
        return ec2;
    }
    
    
    /**
     * 
     * @Title: updateFleetLaunchTemplateConfig
     * @Description: Update fleet launch template by using LSF provided user_data if it exists.
     * @param t
     * @param tagValue
     * @param fleetRequest
     * @return
     */
    public static List<FleetLaunchTemplateConfigRequest> updateFleetLaunchTemplateConfig(AwsTemplate t, String tagValue, CreateFleetRequest fleetRequest) {
		if (log.isTraceEnabled()) {
			log.trace("Start in class AWSClient in method updateFleetLaunchTemplateConfig with parameters: t: " + t + ", tagValue: " + tagValue + ", fleetRequest: " + fleetRequest);
		}
    	
    	if (fleetRequest == null
    			|| CollectionUtils.isNullOrEmpty(fleetRequest.getLaunchTemplateConfigs())) {
    		log.error("EC2 Fleet configuration error");
    		return null;
    	}
    	List<FleetLaunchTemplateConfigRequest> fleetLaunchTemplateConfigList = fleetRequest.getLaunchTemplateConfigs();
		List<FleetLaunchTemplateConfigRequest> newFleetLaunchTemplateConfigList = new ArrayList<FleetLaunchTemplateConfigRequest>();
		//Create new version template with LSF provided user_data.sh
		if (fleetLaunchTemplateConfigList != null) {
			for (FleetLaunchTemplateConfigRequest config : fleetLaunchTemplateConfigList) {
				FleetLaunchTemplateSpecificationRequest launchTemplateSpecification = config.getLaunchTemplateSpecification();

				CreateLaunchTemplateVersionRequest createLaunchTemplateVersionRequest = new CreateLaunchTemplateVersionRequest();
				createLaunchTemplateVersionRequest.withLaunchTemplateId(launchTemplateSpecification.getLaunchTemplateId());
				createLaunchTemplateVersionRequest.withVersionDescription("lsf-auto-created-version");
				if (!StringUtils.isNullOrEmpty(launchTemplateSpecification.getVersion())) {
					createLaunchTemplateVersionRequest.withSourceVersion(launchTemplateSpecification.getVersion());
				} else {
					createLaunchTemplateVersionRequest.withSourceVersion("$Default");
				}

				// lsf-L3-tracker/issues/378 - If no LSF user_data, should not override user_data in launch template
				String userData = AwsUtil.getEncodedUserData(t, tagValue);
				if (!StringUtils.isNullOrEmpty(userData)) {
					RequestLaunchTemplateData requestLaunchTemplateData = new RequestLaunchTemplateData();
					requestLaunchTemplateData.withUserData(userData);
					createLaunchTemplateVersionRequest.withLaunchTemplateData(requestLaunchTemplateData);
				}
				try {
					CreateLaunchTemplateVersionResult createLaunchTemplateVersionResult = ec2.createLaunchTemplateVersion(createLaunchTemplateVersionRequest);
					launchTemplateSpecification.withVersion(createLaunchTemplateVersionResult.getLaunchTemplateVersion().getVersionNumber().toString());
					newFleetLaunchTemplateConfigList.add(config);
				} catch (Exception e) {
					log.error("Create EC2 Fleet launch template version error.", e.getMessage(), e);
					deleteEC2FleetLaunchTemplate(newFleetLaunchTemplateConfigList);
					return null;
				}
			}
		}
		
        if (log.isTraceEnabled()) {
            log.trace("End in class AWSClient in method updateFleetLaunchTemplateConfig with return: newFleetLaunchTemplateConfigList: " + newFleetLaunchTemplateConfigList);
        }
		
		return newFleetLaunchTemplateConfigList;
		
    }
    
    /**
     * 
     * @Title: updateTargetCapacitySpecification
     * @Description: Update targetCapacitySpecification accordingly
     * @param t
     * @param fleetRequest
     * @return
     */
    public static void updateTargetCapacitySpecification( AwsTemplate t, CreateFleetRequest fleetRequest) {
		if (log.isTraceEnabled()) {
			log.trace("Start in class AWSClient in method updateTargetCapacitySpecification with parameters: fleetRequest: " + fleetRequest);
		}
    	TargetCapacitySpecificationRequest targetCapacitySpec = fleetRequest.getTargetCapacitySpecification();
    	if (targetCapacitySpec == null) {
    		targetCapacitySpec = new TargetCapacitySpecificationRequest();
    	}
    	targetCapacitySpec.setTotalTargetCapacity(t.getVmNumber());
    	
    	if (t.getOnDemandTargetCapacityRatio() != null) {  	
    		Integer onDemandTargetCapacity = (int) Math.ceil(t.getVmNumber() * t.getOnDemandTargetCapacityRatio());
    		Integer spotTargetCapacity = t.getVmNumber() - onDemandTargetCapacity;
    		targetCapacitySpec.withOnDemandTargetCapacity(onDemandTargetCapacity)
    						  .withSpotTargetCapacity(spotTargetCapacity);
    	}
    	
    	//Revise the onDemandTargetCapacity or spotTargetCapacity if user specify a value larger than totalTargetCapacity
    	if (targetCapacitySpec.getOnDemandTargetCapacity() > targetCapacitySpec.getTotalTargetCapacity()) {
    		log.warn("The specified onDemandTargetCapacity <%d> is larger than totalTargetCapacity <%d>, reset it to <%d>", 
    				targetCapacitySpec.getOnDemandTargetCapacity(), targetCapacitySpec.getTotalTargetCapacity(), targetCapacitySpec.getTotalTargetCapacity());
    		targetCapacitySpec.setOnDemandTargetCapacity(targetCapacitySpec.getTotalTargetCapacity());
    	}
    	
    	if (targetCapacitySpec.getSpotTargetCapacity() > targetCapacitySpec.getTotalTargetCapacity()) {
    		log.warn("The specified spotTargetCapacity <%d> is larger than totalTargetCapacity <%d>, reset it to <%d>", 
    				targetCapacitySpec.getSpotTargetCapacity(), targetCapacitySpec.getTotalTargetCapacity(), targetCapacitySpec.getTotalTargetCapacity());
    		targetCapacitySpec.setSpotTargetCapacity(targetCapacitySpec.getTotalTargetCapacity());
    	}
    	
    	fleetRequest.setTargetCapacitySpecification(targetCapacitySpec);
    	
        if (log.isTraceEnabled()) {
            log.trace("End in class AWSClient in method updateTargetCapacitySpecification with return: fleetRequest: " + fleetRequest);
        }
    }
    
    
    /**
     * 
     * @Title: createVMByEC2Fleet
     * @Description: create VM by calling EC2 Fleet API
     * @param t
     * @param tagValue
     * @param rsp
     * @return
     */
    public static CreateFleetResult createVMByEC2Fleet(AwsTemplate t, String tagValue, AwsEntity rsp) {
        log.info("Start in class AWSClient in method createVMByEC2Fleet with parameters: t: " + t + ", tagValue: "
                 + tagValue);
        
        List<FleetLaunchTemplateConfigRequest> templateConfigRequestList = null;
        
        try {
        	//Get request expire time period by parsing requestValidity 
        	AwsUtil.applyDefaultValuesForSpotInstanceTemplate(t);
        	
        	CreateFleetRequest fleetRequest = new CreateFleetRequest();
        	//Generate request according to EC2 Fleet configuration file
        	
        	//Replace fixed pattern in target capacity specification
        	String fileContent = AwsUtil.replaceTargetCapacitySpecification(t);
        	fleetRequest = AwsUtil.toObjectCaseInsensitive(fileContent, CreateFleetRequest.class);
        	
        	//Update target capacity if fixed pattern not specified
        	updateTargetCapacitySpecification(t, fleetRequest);
        	
        	//Update launchTemplate with LSF provided user_data if exists
        	templateConfigRequestList = updateFleetLaunchTemplateConfig(t, tagValue, fleetRequest);
        	if (fleetRequest == null
        			|| CollectionUtils.isNullOrEmpty(templateConfigRequestList)) {
        		log.error("Error parsing fleet configuration file <%s> ", t.getEc2FleetConfig());
                if (rsp != null) {
                    rsp.setStatus(AwsConst.EBROKERD_STATE_WARNING);
                    rsp.setRsp(1, "Request EC2 Fleet Instance on " + AwsUtil.getProviderName()
                               + " EC2 failed due to fleet configuration error.");
                }
        		return null;
        	}
        	
        	//Set expire time for request type fleet request. By default is 30 min.
        	if (FleetType.Request.toString().equalsIgnoreCase(fleetRequest.getType())) {
        		fleetRequest.withValidFrom(t.getRequestValidityStartTime())
        					.withValidUntil(t.getRequestValidityEndTime())
        					.withTerminateInstancesWithExpiration(false);
        	}

        	// Set user tag string
        	String userTagString = "RC_ACCOUNT=" + tagValue + ";" + t.getInstanceTags();
        	List<TagSpecification> tagSpecifications = createUserTagSpecification(userTagString);
        	if (!tagSpecifications.isEmpty() && FleetType.Instant.toString().equalsIgnoreCase(fleetRequest.getType())) {
			fleetRequest.setTagSpecifications(tagSpecifications);
		}


            if (log.isTraceEnabled()) {
                log.trace("Start to call EC2 Fleet API createFleet with request: " + fleetRequest);
            }
        	CreateFleetResult fleetResult = ec2.createFleet(fleetRequest);
        	
            if (log.isTraceEnabled()) {
                log.trace("End in class AWSClient in method createVMByEC2Fleet with return: fleetResult: " + fleetResult);
            }
        	return fleetResult;

        } catch (AmazonServiceException ase) {
            if (rsp != null) {
                if (isFatalError(ase.getErrorCode())) {
                    rsp.setStatus(AwsConst.EBROKERD_STATE_ERROR);
                } else {
                    rsp.setStatus(AwsConst.EBROKERD_STATE_WARNING);
                }
                rsp.setRsp(1, "Request EC2 Fleet Instance on " + AwsUtil.getProviderName() + " EC2 failed. " + ase.getMessage());
            }
            log.error("Create instances error." + ase.getMessage(), ase);
            deleteEC2FleetLaunchTemplate(templateConfigRequestList);
        } catch (AmazonClientException ace) {
            if (rsp != null) {
                rsp.setStatus(AwsConst.EBROKERD_STATE_WARNING);
                rsp.setRsp(1, "Request EC2 Fleet Instance on " + AwsUtil.getProviderName()
                           + " EC2 failed." + ace.getMessage());
            }
            log.error("Create instances error." + ace.getMessage(), ace);
            deleteEC2FleetLaunchTemplate(templateConfigRequestList);
        } catch (Exception e) {
            if (rsp != null) {
                rsp.setStatus(AwsConst.EBROKERD_STATE_WARNING);
                rsp.setRsp(1, "Request EC2 Fleet Instance on " + AwsUtil.getProviderName()
                           + " EC2 failed." + e.getMessage());
            }
            log.error("Create instances error.", e);
            deleteEC2FleetLaunchTemplate(templateConfigRequestList);
        }

        return null;
            
    }


    /**
     *
     * @Title: createVM
     * @Description: create EC2 VM
     * @param @return
     * @return List<Instance>
     * @throws
     */
    public static Reservation createVM(AwsTemplate t, String tagValue, AwsEntity rsp) {
        log.info("Start in class AWSClient in method createVM with parameters: t: " + t + ", tagValue: "
                 + tagValue );
        try {
            RunInstancesRequest req = new RunInstancesRequest();

            if (!StringUtils.isNullOrEmpty(t.getLaunchTemplateId())) {
                LaunchTemplateSpecification launchTemplateSpecification = new LaunchTemplateSpecification();
                launchTemplateSpecification.withLaunchTemplateId(t.getLaunchTemplateId());
                if (!StringUtils.isNullOrEmpty(t.getLaunchTemplateVersion())) {
                    launchTemplateSpecification.withVersion(t.getLaunchTemplateVersion());
                }
                req.withLaunchTemplate(launchTemplateSpecification);
            }
            if (!StringUtils.isNullOrEmpty(t.getImageId())) {
                req.setImageId(t.getImageId());
            }
            /* SUB_BY_DEV#261330 set request min count to 1 to avoid InsufficientInstanceCapacity */
            req.setMinCount(1);
            req.setMaxCount(t.getVmNumber());
            String encodedUserData = AwsUtil.getEncodedUserData(t, tagValue);

            if (!StringUtils.isNullOrEmpty(t.getInterfaceType())) {
                InstanceNetworkInterfaceSpecification nic = new InstanceNetworkInterfaceSpecification();
                nic.withDeviceIndex(0);
                nic.withSubnetId(t.getSubnetId());
                nic.withGroups(t.getSgIds());
                nic.withInterfaceType(t.getInterfaceType().trim());
                req.withInstanceType(t.getVmType()).withNetworkInterfaces(nic);
            } else {
                req.withInstanceType(t.getVmType()).withSubnetId(t.getSubnetId()).withSecurityGroupIds(t.getSgIds());
            }
            
            // lsf-L3-tracker/issues/378 - If no LSF user_data, should not override user_data in launch template
            if (!StringUtils.isNullOrEmpty(encodedUserData)) {
            	req.withUserData(encodedUserData);
            }

            // Initialize a default key pair name
            if (createKeyPair(t.getKeyName(), rsp) == 0) {
                if (!StringUtils.isNullOrEmpty(t.getKeyName())) {
                    req.setKeyName(t.getKeyName());
                }
            }

            if (!StringUtils.isNullOrEmpty(t.getPGrpName())) {
                Placement placementGrp = new Placement();
                placementGrp.setGroupName(t.getPGrpName());
                if (!StringUtils.isNullOrEmpty(t.getTenancy())) {
                    placementGrp.setTenancy(t.getTenancy());
                }
                req.setPlacement(placementGrp);
            } else if (!StringUtils.isNullOrEmpty(t.getTenancy())) {
                Placement placementGrp = new Placement();
                placementGrp.setTenancy(t.getTenancy());
                req.setPlacement(placementGrp);
            }


            // Set user tag string
            String userTagString = "RC_ACCOUNT=" + tagValue + ";" + t.getInstanceTags();
            List<TagSpecification> tagSpecifications = createUserTagSpecification(userTagString);
            if (!tagSpecifications.isEmpty()) {
                req.setTagSpecifications(tagSpecifications);
            }

            // Use EC2 instance profile if specified by the template (RTC
            // 127032)
            String profileRef = t.getInstanceProfile();
            if (!StringUtils.isNullOrEmpty(profileRef)) {
                req.setIamInstanceProfile(AwsUtil
                                          .getIamInstanceProfile(profileRef));

            }

            // Set ebsOptimized attribute
            boolean ebsOptimized = t.getEbsOptimized();
            if (ebsOptimized == true) {
                req.setEbsOptimized(ebsOptimized);
            }

            // Create instances
            RunInstancesResult rs = ec2.runInstances(req);
            Reservation rsv = rs.getReservation();
            List<Instance> vmLst = rsv.getInstances();
            log.debug("The created instances: " + vmLst);
            if (log.isTraceEnabled()) {
                log.trace("End in class AWSClient in method createVM with return: Reservation: " + rsv);
            }
            return rsv;

        } catch (AmazonServiceException ase) {
            if (rsp != null) {
                if (isFatalError(ase.getErrorCode())) {
                    rsp.setStatus(AwsConst.EBROKERD_STATE_ERROR);
                } else {
                    rsp.setStatus(AwsConst.EBROKERD_STATE_WARNING);
                }
                rsp.setRsp(1, "Request Instance on " + AwsUtil.getProviderName() + " EC2 failed. " + ase.getMessage());
            }
            log.error("Create instances error: " + ase.getErrorType() + " " + ase.getMessage(), ase);
        } catch (AmazonClientException ace) {
            if (rsp != null) {
                rsp.setStatus(AwsConst.EBROKERD_STATE_WARNING);
                rsp.setRsp(1, "Request Instance on " + AwsUtil.getProviderName()
                           +  " EC2 failed." + ace.getMessage());
            }
            log.error("Create instances error." + ace.getMessage(), ace);
        } catch (Exception e) {
            if (rsp != null) {
                rsp.setStatus(AwsConst.EBROKERD_STATE_WARNING);
                rsp.setRsp(1, "Request Instance on " + AwsUtil.getProviderName()
                           +  " EC2 failed." + e.getMessage());
            }
        }

        return null;
    }

    private static List<Tag> createInstanceTags(String userTagString) {
    	List<Tag> tagsList = new ArrayList<Tag>();
    	
        if (!StringUtils.isNullOrEmpty(userTagString)) {
            Tag tag;
            String[] tagStr = userTagString.split(";");
            for (String inst : tagStr) {
                String[] instSubStr = inst.split("=", 2);
                if (instSubStr.length == 2
                        && !StringUtils.isNullOrEmpty(instSubStr[0])
                        && !StringUtils.isNullOrEmpty(instSubStr[1])) {
                    if (instSubStr[0].toLowerCase().
                            startsWith(AwsConst.RESERVED_TAG_PREFIX.toLowerCase())) {
                        log.error("User tags cannot start with [" +
                                  AwsConst.RESERVED_TAG_PREFIX
                                  + "]. This prefix is reserved for internal AWS tags. Ignoring the tag: "
                                  + inst);
                    } else {
                        tag = new Tag(instSubStr[0], instSubStr[1]);
                        tagsList.add(tag);
                    }
                }
            }

        }
        
        return tagsList;
    }

    private static List<TagSpecification> createUserTagSpecification( String userTagString) {
        List<TagSpecification> tagSpecifications = new ArrayList<TagSpecification>();

        if (!StringUtils.isNullOrEmpty(userTagString)) {
            List<Tag> tagList = createInstanceTags(userTagString);
            List<Tag> tagsToInstance = new ArrayList<Tag>();
            List<Tag> tagsToVolume = new ArrayList<Tag>();
            for (Tag tag : tagList) {
                tagsToInstance.add(tag);
                tagsToVolume.add(tag);
            }
            TagSpecification tagSpec = new TagSpecification();
            tagSpec.setResourceType(ResourceType.Instance);
            tagSpec.setTags(tagsToInstance);
            tagSpecifications.add(tagSpec);

            tagSpec = new TagSpecification();
            tagSpec.setResourceType(ResourceType.Volume);
            tagSpec.setTags(tagsToVolume);
            tagSpecifications.add(tagSpec);
        }
        return tagSpecifications;

    }

    /**
     * Retrieves the list of tags that are needed to be attached to the
     * instance, EBS volumes, .... <br>
     * The default tags to be created are: <br>
     * - Key: RC_ACCOUNT ; Value: {accountTagValue}
     *
     * Additional tags can be provided in the {additionalInstanceTags} parameter
     * in the following format: {Key1=Value1;Key2=Value2}
     *
     * @param instanceTags
     *            instanceTag defined in LSF template
     * @param accountTagValue
     *            The value of the RC_ACCOUNT tag
     */
    private static List<Tag> createTagsForInstanceCreation(
        String instanceTags,
        String accountTagValue) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class AWSClient in method createTagsForInstanceCreation with parameters:"
                      + "instanceTags: "
                      + instanceTags
                      + ", accountTagValue: "
                      + accountTagValue);
        }

        // First create tag based on LSF template instanceTags
        List<Tag> tags = createInstanceTags(instanceTags);

        try {
            Tag tag = null;
            if (!StringUtils.isNullOrEmpty(accountTagValue)) {
                tag = new Tag("RC_ACCOUNT", accountTagValue);
                tags.add(tag);
            }

        } catch (Exception e) {
            log.error("Create tag failed for accountTagValue: " + accountTagValue
                      + " ] with the following error: " + e.getMessage(), e);
        }

        if (log.isTraceEnabled()) {
            log.trace("End in class AWSClient in method createTagsForInstanceCreation with return: tags: " + tags);
        }
        return tags;
    }

    /**
     * Retrieves the list of tags that are needed to be attached to the
     * instance, EBS volumes, .... <br>
     * The default tags to be created are: <br>
     * - Key: InstanceId ; Value: {instanceId} <br>
     * - Key: RC_ACCOUNT ; Value: {accountTagValue}
     *
     * Additional tags can be provided in the {additionalInstanceTags} parameter
     * in the following format: {Key1=Value1;Key2=Value2}
     *
     * @param instance
     *            the instance object
     * @param instanceTags
     *            instanceTag defined in LSF template
     * @param accountTagValue
     *            The value of the RC_ACCOUNT tag
     */
    private static List<Tag> createTagsForInstanceCreation(
        Instance instance,
        String instanceTags,
        String accountTagValue) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class AWSClient in method createTagsForInstanceCreation with parameters: instance: "
                      + instance
                      + "instanceTags: "
                      + instanceTags
                      + ", accountTagValue: "
                      + accountTagValue);
        }

        // First create tag based on LSF template instanceTags
        List<Tag> tags = createInstanceTags(instanceTags);

        if (instance != null) {
            String instanceId = instance.getInstanceId();
            try {
                Tag tag = null;
                if (!StringUtils.isNullOrEmpty(accountTagValue)) {
                    tag = new Tag("RC_ACCOUNT", accountTagValue);
                    tags.add(tag);
                }

                if (!StringUtils.isNullOrEmpty(instanceId)) {
                    tag = new Tag("InstanceID", instanceId);
                    tags.add(tag);
                }

            } catch (Exception e) {
                log.error("Create tag failed for [instanceId: " + instanceId
                          + ", accountTagValue: " + accountTagValue
                          + " ] with the following error: " + e.getMessage(), e);
            }
        }
        if (log.isTraceEnabled()) {
            log.trace("End in class AWSClient in method createTagsForInstanceCreation with return: tags: " + tags);
        }
        return tags;
    }

    /**
     * @Title: deleteCloudVM
     * @Description: delete instances from cloud
     * @param instIdsToDelete
     * @return
     */
    public static List<InstanceStateChange> deleteCloudVM(List<String> instIdsToDelete) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class AWSClient in method deleteCloudVM with parameters: instIdsToDelete: "
                      + instIdsToDelete);
        }

        List<InstanceStateChange> stateChanges = new ArrayList<InstanceStateChange> ();

        AmazonEC2 ec2 = getEC2Client();

        // delete eligible instances
        TerminateInstancesRequest req = new TerminateInstancesRequest(
            instIdsToDelete);
        TerminateInstancesResult rs = ec2.terminateInstances(req);
        if(rs.getTerminatingInstances() != null) {
            log.debug("Add terminated instances to stateChanges" + rs.getTerminatingInstances());
            stateChanges.addAll(rs.getTerminatingInstances());
        }

        if (log.isTraceEnabled()) {
            log.trace("End in class AWSClient in method deleteCloudVM with return: stateChanges: " + stateChanges);
        }

        return stateChanges;
    }


    /**
     * @Title: addVM2StateChange
     * @Description: add VM info to list InstanceStateChange
     * @param id
     * @param stateChanges
     * @return List<InstanceStateChange>
     * @throws
     */
    public static void addVM2StateChange(String id, String stateName, List<InstanceStateChange> stateChanges) {
        if (stateChanges != null) {
            InstanceStateChange instanceStateChange = new InstanceStateChange();
            instanceStateChange.setInstanceId(id);
            InstanceState instanceState = new InstanceState();
            instanceState.setName(stateName); // Need evaluation
            instanceStateChange.setCurrentState(instanceState);
            stateChanges.add(instanceStateChange);
            if (AwsConst.markedForTerminationStates.contains(stateName)) {
                log.warn("Instance [" + id + "] is interruppted by AWS, update it status to: " + stateName);
            } else {
                log.warn("Instance [" + id + "] not found on cloud, mark it status to: " + stateName);
            }

        }
    }


    /**
     * @Title: deleteVMOneByOne
     * @Description: delete VM one by one
     * @param instIdsToDelete
     * @param
     * @return List<InstanceStateChange>
     * @throws
     */
    public static List<InstanceStateChange> deleteVMOneByOne(List<String> instIdsToDelete) {
        List<InstanceStateChange> stateChanges = new ArrayList<InstanceStateChange>();
        for (String id : instIdsToDelete) {
            try {
                List<InstanceStateChange> instaceStateChanges = deleteCloudVM(Collections.singletonList(id));
                stateChanges.addAll(instaceStateChanges);
            } catch (AmazonServiceException ase) {
                if (ase.getErrorCode().contains("InvalidInstanceID")) {
                    //Manually mark this instance as deleted
                    addVM2StateChange(id, "DELETED", stateChanges);
                } else {
                    log.error("Failed to delete VM [" + id + "]." + ase.getErrorMessage());
                }
            }
            log.debug("Instance [" + id + "] is being deleted");
        }
        return stateChanges;

    }

    /**
     * @Title: deleteVMWithRetry
     * @Description: delete EC2 VM with retry logic
     * @param instIdsToDelete
     * @param
     * @return List<InstanceStateChange>
     * @throws AmazonServiceException
     */
    public static List<InstanceStateChange> deleteVMWithRetry(List<String> instIdsToDelete) throws AmazonServiceException {
        if (log.isTraceEnabled()) {
            log.trace("Start in class AWSClient in method deleteVMWithRetry with parameters: instIdsToDelete: "
                      + instIdsToDelete);
        }

        List<InstanceStateChange> stateChanges = new ArrayList<InstanceStateChange>();
        if (CollectionUtils.isNullOrEmpty(instIdsToDelete)) {
            log.error("Invalid instance Ids: " + instIdsToDelete);
            return stateChanges;
        }
        try {
            stateChanges = deleteCloudVM(instIdsToDelete);
        } catch (AmazonServiceException ase) {
            if (ase.getErrorCode().contains("InvalidInstanceID")) {
                if (instIdsToDelete.size() > 1) {
                    log.warn("Delete instance error, retrying to delete them one by one. " + ase.getMessage());
                    stateChanges = deleteVMOneByOne(instIdsToDelete);
                } else {
                    //Manually mark this instance as deleted
                    addVM2StateChange(instIdsToDelete.get(0), "DELETED", stateChanges);
                }
            } else {
                throw new AmazonServiceException(ase.getErrorMessage(), ase);
            }
        }

        if (log.isTraceEnabled()) {
            log.trace("End in class AWSClient in method deleteVMWithRetry with return: stateChanges: " + stateChanges);
        }

        return stateChanges;

    }


    /**
     * @Title: skipAWSTerminatingSpotInstance
     * @Description: If an instance is scheduled to be terminated by AWS, remove it from instanceIds so that LSF will not terminated it
     * @param instanceIds
     * @param stateChanges
     * @return List<String>
     * @throws
     */
    public static List<String> skipAWSTerminatingSpotInstance(List<String> instanceIds, List<InstanceStateChange> stateChanges) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class AWSClient in method skipAWSTerminatingSpotInstance with parameters: instanceIds: "
                      + instanceIds );
        }

        List<String> instIdsToDelete = new ArrayList<String>(instanceIds);      // instances to terminate
        List<String> dyingSpotInstIds = new ArrayList<String>();    // instances assumed terminated

        //Get instance info from cloud
        Map<String, Instance> vmMap = listVM(instanceIds, null);
        if (vmMap == null) {
            log.warn("listVM failed : " + vmMap);
            return instIdsToDelete;
        }

        // Remove instance id from instIdsToDelete if instance ID not found
        for (String id : instanceIds) {
            if (vmMap.get(id) == null) {
                //Manually mark this instance as deleted
                addVM2StateChange(id, "DELETED", stateChanges);
                instIdsToDelete.remove(id);
            }
        }

        List<Instance> instanceList = new ArrayList<Instance>(vmMap.values());
        // RTC 154322 -- do not terminate spot instances if they are already
        // scheduled for termination, unless configured
        if (!CollectionUtils.isNullOrEmpty(instanceList)) {
            for (Instance inst: instanceList) {
                if (InstanceLifecycleType.Spot.toString().equals(inst.getInstanceLifecycle())) {
                    // spot instance -- check its request status
                    DescribeSpotInstanceRequestsResult spotInstResult = ec2.describeSpotInstanceRequests(
                                new DescribeSpotInstanceRequestsRequest().withSpotInstanceRequestIds(
                                    inst.getSpotInstanceRequestId()
                                )
                            );
                    if (spotInstResult != null) {
                        for (SpotInstanceRequest spotReq: spotInstResult.getSpotInstanceRequests()) {
                            SpotInstanceStatus spotInstanceStatus = spotReq.getStatus();
                            if (spotInstanceStatus != null &&
                                    AwsConst.markedForTerminationStates.contains(spotInstanceStatus.getCode())) {
                                // the instance will be imminently terminated by AWS
                                if (!AwsUtil.getConfig().isSpotTerminateOnReclaim()) {
                                    // unless the user wants us to forcefully terminate,
                                    // remove this instance from the list
                                    instIdsToDelete.remove(inst.getInstanceId());
                                    dyingSpotInstIds.add(inst.getInstanceId());
                                    //Add to the response the instance state changed manually
                                    addVM2StateChange(spotReq.getInstanceId(), spotInstanceStatus.getCode(), stateChanges);
                                }
                            }
                        }
                    }
                }
            }
        }

        log.info(
            String.format("The instance state change : %s; spot instances scheduled for termination: %d"
                          ,stateChanges.toString()
                          ,dyingSpotInstIds.size()));


        if (log.isTraceEnabled()) {
            log.trace("End in class AWSClient in method skipAWSTerminatingSpotInstance with return: List<instIdsToDelete>: " + instIdsToDelete);
        }

        return instIdsToDelete;
    }


    /**
     * @Title: deleteVM
     * @Description: delete EC2 VM
     * @param instanceIds
     * @param rsp
     * @return List<InstanceStateChange>
     * @throws
     */
    public static List<InstanceStateChange> deleteVM(List<String> instanceIds, AwsEntity rsp) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class AWSClient in method deleteVM with parameters: instanceIds: "
                      + instanceIds );
        }
        List<InstanceStateChange> stateChanges = new ArrayList<InstanceStateChange>();
        List<InstanceStateChange> newlyStateChanges = null;

        if (CollectionUtils.isNullOrEmpty(instanceIds)) {
            log.error("Invalid instance Ids: " + instanceIds);
            return stateChanges;
        }

        try {
            // RTC 154322 -- do not terminate spot instances if they are already
            // scheduled for termination, unless configured
            List<String> instIdsToDelete = skipAWSTerminatingSpotInstance(instanceIds, stateChanges); // instances to terminate
            // delete eligible instances
            newlyStateChanges = deleteVMWithRetry(instIdsToDelete);
            stateChanges.addAll(newlyStateChanges);
        } catch (AmazonServiceException ase) {
            if (rsp != null) {
                rsp.setStatus(AwsConst.EBROKERD_STATE_WARNING);
                rsp.setRsp(1, "Delete VM failed. " +  ase.getErrorMessage());
            }
            log.error("Delete instance error: " + ase.getErrorMessage(), ase);
        } catch (Exception e) {
            if (rsp != null) {
                rsp.setStatus(AwsConst.EBROKERD_STATE_WARNING);
                rsp.setRsp(1, "Delete VM failed. " +  e.getMessage() );
            }
            log.error("Delete instance error: " + e.getMessage(), e);
        }

        if (log.isTraceEnabled()) {
            log.trace("End in class AWSClient in method deleteVM with return: List<InstanceStateChange>: " + stateChanges);
        }
        return stateChanges;
    }

    /**
     *
     * @Title: listVM
     * @Description: query EC2 VM
     * @param @return
     * @return List<Reservation>
     * @throws
     */
    public static List<Reservation> listVM() {
        if (log.isTraceEnabled()) {
            log.trace("Start in class AWSClient in method listVM with parameters: ");
        }
        try {
            AmazonEC2 ec2 = getEC2Client();

            DescribeInstancesResult rs = ec2.describeInstances();
            List<Reservation> rsvLst = rs.getReservations();
            if (log.isTraceEnabled()) {
                log.trace("End in class AWSClient in method listVM with return: List<Reservation>: " + rsvLst);
            }
            return rsvLst;
        } catch (AmazonClientException ace) {
            log.error("Failed to query the list.");
            return null;
        }
    }

    /**
     *
     * @Title: listVMStatus
     * @Description: query Instance status
     * @param @param instanceIds
     * @param @return
     * @return List<InstanceStatus>
     * @throws
     */
    public static Map<String, InstanceStatus> listVMStatus(
        List<String> instanceIds) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class AWSClient in method listVMStatus with parameters: instanceIds: " + instanceIds );
        }

        try {
            AmazonEC2 ec2 = getEC2Client();

            DescribeInstanceStatusRequest req = new DescribeInstanceStatusRequest();

            if (!CollectionUtils.isNullOrEmpty(instanceIds)) {
                req.setInstanceIds(instanceIds);
            }

            DescribeInstanceStatusResult rs = ec2.describeInstanceStatus(req);
            List<InstanceStatus> statusLst = rs.getInstanceStatuses();

            Map<String, InstanceStatus> statusMap = new HashMap<String, InstanceStatus>();
            if (CollectionUtils.isNullOrEmpty(statusLst)) {
                log.debug ("Empty result");
                return statusMap;
            }

            for (InstanceStatus status : statusLst) {
                statusMap.put(status.getInstanceId(), status);
                log.debug("Instance " + status.getInstanceId() + " status " + status.toString());
            }
            if (log.isTraceEnabled()) {
                log.trace("End in class AWSClient in method listVMStatus with return: Map<String,InstanceStatus>: " + statusMap);
            }
            return statusMap;
        } catch (AmazonClientException ace) {
            log.error("Failed to query instance status.");
            return null;
        }

    }

    /**
     * @Title: getCloudVM
     * @Description: Get VM info from cloud
     * @param instanceIds
     * @return
     */
    public static Map<String, Instance> getCloudVM(List<String> instanceIds) {
        DescribeInstancesRequest req = new DescribeInstancesRequest();
        DescribeInstancesResult rs = null;
        List<Reservation> rsvLst = new ArrayList<Reservation> ();
        Map<String, Instance> vmMap = new HashMap<String, Instance>();

        if (!CollectionUtils.isNullOrEmpty(instanceIds)) {
            req.setInstanceIds(instanceIds);
        } else {
            req.withMaxResults(500);
            //Use tag RC_ACCOUNT as filter when listing all VMs
            //lsf-tracker#2680 EC2 Fleet instant request instances keeps pending for some longer time.
            //req.withFilters(new Filter().withName("tag-key").withValues("RC_ACCOUNT"));
        }

        AmazonEC2 ec2 = getEC2Client();

        do {
            rs = ec2.describeInstances(req);
            if (rs == null || rs.getReservations() == null) {
                continue;
            }
            rsvLst.addAll(rs.getReservations());
            req.setNextToken(rs.getNextToken());
        } while (rs.getNextToken() != null);

        //Add instance info to vmMap
        if (!CollectionUtils.isNullOrEmpty(rsvLst)) {
            for (Reservation rsv : rsvLst) {
                if (CollectionUtils.isNullOrEmpty(rsv.getInstances())) {
                    continue;
                }

                for (Instance i : rsv.getInstances()) {
                    vmMap.put(i.getInstanceId(), i);
                }
            }
        }

        return vmMap;

    }


    /**
     *
     * @Title: retrieveSpecificVMFromAllVM
     * @Description: List all VMs first, then retrieve specific instances listed in instanceIds
     * @param instanceIds
     * @return
     */
    public static Map<String, Instance> retrieveSpecificVMFromAllVM(List<String> instanceIds) {

        Map<String, Instance> tempVmMap = getCloudVM(null);
        Map<String, Instance> vmMap = new HashMap<String, Instance>();

        for (String id : instanceIds) {
            Instance inst = tempVmMap.get(id);
            if (inst != null ) {
                vmMap.put(id, inst);
            } else {
                log.warn("Not get instance info from cloud for [" + id + "], ignoring it");
            }
        }

        log.debug("instanceIds.size: " + instanceIds.size() + ", vmMap.size: " + vmMap.size());

        return vmMap;
    }


    /**
     *
     * @Title: listVMWithRetry
     * @Description: List VM by ID with retry logic
     * @param instanceIds
     * @return List<Reservation>
     * @throws AmazonServiceException
     */
    public static Map<String, Instance> listVMWithRetry(List<String> instanceIds) throws AmazonServiceException {

        Map<String, Instance> vmMap = null;

        try {
            vmMap = getCloudVM(instanceIds);
        } catch (AmazonServiceException ase) {
            if (ase.getErrorCode().contains("InvalidInstanceID")) {
                log.error("Failed to list instances due to InvalidInstanceID error, will retry later: " + ase.getMessage());
                //Retrieve specified VM info from all VM info
                vmMap = retrieveSpecificVMFromAllVM(instanceIds);
            } else if (ase.getErrorCode().contains("RequestLimitExceeded")) {
                //Retry after 5 seconds
                log.error("Failed to list instances due to RequestLimitExceeded error, will retry later after 5 seconds: " + ase.getMessage());
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                vmMap = getCloudVM(instanceIds);
            } else {
                throw new AmazonServiceException(ase.getErrorMessage(), ase);
            }
        }

        return vmMap;

    }



    /**
     *
     * @Title: listVM
     * @Description: List VM by ID, list all VMs if instanceIds is null
     * @param instanceIds
     * @param rsp
     * @return
     */
    public static Map<String, Instance> listVM(List<String> instanceIds, AwsEntity rsp) {

        if (log.isTraceEnabled()) {
            log.trace("Start in class AWSClient in method listVM with parameters: instanceIds: "
                      + instanceIds);
        }

        Map<String, Instance> vmMap = new HashMap<String, Instance>();
        try {
            vmMap = listVMWithRetry(instanceIds);
        } catch (AmazonServiceException ase) {
            if (rsp != null) {
                if (isFatalError(ase.getErrorCode())) {
                    rsp.setStatus(AwsConst.EBROKERD_STATE_ERROR);
                } else {
                    rsp.setStatus(AwsConst.EBROKERD_STATE_WARNING);
                }
                rsp.setRsp(1, "Failed to list instances: " + ase.getMessage());
            }
            log.error("Failed to list instances: " + ase.getErrorMessage(), ase);
            return null;

        } catch (AmazonClientException ace) {
            log.error("Failed to list instances: " + ace.getMessage(), ace);
            return null;
        }

        if (log.isTraceEnabled()) {
            log.trace("End in class AWSClient in method listVM with return: vmMap.size: "
                      + vmMap.size() + ", vmMap: " + vmMap);
        }

        return vmMap;
    }

    /**
     *
     * @Title: getKeyPairs
     * @Description: query key pairs
     * @param @param keyNames
     * @param @return
     * @return List<KeyPairInfo>
     * @throws
     */
    public static List<KeyPairInfo> getKeyPairs(List<String> keyNames) {
        try {
            AmazonEC2 ec2 = getEC2Client();

            DescribeKeyPairsRequest dreq = new DescribeKeyPairsRequest();
            dreq.setKeyNames(keyNames);

            DescribeKeyPairsResult drs = ec2.describeKeyPairs(dreq);
            return drs.getKeyPairs();
        } catch (Exception e) {
            log.debug("Cannot get key pairs " + keyNames +  " from " + AwsUtil.getProviderName() + ". "  + e.getMessage());
        }

        return null;
    }

    /**
     *
     * @Title: createKeyPair
     * @Description: create a key pair
     * @param @param keyName
     * @param @return
     * @return int
     * @throws
     */
    public static int createKeyPair(String keyName, AwsEntity rsp) {
        String keyFile = "";
        try {
            // check if the key file exists
            keyFile = AwsUtil.getConfig().getAwsKeyFile();
            if (StringUtils.isNullOrEmpty(keyFile)) {
                keyFile = AwsUtil.getConfDir() + "/data";
                log.debug("AWS_KEY_FILE is not defined in awsprov_config.json. Using default key file location: "
                          + keyFile);
            }
            File kf = new File(keyFile + "/" + keyName + ".pem");
            if (kf.exists()) {
                log.info("The local key pair exists." + kf.getPath());
                return 0;
            }

            // check if the key pair exists
            if (!StringUtils.isNullOrEmpty(keyName)) {
                List<String> keyNames = new ArrayList<String>();
                keyNames.add(keyName);

                List<KeyPairInfo> keyLst = getKeyPairs(keyNames);
                if (!CollectionUtils.isNullOrEmpty(keyLst)) {
                    log.debug("The key pair exists on " + AwsUtil.getProviderName()
                              + ": " + keyLst);
                    return 0;
                }

                // create a key pair
                CreateKeyPairRequest req = new CreateKeyPairRequest(keyName);
                CreateKeyPairResult rs = ec2.createKeyPair(req);
                KeyPair key = rs.getKeyPair();

                log.info("The new key pair <" + key + "> is created and stored at " + keyFile + ".");

                AwsUtil.writeToFile(kf, key.getKeyMaterial());
            }
        } catch (AmazonServiceException ase) {
            if (rsp != null) {
                rsp.setMsg("createKeyPair() AmazonServiceException: " + ase.getMessage());
            }
            log.error("Key pair create error:" + ase.getMessage());
            return -1;
        } catch (AmazonClientException ace) {
            if (rsp != null) {
                rsp.setMsg("createKeyPair() AmazonClientException: " + ace.getMessage());
            }
            log.error("Key pair create error:" + ace.getMessage());
            return -1;
        } catch (Exception e) {
            if (rsp != null) {
                rsp.setMsg("createKeyPair() Exception: " + e.getMessage());
            }
            log.error("Key pair create error:" + e.getMessage());
            return -1;
        }
        return 0;
    }


    /**
    *
    * @Title: tagResources
    * @Description: tag a list of resourceIds.
    *         If resources number exceed 500, it will break into several request each with 500 resources.
    * @param @param resourceIds,tagValue
    * @param @return
    * @return void
    * @throws
    */
    public static void tagResources(List<String> resourceIdList,  List<Tag> tagsToBeCreated) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class AWSClient in method tagEbsVolumes with parameters: resourceIdList: " + resourceIdList + ", tagsToBeCreated: "
                      + tagsToBeCreated);
        }

        if (CollectionUtils.isNullOrEmpty(resourceIdList)) {
            return;
        }

        try {
            /* Refer: https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/ec2/model/CreateTagsRequest.html
             * CreateTagsRequest:
             * Constraints: Up to 1000 resource IDs. We recommend breaking up this request into smaller batches.
            */
            int MX_RESOURCES_NUM = 500;

            log.trace("Creating tags with resource IDs: " + resourceIdList.toString());
            log.trace("Creating the following tags: " + tagsToBeCreated);

            List<String> resourceIdsInLoop = new ArrayList<String>();
            for (String resourceId: resourceIdList) {
                resourceIdsInLoop.add(resourceId);
                if (resourceIdsInLoop.size() >= MX_RESOURCES_NUM) {
                    tagResourcesOneBatch(resourceIdsInLoop, tagsToBeCreated);
                    resourceIdsInLoop.clear();
                }
            }

            if (! CollectionUtils.isNullOrEmpty(resourceIdsInLoop)) {
                tagResourcesOneBatch(resourceIdsInLoop, tagsToBeCreated);
            }

            if (log.isTraceEnabled()) {
                log.trace("End in class AWSClient in method tagResources with return: void: ");
            }

        } catch (AmazonServiceException ase) {
            log.error("Tag resources list error." + ase.getMessage(),ase);
        } catch (AmazonClientException ace) {
            log.error("Tag  resources list error." + ace.getMessage(),ace);
        } catch(Exception e) {
            log.error("Tag  resources list error.", e);
        }
    }


    /**
    *
    * @Title: tagResourcesOneBatch
    * @Description: tag a list of resourceIds less than 500.
    * @param @param resourceIds,tagValue
    * @param @return
    * @return void
    * @throws
    */
    private static void tagResourcesOneBatch(List<String> resourceIds, List<Tag> tagsToBeCreated) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class AWSClient in method tagEbsVolumes with parameters: resourceIds: " + resourceIds + ", tagsToBeCreated: "
                      + tagsToBeCreated);
        }
        AmazonEC2 ec2 = getEC2Client();


        try {
            CreateTagsRequest createTagsRequest = new CreateTagsRequest();

            log.trace("Creating tags with resource IDs: " + resourceIds);
            log.trace("Creating the following tags: " + tagsToBeCreated);

            createTagsRequest.withResources(resourceIds)
            .withTags(tagsToBeCreated);
            CreateTagsResult createTagsResult = ec2.createTags(createTagsRequest);
            log.trace("Result tag: " + createTagsResult);
            if (log.isTraceEnabled()) {
                log.trace("End in class AWSClient in method tagResourcesOneBatch with return: void: ");
            }

        } catch (AmazonServiceException ase) {
            log.error("Tag volume error." + ase.getMessage(),ase);
        } catch (AmazonClientException ace) {
            log.error("Tag volume error." + ace.getMessage(),ace);
        } catch(Exception e) {
            log.error("Tag volume error.", e);
        }
    }

    /**
     *
     * @Title: tagEbsVolumes
     * @Description: Tag EBS Volumes
     * @param @param instance,tagValue
     * @param @return
     * @return void
     * @throws
     */
    public static void tagEbsVolumes(Instance instance, List<Tag> tagsToBeCreated) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class AWSClient in method tagEbsVolumes with parameters: instance: " + instance + ", tagsToBeCreated: "
                      + tagsToBeCreated);
        }
        AmazonEC2 ec2 = getEC2Client();
        List<String> resourceIds = new ArrayList<String>();


        try {
            CreateTagsRequest createTagsRequest = new CreateTagsRequest();


            List<InstanceBlockDeviceMapping> mappingList = instance.getBlockDeviceMappings();
            for(InstanceBlockDeviceMapping mapping: mappingList) {
                if(mapping != null)	 {
                    EbsInstanceBlockDevice ebs = mapping.getEbs();
                    log.trace(mapping);
                    if (ebs != null) {
                        if (!StringUtils.isNullOrEmpty(ebs.getVolumeId())) {
                            resourceIds.add(ebs.getVolumeId());
                        }
                    }
                }
            }

            log.trace("Creating tags with resource IDs: " + resourceIds);
            log.trace("Creating the following tags: " + tagsToBeCreated);

            createTagsRequest.withResources(resourceIds)
            .withTags(tagsToBeCreated);
            CreateTagsResult createTagsResult = ec2.createTags(createTagsRequest);
            log.trace("Result tag: " + createTagsResult);
            if (log.isTraceEnabled()) {
                log.trace("End in class AWSClient in method tagEbsVolumes with return: void: ");
            }

        } catch (AmazonServiceException ase) {
            log.error("Tag volume error." + ase.getMessage(),ase);
        } catch (AmazonClientException ace) {
            log.error("Tag volume error." + ace.getMessage(),ace);
        } catch(Exception e) {
            log.error("Tag volume error.", e);
        }
    }


    /**
     *
     * @Title: requestSpotInstance
     * @Description: Create a Spot Instance(s) request
     * @param @return
     * @return List<Instance>
     * @throws
     */
    public static RequestSpotFleetResult requestSpotInstance(AwsTemplate t,
            String tagValue, AwsEntity rsp) {
        List<LaunchTemplateConfig> launchTemplateConfigs = null;
        if (log.isTraceEnabled()) {
            log.trace("Start in class AWSClient in method requestSpotInstance with parameters: t: "
                      + t + ", tagValue: " + tagValue);
        }
        try {
            boolean validSpotInstanceRequest = AwsUtil.validateSpotInstanceRequest(t);
            if (validSpotInstanceRequest) {

                AwsUtil.applyDefaultValuesForSpotInstanceTemplate(t);

                RequestSpotFleetRequest spotFleetRequest = new RequestSpotFleetRequest();
                SpotFleetRequestConfigData configData = new SpotFleetRequestConfigData();
                spotFleetRequest.setSpotFleetRequestConfig(configData);

                // Create a key pair
                String keyName = null;
                if (createKeyPair(t.getKeyName(), rsp) == 0
                        && !StringUtils.isNullOrEmpty(t.getKeyName())) {
                    keyName = t.getKeyName();
                }
                if (!StringUtils.isNullOrEmpty(t.getLaunchTemplateId())) {
                    launchTemplateConfigs = launchSpotFleetFromTemplate(t, tagValue, keyName);
                    if (launchTemplateConfigs != null) {
                        configData.withLaunchTemplateConfigs(launchTemplateConfigs);
                    }
                } else if (!StringUtils.isNullOrEmpty(t.getInterfaceType())) {
                    launchTemplateConfigs = createTemplateForSpotFleet(t, tagValue, keyName);
                    if (launchTemplateConfigs != null) {
                        configData.withLaunchTemplateConfigs(launchTemplateConfigs);
                    }
                } else {
                    List<SpotFleetLaunchSpecification> listOfLaunchSpecifications = AwsUtil.mapTemplateToLaunchSpecificationList(t, tagValue, keyName);
                    configData.setLaunchSpecifications(listOfLaunchSpecifications);
                }

                if (!StringUtils.isNullOrEmpty(t.getFleetRole())) {
                    configData.setIamFleetRole(t.getFleetRole());
                }
                configData.setAllocationStrategy(t.getAllocationStrategy());
                configData.setValidFrom(t.getRequestValidityStartTime());
                configData.setValidUntil(t.getRequestValidityEndTime());
                configData.setSpotPrice(t.getSpotPrice() + "");
                configData.setTargetCapacity(t.getVmNumber());

                // Allow instances to live even after expiration, give the
                // control to terminate the instances to LSF
                configData.setTerminateInstancesWithExpiration(false);

                // Support only non-maintain spot fleet requests
                configData.setType(FleetType.Request);

                RequestSpotFleetResult requestSpotFleetResult = ec2
                        .requestSpotFleet(spotFleetRequest);
                log.debug("[Instance - " + requestSpotFleetResult.getSpotFleetRequestId() + "] Request created");
                if (log.isTraceEnabled()) {
                    log.trace("End in class AWSClient in method requestSpotInstance with return: RequestSpotFleetResult: "
                              + requestSpotFleetResult.getSpotFleetRequestId());
                }
                return requestSpotFleetResult;
            }
        } catch (AmazonServiceException ase) {
            if (rsp != null) {
                if (isFatalError(ase.getErrorCode())) {
                    rsp.setStatus(AwsConst.EBROKERD_STATE_ERROR);
                } else {
                    rsp.setStatus(AwsConst.EBROKERD_STATE_WARNING);
                }
                rsp.setRsp(1, "Request Spot Instance on " + AwsUtil.getProviderName() + " EC2 failed. " + ase.getMessage());
            }
            log.error("Create instances error." + ase.getMessage(), ase);
            if (launchTemplateConfigs != null && !launchTemplateConfigs.isEmpty()) {
                deleteLaunchTemplate(launchTemplateConfigs, t);
            }
        } catch (AmazonClientException ace) {
            if (rsp != null) {
                rsp.setStatus(AwsConst.EBROKERD_STATE_WARNING);
                rsp.setRsp(1, "Request Spot Instance on " + AwsUtil.getProviderName()
                           + " EC2 failed." + ace.getMessage());
            }
            log.error("Create instances error." + ace.getMessage(), ace);
            if (launchTemplateConfigs != null && !launchTemplateConfigs.isEmpty()) {
                deleteLaunchTemplate(launchTemplateConfigs, t);
            }
        } catch (Exception e) {
            if (rsp != null) {
                rsp.setStatus(AwsConst.EBROKERD_STATE_WARNING);
                rsp.setRsp(1, "Request Spot Instance on " + AwsUtil.getProviderName()
                           + " EC2 failed." + e.getMessage());
            }
            log.error("Create instances error.", e);
            if (launchTemplateConfigs != null && !launchTemplateConfigs.isEmpty()) {
                deleteLaunchTemplate(launchTemplateConfigs, t);
            }
        }
        return null;
    }

    public static List<LaunchTemplateConfig> launchSpotFleetFromTemplate(AwsTemplate t, String tagValue, String keyName) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class AwsUtil in method launchSpotFleetFromTemplate with parameters: t: " + t + ", tagValue: "
                      +  tagValue );
        }
        List<LaunchTemplateConfig> launchTemplateConfigList = new ArrayList<LaunchTemplateConfig>();
        LaunchTemplateConfig launchTemplateConfig = new LaunchTemplateConfig();
        FleetLaunchTemplateSpecification fleetLaunchTemplateSpecification = new FleetLaunchTemplateSpecification();
        fleetLaunchTemplateSpecification.withLaunchTemplateId(t.getLaunchTemplateId());

        CreateLaunchTemplateVersionRequest createLaunchTemplateVersionRequest = new CreateLaunchTemplateVersionRequest();
        createLaunchTemplateVersionRequest.withLaunchTemplateId(t.getLaunchTemplateId());
        createLaunchTemplateVersionRequest.withVersionDescription("lsf-auto-created-version");
        if (!StringUtils.isNullOrEmpty(t.getLaunchTemplateVersion())) {
            createLaunchTemplateVersionRequest.withSourceVersion(t.getLaunchTemplateVersion());
        } else {
            createLaunchTemplateVersionRequest.withSourceVersion("$Default");
        }
        
        // lsf-L3-tracker/issues/378 - If no LSF user_data, should not override user_data in launch template
        String userData = AwsUtil.getEncodedUserData(t, tagValue);
        if (!StringUtils.isNullOrEmpty(userData)) {
        	RequestLaunchTemplateData requestLaunchTemplateData = new RequestLaunchTemplateData();
        	requestLaunchTemplateData.withUserData(userData);
        	createLaunchTemplateVersionRequest.withLaunchTemplateData(requestLaunchTemplateData);
        }
        try {
            CreateLaunchTemplateVersionResult createLaunchTemplateVersionResult = ec2.createLaunchTemplateVersion(createLaunchTemplateVersionRequest);
            fleetLaunchTemplateSpecification.withVersion(createLaunchTemplateVersionResult.getLaunchTemplateVersion().getVersionNumber().toString());
        } catch (Exception e) {
            log.error("Create template version error.", e.getMessage(), e);
            return null;
        }
        launchTemplateConfig.withLaunchTemplateSpecification(fleetLaunchTemplateSpecification);
        List<LaunchTemplateOverrides> launchTemplateOverridesList = new ArrayList<LaunchTemplateOverrides>();
        if (!StringUtils.isNullOrEmpty(t.getVmType())) {
            String instanceTypes = t.getVmType();
            String[] instanceTypesArray = instanceTypes.split(",");
            for (String instanceType : instanceTypesArray) {
                if (StringUtils.isNullOrEmpty(instanceType.trim())) {
                    continue;
                }
                log.debug("Creating a launch template override for the instance type: " + instanceType.trim());
                LaunchTemplateOverrides launchTemplateOverride = new LaunchTemplateOverrides();
                launchTemplateOverride.withInstanceType(instanceType.trim());
                launchTemplateOverride.withSubnetId(t.getSubnetId());
                launchTemplateOverride.withSpotPrice(t.getSpotPrice().toString());
                launchTemplateOverridesList.add(launchTemplateOverride);
            }
        } else if (!StringUtils.isNullOrEmpty(t.getSubnetId())) {
            LaunchTemplateOverrides launchTemplateOverride = new LaunchTemplateOverrides();
            launchTemplateOverride.withSubnetId(t.getSubnetId());
            launchTemplateOverride.withSpotPrice(t.getSpotPrice().toString());
            launchTemplateOverridesList.add(launchTemplateOverride);
        }
        launchTemplateConfig.withOverrides(launchTemplateOverridesList);

        launchTemplateConfigList.add(launchTemplateConfig);
        if (log.isTraceEnabled()) {
            log.trace("End in class AwsClient in method launchSpotFleetFromTemplate with return: List<LaunchTemplateConfig>: " + launchTemplateConfigList);
        }
        return launchTemplateConfigList;
    }

    /**
     * Maps an AWSTemplate to a Spot Fleet Launch Specification
     * @param t
     * @return
     */
    public static List<LaunchTemplateConfig> createTemplateForSpotFleet(AwsTemplate t, String tagValue, String keyName) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class AwsUtil in method createTemplateForSpotFleet with parameters: t: " + t + ", tagValue: "
                      +  tagValue );
        }
        List<LaunchTemplateConfig> launchTemplateConfigList = new ArrayList<LaunchTemplateConfig>();
        String encodedUserData = AwsUtil.getEncodedUserData(t, tagValue);

        String instanceTypes = t.getVmType();
        String[]instanceTypesArray = instanceTypes.split(",");

        for(String instanceType : instanceTypesArray) {
            if (StringUtils.isNullOrEmpty(instanceType.trim())) {
                continue;
            }
            log.debug("Creating a launch specification for the instance type: " + instanceType.trim());
            LaunchTemplateConfig launchTemplateConfig = new LaunchTemplateConfig();

            CreateLaunchTemplateRequest createLaunchTemplateRequest = new CreateLaunchTemplateRequest();
            RequestLaunchTemplateData requestLaunchTemplateData = new RequestLaunchTemplateData();
            requestLaunchTemplateData.withImageId(t.getImageId());
            requestLaunchTemplateData.withInstanceType(instanceType.trim());
            boolean ebsOptimized = t.getEbsOptimized();
            if (ebsOptimized == true) {
                requestLaunchTemplateData.setEbsOptimized(ebsOptimized);
            }

            if (!StringUtils.isNullOrEmpty(t.getPGrpName())) {
                LaunchTemplatePlacementRequest placementGrp = new LaunchTemplatePlacementRequest();
                placementGrp.setGroupName(t.getPGrpName());
                if (!StringUtils.isNullOrEmpty(t.getTenancy())) {
                    placementGrp.setTenancy(t.getTenancy());
                }
                requestLaunchTemplateData.setPlacement(placementGrp);
            } else if (!StringUtils.isNullOrEmpty(t.getTenancy())) {
                LaunchTemplatePlacementRequest placementGrp = new LaunchTemplatePlacementRequest();
                placementGrp.setTenancy(t.getTenancy());
                requestLaunchTemplateData.setPlacement(placementGrp);
            }

            if (!StringUtils.isNullOrEmpty(t.getInstanceProfile())) {
                requestLaunchTemplateData.setIamInstanceProfile(AwsUtil.getLaunchTemplateIamInstanceProfile(t.getInstanceProfile()));
            }
            if (!StringUtils.isNullOrEmpty(keyName)) {
                requestLaunchTemplateData.withKeyName(keyName);
            }
            if (!StringUtils.isNullOrEmpty(t.getInstanceTags())) {
                List<LaunchTemplateTagSpecificationRequest> tagSpecifications = AwsUtil.createLaunchTemplateTagSpecification(t.getInstanceTags());
                if (!tagSpecifications.isEmpty()) {
                    requestLaunchTemplateData.setTagSpecifications(tagSpecifications);
                }
            }



            LaunchTemplateInstanceNetworkInterfaceSpecificationRequest nic = new LaunchTemplateInstanceNetworkInterfaceSpecificationRequest();
            nic.setDeviceIndex(0);
            nic.setGroups(t.getSgIds());
            nic.withSubnetId(t.getSubnetId());
            nic.setInterfaceType(t.getInterfaceType().trim());

            requestLaunchTemplateData.withNetworkInterfaces(nic);
            requestLaunchTemplateData.setUserData(encodedUserData);

            createLaunchTemplateRequest.withLaunchTemplateData(requestLaunchTemplateData);
            createLaunchTemplateRequest.setLaunchTemplateName("lsf" + String.format("-%s-%s", t.getTemplateId(), Long.toString(System.nanoTime())));
            try {
                CreateLaunchTemplateResult createLaunchTemplateResult = ec2.createLaunchTemplate(createLaunchTemplateRequest);
                FleetLaunchTemplateSpecification fleetLaunchTemplateSpecification = new FleetLaunchTemplateSpecification();
                fleetLaunchTemplateSpecification.withLaunchTemplateId(createLaunchTemplateResult.getLaunchTemplate().getLaunchTemplateId());
                fleetLaunchTemplateSpecification.withVersion(createLaunchTemplateResult.getLaunchTemplate().getDefaultVersionNumber().toString());
                launchTemplateConfig.withLaunchTemplateSpecification(fleetLaunchTemplateSpecification);
                launchTemplateConfigList.add(launchTemplateConfig);
            } catch (Exception e) {
                log.error("Create template error.", e.getMessage(), e);
                return null;
            }
        }

        if (log.isTraceEnabled()) {
            log.trace("End in class AwsClient in method createTemplateForSpotFleet with return: List<LaunchTemplateConfig>: " + launchTemplateConfigList);
        }
        return launchTemplateConfigList;
    }

    
    /**
     * 1. Updates the status of the machine request by checking the EC2 Fleet status<br>
     * 2. Return the machines newly created since the last request check
     * @param awsRequest
     * @return The list of machines newly created since the last request
     */
    public static List<AwsMachine> updateEC2FleetStatus(AwsRequest awsRequest, AwsEntity rsp) {

        if (log.isDebugEnabled()) {
            log.debug("Start in class AWSClient in method updateEC2FleetStatus with parameters: awsRequest: "
                      + awsRequest);
        }
        List<AwsMachine> newMachinesList = new ArrayList<AwsMachine>();
        String fleetRequestId = awsRequest.getReqId();
        
        DescribeFleetsRequest describeFleetsRequest = new DescribeFleetsRequest();
        describeFleetsRequest.setFleetIds(Arrays
                .asList(new String[] { fleetRequestId }));
        String ebrokerdRequestStatus = null;
        DescribeFleetsResult describeFleetsResult = null;

        try {
        	describeFleetsResult = ec2.describeFleets(describeFleetsRequest);
        } catch (AmazonServiceException ase) {
            if (ase.getErrorCode().contains("InvalidFleetId")) {
                ebrokerdRequestStatus = AwsConst.EBROKERD_STATE_COMPLETE_WITH_ERROR;
                awsRequest.setStatus(ebrokerdRequestStatus);
            }
            log.error("Cannot update EC2 Fleet request <" + fleetRequestId + ">. " +  ase.getMessage(), ase);
            return null;
        } catch (Exception e) {
            log.error("Cannot update EC2 Fleet request <" + fleetRequestId + ">. " +  e.getMessage(), e);
            return null;
        }
        
        //Get EC2 fleet request status
        FleetData fleetData = describeFleetsResult.getFleets().get(0);
        String fleetState = fleetData.getFleetState();
        String fleetActivityStatus = fleetData.getActivityStatus();
        log.debug("[EC2 Fleet request - " + fleetRequestId + "] State: " + fleetState);
        log.debug("[EC2 Fleet request - " + fleetRequestId + "] Activity Status: " + fleetActivityStatus);
        
        if (log.isTraceEnabled()) {
        	log.trace("[EC2 Fleet request - " + fleetRequestId + "] Detailed info: " + fleetData.toString());
        }
        
        if (FleetStateCode.Submitted.toString().equals(fleetState)
        		|| FleetStateCode.Modifying.toString().equals(fleetState)) {
        	ebrokerdRequestStatus = AwsConst.EBROKERD_STATE_RUNNING;
        } else if (FleetStateCode.Active.toString().equals(fleetState)) {
        	//One of the main reasons causing the Error status: When the price in the request is lower than the current market price
        	if (FleetActivityStatus.Error.toString().equals(fleetActivityStatus)) {
        		ebrokerdRequestStatus = AwsConst.EBROKERD_STATE_RUNNING;
        	} else if (FleetActivityStatus.Pending_fulfillment.toString().equals(fleetActivityStatus)
        			|| FleetActivityStatus.Pending_termination.toString().equals(fleetActivityStatus)) {
        		ebrokerdRequestStatus = AwsConst.EBROKERD_STATE_RUNNING;
        	} else if (FleetActivityStatus.Fulfilled.toString().equals(fleetActivityStatus)) {
        		ebrokerdRequestStatus = AwsConst.EBROKERD_STATE_COMPLETE;
        	}
        } else if (FleetStateCode.Deleted_running.toString().equals(fleetState)
        		|| FleetStateCode.Deleted_terminating.toString().equals(fleetState)) {
        	//The request has expires, mark the aws lack of capacity
        	ebrokerdRequestStatus = AwsConst.EBROKERD_STATE_COMPLETE;
        	if (rsp != null) {
        		rsp.setRsp(0, "Error Code: InsufficientCapacity");
        		log.warn("Not fulfilled target capacity for EC2 Fleet Request <" + fleetRequestId + "> within specified time period, return InsufficientCapacity to disable the template for a while");
        	}
        } else if (FleetStateCode.Deleted.toString().equals(fleetState)) {
        	ebrokerdRequestStatus = AwsConst.EBROKERD_STATE_COMPLETE;
        } else if (FleetStateCode.Failed.toString().equals(fleetState)) {
        	ebrokerdRequestStatus = AwsConst.EBROKERD_STATE_COMPLETE_WITH_ERROR;
        }
        
        
        // Setting the status of the request
        awsRequest.setStatus(ebrokerdRequestStatus);
        
        //Get instances created by this fleet request, checking if there are new instances created 
        DescribeFleetInstancesRequest describeFleetInstancesRequest = new DescribeFleetInstancesRequest ();
        describeFleetInstancesRequest.setFleetId(fleetRequestId);
        DescribeFleetInstancesResult describeFleetInstancesResult = ec2.describeFleetInstances(describeFleetInstancesRequest);
        List<ActiveInstance> activeInstances = describeFleetInstancesResult.getActiveInstances();
        log.debug("Active Instances for EC2 Fleet request "
                + describeFleetInstancesResult.getFleetId() + " : " + activeInstances);
        
        for (ActiveInstance activeInstance: activeInstances) {
            // If the system does not have this activeInstance, add it to the
            // newMachines list
        	AwsMachine tempAwsMachine = new AwsMachine();
        	tempAwsMachine.setMachineId(activeInstance.getInstanceId());
        	if (!awsRequest.getMachines().contains(tempAwsMachine)) {
        		// This instance is not exist in input awsRequest.
        		log.debug("This is an active machine not in awsRequest: " + activeInstance);
        		newMachinesList.add(tempAwsMachine);
        	}
        }

        if (!newMachinesList.isEmpty()) {

            // Add the new machines to the actual machines list
            log.trace("The count of machines before adding active machines: "
                      + awsRequest.getMachines().size());
            awsRequest.getMachines().addAll(newMachinesList);
            log.trace("The count of machines after adding active machines: "
                      + awsRequest.getMachines().size());

        }
        if (log.isTraceEnabled()) {
            log.trace("End in class AWSClient in method updateEC2FleetStatus with return: "
                      + newMachinesList);
        }
        return newMachinesList;
    }


    /**
     * 1. Updates the status of the machine request by checking the Spot Fleet status<br>
     * 2. Return the machines newly created since the last request check
     * @param awsRequest
     * @return The list of machines newly created since the last request
     */
    public static List<AwsMachine> updateSpotFleetStatus(AwsRequest awsRequest) {

        if (log.isDebugEnabled()) {
            log.debug("Start in class AWSClient in method updateSpotFleetStatus with parameters: awsRequest: "
                      + awsRequest);
        }
        List<AwsMachine> newMachinesList = new ArrayList<AwsMachine>();
        String spotFleetRequestId = awsRequest.getReqId();
        DescribeSpotFleetRequestsRequest describeSpotFleetRequestsRequest = new DescribeSpotFleetRequestsRequest();
        describeSpotFleetRequestsRequest.setSpotFleetRequestIds(Arrays
                .asList(new String[] { spotFleetRequestId }));
        String ebrokerdRequestStatus = null;
        DescribeSpotFleetRequestsResult describeSpotFleetRequestsResult = null;

        try {
            describeSpotFleetRequestsResult = ec2.describeSpotFleetRequests(describeSpotFleetRequestsRequest);
        } catch (AmazonServiceException ase) {
            if (ase.getErrorCode().contains("InvalidSpotFleetRequestId")) {
                ebrokerdRequestStatus = AwsConst.EBROKERD_STATE_COMPLETE_WITH_ERROR;
                awsRequest.setStatus(ebrokerdRequestStatus);
            }
            log.error("Cannot update Spot fleet request <" + spotFleetRequestId + ">. " +  ase.getMessage(), ase);
            return null;
        } catch (Exception e) {
            log.error("Cannot update Spot fleet request <" + spotFleetRequestId + ">. " +  e.getMessage(), e);
            return null;
        }

        List<SpotFleetRequestConfig> spotFleetRequestConfigList = describeSpotFleetRequestsResult
                .getSpotFleetRequestConfigs();
        SpotFleetRequestConfig spotFleetRequestConfig = spotFleetRequestConfigList
                .get(0);
        String spotFleetState = spotFleetRequestConfig
                                .getSpotFleetRequestState();
        String spotFleetActivityStatus = spotFleetRequestConfig
                                         .getActivityStatus();
        log.debug("[Instance - " + spotFleetRequestId + "] State: " + spotFleetState);
        log.debug("[Instance - " + spotFleetRequestId + "] Activity Status: " + spotFleetActivityStatus);

        if (BatchState.Submitted.toString().equals(spotFleetState)) {
            ebrokerdRequestStatus = AwsConst.EBROKERD_STATE_RUNNING;
        } else if (BatchState.Active.toString().equals(spotFleetState)) {
            //One of the main reasons causing the Error status: When the price in the request is lower than the current market price
            if (ActivityStatus.Error.toString().equals(spotFleetActivityStatus)) {
                ebrokerdRequestStatus = AwsConst.EBROKERD_STATE_RUNNING;
            } else if (ActivityStatus.Pending_fulfillment.toString().equals(
                           spotFleetActivityStatus)
                       || ActivityStatus.Pending_termination.toString().equals(
                           spotFleetActivityStatus)) {
                ebrokerdRequestStatus = AwsConst.EBROKERD_STATE_RUNNING;
            } else if (ActivityStatus.Fulfilled.toString().equals(
                           spotFleetActivityStatus)) {
                ebrokerdRequestStatus = AwsConst.EBROKERD_STATE_COMPLETE;
            }

        } else if (BatchState.Cancelled.toString().equals(spotFleetState)
                || BatchState.Cancelled_terminating.toString().equals(
                        spotFleetState)
                    || BatchState.Cancelled_running.toString().equals(
                        spotFleetState)) {
            ebrokerdRequestStatus = AwsConst.EBROKERD_STATE_COMPLETE;
        } else if (BatchState.Failed.toString().equals(spotFleetState)) {
            ebrokerdRequestStatus = AwsConst.EBROKERD_STATE_COMPLETE_WITH_ERROR;
        }

        // Setting the status of the request
        awsRequest.setStatus(ebrokerdRequestStatus);

        // Checking if there are new spot instances created
        DescribeSpotFleetInstancesRequest describeSpotFleetInstancesRequest = new DescribeSpotFleetInstancesRequest();
        describeSpotFleetInstancesRequest
        .setSpotFleetRequestId(spotFleetRequestId);
        DescribeSpotFleetInstancesResult describeSpotFleetInstancesResult = ec2
                .describeSpotFleetInstances(describeSpotFleetInstancesRequest);
        List<ActiveInstance> activeInstances = describeSpotFleetInstancesResult
                                               .getActiveInstances();
        log.debug("Active Instances for spot fleet request "
                  + describeSpotFleetInstancesResult.getSpotFleetRequestId()
                  + " : " + activeInstances);

        for (ActiveInstance activeInstance : activeInstances) {
            // If the system does not have this activeInstance, add it to the
            // newMachines list
            AwsMachine tempAwsMachine = new AwsMachine();
            tempAwsMachine.setMachineId(activeInstance.getInstanceId());
            if (!awsRequest.getMachines().contains(tempAwsMachine)) {
                log.debug("This is an active machine not in awsRequest: " + activeInstance);
                newMachinesList.add(tempAwsMachine);
            }
        }

        if (!newMachinesList.isEmpty()) {

            // Add the new machines to the actual machines list
            log.trace("The count of machines before adding active machines: "
                      + awsRequest.getMachines().size());
            awsRequest.getMachines().addAll(newMachinesList);
            log.trace("The count of machines after adding active machines: "
                      + awsRequest.getMachines().size());

        }
        if (log.isTraceEnabled()) {
            log.trace("End in class AWSClient in method updateSpotFleetStatus with return: void: "
                      + newMachinesList);
        }
        return newMachinesList;
    }

    public static void deleteLaunchTemplate(List<LaunchTemplateConfig> templateConfigList, AwsTemplate t) {

        try {
            if (templateConfigList != null &&
                    !templateConfigList.isEmpty()) {
                if (!StringUtils.isNullOrEmpty(t.getInterfaceType())) {
                    DeleteLaunchTemplateRequest deleteLaunchTemplateRequest = new DeleteLaunchTemplateRequest();
                    deleteLaunchTemplateRequest.withLaunchTemplateId(templateConfigList.get(0).getLaunchTemplateSpecification().getLaunchTemplateId());
                    DeleteLaunchTemplateResult deleteLaunchTemplateResult = ec2.deleteLaunchTemplate(deleteLaunchTemplateRequest);
                } else if (!StringUtils.isNullOrEmpty(t.getLaunchTemplateId())) {
                    DeleteLaunchTemplateVersionsRequest deleteLaunchTemplateVersionsRequest = new DeleteLaunchTemplateVersionsRequest();
                    deleteLaunchTemplateVersionsRequest.withLaunchTemplateId(templateConfigList.get(0).getLaunchTemplateSpecification().getLaunchTemplateId());
                    deleteLaunchTemplateVersionsRequest.withVersions(templateConfigList.get(0).getLaunchTemplateSpecification().getVersion());
                    DeleteLaunchTemplateVersionsResult deleteLaunchTemplateVersionsResult = ec2.deleteLaunchTemplateVersions(deleteLaunchTemplateVersionsRequest);

                }
            }
        } catch (AmazonServiceException ase) {
            log.error("Cannot delete Spot template for fleet request " +  ase.getMessage(), ase);
        } catch (Exception e) {
            log.error("Cannot delete template for Spot fleet request " +  e.getMessage(), e);
        }

    }

    /**
     * @Description Delete launch template of EC2 Fleet request
     * @param fleetLaunchTmeplateConfigList
     */
    public static void deleteEC2FleetLaunchTemplate(List<FleetLaunchTemplateConfigRequest> fleetLaunchTmeplateConfigList) {
    	if (CollectionUtils.isNullOrEmpty(fleetLaunchTmeplateConfigList)) {
    		return;
    	}
    	for (FleetLaunchTemplateConfigRequest config: fleetLaunchTmeplateConfigList) {
    		DeleteLaunchTemplateVersionsRequest deleteLaunchTemplateVersionsRequest = new DeleteLaunchTemplateVersionsRequest();
    		try {
    			deleteLaunchTemplateVersionsRequest.withLaunchTemplateId(config.getLaunchTemplateSpecification().getLaunchTemplateId());
    			deleteLaunchTemplateVersionsRequest.withVersions(config.getLaunchTemplateSpecification().getVersion());
    			DeleteLaunchTemplateVersionsResult deleteLaunchTemplateVersionsResult = ec2.deleteLaunchTemplateVersions(deleteLaunchTemplateVersionsRequest);
    		} catch (AmazonServiceException ase) {
    			log.error("Cannot delete EC2 Fleet launch template: " + deleteLaunchTemplateVersionsRequest + ", " + ase.getMessage(), ase);
    		} catch (Exception e) {
    			log.error("Cannot delete EC2 Fleet launch template: " + deleteLaunchTemplateVersionsRequest + ", " +  e.getMessage(), e);
    		}
    	}
    }
    
    
    /**
     * Deletes the template for a spot request
     *
     * @param awsRequest
    */
    public static void deleteSpotFleetTemplateForAwsRequest(AwsRequest awsRequest) {

        log.debug("delete template for spot request " + awsRequest.getReqId());
        String spotFleetRequestId = awsRequest.getReqId();
        DescribeSpotFleetRequestsRequest describeSpotFleetRequestsRequest = new DescribeSpotFleetRequestsRequest();
        describeSpotFleetRequestsRequest.setSpotFleetRequestIds(Arrays
                .asList(new String[] { spotFleetRequestId }));
        try {
            DescribeSpotFleetRequestsResult describeSpotFleetRequestsResult = ec2.describeSpotFleetRequests(describeSpotFleetRequestsRequest);
            List<SpotFleetRequestConfig> spotFleetRequestConfigList = describeSpotFleetRequestsResult.getSpotFleetRequestConfigs();
            SpotFleetRequestConfig spotFleetRequestConfig = spotFleetRequestConfigList.get(0);
            List<LaunchTemplateConfig> templateConfigList = spotFleetRequestConfig.getSpotFleetRequestConfig().getLaunchTemplateConfigs();
            if (templateConfigList != null &&
                    !templateConfigList.isEmpty()) {
                AwsTemplate t = AwsUtil.getTemplateFromFile(awsRequest.getTemplateId());
                if (!StringUtils.isNullOrEmpty(t.getInterfaceType())) {
                    DeleteLaunchTemplateRequest deleteLaunchTemplateRequest = new DeleteLaunchTemplateRequest();
                    deleteLaunchTemplateRequest.withLaunchTemplateId(templateConfigList.get(0).getLaunchTemplateSpecification().getLaunchTemplateId());
                    DeleteLaunchTemplateResult deleteLaunchTemplateResult = ec2.deleteLaunchTemplate(deleteLaunchTemplateRequest);
                } else if (!StringUtils.isNullOrEmpty(t.getLaunchTemplateId())) {
                    DeleteLaunchTemplateVersionsRequest deleteLaunchTemplateVersionsRequest = new DeleteLaunchTemplateVersionsRequest();
                    deleteLaunchTemplateVersionsRequest.withLaunchTemplateId(templateConfigList.get(0).getLaunchTemplateSpecification().getLaunchTemplateId());
                    deleteLaunchTemplateVersionsRequest.withVersions(templateConfigList.get(0).getLaunchTemplateSpecification().getVersion());
                    DeleteLaunchTemplateVersionsResult deleteLaunchTemplateVersionsResult = ec2.deleteLaunchTemplateVersions(deleteLaunchTemplateVersionsRequest);

                }
            }
        } catch (AmazonServiceException ase) {

            log.error("Cannot delete Spot template for fleet request <" + spotFleetRequestId + ">. " +  ase.getMessage(), ase);
        } catch (Exception e) {
            log.error("Cannot delete template for Spot fleet request <" + spotFleetRequestId + ">. " +  e.getMessage(), e);
        }

    }
    
    
    /**
     * Deletes the template for a EC2 Fleet request
     *
     * @param awsRequest
    */
    public static void deleteEC2FleetTemplateForAwsRequest(AwsRequest awsRequest) {
        log.debug("delete template for EC2 Fleet request " + awsRequest.getReqId());
        String fleetRequestId = awsRequest.getReqId();
        DescribeFleetsRequest describeFleetsRequest = new DescribeFleetsRequest();
        describeFleetsRequest.setFleetIds(Arrays
                .asList(new String[] { fleetRequestId }));
        DescribeFleetsResult describeFleetsResult = null;
        try {
        	describeFleetsResult = ec2.describeFleets(describeFleetsRequest);
        } catch (AmazonServiceException ase) {
        	log.error("Failed to call describeFleets: " + ase.getMessage(), ase);
        	return;
        } catch (Exception e) {
        	log.error("Failed to call describeFleets: " + e.getMessage(), e);
        	return;
        }
        
        if (describeFleetsResult != null && !CollectionUtils.isNullOrEmpty(describeFleetsResult.getFleets())) {
        	FleetData fleetData = describeFleetsResult.getFleets().get(0);
        	List <FleetLaunchTemplateConfig> fleetLaunchTemplateConfigList = fleetData.getLaunchTemplateConfigs();
        	for (FleetLaunchTemplateConfig config : fleetLaunchTemplateConfigList) {
        		DeleteLaunchTemplateVersionsRequest deleteLaunchTemplateVersionsRequest = new DeleteLaunchTemplateVersionsRequest();
        		try {
        			deleteLaunchTemplateVersionsRequest.withLaunchTemplateId(config.getLaunchTemplateSpecification().getLaunchTemplateId());
        			deleteLaunchTemplateVersionsRequest.withVersions(config.getLaunchTemplateSpecification().getVersion());
        			DeleteLaunchTemplateVersionsResult deleteLaunchTemplateVersionsResult = ec2.deleteLaunchTemplateVersions(deleteLaunchTemplateVersionsRequest);
        		} catch (AmazonServiceException ase) {
        			log.error("Cannot delete EC2 Fleet launch template: " + deleteLaunchTemplateVersionsRequest + ", " + ase.getMessage(), ase);
        		} catch (Exception e) {
        			log.error("Cannot delete EC2 Fleet launch template: " + deleteLaunchTemplateVersionsRequest + ", " +  e.getMessage(), e);
        		}
        	}
        }
    }

    /**
     * Performs the logic needed after Instances are created: <br>- Creates tags for
     * the created instance <br>-Create tags for the EBS volumes
     *
     * @param awsRequest
     * @param newInstances
     * @param usedTemplate
     */
    public static void applyPostCreationBehaviorForInstanceList(AwsRequest awsRequest,
            List<Instance> newInstances, AwsTemplate usedTemplate) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class AWSClient in method applyPostCreationBehaviorForInstanceList with parameters: newInstances: "
                      + newInstances);
        }
        if (CollectionUtils.isNullOrEmpty(newInstances)) {
            if (log.isTraceEnabled()) {
                log.trace("End in class AWSClient in method applyPostCreationBehaviorForInstanceLis. No instances need tagged ");
            }
            return;
        }

        List<Tag> tagsToBeCreated = createTagsForInstanceCreation(
                usedTemplate.getInstanceTags(),
                awsRequest.getTagValue());
        log.trace("Creating the following tags: " + tagsToBeCreated);

        List<String> resourceIdList = new ArrayList<String>();
        for (Instance instance : newInstances) {
            String instanceId = instance.getInstanceId();
            List<String> ebsIds = new ArrayList<String>();

            List<InstanceBlockDeviceMapping> mappingList = instance.getBlockDeviceMappings();
            for (InstanceBlockDeviceMapping mapping: mappingList) {
                 if (mapping != null)	 {
                     EbsInstanceBlockDevice ebs = mapping.getEbs();
                     log.trace(mapping);
                     if (ebs != null) {
                        if (!StringUtils.isNullOrEmpty(ebs.getVolumeId())) {
                            ebsIds.add(ebs.getVolumeId());
                        }
                    }
                }
            }

            resourceIdList.add(instanceId);
            resourceIdList.addAll(ebsIds);
            log.trace("Add to resourceIdList of instance" + instanceId +" Ebs Volumes: " + ebsIds);
        }

        tagResources(resourceIdList, tagsToBeCreated);

        if (log.isTraceEnabled()) {
            log.trace("End in class AWSClient in method applyPostCreationBehaviorForInstanceList with return: void: ");
        }
    }


    /**
     * Performs the logic needed after Instances are created: <br>- Creates tags for
     * the created instance <br>-Create tags for the EBS volumes
     *
     * @param awsRequest
     * @param newInstances
     * @param usedTemplate
     */
    public static void applyPostCreationBehaviorForInstance(AwsRequest awsRequest,
            Instance newInstance, AwsTemplate usedTemplate) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class AWSClient in method applyPostCreationBehaviorForInstance with parameters: newInstances: "
                      + newInstance);
        }
        if (newInstance != null) {
            List<Tag> tagsToBeCreated = createTagsForInstanceCreation(newInstance,
            		                    usedTemplate.getInstanceTags(),
                                        awsRequest.getTagValue());
            AWSClient.tagInstance(newInstance,tagsToBeCreated);
            AWSClient.tagEbsVolumes(newInstance, tagsToBeCreated);

        }
        if (log.isTraceEnabled()) {
            log.trace("End in class AWSClient in method applyPostCreationBehaviorForInstance with return: void: ");
        }

    }

    private static void tagInstance(Instance instance, List<Tag> tagsToBeCreated) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class AWSClient in method tagInstance with parameters:  instance: " + instance + ", tagsToBeCreated: "
                      + tagsToBeCreated);
        }

        String instanceId = instance.getInstanceId();
        try {
            CreateTagsRequest createTagsRequest = new CreateTagsRequest();
            createTagsRequest.withResources(instanceId).withTags(tagsToBeCreated);
            ec2.createTags(createTagsRequest);

        } catch (AmazonServiceException ase) {
            log.error("Create tag failed for [instanceId: "
                      + instanceId
                      + ", with the tags: "
                      + tagsToBeCreated
                      + " ] with the following error: " + ase.getMessage(), ase);
        } catch (AmazonClientException ace) {
            log.error("Create tag failed for [instanceId: "
                      + instanceId
                      + ", with the tags: "
                      + tagsToBeCreated
                      + " ] with the following error: " + ace.getMessage(), ace);
        } catch (Exception e) {
            log.error("Create tag failed for [instanceId: "
                      + instanceId
                      + ", with the tags: "
                      + tagsToBeCreated
                      + " ] with the following error: " + e.getMessage(), e);
        }
        if (log.isTraceEnabled()) {
            log.trace("End in class AWSClient in method tagInstance with return: void: ");
        }

    }

    /**
     * @param requestsToBeChecked
     */
    public static List<AwsRequest> retrieveInstancesMarkedForTermination(List<AwsRequest> requestsList) {
        if (log.isTraceEnabled()) {
            log.trace("[getReturnRequest]Start in class AWSClient in method retrieveInstancesMarkedForTermination with parameters: requestsList: "
                      +  requestsList );
        }
        List<String> terminatedStates = Arrays.asList(new String[] {"stopped","shutting-down","terminated","stopping",AwsConst.SPOT_INSTANTCE_STATUS_MARKED_FOR_TERMINATION});
        List<AwsRequest> instancesMarkedForTermination = new ArrayList<AwsRequest>();
        if(!CollectionUtils.isNullOrEmpty(requestsList)) {
            List<String> spotInstanceRequestIdList = new ArrayList<String>();
            Map<String,AwsMachine> machinesMap = new HashMap<String,AwsMachine>();
            Map<String,AwsMachine> updatedMachinesMap = new HashMap<String,AwsMachine>();
            Map<String,AwsRequest> requestsMap = new HashMap<String,AwsRequest>();
            for(AwsRequest awsRequest : requestsList) {

                if(!CollectionUtils.isNullOrEmpty(awsRequest.getMachines())) {
                    //Handle only Spot Instances for now
                	if (awsRequest.getFleetType() != null) {
                		for(AwsMachine awsMachine : awsRequest.getMachines()) {
                            //Only query running machines. Terminated machines have been already handled
                            if(!terminatedStates.contains(awsMachine.getStatus())) {
                            	if (HostAllocationType.Spot.equals(awsMachine.getLifeCycleType())) { 
                            		spotInstanceRequestIdList.add(awsMachine.getReqId());
                            		machinesMap.put(awsMachine.getReqId(),awsMachine);
                            		requestsMap.put(awsMachine.getReqId(), awsRequest);
                            	}
                            }
                		} 
                	} else if (HostAllocationType.Spot.toString().equals(awsRequest.getHostAllocationType())) {
                    	//Old spot fleet request may have no lifeCycleType
                		for(AwsMachine awsMachine : awsRequest.getMachines()) {
                			//Only query running machines. Terminated machines have been already handled
                			if(!terminatedStates.contains(awsMachine.getStatus())) {
                				spotInstanceRequestIdList.add(awsMachine.getReqId());
                				machinesMap.put(awsMachine.getReqId(),awsMachine);
                				requestsMap.put(awsMachine.getReqId(), awsRequest);
                			}
                		}
                	}	
                    //TODO To be discussed, if handling is needed for on-demand instances that are notified from AWS side but their status is still shown as running in host factory side
                }
            }
            //If there are machines in the local DB that are not terminated, check if AWS is requesting to reclaim them
            if(!spotInstanceRequestIdList.isEmpty()) {
                DescribeSpotInstanceRequestsRequest describeSpotInstanceRequestsRequest = new DescribeSpotInstanceRequestsRequest();
                describeSpotInstanceRequestsRequest.withSpotInstanceRequestIds(spotInstanceRequestIdList);
                boolean addMachineToReclaimed;
                DescribeSpotInstanceRequestsResult describeSpotInstanceRequestsResult = null;
                try {
                    describeSpotInstanceRequestsResult = ec2.describeSpotInstanceRequests(describeSpotInstanceRequestsRequest);
                } catch (Exception e) {
                    log.error("Failed to get spot instance request status" + e.getMessage(), e);
                    return instancesMarkedForTermination;
                }
                List<SpotInstanceRequest> updatedSpotInstancesRequests = describeSpotInstanceRequestsResult.getSpotInstanceRequests();
                log.debug("[getReturnRequest]Spot Instances Requests: " + updatedSpotInstancesRequests);
                long gracePeriod;
                for(SpotInstanceRequest spotInstanceRequest : updatedSpotInstancesRequests) {
                    if(!StringUtils.isNullOrEmpty(spotInstanceRequest.getState()) && spotInstanceRequest.getStatus() != null) {
                        addMachineToReclaimed = false;
                        gracePeriod=0;
                        SpotInstanceStatus spotInstanceStatus = spotInstanceRequest.getStatus();
                        //If spot instance is marked for termination
                        if (AwsConst.markedForTerminationStates.contains(spotInstanceStatus.getCode())) {
                            log.warn("Machine has been marked for termination spotInstanceRequest : "
                                     + spotInstanceRequest);
                            log.warn("Machine has been marked for termination current SpotInstanceStatus: "
                                     + spotInstanceStatus);
                            addMachineToReclaimed = true;
                            // Set the gracePeriod = Termination notification
                            // period(2 minutes) - Time lapsed since the state
                            // changed to marked for termination
                            long stateChangeTime = spotInstanceStatus.getUpdateTime().getTime();
                            long currentTime = Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTimeInMillis();
                            long timeSinceStateChangedInSeconds = (currentTime - stateChangeTime) / 1000;
                            if (timeSinceStateChangedInSeconds >= 0
                                    && timeSinceStateChangedInSeconds <= AwsConst.SPOT_INSTANCE_TERMINATION_NOTICE_PERIOD_IN_SECONDS) {
                                gracePeriod = AwsConst.SPOT_INSTANCE_TERMINATION_NOTICE_PERIOD_IN_SECONDS
                                              - timeSinceStateChangedInSeconds;
                            }

                        } else if (SpotInstanceState.Closed.toString().equals(spotInstanceRequest.getState())) {
                            // If spot instance is closed as a result of
                            // other reasons: no-capacity,
                            // capacity-oversubscribed,
                            // launch-group-constraint, ...
                            log.warn(
                                "Machine was been terminated due to an un-expected reason: " + spotInstanceRequest);
                            addMachineToReclaimed = true;
                        }


                        //If this machine has been reclaimed or has been already terminated, add the machine to the response
                        if (addMachineToReclaimed) {
                            AwsMachine correspondingMachine = machinesMap.get(spotInstanceRequest.getSpotInstanceRequestId());
                            if (correspondingMachine != null ) {
                                //If the machine's status is terminated-by-price, set the machine status in the AWS plugin to terminated, otherwise set the machine status as marked-for-termination
                                String machineStatus = (AwsConst.markedForTerminationStates.contains(spotInstanceStatus.getCode())) ?
                                                       spotInstanceRequest.getStatus().getCode() : "terminated";
                                correspondingMachine.setStatus(machineStatus);
                                updatedMachinesMap.put(correspondingMachine.getMachineId(),correspondingMachine);
                                AwsRequest awsRequest = new AwsRequest();
                                // add machineId for reclaimed instances
                                awsRequest.setMachineId(correspondingMachine.getMachineId());
                                awsRequest.setVmName(correspondingMachine.getName());
                                awsRequest.setGracePeriod(gracePeriod);
                                instancesMarkedForTermination.add(awsRequest);

                                String machineId = null;

                                machineId = correspondingMachine.getMachineId();
                                log.debug("[Instance - " +
                                          requestsMap.get(spotInstanceRequest.getSpotInstanceRequestId()).getReqId() + " - "
                                          + spotInstanceRequest.getSpotInstanceRequestId() + " - "
                                          + machineId +"] "
                                          + "Get Return Machine State: " + spotInstanceRequest.getState());
                            }
                        }
                    }
                }
                // Update the status of the machines in the local DB that have
                // been marked for termination or have been terminated by price
                // AwsUtil.updateToFile(updatedMachinesMap);
            }
        }

        if (log.isTraceEnabled()) {
            log.trace("[getReturnRequest]End in class AWSClient in method retrieveInstancesMarkedForTermination with return: void: " + instancesMarkedForTermination);
        }
        return instancesMarkedForTermination;
    }

    static boolean isFatalError(String errorCode) {
        List<String> fatal = Arrays.asList(new String[] {"AuthFailure","Blocked","UnauthorizedOperation", "InvalidAction","UnsupportedInstanceAttribute","Unsupported", "InvalidParameterCombination", "InvalidSpotFleetRequestConfig", "InvalidSubnetID.NotFound"});
        if(fatal.contains(errorCode)) {
            return true;
        }
        return false;
    }

    public static Double doCurrentSpotPrice(AwsTemplate t) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class AWSClient in method getCurrentSpotPrice with parameters: ");
        }
        if (StringUtils.isNullOrEmpty(t.getVmType()) ||
            StringUtils.isNullOrEmpty(t.getSubnetId())) {
                return 0.0;
        }
        if (!t.getAllocationStrategy().equalsIgnoreCase(AllocationStrategy.LowestPrice.toString())) {
            log.debug("market spot price is only supported for lowestPrice allocation strategy");
            return 0.0;
        }
        String[] instanceTypesArray = t.getVmType().split(",");
        if (instanceTypesArray.length > 1) {
            log.debug("market spot price is not supported for multiple vm types");
            return 0.0;
        }
        try {
            DescribeSpotPriceHistoryRequest request = new DescribeSpotPriceHistoryRequest();
            String[] subnetArray = t.getSubnetId().split(",");
            Collection<String> subnetList = new ArrayList<String>();
            for (String s : subnetArray) {
                 subnetList.add(s);
            }    
            DescribeSubnetsRequest subnetRequest = new DescribeSubnetsRequest();
            subnetRequest.setSubnetIds(subnetList);
            DescribeSubnetsResult subnetResult = ec2.describeSubnets(subnetRequest); 
            List<Subnet> subnets = subnetResult.getSubnets();
            Double minPrice = Double.MAX_VALUE;
            for (Subnet subnet : subnets) {
             
                 Collection<String> instanceType = new ArrayList<String>();
                 instanceType.add(instanceTypesArray[0].trim());
                 request.setInstanceTypes(instanceType);
                 Collection<String> productDescriptions = new ArrayList<String>(); 
                 productDescriptions.add("Linux/UNIX");
                 request.setProductDescriptions(productDescriptions);
                 request.setStartTime(new Date());
                 request.setAvailabilityZone(subnet.getAvailabilityZone());
                 DescribeSpotPriceHistoryResult result = ec2.describeSpotPriceHistory(request);
                 if (!result.getSpotPriceHistory().isEmpty()) {
                     Double currentPrice = Double.parseDouble(result.getSpotPriceHistory().get(0).getSpotPrice());
                     if (currentPrice < minPrice) {
                         minPrice = currentPrice;
                     }
                 }
            }
            if (minPrice != Double.MAX_VALUE) {
                log.debug("minimum price for template " + t.getTemplateId() + " vm type " + t.getVmType() + " in zone is " + minPrice);
                return minPrice;
            } else {
                log.error("Could not retrieve current spot price");
                return 0.0;
            }
        } catch (AmazonServiceException e) {
            log.error("Exception in doCurrentSpotPrice " + e.getMessage());
            return 0.0;
        }
    }
}
