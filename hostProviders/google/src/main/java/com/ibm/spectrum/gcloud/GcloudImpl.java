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


package com.ibm.spectrum.gcloud;

import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import com.ibm.spectrum.gcloud.client.GcloudClient;
import com.ibm.spectrum.constant.GcloudConst;
import com.ibm.spectrum.model.GcloudEntity;
import com.ibm.spectrum.model.GcloudMachine;
import com.ibm.spectrum.model.GcloudRequest;
import com.ibm.spectrum.model.GcloudTemplate;
import com.ibm.spectrum.model.GcloudUserData;
import com.ibm.spectrum.model.HostAllocationType;
import com.ibm.spectrum.util.GcloudUtil;
import com.google.api.services.compute.model.Instance;

/**
 * @ClassName: GcloudImpl
 * @Description: The implementation of Google Cloud host provider
 * @author zcg
 * @date Sep 11, 2017 3:46:14 PM
 * @version 1.0
 */
public class GcloudImpl implements IGcloud {
    private static Logger log = LogManager.getLogger(GcloudImpl.class);

    // Google Cloud Compute instance life cycle:
    // https://cloud.google.com/compute/docs/instances/instance-life-cycle
    private static List<String> terminationStates = Arrays
            .asList(new String[] { "STOPPING", "STOPPED", "TERMINATED" });
    private static List<String> alreadyTerminatedStates = Arrays.asList(new String[] { "STOPPED", "TERMINATED" });

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
     * @see com.ibm.spectrum.gcloud.IGcloud#getAvailableTemplates(com.ibm.spectrum.model.GcloudEntity)
     */
    @Override
    public GcloudEntity getAvailableTemplates(GcloudEntity req) {
        GcloudEntity rsp = new GcloudEntity();
        Properties properties = new Properties();
        String userDataStr = "";
        File jf = new File(
            GcloudUtil.getConfDir() + File.separator + "conf" + File.separator + GcloudConst.GOOGLEPROV_TEMPLATE_FILENAME);
        if (!jf.exists()) {
            rsp.setRsp(1, "Google provider tempalate " +  GcloudConst.GOOGLEPROV_TEMPLATE_FILENAME + " does not exist in the "
                       + GcloudUtil.getProviderName() + " provider configuration directory.");
            return rsp;
        }

        rsp = GcloudUtil.toObject(jf, GcloudEntity.class);
        if (null == rsp || CollectionUtils.isEmpty(rsp.getTemplates())) {
            rsp.setRsp(1, "Google provider tempalate file " + GcloudConst.GOOGLEPROV_TEMPLATE_FILENAME + " is not a valid JSON format file.");
            return rsp;
        }

        log.debug("The templates: " + rsp);

        for (GcloudTemplate t : rsp.getTemplates()) {
            if (!StringUtils.isEmpty(t.getUserData())) {
                try {
                    userDataStr = t.getUserData().replaceAll(";", "\n");
                    properties.load(new StringReader(userDataStr));
                    GcloudUserData uD = new GcloudUserData(properties);
                    t.setUserDataObj(uD);
                } catch (Exception e) {
                    log.error("Error " + e);
                }

            }
            //t.hide();
        }

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
     * @see com.ibm.spectrum.gcloud.IGcloud#getAvailableMachines(com.ibm.spectrum.model.GcloudEntity)
     */
    @Override
    public GcloudEntity getAvailableMachines(GcloudEntity req) {
        GcloudEntity rsp = new GcloudEntity();
        rsp.setRsp(0, "This interface is not supported by " + GcloudUtil.getProviderName() + " provider.");
        rsp.setMachines(new ArrayList<GcloudMachine>());
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
     * @see com.ibm.spectrum.gcloud.IGcloud#getReturnRequests(com.ibm.spectrum.model.GcloudEntity)
     */
    @Override
    public GcloudEntity getReturnRequests(GcloudEntity req) {
        GcloudEntity rsp = new GcloudEntity();
        List<GcloudRequest> gcloudRequestList = new ArrayList<GcloudRequest>();
        if (log.isDebugEnabled()) {
            log.debug("Start in class GcloudImpl in method getReturnRequests with parameters: req: " + req);
        }

        List<GcloudMachine> machinesToCheck = req.getMachines();
        Map<String, GcloudMachine> machinesToCheckMap = new HashMap<String, GcloudMachine>();
        Map<String, GcloudMachine> machinesCheckedMap = new HashMap<String, GcloudMachine>();
        List<GcloudMachine> terminatingHostList = new ArrayList<GcloudMachine>();
        if (!CollectionUtils.isEmpty(machinesToCheck)) {
            for (GcloudMachine m : machinesToCheck) {
                machinesToCheckMap.put(m.getMachineId(), m);
            }
        }

        Date currentDateTime = new Date();
        Long currentDateSecond = currentDateTime.getTime() / 1000;

        // load information from DB and Google cloud
        GcloudEntity provisionStatusDB = GcloudUtil.getFromFile();
        Map<String, Instance> instances = GcloudClient.getCloudVMMap();

        List<GcloudRequest> requestsToBeChecked = new ArrayList<GcloudRequest>();
        if (provisionStatusDB != null && !CollectionUtils.isEmpty(provisionStatusDB.getReqs())) {
            List<GcloudRequest> reqList = provisionStatusDB.getReqs();
            for (Iterator<GcloudRequest> iterReq = reqList.listIterator(); iterReq.hasNext();) {
                GcloudRequest requestInDB = iterReq.next();

                // Check Google preemptible instances
                if (GcloudUtil.isBulkRequest(requestInDB.getHostAllocationType())
                        && !CollectionUtils.isEmpty(requestInDB.getMachines())) {
                    requestsToBeChecked.add(requestInDB);
                }

                // Sync host status, remove terminated hosts if it is not in the
                // caller list
                List<GcloudMachine> mList = requestInDB.getMachines();
                if (!CollectionUtils.isEmpty(mList)) {
                    for (Iterator<GcloudMachine> iterMachine = mList.listIterator(); iterMachine.hasNext();) {
                        GcloudMachine m = iterMachine.next();
                        boolean remove = false;

                        if (instances != null) {
                            Instance inst = instances.get(m.getMachineId());

                            if (inst != null) {
                                String state = inst.getStatus();
                                if (!state.equals(m.getStatus())) {
                                    log.debug("Host <" + m.getName() + "> status changes from <" + m.getStatus()
                                              + "> to <" + state + ">");
                                    m.setStatus(state);
                                }

                                // The machine is not in ebrokerd hosts.json
                                // and in creating status for long than 10 minutes
                                if (machinesToCheckMap.get(m.getMachineId()) == null &&
                                        StringUtils.isEmpty(m.getRetId()) &&
                                        m.getLaunchtime() != null &&
                                        m.getLaunchtime().longValue() > 0 &&
                                        currentDateSecond.longValue() - m.getLaunchtime().longValue() > GcloudConst.GOOGLE_CLEAN_RC_CLOSED_VM_TIMEOUT) {
                                    log.warn("Host <" + m.getName() + "> does not join lsf cluster for more than 10 minutes. Deleting it on Google...");
                                    GcloudClient.deleteCloudVM(m.getName(), m.getZone());
                                    remove = true;
                                }
                            } else {
                                // the host is not a valid instance anymore,
                                if (machinesToCheckMap.get(m.getMachineId()) != null) {
                                    m.setStatus("DELETED");
                                    machinesCheckedMap.put(m.getMachineId(), m);
                                    terminatingHostList.add(m);
                                    log.debug("Host <" + m.getName() + "> instance is not found on cloud. Set it to DELETED. The last status is <"+ m.getStatus() + ">");
                                } else {
                                    // remove it from DB
                                    log.debug("Host <" + m.getName() + "> instance is not found on cloud. Its status is <unknown>, remove it from DB. The last status is <"+ m.getStatus() + ">");
                                    // Remove it from local DB since the it cannot be found on cloud now.
                                    remove = true;
                                }
                            }
                        }

                        // reported terminated hosts to the caller
                        if (terminationStates.contains(m.getStatus())) {
                            if (machinesToCheckMap.get(m.getMachineId()) != null) {
                                machinesCheckedMap.put(m.getMachineId(), m);
                                terminatingHostList.add(m);
                                // DO NOT remove this machine info from local DB now.
                                // Will be removed after it is removed from ebroerd hosts.json
                                log.debug("Host <" +  m.getName() + "> status is <" + m.getStatus() + "> and it exsits in ebrokerd hosts.json. ");
                            } else {
                                if (alreadyTerminatedStates.contains(m.getStatus())) {
                                    // If VM already in terminated or stopped status, remove it from google and DB file
                                    // Otherwise VM would leave along forever on google cloud if being preempted before ebrokerd got its status
                                    log.debug("Host <" +  m.getName() + "> doesn't exist in ebrokerd hosts.json. Deleteing it from DB and Google...");
                                    GcloudClient.deleteCloudVM(m.getName(), m.getZone());
                                    remove = true;
                                }
                            }
                        }

                        if (remove) {
                            log.debug("Host <" +  m.getName() + "> is terminated. Remove it from the DB.");
                            iterMachine.remove();
                        }
                    }
                }

                // remove empty instance request
                if (CollectionUtils.isEmpty(mList)) {
                    if (GcloudUtil.isBulkRequest(requestInDB.getHostAllocationType())) {
                        GcloudClient.updateBulkVMList(requestInDB, rsp);
                        if (requestInDB.getStatus() != null
                                && (requestInDB.getStatus().equals(GcloudConst.EBROKERD_STATE_COMPLETE)
                                    || requestInDB.getStatus().equals(GcloudConst.EBROKERD_STATE_COMPLETE_WITH_ERROR))
                                && CollectionUtils.isEmpty(requestInDB.getMachines())) {
                            log.debug("Bulk request <" + requestInDB.getReqId() + "> is empty. Remove it from the DB.");
                            iterReq.remove();
                        }
                    } else { // On-demand request
                        log.debug(
                            "On-demand request <" + requestInDB.getReqId() + "> is empty. Remove it from the DB.");
                        iterReq.remove();
                    }
                }
            }
        }


        gcloudRequestList = GcloudClient.retrieveInstancesMarkedForTermination(requestsToBeChecked);

        // check if any hosts missed in DB
        if (!CollectionUtils.isEmpty(machinesToCheck)) {
            for (GcloudMachine m : machinesToCheck) {
                if (machinesCheckedMap.get(m.getMachineId()) == null) {
                    if (instances != null) {
                        Instance inst = instances.get(m.getMachineId());
                        if (inst == null) {
                            log.debug("Host <" +  m.getName() + "> does not exist in DB and Gcloud.");
                            // assume the host is terminated
                            terminatingHostList.add(m);
                        } else {
                            String state = inst.getStatus();
                            if (alreadyTerminatedStates.contains(state)) {
                                log.debug("Host <" + m.getName() + "> does not exist in DB."
                                          + "Gcloud reports the host is in <" + state + ">.");
                                terminatingHostList.add(m);
                            }
                        }
                    }
                }
            }
        }

        for (GcloudMachine m : terminatingHostList) {
            GcloudRequest gcloudRequest = new GcloudRequest();
            gcloudRequest.setVmName(m.getName());
            gcloudRequest.setMachineId(m.getMachineId());
            gcloudRequestList.add(gcloudRequest);
        }
        GcloudUtil.saveToFile(provisionStatusDB);


        rsp.setStatus(GcloudConst.EBROKERD_STATE_COMPLETE);
        rsp.setReqs(gcloudRequestList);
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
     * @see com.ibm.spectrum.gcloud.IGcloud#requestMachines(com.ibm.spectrum.model.GcloudEntity)
     */
    @Override
    public GcloudEntity requestMachines(GcloudEntity req) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class GcloudImpl in method requestMachines with parameters: req: " + req);
        }
        GcloudEntity rsp = new GcloudEntity();
        String instanceTagVal = "";
        HostAllocationType hostAllocationType = null;

        List<GcloudMachine> machines = req.getMachines();
        if (!CollectionUtils.isEmpty(machines)) {
            rsp.setRsp(1, "Input parameter machines is not supported.");
            return rsp;
        }

        GcloudTemplate t = req.getTemplate();
        if (null == t || StringUtils.isEmpty(t.getTemplateId())) {
            rsp.setRsp(1, "Invalid template.");
            return rsp;
        }

        GcloudTemplate at = GcloudUtil.getTemplateFromFile(t.getTemplateId());
        if (null == at) {
            rsp.setRsp(1, "The template does not exist.");
            return rsp;
        }

        Integer vmNum = t.getVmNumber();
        if (null == vmNum || vmNum < 1) {
            rsp.setRsp(1, "Invalid VM number.");
            return rsp;
        } else if (vmNum > GcloudConst.MAXIMUM_VM_IN_ONE_REQUEST) {
            log.warn("Requested machine count <{}> exceeds the maximum supported machine count in one request <{}>, setting it to {}",
                     vmNum, GcloudConst.MAXIMUM_VM_IN_ONE_REQUEST, GcloudConst.MAXIMUM_VM_IN_ONE_REQUEST );
            vmNum = 1000;
        }

        // Validate configuration and get allocation type
        hostAllocationType = GcloudUtil.validateConfig(at);
        if (hostAllocationType == null) {
            rsp.setRsp(1, "Invalid Configuration.");
            return rsp;
        }

        at.setVmNumber(vmNum);
        GcloudRequest rq = new GcloudRequest();
        instanceTagVal = req.getTagValue();
        rq.setTagValue(instanceTagVal);

        List<GcloudMachine> machineList = new ArrayList<GcloudMachine>();

        // OnDemand request means to create VMs one by one (old creation model)
        // Bulk request means to create multiple VMs in one request (new creation model)
        String reqId = null;
        String bulkInsertId = null;
        if (hostAllocationType.equals(HostAllocationType.OnDemand)) {
            reqId = GcloudConst.ON_DEMAND_REQUEST_PREFIX + UUID.randomUUID().toString();
            machineList = GcloudClient.createVM(at, instanceTagVal, reqId);
            if (CollectionUtils.isEmpty(machineList)) {
                rsp.setRsp(1, "Create onDemand VMs on " + GcloudUtil.getProviderName() + " failed.");
                return rsp;
            }
        } else { // Bulk request
            // bulkInsertId is used to label the instances created in one bulk request
            bulkInsertId = GcloudConst.BULK_INSERT_ID_PREFIX + UUID.randomUUID().toString();
            reqId = GcloudClient.createBulkVMs(at, instanceTagVal, bulkInsertId, hostAllocationType, rsp);
            if (StringUtils.isBlank(reqId)) {
                return rsp;
            }
        }
        rq.setMachines(machineList);
        rq.setReqId(reqId);
        rq.setTime(System.currentTimeMillis());
        rq.setTtl(at.getTtl());
        rq.setTagValue(instanceTagVal);
        rq.setHostAllocationType(hostAllocationType.toString());
        rq.setTemplateId(t.getTemplateId());
        rq.setBulkInsertId(bulkInsertId);

        GcloudUtil.saveToFile(rq);

        rsp.setMsg("Request VM success from " + GcloudUtil.getProviderName() + ", allocation type: " + hostAllocationType.toString() + ".");
        rsp.setReqId(reqId);
        if (log.isTraceEnabled()) {
            log.trace("End in class GcloudImpl in method requestMachines with return: GcloudEntity: " + rsp);
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
     * @see com.ibm.spectrum.gcloud.IGcloud#requestReturnMachines(com.ibm.spectrum.model.GcloudEntity)
     */
    @Override
    public GcloudEntity requestReturnMachines(GcloudEntity req) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class GcloudImpl in method requestReturnMachines with parameters: req" + req);
        }
        GcloudEntity rsp = new GcloudEntity();

        List<GcloudMachine> machinesToBeReturned = req.getMachines();
        List<GcloudMachine> statusChangeMachineLst = new ArrayList<GcloudMachine>();
        if (CollectionUtils.isEmpty(machinesToBeReturned)) {
            rsp.setStatus(GcloudConst.EBROKERD_STATE_WARNING);
            rsp.setRsp(1, "Invalid input file.");
            return rsp;
        }

        GcloudEntity gcloudEntity = GcloudUtil.getFromFile();

        Map<String, GcloudMachine> machinesMap = new HashMap<String, GcloudMachine>();
        Map<String, GcloudMachine> terminatedMachinesMap = new HashMap<String, GcloudMachine>();
        Map<String, GcloudRequest> requestsMap = new HashMap<String, GcloudRequest>();
        String retId = GcloudConst.RETURN_REQUEST_PREFIX + UUID.randomUUID().toString();
        if (gcloudEntity == null || CollectionUtils.isEmpty(gcloudEntity.getReqs())) {
            log.debug("No requests in local DB.");
        } else {
            for (GcloudRequest gcloudRequest : gcloudEntity.getReqs()) {
                if (!CollectionUtils.isEmpty(gcloudRequest.getMachines())) {
                    for (GcloudMachine gcloudMachine : gcloudRequest.getMachines()) {
                        if (GcloudUtil.getMatchingMachineInList(gcloudMachine, machinesToBeReturned) != null) {
                            // Ignore machines that are already being terminated
                            // or have been terminated
                            if (StringUtils.isEmpty(gcloudMachine.getRetId()) ||
                                    !terminationStates.contains(gcloudMachine.getStatus())) {
                                // If the machine is an exact match to one of
                                // the machines in the list of machines requested to
                                // be returned
                                log.debug("Processing request to terminate machine:  " + gcloudMachine);
                                // TODO: need to handle preemptive instance
                                machinesMap.put(gcloudMachine.getMachineId(), gcloudMachine);
                                requestsMap.put(gcloudMachine.getMachineId(), gcloudRequest);
                            } else {
                                if (StringUtils.isEmpty(gcloudMachine.getRetId())) {
                                    gcloudMachine.setRetId(retId);
                                    terminatedMachinesMap.put(gcloudMachine.getMachineId(), gcloudMachine);
                                }
                            }
                            // Removing from the list of machinesToBeReturned
                            // the current machine after being considered for
                            // return.
                            machinesToBeReturned.remove(gcloudMachine);
                        }
                    }
                }
            }
        }

        // If the machines to be returned list still has values, these machines
        // were not found in the Google plugin local DB, try to delete VM from cloud
        if (!CollectionUtils.isEmpty(machinesToBeReturned)) {
            log.warn("The following machine(s) were not found in Google plugin local DB and will be deleted from cloud: "
                     + machinesToBeReturned);
            GcloudClient.deleteVMListFromCloud(machinesToBeReturned);
        }

        if (!machinesMap.isEmpty()) {
            List<GcloudMachine> machineNameList = new ArrayList<GcloudMachine>(machinesMap.values());
            statusChangeMachineLst = GcloudClient.deleteVM(machineNameList, rsp);
            if (CollectionUtils.isEmpty(statusChangeMachineLst)) {
                return rsp;
            }

            for (GcloudMachine sc : statusChangeMachineLst) {
                GcloudMachine m = machinesMap.get(sc.getMachineId());
                GcloudRequest matchingRequest = requestsMap.get(sc.getMachineId());
                if (m != null) {
                    m.setRetId(retId);
                    m.setStatus(sc.getStatus());
                    m.setResult(sc.getStatus());

                    log.debug("[Instance - " + matchingRequest.getReqId() + " - " + m.getReqId() + " - "
                              + m.getMachineId() + "] Machine Terminated: " + sc.getStatus());
                }
            }
        }

        if (machinesMap.size() > 0) {
            // if any machines are terminating
            machinesMap.putAll(terminatedMachinesMap);
            GcloudUtil.updateToFile(machinesMap);

            rsp.setStatus(GcloudConst.EBROKERD_STATE_RUNNING);
            rsp.setRsp(0, "Delete instances success.");
            rsp.setReqId(retId);
        } else {
            machinesMap.putAll(terminatedMachinesMap);
            if (machinesMap.size() > 0) {
                GcloudUtil.updateToFile(machinesMap);
            }
            rsp.setStatus(GcloudConst.EBROKERD_STATE_COMPLETE);
            rsp.setRsp(0, "No Active instances.");
        }

        if (log.isTraceEnabled()) {
            log.trace("End in class GcloudImpl in method requestReturnMachines with return: GcloudEntity: " + rsp);
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
     * @see com.ibm.spectrum.Gcloud.IGcloud#getRequestStatus(com.ibm.spectrum.model.GcloudEntity)
     */
    @Override
    public GcloudEntity getRequestStatus(GcloudEntity req) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class GcloudImpl in method getRequestStatus with parameters: req: " + req);
        }

        GcloudEntity rsp = new GcloudEntity();
        List<GcloudRequest> reqLst = req.getReqs();

        if (CollectionUtils.isEmpty(reqLst)) {
            rsp.setRsp(1, "Invalid input file.");
            return rsp;
        }

        for (GcloudRequest inReq : reqLst) {
            boolean statusUpdateForReturnMachine = (inReq.getReqId().startsWith(GcloudConst.RETURN_REQUEST_PREFIX));

            GcloudRequest fReq = GcloudUtil.getFromFile(inReq.getReqId());
            if(statusUpdateForReturnMachine) {
                if (null == fReq || StringUtils.isEmpty(fReq.getReqId()) || CollectionUtils.isEmpty(fReq.getMachines())) {
                    log.info("The return machine request " + inReq.getReqId() +
                             " is not found in google-db.json. The request related instances might have been deleted on Google Cloud.");
                    inReq.setStatus(GcloudConst.EBROKERD_STATE_COMPLETE);
                    continue;
                }
            } else { // request for create machine
                if (null == fReq
                        || StringUtils.isEmpty(fReq.getReqId())
                        || (CollectionUtils.isEmpty(fReq.getMachines())
                            && HostAllocationType.OnDemand.toString().equals(fReq.getHostAllocationType()))) {
                    // For bulk request, may no machines in DB request now
                    log.warn("The request machine request " + inReq.getReqId() +
                             " is not found in google-db.json.");
                    inReq.setStatus(GcloudConst.EBROKERD_STATE_COMPLETE);
                    continue;
                }
            }

            // Update status
            updateStatus(fReq, inReq, rsp);

            // Change to returning format
            List<GcloudMachine> mLst = inReq.getMachines();
            if (mLst != null) {
                for (GcloudMachine m : mLst) {
                    m.hide();
                }
            }
        }

        rsp.setReqs(reqLst);
        if (log.isTraceEnabled()) {
            log.trace("End in class GcloudImpl in method getRequestStatus with return: GcloudEntity: " + rsp);
        }
        return rsp;
    }

    /**
     *
     * @Title: updateStatus @Description: Performs the following: <br>
     *         1. Retrieve the latest status from google cloud of the machines
     *         attached to the request <br>
     *         2. Map the status retrieved from Gcloud to the status used by
     *         Ebrokerd <br>
     *         3. Update the status file(GcloudUtil.provStatusFile) with the new
     *         status
     * @param  fReq The current machines request information available in the system
     * @param  inReq The request object sent to the service that needs to be updated
     * @return void
     * @throws
     */
    public static void updateStatus(GcloudRequest fReq, GcloudRequest inReq, GcloudEntity rsp) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class GcloudImpl in method updateStatus with parameters: fReq: " + fReq + ", inReq: "
                      + inReq);
        }
        boolean statusUpdateForReturnMachine = (inReq.getReqId().startsWith(GcloudConst.RETURN_REQUEST_PREFIX));
        boolean statusUpdateForCreateMachine = !statusUpdateForReturnMachine;

        Map<String, Instance> vmMap = null;
        //TODO handle the google cloud instance by Operation status at first, then by Instance status???
        inReq.setReqId(fReq.getReqId());

        String latestRequestStatus = GcloudConst.EBROKERD_STATE_COMPLETE;

        // Handle bulk create request, add newly created machines to fReq
        // Request updates for machine termination does not need status update.
        List<GcloudMachine> machinesListInDB = fReq.getMachines();
        try {
            if (GcloudUtil.isBulkRequest(fReq.getHostAllocationType())
                    && statusUpdateForCreateMachine) {
                vmMap = GcloudClient.updateBulkVMList(fReq, rsp);
                if (vmMap == null) {
                    return;
                }
                machinesListInDB = fReq.getMachines();
                latestRequestStatus = fReq.getStatus();
                log.debug("Setting the bulk request [" + fReq.getReqId() + "] status: " + latestRequestStatus);
            } else {
                vmMap = GcloudClient.listVM(machinesListInDB);
            }
        } catch (Exception e) {
            log.error(e);
            inReq.setMachines(new ArrayList<GcloudMachine>());
            inReq.setStatus(GcloudConst.EBROKERD_STATE_COMPLETE_WITH_ERROR);
            inReq.setMsg(e.getMessage());
            return;
        }

        String latestMachineStatus = GcloudConst.EBROKERD_MACHINE_RESULT_FAIL;
        for (GcloudMachine tempMachineInDB : machinesListInDB) {
            log.debug("Updating the state of the machine: " + tempMachineInDB);
            String tempMachineOldStatus = tempMachineInDB.getStatus();
            Instance correspondingInstanceForTempMachineInDB = vmMap.get(tempMachineInDB.getMachineId());
            // If the machine is not in google cloud, set the result depending
            // on the request type
            if (null == correspondingInstanceForTempMachineInDB) {
                // If this is a status update for creating a machine and the
                // machine is not found in google cloud, consider this as a
                // failure
                // TODO, need to see if create instance operation is still
                // PENDING???
                if (statusUpdateForCreateMachine) {
                    tempMachineInDB.setStatus("CREATE_FAILED");
                    tempMachineInDB.setResult(GcloudConst.EBROKERD_MACHINE_RESULT_FAIL);
                } else {// If this is a status update for terminating a machine
                    // and the machine is not found in google cloud,
                    // consider this as
                    // a success
                    // Set the machine status to "DELETED" as it is not found
                    tempMachineInDB.setStatus("DELETED");
                    tempMachineInDB.setResult(GcloudConst.EBROKERD_MACHINE_RESULT_SUCCEED);
                }
                continue;
            }

            latestMachineStatus = correspondingInstanceForTempMachineInDB.getStatus();
            if(! tempMachineOldStatus.equals(latestMachineStatus)) {
                tempMachineInDB.setStatus(latestMachineStatus);
            }

            /*
             * The status of Operation, which can be one of the following:
             * PENDING, RUNNING, or DONE.
             * The status of Instance, which can be one of the following:
             * PROVISIONING, STAGING, RUNNING, STOPPING,STOPPED, SUSPENDING,
             * SUSPENDED, and TERMINATED.
             *
             * About SUSPENDING and SUSPENDED, they are supported in alpha api. These
             * two states should not occurs in current resource connector model.
             * Currently we treat them as fail, as instance can only change
             * to SUSPENDING or SUSPENDED when it is RUNNING.
             * https://cloud.google.com/sdk/gcloud/reference/alpha/compute/
             * instances/suspend
             *
             * Add handling for new status: REPAIRING SUSPENDED SUSPENDING
             * Refer https://cloud.google.com/compute/docs/instances/instance-life-cycle
             *
             * The status from LSF: executing, fail, succeed
             *
             *
             */
            if ("PROVISIONING".equals(latestMachineStatus) || "STAGING".equals(latestMachineStatus)
                    || "STOPPING".equals(latestMachineStatus) || "REPAIRING".equals(latestMachineStatus)
                    || "SUSPENDING".equals(latestMachineStatus)) {
                tempMachineInDB.setResult("executing");
                latestRequestStatus = GcloudConst.EBROKERD_STATE_RUNNING;
            } else if ("RUNNING".equals(latestMachineStatus) || "TERMINATED".equals(latestMachineStatus)
                       || "STOPPED".equals(latestMachineStatus) || "SUSPENDED".equals(latestMachineStatus)) {
                // Setting the machine's result depending on if the request
                // update is for a machine creation or termination
                // If this is an update request for a machine creation request
                String machineResult = "";
                if (statusUpdateForCreateMachine) {
                    if (latestMachineStatus.equals("RUNNING")) {
                        machineResult = (GcloudConst.EBROKERD_MACHINE_RESULT_SUCCEED);
                    } else {
                        machineResult = (GcloudConst.EBROKERD_MACHINE_RESULT_FAIL);
                    }
                } else {
                    if (latestMachineStatus.equals("RUNNING") || latestMachineStatus.equals("SUSPENDED")) {
                        machineResult = (GcloudConst.EBROKERD_MACHINE_RESULT_FAIL);
                    } else {
                        machineResult = (GcloudConst.EBROKERD_MACHINE_RESULT_SUCCEED);
                    }
                }
                tempMachineInDB.setResult(machineResult);
            } else {
                tempMachineInDB.setResult(GcloudConst.EBROKERD_MACHINE_RESULT_FAIL);
                latestRequestStatus = GcloudConst.EBROKERD_STATE_COMPLETE_WITH_ERROR;
            }

            log.debug("Machine old status: " + tempMachineOldStatus);
            log.debug("Machine new status: " + latestMachineStatus);

            // Need update privateIp and PublicIp when creating machine
            if (statusUpdateForCreateMachine) {
                if (StringUtils.isEmpty(tempMachineInDB.getPrivateIpAddress())) {
                    tempMachineInDB.setPrivateIpAddress(
                        GcloudClient.getInstancePrivateIP(correspondingInstanceForTempMachineInDB));
                    GcloudUtil.removeSameIpMachineInDB(tempMachineInDB);
                }
                GcloudTemplate temTemplate = GcloudUtil.getTemplateFromFile(fReq.getTemplateId());
                if ((temTemplate.getPrivateNetworkOnlyFlag() != null && temTemplate.getPrivateNetworkOnlyFlag().equals(Boolean.FALSE))
                        || (temTemplate.getPrivateNetworkOnlyFlag() == null && StringUtils.isNotBlank(temTemplate.getLaunchTemplateId()))) {
                    tempMachineInDB.setPublicIpAddress(
                        GcloudClient.getInstancePublicIP(correspondingInstanceForTempMachineInDB));
                    tempMachineInDB.setPublicDnsName("");
                }
            }

            tempMachineInDB.setMsg("");
            log.debug("[Instance - " + fReq.getReqId() + " - " + tempMachineInDB.getReqId() + " - "
                      + tempMachineInDB.getMachineId() + "] Machine Status in Google Cloud: "
                      + tempMachineInDB.getStatus());
            log.debug("[Instance - " + fReq.getReqId() + " - " + tempMachineInDB.getReqId() + " - "
                      + tempMachineInDB.getMachineId() + "] Machine Result in Ebrokerd: " + tempMachineInDB.getResult());

        }

        // 'running','complete','complete_with_error'
        log.debug("Setting the machine list to the response: " + machinesListInDB);
        inReq.setMachines(machinesListInDB);
        inReq.setStatus(latestRequestStatus);
        inReq.setMsg("");
        log.debug("[Instance - " + fReq.getReqId() + "] Request Status in Google Cloud: " + latestRequestStatus);

        // update VM record
        updateVmStatus(fReq);
        if (log.isTraceEnabled()) {
            log.trace("End in class GcloudImpl in method updateStatus with return: void: ");
        }
    }

    /**
     * @Title: updateVmStatus
     * @Description: Sets the new status to the status
     *         file (GcloudUtil.provStatusFile)
     * @param
     *         requestWithNewValues
     * @return void
     * @throws
     */
    public static void updateVmStatus(GcloudRequest requestWithNewValues) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class GcloudImpl in method updateVmStatus with parameters: requestWithNewValues: "
                      + requestWithNewValues);
        }

        boolean statusUpdateForReturnMachine = (requestWithNewValues.getReqId()
                                                .startsWith(GcloudConst.RETURN_REQUEST_PREFIX));
        boolean statusUpdateForCreateMachine = !statusUpdateForReturnMachine;

        List<GcloudMachine> updatedMachinesList = new ArrayList<GcloudMachine>();

        if (null == requestWithNewValues || CollectionUtils.isEmpty(requestWithNewValues.getMachines())) {
            log.debug("Request " + requestWithNewValues + " does not have any machines allocated yet.");
            return;
        }

        updatedMachinesList.addAll(requestWithNewValues.getMachines());
        Map<String, GcloudMachine> machinesInDBMap = null;

        GcloudEntity provisionStatusDB = GcloudUtil.getFromFile();
        if (null == provisionStatusDB || provisionStatusDB.getReqs() == null) {
            log.error("Provision Status DB is missing or corrupted: " + GcloudUtil.getWorkDir() + "/"
                      + GcloudUtil.getProvStatusFile());
            return;
        }
        List<GcloudRequest> requestListInDB = provisionStatusDB.getReqs();
        log.trace("The requests in DB before update: " + requestListInDB);
        for (GcloudRequest requestInDB : requestListInDB) {

            if (statusUpdateForCreateMachine) {
                // If this is the request that needs to be updated, perform the
                // updates. Otherwise, skip to the next request
                if (requestInDB.getReqId().equals(requestWithNewValues.getReqId())) {
                    machinesInDBMap = new HashMap<String, GcloudMachine>();
                    for (GcloudMachine m : requestInDB.getMachines()) {
                        machinesInDBMap.put(m.getMachineId(), m);
                    }
                    for (GcloudMachine machineWithNewValues : requestWithNewValues.getMachines()) {
                        // If the machine already exists in the DB, update the
                        // attributes
                        if (machinesInDBMap.get(machineWithNewValues.getMachineId()) != null) {
                            GcloudMachine machineToBeUpdated = machinesInDBMap.get(machineWithNewValues.getMachineId());
                            machineToBeUpdated.copyValues(machineWithNewValues);
                            log.debug("Value of machine in updateVm after updating attributes: " + machineToBeUpdated);
                        } else {
                            requestInDB.getMachines().add(machineWithNewValues);
                            log.debug("Value of machine in updateVm after adding new machine: " + machineWithNewValues);
                        }

                    }
                    break;
                }
            } else {// This is a request update for a return machine request
                for (GcloudMachine machineInDB : requestInDB.getMachines()) {
                    // Ignore this machine if it is terminated
                    if (!alreadyTerminatedStates.contains(machineInDB.getStatus())) {
                        // Find matching non-terminated machine
                        GcloudMachine matchingMachine = GcloudUtil.getMatchingMachineInList(machineInDB,
                                                        updatedMachinesList);
                        if (matchingMachine != null) {
                            log.trace("Value of machine in updateVm before updating attributes: " + machineInDB);
                            machineInDB.copyValues(matchingMachine);
                            log.trace("Value of machine in updateVm before updating attributes: " + machineInDB);
                            // Remove the current machine from the update
                            // machine list since its values are copied to the
                            // DB object
                            updatedMachinesList.remove(matchingMachine);
                        }
                        if (updatedMachinesList.isEmpty()) {
                            log.trace("All machines have been save to the DB object");
                            break;
                        }

                    }

                }

            }
        }
        if (log.isTraceEnabled()) {
            log.trace("Saving to file: " + provisionStatusDB);
        }
        GcloudUtil.saveToFile(provisionStatusDB);

        if (log.isTraceEnabled()) {
            log.trace("End in class GcloudImpl in method updateVmStatus with return: void: ");
        }
    }


}
