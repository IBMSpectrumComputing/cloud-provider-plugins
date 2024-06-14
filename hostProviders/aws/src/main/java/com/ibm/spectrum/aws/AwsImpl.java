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

import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.Iterator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.amazonaws.services.ec2.model.CreateFleetInstance;
import com.amazonaws.services.ec2.model.CreateFleetRequest;
import com.amazonaws.services.ec2.model.CreateFleetResult;
import com.amazonaws.services.ec2.model.FleetType;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceStateChange;
import com.amazonaws.services.ec2.model.InstanceStatus;
import com.amazonaws.services.ec2.model.RequestSpotFleetResult;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.util.CollectionUtils;
import com.amazonaws.util.StringUtils;
import com.ibm.spectrum.aws.client.AWSClient;
import com.ibm.spectrum.constant.AwsConst;
import com.ibm.spectrum.model.AwsEntity;
import com.ibm.spectrum.model.AwsMachine;
import com.ibm.spectrum.model.AwsRequest;
import com.ibm.spectrum.model.AwsTemplate;
import com.ibm.spectrum.model.AwsUserData;
import com.ibm.spectrum.model.HostAllocationType;
import com.ibm.spectrum.util.AwsUtil;

/**
 * @ClassName: AwsImpl
 * @Description: The implementation of AWS host provider
 * @author xawangyd
 * @date Jan 26, 2016 3:46:14 PM
 * @version 1.0
 */
public class AwsImpl implements IAws {
    private static Logger log = LogManager.getLogger(AwsImpl.class);

    private static List<String> terminationStates = Arrays.asList(new String[] {"stopped","shutting-down","terminated","stopping"});
    private static List<String> alreadyTerminatedStates = Arrays.asList(new String[] {"stopped","terminated"});
    private static String       terminatedState = "terminated";

    /**
     * (Non Javadoc)
     * <p>
     * Title: getAvailableTemplates
     * </p>
     * <p>
     * Description:
     * </p>
     *
     * @param req
     * @return
     * @see com.ibm.spectrum.aws.IAws#getAvailableTemplates(com.ibm.spectrum.model.AwsEntity)
     */
    @Override
    public AwsEntity getAvailableTemplates(AwsEntity req) {
        String msg = null;
        AwsEntity rsp = null;
        Properties properties = new Properties();
        String userDataStr = "";
        File jf = new File(AwsUtil.getConfDir() + File.separator + "conf"
                           + File.separator + "awsprov_templates.json");
        if (!jf.exists()) {
            rsp = new AwsEntity();
            rsp.setStatus(AwsConst.EBROKERD_STATE_ERROR);
            msg = "AWS template file awsprov_templates.json does not exist in the "
                  + AwsUtil.getProviderName()
                  + " provider configuration directory.";
            rsp.setRsp(1, msg);
            log.debug(msg);
            return rsp;
        }

        rsp = AwsUtil.toObject(jf, AwsEntity.class);
        
        if (rsp == null
                || CollectionUtils.isNullOrEmpty(rsp.getTemplates()) ) {
            if (rsp == null) {
                rsp = new AwsEntity();
            }
            rsp.setStatus(AwsConst.EBROKERD_STATE_ERROR);
            msg = "Template file awsprov_templates.json is not a valid JSON format file.";
            rsp.setRsp(1, msg);
            log.debug( msg);
            return rsp;
        }

        log.debug("The templates: " + rsp);

        for (AwsTemplate t : rsp.getTemplates()) {
            if (t.getSpotPrice() != null && t.getSpotPrice() > 0f) {
                t.setMarketSpotPrice(AWSClient.doCurrentSpotPrice(t));
            }
            if (!StringUtils.isNullOrEmpty(t.getUserData())) {
                try {
                    userDataStr = t.getUserData().replaceAll(";", "\n");
                    properties.load(new StringReader(userDataStr));
                    AwsUserData uD = new AwsUserData(properties);
                    t.setUserDataObj(uD);
                } catch (Exception e) {
                    log.error("Error " + e);
                }

            }
            //t.hide();
        }

        rsp.setStatus(AwsConst.EBROKERD_STATE_COMPLETE);
        rsp.setRsp(0, "Get available templates success.");
        return rsp;
    }

    /**
     * (Non Javadoc)
     * <p>
     * Title: getAvailableMachines
     * </p>
     * <p>
     * Description:
     * </p>
     *
     * @param req
     * @return
     * @see com.ibm.spectrum.aws.IAws#getAvailableMachines(com.ibm.spectrum.model.AwsEntity)
     */
    @Override
    public AwsEntity getAvailableMachines(AwsEntity req) {
        AwsEntity rsp = new AwsEntity();
        rsp.setRsp(
            0,
            "This interface is not supported by "
            + AwsUtil.getProviderName() + " provider.");
        rsp.setMachines(new ArrayList<AwsMachine>());
        return rsp;
    }

    /**
     * (Non Javadoc)
     * <p>
     * Title: getReturnRequests
     * </p>
     * <p>
     * Description:
     * </p>
     *
     * @param req
     * @return
     * @see com.ibm.spectrum.aws.IAws#getReturnRequests(com.ibm.spectrum.model.AwsEntity)
     */
    @Override
    public AwsEntity getReturnRequests(AwsEntity req) {
        AwsEntity rsp = new AwsEntity();
        List<AwsRequest> awsRequestList = new ArrayList<AwsRequest>();
        if (log.isDebugEnabled()) {
            log.debug("Start in class AwsImpl in method getReturnRequests with parameters: req: " + req);
        }

        List<AwsMachine> machinesToCheck = req.getMachines();
        Map<String, AwsMachine> machinesToCheckMap = new HashMap<String, AwsMachine>();
        Map<String, AwsMachine> machinesCheckedMap = new HashMap<String, AwsMachine>();
        List<AwsMachine> terminatingHostList = new ArrayList<AwsMachine>();
        if (!CollectionUtils.isNullOrEmpty(machinesToCheck)) {
            for (AwsMachine m : machinesToCheck) {
                machinesToCheckMap.put(m.getMachineId(), m);
            }
        }

        Date currentDateTime = new Date();
        long currentDateSecond = currentDateTime.getTime() / 1000;
        long instanceCreatinTimeoutSeconds =  AwsConst.INSTANCE_CREATION_TIMEOUT_MINUTES*60;

        if (AwsUtil.getConfig().getInstanceCreationTimeout() != null
                && AwsUtil.getConfig().getInstanceCreationTimeout().intValue() > 0) {
            instanceCreatinTimeoutSeconds = AwsUtil.getConfig().getInstanceCreationTimeout().intValue() * 60;
        }

        // load information from DB and AWS
        AwsEntity provisionStatusDB = AwsUtil.getFromFile();
        Map<String, Instance> instances = AWSClient.listVM(null, null);
        List<String> toBeDeletedInstIds = new ArrayList<String>();

        List<AwsRequest> requestsToBeChecked = new ArrayList<AwsRequest>();
        if (provisionStatusDB != null && !CollectionUtils.isNullOrEmpty(provisionStatusDB.getReqs())) {
            List<AwsRequest> reqList = provisionStatusDB.getReqs();
            for (Iterator<AwsRequest> iterReq = reqList.listIterator(); iterReq.hasNext();) {
                AwsRequest requestInDB = iterReq.next();

                // Check Spot instances
                if ((!StringUtils.isNullOrEmpty(requestInDB.getFleetType()) 
                		|| HostAllocationType.Spot.toString().equals(requestInDB.getHostAllocationType()))
                        && !CollectionUtils.isNullOrEmpty(requestInDB.getMachines())) {
                    requestsToBeChecked.add(requestInDB);
                }

                // Sync host status, remove terminated hosts if it is not in the
                // caller list
                List<AwsMachine> mList = requestInDB.getMachines();
                if (!CollectionUtils.isNullOrEmpty(mList)) {
                    for (Iterator<AwsMachine> iterMachine = mList.listIterator(); iterMachine.hasNext();) {
                        AwsMachine m = iterMachine.next();
                        boolean remove = false;

                        if (instances != null) {
                            Instance inst = instances.get(m.getMachineId());

                            if (inst != null) {
                                String state = inst.getState().getName();
                                if (!state.equals(m.getStatus())) {
                                    if ((AwsConst.markedForTerminationStates.contains(m.getStatus()) ||
                                        "pending".equalsIgnoreCase(m.getStatus())) &&
                                            state.equalsIgnoreCase("running")) {
                                        // Do not update the instance status
                                        // 1. if it is still in running but marked for termination
                                        // 2. if it first change from pending to running. getRequestStatus will 
                                        // update its status, so that it can apply post behavior - tag the instance
                                        log.info("Host <" + m.getMachineId() + "> last spot request status is <" + m.getStatus()
                                                 + ">, now it is in <" + state + ">");
                                    } else {
                                        log.debug("Host <" + m.getMachineId() + "> status changes from <" + m.getStatus()
                                                  + "> to <" + state + ">");
                                        m.setStatus(state);
                                    }
                                }


                                // The machine is not in ebrokerd hosts.json
                                // and in creating status for long than 10 minutes
                                if (machinesToCheckMap.get(m.getMachineId()) == null
                                        && !(terminatedState.equals(inst.getState().getName()))
                                        && m.getRetId() == null
                                        && inst.getLaunchTime() != null
                                        && inst.getLaunchTime().getTime() > 0
                                        && currentDateSecond - (inst.getLaunchTime().getTime())/1000 > instanceCreatinTimeoutSeconds) {
                                    log.warn("Host <" + m.getMachineId() + "> is not added into hosts.json for more than " + instanceCreatinTimeoutSeconds/60 + " minutes. Deleting it on AWS...");
                                    toBeDeletedInstIds.add(m.getMachineId());
                                }
                            } else {
                                // the host is not a valid instance anymore,
                                if (machinesToCheckMap.get(m.getMachineId()) != null) {
                                    m.setStatus("DELETED");
                                    machinesCheckedMap.put(m.getMachineId(), m);
                                    terminatingHostList.add(m);
                                    log.debug("Host <" + m.getMachineId() + "> is already deleted and not found on AWS.");
                                } else {
                                    // remove it from DB
                                    log.debug("Host <" + m.getMachineId()
                                              + "> status is <unknown>, remove it from DB. The last status is <"
                                              + m.getStatus() + ">");
                                    remove = true;
                                }
                            }
                        }

                        // reported terminated hosts to the caller, or remove it
                        // from DB
                        if (terminatedState.equals(m.getStatus())) {
                            if (machinesToCheckMap.get(m.getMachineId()) != null) {
                                machinesCheckedMap.put(m.getMachineId(), m);
                                terminatingHostList.add(m);
                                log.debug("Host <" + m.getMachineId() + "> is terminating.");
                            } else {
                                remove = true;
                            }
                        }

                        if (remove) {
                            log.debug("Host <" + m.getMachineId() + "> is terminated. Remove it from the DB.");
                            iterMachine.remove();
                        }
                    }
                }

                // remove empty instance on-demand request
                if (CollectionUtils.isNullOrEmpty(mList)) {
                	if (!StringUtils.isNullOrEmpty(requestInDB.getFleetType())) {
                		if (FleetType.Request.toString().equalsIgnoreCase(requestInDB.getFleetType())) {
                			List<AwsMachine> newlyCreatedMachines = AWSClient.updateEC2FleetStatus(requestInDB, null);
                			if ((requestInDB.getStatus().equals(AwsConst.EBROKERD_STATE_COMPLETE)
                					|| requestInDB.getStatus().equals(AwsConst.EBROKERD_STATE_COMPLETE_WITH_ERROR))
                					&& CollectionUtils.isNullOrEmpty(newlyCreatedMachines)) {
                				log.debug("EC2 Fleet Request type request <" + requestInDB.getReqId() + "> is empty. Remove it from the DB.");
                				AWSClient.deleteEC2FleetTemplateForAwsRequest(requestInDB);
                				iterReq.remove();
                				requestsToBeChecked.remove(requestInDB);
                			}
                		} else if (FleetType.Instant.toString().equalsIgnoreCase(requestInDB.getFleetType())) {
                			log.debug("EC2 Fleet Instant type request <" + requestInDB.getReqId() + "> is empty. Remove it from the DB.");
                			AWSClient.deleteEC2FleetTemplateForAwsRequest(requestInDB);
                			iterReq.remove();
                		}
                	} else {
                		if (HostAllocationType.Spot.toString().equals(requestInDB.getHostAllocationType())) {
                			List<AwsMachine> newlyCreatedMachines = AWSClient.updateSpotFleetStatus(requestInDB);
                			if ((requestInDB.getStatus().equals(AwsConst.EBROKERD_STATE_COMPLETE)
                					|| requestInDB.getStatus().equals(AwsConst.EBROKERD_STATE_COMPLETE_WITH_ERROR))
                					&& CollectionUtils.isNullOrEmpty(newlyCreatedMachines)) {
                				log.debug("Spot request <" + requestInDB.getReqId() + "> is empty. Remove it from the DB.");
                				AWSClient.deleteSpotFleetTemplateForAwsRequest(requestInDB);
                				iterReq.remove();
                				requestsToBeChecked.remove(requestInDB);
                			}
                		} else {
                			log.debug(
                					"On-demand request <" + requestInDB.getReqId() + "> is empty. Remove it from the DB.");
                			iterReq.remove();
                		}
                	}
                }
            }

            // Delete the creation timeout VMs
            if (!CollectionUtils.isNullOrEmpty(toBeDeletedInstIds)) {
                AWSClient.deleteVM(toBeDeletedInstIds, null);
            }

            awsRequestList = AWSClient.retrieveInstancesMarkedForTermination(requestsToBeChecked);

            // check if any hosts missed in DB
            if (!CollectionUtils.isNullOrEmpty(machinesToCheck)) {
                for (AwsMachine m : machinesToCheck) {
                    if (machinesCheckedMap.get(m.getMachineId()) == null) {
                        if (instances != null) {
                            Instance inst = instances.get(m.getMachineId());
                            if (inst == null) {
                                log.debug("Host <" + m.getMachineId() + "> does not exist in DB and AWS.");
                                // assume the host is terminated
                                terminatingHostList.add(m);
                            } else {
                                String state = inst.getState().getName();
                                if (alreadyTerminatedStates.contains(state)) {
                                    log.debug("Host <" + m.getMachineId() + "> does not exist in DB."
                                              + "AWS reports the host is in <" + state + ">.");
                                    terminatingHostList.add(m);
                                }
                            }
                        }
                    }
                }
            }

            for (AwsMachine m : terminatingHostList) {
                AwsRequest awsRequest = new AwsRequest();
                awsRequest.setVmName(m.getName());
                awsRequest.setMachineId(m.getMachineId());
                awsRequestList.add(awsRequest);
            }
            if(log.isTraceEnabled()) {
                log.trace("Saving to file: " + provisionStatusDB);
            }
            AwsUtil.saveToFile(provisionStatusDB);
        }

        rsp.setStatus(AwsConst.EBROKERD_STATE_COMPLETE);
        rsp.setReqs(awsRequestList);
        rsp.setRsp(0, "Instances marked for termination retrieved successfully");

        return rsp;
    }

    /**
     * (Non Javadoc)
     * <p>
     * Title: requestMachines
     * </p>
     * <p>
     * Description:
     * </p>
     *
     * @param req
     * @return
     * @see com.ibm.spectrum.aws.IAws#requestMachines(com.ibm.spectrum.model.AwsEntity)
     */
    @Override
    public AwsEntity requestMachines(AwsEntity req) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class AwsImpl in method requestMachines with parameters: req: "
                      + req);
        }
        boolean onDemandRequest = true;
        AwsEntity rsp = new AwsEntity();
        String instanceTagVal = "";
        boolean fleetRequest = false;

        List<AwsMachine> machines = req.getMachines();
        if (!CollectionUtils.isNullOrEmpty(machines)) {
            rsp.setStatus(AwsConst.EBROKERD_STATE_WARNING);
            rsp.setRsp(1, "Input parameter machines is not supported.");
            return rsp;
        }

        AwsTemplate t = req.getTemplate();
        if (null == t || StringUtils.isNullOrEmpty(t.getTemplateId())) {
            rsp.setStatus(AwsConst.EBROKERD_STATE_WARNING);
            rsp.setRsp(1, "Invalid template.");
            return rsp;
        }

        AwsTemplate at = AwsUtil.getTemplateFromFile(t.getTemplateId());
        if (null == at) {
            rsp.setStatus(AwsConst.EBROKERD_STATE_WARNING);
            rsp.setRsp(1, "The template does not exist.");
            return rsp;
        }

        Integer vmNum = t.getVmNumber();
        if (null == vmNum || vmNum < 1) {
            rsp.setStatus(AwsConst.EBROKERD_STATE_WARNING);
            rsp.setRsp(1, "Invalid instances number.");
            return rsp;
        }

        at.setVmNumber(vmNum);
        AwsRequest rq = new AwsRequest();
        instanceTagVal = req.getTagValue();
        rq.setTagValue(instanceTagVal);
        String hostAllocationType = HostAllocationType.OnDemand.toString();
        String fleetType = null;
        
        //If ec2FleetConfig defined, then go to EC2 fleet API
        if (!StringUtils.isNullOrEmpty(at.getEc2FleetConfig())) {
        	boolean validRequest = AwsUtil.validateEC2FleetRequest(at, rsp);
        	if (!validRequest) {
        		rsp.setStatus(AwsConst.EBROKERD_STATE_WARNING);
        		rsp.setRsp(1, "EC2_FLEET_CONFIG_ERROR: " + rsp.getMsg());
        		return rsp;
        	}
        	fleetType = at.getFleetType();
        	fleetRequest = true;
        }
        
        //If the spotPrice is defined then this is Spot Pricing request
        if (at.getSpotPrice() != null && at.getSpotPrice() > 0f) {
            onDemandRequest = false;
            hostAllocationType = HostAllocationType.Spot.toString();
        }       		

        String reqId = null;
        List<AwsMachine> mLst = new ArrayList<AwsMachine>();

        // Request type is EC2 fleet
        if (fleetRequest) {
        	CreateFleetResult fleetResult = AWSClient.createVMByEC2Fleet(at, instanceTagVal, rsp); 
        	if (fleetResult == null) {
        		return rsp;
        	}
        	reqId = fleetResult.getFleetId();
        	if (FleetType.Instant.toString().equalsIgnoreCase(at.getFleetType())) {
        		List <CreateFleetInstance> fleetInstancesList = fleetResult.getInstances();
        		for (CreateFleetInstance fleetInstance: fleetInstancesList) {
        			for (String id: fleetInstance.getInstanceIds()) {
        				AwsMachine m = new AwsMachine();
        				m.setMachineId(id);
        				m.setReqId(reqId);
        				mLst.add(m);
        			}
        		}
        	}

        } else { // Not EC2 fleet request, keep old behavior
        	if (onDemandRequest) {
        		Reservation rsv = null;

        		rsv = AWSClient.createVM(at, instanceTagVal, rsp);

        		if (null == rsv || CollectionUtils.isNullOrEmpty(rsv.getInstances())) {
        			return rsp;
        		}

        		reqId = rsv.getReservationId();
        		for (Instance vm : rsv.getInstances()) {
        			AwsMachine m = AwsUtil.mapAwsInstanceToAwsMachine(
        					at.getTemplateId(), reqId, vm, instanceTagVal);
        			mLst.add(m);
        		}

        	} else {// If Host allocation is spot

        		RequestSpotFleetResult requestSpotFleetResult = AWSClient
        				.requestSpotInstance(at, instanceTagVal, rsp);
        		if (null == requestSpotFleetResult) {
        			rsp.setRsp(1,
        					"Request Spot Instance on " + AwsUtil.getProviderName()
        					+ " EC2 failed.");
        			return rsp;
        		}
        		reqId = requestSpotFleetResult.getSpotFleetRequestId();
        	}
        }

        rq.setMachines(mLst);
        rq.setReqId(reqId);
        rq.setTime(System.currentTimeMillis());
        rq.setTtl(at.getTtl());
        rq.setTagValue(instanceTagVal);
        rq.setHostAllocationType(hostAllocationType);
        rq.setFleetType(fleetType);
        rq.setTemplateId(t.getTemplateId());

        AwsUtil.saveToFile(rq);

        rsp.setStatus(AwsConst.EBROKERD_STATE_RUNNING);
        rsp.setRsp(0, "Request instances success from " + AwsUtil.getProviderName() + ".");
        rsp.setReqId(reqId);
        if (log.isTraceEnabled()) {
            log.trace("End in class AwsImpl in method requestMachines with return: AwsEntity: "
                      + rsp);
        }
        return rsp;

    }

    /**
     * (Non Javadoc)
     * <p>
     * Title: requestReturnMachines
     * </p>
     * <p>
     * Description:
     * </p>
     *
     * @param req
     * @return
     * @see com.ibm.spectrum.aws.IAws#requestReturnMachines(com.ibm.spectrum.model.AwsEntity)
     */
    @Override
    public AwsEntity requestReturnMachines(AwsEntity req) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class AwsImpl in method requestReturnMachines with parameters: req" + req);
        }
        AwsEntity rsp = new AwsEntity();

        List<AwsMachine> machinesToBeReturned = req.getMachines();
        List<InstanceStateChange> scLst = new ArrayList<InstanceStateChange>();
        if (CollectionUtils.isNullOrEmpty(machinesToBeReturned)) {
            rsp.setStatus(AwsConst.EBROKERD_STATE_WARNING);
            rsp.setRsp(1, "Invalid input file.");
            return rsp;
        }

        AwsEntity awsEntity = AwsUtil.getFromFile();

        Map<String, AwsMachine> machinesMap = new HashMap<String, AwsMachine>();
        Map<String, AwsMachine> terminatedMachinesMap = new HashMap<String, AwsMachine>();
        Map<String, AwsMachine> updatedMachinesMarkedForTerminationMap = new HashMap<String, AwsMachine>();
        Map<String, AwsRequest> requestsMap = new HashMap<String, AwsRequest>();
        String retId = AwsConst.RETURN_REQUEST_PREFIX + UUID.randomUUID().toString();
        if (awsEntity == null || CollectionUtils.isNullOrEmpty(awsEntity.getReqs())) {
            log.debug("No requests in local DB.");
        } else {
            for (AwsRequest awsRequest : awsEntity.getReqs()) {
                if (!CollectionUtils.isNullOrEmpty(awsRequest.getMachines())) {
                    for (AwsMachine awsMachine : awsRequest.getMachines()) {
                        if (AwsUtil.getMatchingMachineInList(awsMachine, machinesToBeReturned) != null) {
                            // Ignore machines that are already being terminated
                            // or have been terminated
                            if (!terminationStates.contains(awsMachine.getStatus())) {
                                // If the machine is an exact match to one of the
                                // machines in the list of machines requested to be returned
                                log.debug("Processing request to terminate machine:  " + awsMachine);
                                // Do not request a termination and let AWS perform
                                // the termination from its side if the following
                                // conditions are met:
                                // 1. The AWS plugin is configured to not terminate on reclaim
                                // 2. If the machine is marked for termination by AWS
                                if (AwsConst.markedForTerminationStates.contains(awsMachine.getStatus())
                                        && !AwsUtil.getConfig().isSpotTerminateOnReclaim()) {
                                    // If machine return ID is not set, set the
                                    // machine return ID to be added to the response
                                    if (StringUtils.isNullOrEmpty(awsMachine.getRetId())) {
                                        awsMachine.setRetId(retId);
                                        updatedMachinesMarkedForTerminationMap.put(awsMachine.getMachineId(),
                                                awsMachine);
                                        log.debug("[Instance - " + awsRequest.getReqId() + " - " + awsMachine.getReqId()
                                                  + " - " + awsMachine.getMachineId()
                                                  + "] Machine pending reclaim from AWS side");
                                    } else {// If machine return ID is already
                                        // set, log an error that this
                                        // machine has already been
                                        // requested to be returned and is
                                        // pending reclaim by AWS
                                        log.error("[Instance - " + awsRequest.getReqId() + " - " + awsMachine.getReqId()
                                                  + " - " + awsMachine.getMachineId()
                                                  + "] Machine already requested to be returned and is pending reclaim from AWS. Ignoring the request to return this machine.");
                                    }
                                } else {
                                    machinesMap.put(awsMachine.getMachineId(), awsMachine);
                                    requestsMap.put(awsMachine.getMachineId(), awsRequest);
                                }
                            } else {
                                if (StringUtils.isNullOrEmpty(awsMachine.getRetId())) {
                                    awsMachine.setRetId(retId);
                                    terminatedMachinesMap.put(awsMachine.getMachineId(), awsMachine);
                                }
                            }
                            // Removing from the list of machinesToBeReturned
                            // the current machine after being considered for
                            // return.
                            machinesToBeReturned.remove(awsMachine);
                        }
                    }
                }
            }
        }

        // If the machines to be returned list still has values, these machines
        // were not found in the AWS plugin local DB or the machine. The
        // machines will be returned.
        if (!CollectionUtils.isNullOrEmpty(machinesToBeReturned)) {
            log.error("The following machine(s) were not found in AWS plugin local DB and will be ignored: "
                      + machinesToBeReturned);
            List<String> vmIdLst = new ArrayList<String>();

            // try to delete, but will not track them
            for (AwsMachine m : machinesToBeReturned) {
                if (m.getMachineId() != null) {
                    vmIdLst.add(m.getMachineId());
                }
            }

            if (!CollectionUtils.isNullOrEmpty(vmIdLst)) {
                AWSClient.deleteVM(vmIdLst, null);
            }
        }

        if (!machinesMap.isEmpty()) {
            List<String> vmIdLst = new ArrayList<String>(machinesMap.keySet());
            log.debug("Deleting the following instances: " + vmIdLst);
            for (String vmID : vmIdLst) {
                AwsMachine awsMachine = machinesMap.get(vmID);
                log.debug("[Instance - " + requestsMap.get(vmID).getReqId() + " - " + awsMachine.getReqId() + " - "
                          + awsMachine.getMachineId() + "] Request to terminate machine.");
            }
            scLst = AWSClient.deleteVM(vmIdLst, rsp);
            if (CollectionUtils.isNullOrEmpty(scLst)) {
                return rsp;
            }

            for (InstanceStateChange sc : scLst) {
                AwsMachine m = machinesMap.get(sc.getInstanceId());
                AwsRequest matchingRequest = requestsMap.get(sc.getInstanceId());
                if (m != null) {
                    m.setRetId(retId);
                    m.setStatus(sc.getCurrentState().getName());
                    m.setResult(sc.getCurrentState().getName());
                    if (AwsConst.markedForTerminationStates.contains(sc.getCurrentState().getName())) {
                        log.warn("[Instance - " + matchingRequest.getReqId() + " - " + m.getReqId() + " - "
                                 + m.getMachineId() + "] Machine pending reclaim from AWS side");
                    } else {
                        log.debug("[Instance - " + matchingRequest.getReqId() + " - " + m.getReqId() + " - "
                                  + m.getMachineId() + "] Machine Terminated: " + sc.getCurrentState());
                    }
                }
            }
        }
        machinesMap.putAll(updatedMachinesMarkedForTerminationMap);
        if (machinesMap.size() > 0) {
            // if any machines are terminating
            machinesMap.putAll(terminatedMachinesMap);
            AwsUtil.updateToFile(machinesMap);

            rsp.setStatus(AwsConst.EBROKERD_STATE_RUNNING);
            rsp.setRsp(0, "Delete instances success.");
            rsp.setReqId(retId);
        } else {
            machinesMap.putAll(terminatedMachinesMap);
            if (machinesMap.size() > 0) {
                AwsUtil.updateToFile(machinesMap);
            }
            rsp.setStatus(AwsConst.EBROKERD_STATE_COMPLETE);
            rsp.setRsp(0, "No Active instances.");
        }

        if (log.isTraceEnabled()) {
            log.trace("End in class AwsImpl in method requestReturnMachines with return: AwsEntity: " + rsp);
        }
        return rsp;
    }

    /**
     * (Non Javadoc)
     * <p>
     * Title: getRequestStatus
     * </p>
     * <p>
     * Description:
     * </p>
     *
     * @param req
     * @return
     * @see com.ibm.spectrum.aws.IAws#getRequestStatus(com.ibm.spectrum.model.AwsEntity)
     */
    @Override
    public AwsEntity getRequestStatus(AwsEntity req) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class AwsImpl in method getRequestStatus with parameters: req: "
                      + req);
        }
        AwsEntity rsp = new AwsEntity();
        List<AwsRequest> reqLst = req.getReqs();

        if (CollectionUtils.isNullOrEmpty(reqLst)) {
            rsp.setStatus(AwsConst.EBROKERD_STATE_WARNING);
            rsp.setRsp(1, "Invalid input file.");
            return rsp;
        }

        for (AwsRequest inReq : reqLst) {
            AwsRequest fReq = AwsUtil.getFromFile(inReq.getReqId());
            if (null == fReq || StringUtils.isNullOrEmpty(fReq.getReqId())) {
                // mark the request completed as no request in DB
                inReq.setStatus(AwsConst.EBROKERD_STATE_COMPLETE_WITH_ERROR);
                continue;
            }

            // Update status
            updateStatus(fReq, inReq, rsp);
            if (rsp.getStatus() != null && rsp.getStatus().equals(AwsConst.EBROKERD_STATE_ERROR)) {
                return rsp;
            }

            // Change to returning format
            List<AwsMachine> mLst = inReq.getMachines();
            for (AwsMachine m : mLst) {
                m.hide();
            }
        }

        rsp.setStatus(AwsConst.EBROKERD_STATE_COMPLETE);
        if (rsp.getMsg() == null) {
        	rsp.setRsp(0, "");
        }
        rsp.setReqs(reqLst);
        if (log.isTraceEnabled()) {
            log.trace("End in class AwsImpl in method getRequestStatus with return: AwsEntity: "
                      + rsp);
        }
        return rsp;
    }

    /**
     *
     * @Title: updateStatus
     * @Description: Performs the following:<br>
     * 1. Retrieve the latest status from  AWS of the machines attached to the request <br>
     * 2. Map the status retrieved from AWS to the status used by Ebrokerd <br>
     * 3.Update the status file(AwsUtil.provStatusFile) with the new status
     * @param fReq The current machines request information available in
     *        the system
     * @param inReq The request object sent to the service that needs to
     *        be updated
     * @return void
     * @throws
     */
    public static void updateStatus(AwsRequest fReq, AwsRequest inReq, AwsEntity rsp) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class AwsImpl in method updateStatus with parameters: fReq: "
                      + fReq + ", inReq: " + inReq);
        }
        boolean statusUpdateForReturnMachine = (inReq.getReqId().startsWith(AwsConst.RETURN_REQUEST_PREFIX));
        boolean statusUpdateForCreateMachine = !statusUpdateForReturnMachine;

        Map<String, Instance> vmMap = null;
        AwsTemplate usedTemplate = AwsUtil.getTemplateFromFile(fReq.getTemplateId());
        List<String> vmIdLst = new ArrayList<String>();
        List<AwsMachine> newlyCreatedMachines = new ArrayList<AwsMachine>();
        List<Instance> postCreationInstList = new ArrayList<Instance>();

        inReq.setReqId(fReq.getReqId());
        String latestRequestStatus = AwsConst.EBROKERD_STATE_COMPLETE;
        // If this is a Spot Fleet Request and the request update is for a create request, call the Spot Fleet APIs to update the status
        //Request updates for machine termination does not need a spot fleet status update.
        if (statusUpdateForCreateMachine) {
        	if (!StringUtils.isNullOrEmpty(fReq.getFleetType())) {
        		if (FleetType.Request.toString().equalsIgnoreCase(fReq.getFleetType())) {
            		newlyCreatedMachines = AWSClient.updateEC2FleetStatus(fReq, rsp);
            		latestRequestStatus = fReq.getStatus();
            		log.debug("Setting the EC2 Fleet request status: " + latestRequestStatus);
            		log.debug("newlyCreatedMachines: " + newlyCreatedMachines);
        		}
        	} else if (HostAllocationType.Spot.toString().equals(fReq.getHostAllocationType())) {
        		// Check the Spot Fleet Request Status
        		newlyCreatedMachines = AWSClient.updateSpotFleetStatus(fReq);
        		latestRequestStatus = fReq.getStatus();
        		log.debug("Setting the Spot Fleet request status: " + latestRequestStatus);
        		log.debug("newlyCreatedMachines: " + newlyCreatedMachines);
        	}
        }
        List<AwsMachine> machinesListInDB = fReq.getMachines();
        for (AwsMachine tempMachineInDB : machinesListInDB) {
            vmIdLst.add(tempMachineInDB.getMachineId());
        }

        try {
            vmMap = AWSClient.listVM(vmIdLst, rsp);
        } catch (Exception e) {
            log.error(e);
            inReq.setMachines(new ArrayList<AwsMachine>());
            inReq.setStatus(AwsConst.EBROKERD_STATE_COMPLETE_WITH_ERROR);
            inReq.setMsg(e.getMessage());
            return;
        }

        if (vmMap == null) {
            return;
        }

        String latestMachineStatus = AwsConst.EBROKERD_MACHINE_RESULT_FAIL;
        for (AwsMachine tempMachineInDB : machinesListInDB) {
            log.debug("Updating the state of the machine: " + tempMachineInDB);
            String tempMachineOldStatus = tempMachineInDB.getStatus();
            Instance correspondingInstanceForTempMachineInDB = vmMap.get(tempMachineInDB.getMachineId());
            //If the machine is not in AWS, set the result depending on the request type
            if (null == correspondingInstanceForTempMachineInDB) {
                // If this is a status update for creating a machine and the
                // machine is not found in AWS, consider this as a failure
                if(statusUpdateForCreateMachine) {
                    tempMachineInDB.setResult(AwsConst.EBROKERD_MACHINE_RESULT_FAIL);
                } else {// If this is a status update for terminating a machine
                    // and the machine is not found in AWS, consider this as
                    // a success
                    tempMachineInDB.setResult(AwsConst.EBROKERD_MACHINE_RESULT_SUCCEED);
                }
                log.debug("[Instance is null for tempMachineInDB " + tempMachineInDB);
                continue;
            }

            latestMachineStatus = correspondingInstanceForTempMachineInDB.getState().getName();

            //If the machine is marked for termination and the machine is not getting terminated yet, skip until the termination process starts
            if(AwsConst.markedForTerminationStates.contains(tempMachineOldStatus) &&
                    !terminationStates.contains(latestMachineStatus)) {
                log.warn("[Instance - " + fReq.getReqId() + " - " + tempMachineInDB.getReqId() + " - " + tempMachineInDB.getMachineId()
                         +"] is interrupted. Machine last Status : " + tempMachineInDB.getStatus() + " , latestMachineStatus in AWS State: " + latestMachineStatus );

                if (statusUpdateForCreateMachine) {
                    // When we requesting spot instances, and the spot requesting is still running, but this instance is interrupted to terminate
                    tempMachineInDB.setResult(AwsConst.EBROKERD_MACHINE_RESULT_FAIL);
                    log.warn("[Instance - " + fReq.getReqId() + " - " + tempMachineInDB.getReqId() + " - " + tempMachineInDB.getMachineId()
                             +"] is interrupted. Setting the instance restult to " + AwsConst.EBROKERD_MACHINE_RESULT_FAIL);
                } else {
                    latestRequestStatus = AwsConst.EBROKERD_STATE_RUNNING;
                }
                continue;
            }
            //If this is a machine newly created during the status check map the
            // parameters retrieved from AWS
            if(newlyCreatedMachines.contains(tempMachineInDB)
                    // Defect#197080, host name and priviate ip may be null when first get the instance.
                    || ( ((!StringUtils.isNullOrEmpty(fReq.getFleetType()) ||
                    		HostAllocationType.Spot.toString().equals(fReq.getHostAllocationType())) && statusUpdateForCreateMachine)
                         && (StringUtils.isNullOrEmpty(tempMachineInDB.getName()) || StringUtils.isNullOrEmpty(tempMachineInDB.getPrivateIpAddress())) )
              ) {
                tempMachineInDB = AwsUtil.mapAwsInstanceToAwsMachine(usedTemplate.getTemplateId(), fReq.getReqId(),correspondingInstanceForTempMachineInDB, usedTemplate.getInstanceTags(), tempMachineInDB);
                log.debug("[Instance - " + fReq.getReqId() + "] Machine Created: " + tempMachineInDB);
            }
            /*
             * Status from AWS: pending, running, shutting-down, terminated,
             * stopping, stopped Status from LSF: executing, fail, succeed
             */
            if ("pending".equals(latestMachineStatus) || "shutting-down".equals(latestMachineStatus)
                    || "stopping".equals(latestMachineStatus)) {
                tempMachineInDB.setResult("executing");
                latestRequestStatus = AwsConst.EBROKERD_STATE_RUNNING;
            } else if ("running".equals(latestMachineStatus) || "terminated".equals(latestMachineStatus)
                       || "stopped".equals(latestMachineStatus)) {
                //Setting the machine's result depending on if the request update is for a machine creation or termination
                //If this is an update request for a machine creation request
                String machineResult = "";
                if(statusUpdateForCreateMachine) {
                    if(latestMachineStatus.equals("running")) {
                        machineResult = (AwsConst.EBROKERD_MACHINE_RESULT_SUCCEED);
                    } else {
                        machineResult = (AwsConst.EBROKERD_MACHINE_RESULT_FAIL);;
                    }
                } else {
                    if(latestMachineStatus.equals("running")) {
                        machineResult = (AwsConst.EBROKERD_MACHINE_RESULT_FAIL);
                    } else {
                        machineResult = (AwsConst.EBROKERD_MACHINE_RESULT_SUCCEED);
                    }
                }
                tempMachineInDB.setResult(machineResult);
            } else {
                tempMachineInDB.setResult(AwsConst.EBROKERD_MACHINE_RESULT_FAIL);
                latestRequestStatus = AwsConst.EBROKERD_STATE_COMPLETE_WITH_ERROR;
            }

            log.debug("Machine old status: " + tempMachineOldStatus);
            log.debug("Machine new status: " + latestMachineStatus);

            //If the machine's status changed to running, apply the post creation behavior
            if("running".equalsIgnoreCase(latestMachineStatus) && !latestMachineStatus.equalsIgnoreCase(tempMachineOldStatus)) {
                log.debug("[Instance - " + inReq.getReqId() + " - " + tempMachineInDB.getReqId() + " - " + tempMachineInDB.getMachineId()
                           +"] Machine is successfully initiated. Ready for post creation behavior..");
                postCreationInstList.add(correspondingInstanceForTempMachineInDB);
            }
            tempMachineInDB.setStatus(latestMachineStatus);
            tempMachineInDB.setPublicIpAddress(correspondingInstanceForTempMachineInDB.getPublicIpAddress());
            if (correspondingInstanceForTempMachineInDB.getPublicDnsName() != null) {
                tempMachineInDB.setPublicDnsName(correspondingInstanceForTempMachineInDB.getPublicDnsName());
            }

            tempMachineInDB.setMsg("");
            log.debug("[Instance - " + fReq.getReqId() + " - " + tempMachineInDB.getReqId() + " - " + tempMachineInDB.getMachineId() +"] Machine Status in AWS: " + tempMachineInDB.getStatus());
            log.debug("[Instance - " + fReq.getReqId() + " - " + tempMachineInDB.getReqId() + " - " + tempMachineInDB.getMachineId() +"] Machine Result in Ebrokerd: " + tempMachineInDB.getResult());

        }

        if (! CollectionUtils.isNullOrEmpty(postCreationInstList)) {
            AWSClient.applyPostCreationBehaviorForInstanceList(fReq, postCreationInstList, usedTemplate);
            log.debug("[ Running the post creation flow for instances:" + postCreationInstList.toString());
        }
        // 'running','complete','complete_with_error'
        log.debug("Setting the machine list to the response: " + machinesListInDB);
        inReq.setMachines(machinesListInDB);
        inReq.setStatus(latestRequestStatus);
        inReq.setMsg("");
        log.debug("[Instance - " + fReq.getReqId() + "] Request Status in AWS: " + latestRequestStatus);

        // update VM record
        updateVmStatus(fReq);
        if (log.isTraceEnabled()) {
            log.trace("End in class AwsImpl in method updateStatus with return: void: ");
        }
    }

    /**
     *
     *
     * @Title: updateVmStatus
     * @Description: Sets the new status to the status file
     *               (AwsUtil.provStatusFile)
     * @param requestWithNewValues
     * @return void
     * @throws
     */
    public static void updateVmStatus(AwsRequest requestWithNewValues) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class AwsImpl in method updateVmStatus with parameters: requestWithNewValues: "
                      + requestWithNewValues);
        }

        boolean statusUpdateForReturnMachine = (requestWithNewValues.getReqId().startsWith(AwsConst.RETURN_REQUEST_PREFIX));
        boolean statusUpdateForCreateMachine = !statusUpdateForReturnMachine;

        List<AwsMachine> updatedMachinesList = new ArrayList<AwsMachine>();

        if (null == requestWithNewValues
                || CollectionUtils.isNullOrEmpty(requestWithNewValues
                        .getMachines())) {
            log.debug("Request " + requestWithNewValues + " does not have any machines allocated yet.");
            return;
        }

        updatedMachinesList.addAll(requestWithNewValues.getMachines());
        Map<String, AwsMachine> machinesInDBMap = null;

        AwsEntity provisionStatusDB = AwsUtil.getFromFile();
        if (null == provisionStatusDB || provisionStatusDB.getReqs() == null) {
            log.error("Provision Status DB is missing or corrupted: "
                      + AwsUtil.getWorkDir() + "/" + AwsUtil.getProvStatusFile());
            return;
        }
        List<AwsRequest> requestListInDB = provisionStatusDB.getReqs();
        log.trace("The requests in DB before update: " + requestListInDB);
        for (AwsRequest requestInDB : requestListInDB) {

            if(statusUpdateForCreateMachine) {
                // If this is the request that needs to be updated, perform the
                // updates. Otherwise, skip to the next request
                if (requestInDB.getReqId().equals(requestWithNewValues.getReqId())) {
                    machinesInDBMap = new HashMap<String, AwsMachine>();
                    for (AwsMachine m : requestInDB.getMachines()) {
                        machinesInDBMap.put(m.getMachineId(), m);
                    }
                    for (AwsMachine machineWithNewValues : requestWithNewValues
                            .getMachines()) {
                        // If the machine already exists in the DB, update the
                        // attributes
                        if (machinesInDBMap
                                .get(machineWithNewValues.getMachineId()) != null) {
                            AwsMachine machineToBeUpdated = machinesInDBMap
                                                            .get(machineWithNewValues.getMachineId());
                            machineToBeUpdated.copyValues(machineWithNewValues);
                            log.debug("Value of machine in updateVm after updating attributes: "
                                      + machineToBeUpdated);
                        } else {
                            requestInDB.getMachines().add(machineWithNewValues);
                            log.debug("Value of machine in updateVm after adding new machine: "
                                      + machineWithNewValues);
                        }

                    }
                    break;
                }
            } else { //This is a request update for a return machine request
                for(AwsMachine machineInDB : requestInDB.getMachines()) {
                    //Ignore this machine if it is terminated
                    if(!alreadyTerminatedStates.contains(machineInDB.getStatus())) {
                        //Find matching non-terminated machine
                        AwsMachine matchingMachine = AwsUtil.getMatchingMachineInList(machineInDB,updatedMachinesList);
                        if(matchingMachine!= null) {
                            log.trace("Value of machine in updateVm before updating attributes: " + machineInDB);
                            machineInDB.copyValues(matchingMachine);
                            log.trace("Value of machine in updateVm after updating attributes: " + machineInDB);
                            //Remove the current machine from the update machine list since its values are copied to the DB object
                            updatedMachinesList.remove(matchingMachine);
                        }
                        if(updatedMachinesList.isEmpty()) {
                            log.trace("All machines have been save to the DB object");
                            break;
                        }

                    }

                }

            }
        }
        if(log.isTraceEnabled()) {
            log.trace("Saving to file: " + provisionStatusDB);
        }
        AwsUtil.saveToFile(provisionStatusDB);

        if (log.isTraceEnabled()) {
            log.trace("End in class AwsImpl in method updateVmStatus with return: void: ");
        }
    }
}
