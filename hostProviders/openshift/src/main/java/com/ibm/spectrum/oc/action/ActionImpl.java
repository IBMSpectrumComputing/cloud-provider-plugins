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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Iterator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ibm.spectrum.oc.model.Machine;
import com.ibm.spectrum.oc.model.Request;
import com.ibm.spectrum.oc.model.Template;
import com.ibm.spectrum.oc.model.Entity;
import com.ibm.spectrum.oc.util.Util;

public class ActionImpl implements IAction {
    private static Logger log = LogManager.getLogger(ActionImpl.class);

    private static List<String> alreadyTerminatedStates = Arrays.asList(new String[] {Util.OPENSHIFT_MACHINE_STATUS_SUCCEEDED});
    private static String       terminatedState = Util.OPENSHIFT_MACHINE_STATUS_SUCCEEDED;

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
     */
    @Override
    public Entity requestMachines(Entity req) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class ActionImpl in method requestMachines with parameters: req: "
                      + req);
        }
        Entity rsp = new Entity();

        // sanity check req
        if (req.getMachines() != null) {
            rsp.setStatus(Util.EBROKERD_STATE_WARNING);
            rsp.setRsp(1, "Inputting machines parameter is not supported.");
            return rsp;
        }

        Template tmpl = req.getTemplate();
        if (tmpl == null
                || tmpl.getTemplateId() == null
                || tmpl.getTemplateId().isEmpty()) {
            rsp.setStatus(Util.EBROKERD_STATE_WARNING);
            rsp.setRsp(1, "The template is not valid.");
            return rsp;
        }

        Template gTemplate = Util.getTemplateFromFile(tmpl.getTemplateId());
        if (null == gTemplate) {
            rsp.setStatus(Util.EBROKERD_STATE_ERROR);
            rsp.setRsp(1, "The template does not exist.");
            return rsp;
        }

        Integer numPods = tmpl.getNumPods();
        if (numPods == null || numPods < 1) {
            rsp.setStatus(Util.EBROKERD_STATE_ERROR);
            rsp.setRsp(1, "The number of instances is not valid.");
            return rsp;
        }

        String instanceTagVal = req.getTagValue();
        String reqId = Util.REQUEST_PREFIX + UUID.randomUUID().toString();

        rsp.setReqId(reqId);
        List<Machine> mList = Util.createMachines(req, gTemplate, rsp);
        if (mList == null
                || mList.isEmpty()) {
            rsp.setRsp(1,
                       "Failed to request instances on  " + Util.getProviderName()
                       + ".");
            return rsp;
        }
        Request pRequest = new Request();
        pRequest.setTagValue(instanceTagVal);
        pRequest.setReqId(reqId);
        pRequest.setMachines(mList);
        pRequest.setTime(System.currentTimeMillis());
        pRequest.setTtl(gTemplate.getTtl());
        pRequest.setTagValue(instanceTagVal);
        pRequest.setTemplateId(tmpl.getTemplateId());

        Util.saveToFile(pRequest);

        rsp.setStatus(Util.EBROKERD_STATE_RUNNING);
        rsp.setRsp(0, "Successfully requested instances from " + Util.getProviderName() + ".");
        rsp.setReqId(reqId);
        if (log.isTraceEnabled()) {
            log.trace("End in class ActionImpl in method requestMachines with return: Entity: "
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
     * @see com.ibm.spectrum.aws.I#requestReturnMachines(com.ibm.spectrum.oc.model.Entity)
     */
    @Override
    public Entity requestReturnMachines(Entity req) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class ActionImpl in method requestReturnMachines with parameters: req" + req);
        }
        Entity rsp = new Entity();
        String retId = Util.RETURN_REQUEST_PREFIX + UUID.randomUUID().toString();
        rsp.setRetId(retId);

        List<Machine> machinesToBeReturned = req.getMachines();
        if (machinesToBeReturned == null
                || machinesToBeReturned.isEmpty()) {
            rsp.setStatus(Util.EBROKERD_STATE_WARNING);
            rsp.setRsp(1, "The input file is not valid.");
            return rsp;
        }

        Entity gEntities = Util.getFromFile();

        Map<String, Machine> machinesMap = new HashMap<String, Machine>();

        if (gEntities == null
                || gEntities.getReqs() == null
                || gEntities.getReqs().isEmpty()) {
            log.debug("No requests in local DB.");
        } else {
            for (Request request : gEntities.getReqs()) {
                if (request.getMachines() != null) {
                    for (Machine m : request.getMachines()) {
                        if (Util.getMatchingMachineInList(m, machinesToBeReturned) != null) {
                            machinesMap.put(m.getMachineId(), m);
                            machinesToBeReturned.remove(m);
                        }
                    }
                }
            }
        }

        // If the machines to be returned list still has values, these machines
        // were not found in the plugin local DB.
        // The machines will be returned.
        if (! machinesToBeReturned.isEmpty()) {
            log.error("The following machines were not found in the plugin local database and will be ignored: "
                      + machinesToBeReturned);
            Util.deleteMachines(req, machinesToBeReturned, null);
        }

        if (machinesMap.size() > 0) {
            List<Machine> mList = new ArrayList<Machine>(machinesMap.values());
            if (Util.deleteMachines(req, mList, rsp) < 0) {
                rsp.setStatus(Util.EBROKERD_STATE_WARNING);
                rsp.setRsp(1, "Delete pods failed.");
                return rsp;
            }
            rsp.setReqId(retId);
            rsp.setStatus(Util.EBROKERD_STATE_RUNNING);
            rsp.setRsp(0, "Delete pods succeeded.");
            // update machines
            for (Machine m : mList) {
                m.setRetId(retId);
            }

            Util.updateToFile(machinesMap);

        } else {
            rsp.setStatus(Util.EBROKERD_STATE_COMPLETE);
            rsp.setRsp(0, "No active pods.");
        }

        if (log.isTraceEnabled()) {
            log.trace("End in class Impl in method requestReturnMachines with return: Entity: " + rsp);
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
     * @see com.ibm.spectrum.aws.I#getRequestStatus(com.ibm.spectrum.oc.model.Entity)
     */
    @Override
    public Entity getRequestStatus(Entity req) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class ActionImpl in method getRequestStatus with parameters: req: "
                      + req);
        }
        Entity rsp = new Entity();
        List<Request> reqList = req.getReqs();

        if (reqList == null
                || reqList.isEmpty()) {
            rsp.setStatus(Util.EBROKERD_STATE_WARNING);
            rsp.setRsp(1, "The input file is not valid.");
            return rsp;
        }

        Map<String, Machine>mMap = Util.retrieveAllMachines();
        for (Request inReq : reqList) {
            if (inReq.getReqId() == null) {
                // mark the request completed as no request in DB
                inReq.setStatus(Util.EBROKERD_STATE_COMPLETE_WITH_ERROR);
                continue;
            }

            Request fReq = Util.getFromFile(inReq.getReqId());
            if (fReq == null
                    || fReq.getReqId() == null
                    || fReq.getReqId().isEmpty()) {
                // mark the request completed as no request in DB
                inReq.setStatus(Util.EBROKERD_STATE_COMPLETE_WITH_ERROR);
                continue;
            }

            // Update status
            updateStatus(mMap, fReq, inReq, rsp);
            if (rsp.getStatus() != null
                    && rsp.getStatus().equals(Util.EBROKERD_STATE_ERROR)) {
                return rsp;
            }

            // Change to returning format
            List<Machine> mList = inReq.getMachines();
            if (mList != null) {
                for (Machine m : mList) {
                    m.hide();
                }
            }
        }

        rsp.setRsp(0, "");
        rsp.setReqs(reqList);
        if (log.isTraceEnabled()) {
            log.trace("End in class ActionImpl in method getRequestStatus with return: Entity: "
                      + rsp);
        }
        return rsp;
    }

    /**
     *
     * @Title: updateStatus
     * @Description: Performs the following:<br>
     * 1. Retrieve the latest status from OpenShift of the machines attached to the request <br>
     * 2. Map the status retrieved from OpenShift to the status used by Ebrokerd <br>
     * 3.Update the status file(Util.provStatusFile) with the new status
     * @param fReq The current machines request information available in the system
     * @param inReq The request object sent to the service that needs to be updated
     * @return void
     * @throws
     */
    public static void updateStatus(Map<String, Machine> mMap, Request fReq, Request inReq, Entity rsp) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class ActionImpl in method updateStatus with parameters: " + inReq);
        }
        if (mMap == null
                || mMap.isEmpty()) {
            return;
        }
        boolean isUpdatingForReturnMachine = false;
        if (inReq.getReqId() != null && inReq.getReqId().startsWith(Util.RETURN_REQUEST_PREFIX)) {
            isUpdatingForReturnMachine = true;
        }

        String latestRequestStatus = Util.EBROKERD_STATE_COMPLETE;

        List<Machine> machinesListInDB = fReq.getMachines();
        for (Machine tempMachineInDB : machinesListInDB) {
            log.debug("Updating the state of the machine: " + tempMachineInDB);
            Machine machineInActive = mMap.get(tempMachineInDB.getMachineId());
            //If the machine is not in OpenShift, set the result depending on the request type
            if (machineInActive == null) {
                log.debug("No Pod found in OpenShift for " + tempMachineInDB);
                // If this is a status update for creating a machine and the
                // machine is not found in OpenShift, consider this as a failure
                if(!isUpdatingForReturnMachine) {
                    tempMachineInDB.setResult(Util.EBROKERD_MACHINE_RESULT_FAIL);
                    latestRequestStatus = Util.EBROKERD_STATE_ERROR;
                } else {// If this is a status update for terminating a machine
                    // and the machine is not found in OpenShift, consider this as
                    // a success
                    tempMachineInDB.setResult(Util.EBROKERD_MACHINE_RESULT_SUCCEED);
                }
                continue;
            }
            String tempMachineOldStatus = tempMachineInDB.getStatus();
            String latestMachineStatus = machineInActive.getStatus();
            if(! isUpdatingForReturnMachine) {
                if (latestMachineStatus.equals(Util.EBROKERD_MACHINE_RESULT_FAIL)) {
                    latestRequestStatus = Util.EBROKERD_STATE_ERROR;
                } else if (latestMachineStatus.equals(Util.EBROKERD_MACHINE_RESULT_EXECUTING)) {
                    latestRequestStatus = Util.EBROKERD_STATE_RUNNING;
                } else if (machineInActive.getPrivateIpAddress() == null) {
                    // podIP not assigned yet
                    latestRequestStatus = Util.EBROKERD_STATE_RUNNING;
                }
            } else {
                if (latestMachineStatus.equals(Util.EBROKERD_MACHINE_RESULT_FAIL)) {
                    latestRequestStatus = Util.EBROKERD_STATE_ERROR;
                } else if (latestMachineStatus.equals(Util.EBROKERD_MACHINE_RESULT_EXECUTING)) {
                    latestRequestStatus = Util.EBROKERD_STATE_RUNNING;
                }
            }

            if (latestMachineStatus.equals(tempMachineOldStatus)) {
                log.debug("Machine [" + tempMachineInDB.getMachineId() +"] status unchanged");
            } else {
                log.debug("Machine [" + tempMachineInDB.getMachineId() +"]  status changed: " + tempMachineOldStatus + " -->" + latestMachineStatus );
            }
            tempMachineInDB.copyValues(machineInActive);
            tempMachineInDB.setStatus(latestMachineStatus);
            tempMachineInDB.setResult(machineInActive.getResult());
            tempMachineInDB.setMsg("");

        }

        log.debug("Setting the machine list to the response: " + machinesListInDB);
        inReq.setMachines(machinesListInDB);
        inReq.setStatus(latestRequestStatus);
        inReq.setMsg("");
        log.debug("[" + fReq.getReqId() + "] Request Status in OpenShift: " + latestRequestStatus);

        Map<String, Machine> machinesInDBMap = new HashMap<String, Machine>();
        for (Machine m: fReq.getMachines()) {
            machinesInDBMap.put(m.getMachineId(), m);
        }
        // save machines into file
        Util.updateToFile(machinesInDBMap);
        if (log.isTraceEnabled()) {
            log.trace("End in class ActionImpl in method updateStatus with return: void: ");
        }
    }

    /**
     * @Title: updateMachineStatus
     * @Description: Sets the new status to the status file
     *               (Util.provStatusFile)
     * @param requestWithNewValues
     * @return void
     * @throws
     */
    public static void updateMachineStatus(Request requestWithNewValues) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class ActionImpl in method updateVmStatus with parameters: requestWithNewValues: "
                      + requestWithNewValues);
        }
        if (null == requestWithNewValues
                || requestWithNewValues.getMachines() == null
                || requestWithNewValues.getMachines().isEmpty()) {
            log.debug("Request " + requestWithNewValues + " does not have any machines allocated yet.");
            return;
        }

        boolean isUpdatingForReturnMachine = (requestWithNewValues.getReqId().startsWith(Util.RETURN_REQUEST_PREFIX));

        List<Machine> updatedMachinesList = new ArrayList<Machine>();

        updatedMachinesList.addAll(requestWithNewValues.getMachines());
        Map<String, Machine> machinesInDBMap = null;

        Entity provisionStatusDB = Util.getFromFile();
        if (null == provisionStatusDB || provisionStatusDB.getReqs() == null) {
            log.error("Provision status database is missing or corrupted: "
                      + Util.getProvWorkDir() + "/" + Util.getProvStatusFile());
            return;
        }
        List<Request> requestListInDB = provisionStatusDB.getReqs();
        log.trace("The requests in DB before update: " + requestListInDB);
        for (Request requestInDB : requestListInDB) {

            if(! isUpdatingForReturnMachine) {
                // If this is the request that needs to be updated, perform the
                // updates. Otherwise, skip to the next request
                if (requestInDB.getReqId().equals(requestWithNewValues.getReqId())) {
                    machinesInDBMap = new HashMap<String, Machine>();
                    for (Machine m : requestInDB.getMachines()) {
                        machinesInDBMap.put(m.getMachineId(), m);
                    }
                    for (Machine machineWithNewValues : requestWithNewValues
                            .getMachines()) {
                        // If the machine already exists in the DB, update the
                        // attributes
                        Machine machineToBeUpdated = machinesInDBMap.get(machineWithNewValues.getMachineId());
                        if (machineToBeUpdated != null) {
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
                for(Machine machineInDB : requestInDB.getMachines()) {
                    //Ignore this machine if it is terminated
                    if(!alreadyTerminatedStates.contains(machineInDB.getStatus())) {
                        //Find matching non-terminated machine
                        Machine matchingMachine = Util.getMatchingMachineInList(machineInDB,updatedMachinesList);
                        if(matchingMachine!= null) {
                            log.trace("Value of machine  before updating attributes: " + machineInDB);
                            machineInDB.copyValues(matchingMachine);
                            log.trace("Value of machine  after updating attributes: " + machineInDB);
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
        Util.saveToFile(provisionStatusDB);

        if (log.isTraceEnabled()) {
            log.trace("End in class ActionImpl in method updateMachineStatus with return: void: ");
        }
    }
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
     * @see com.ibm.spectrum.aws.I#getAvailableTemplates(com.ibm.spectrum.oc.model.Entity)
     */
    @Override
    public Entity getAvailableTemplates(Entity req) {
        Entity rsp = new Entity();

        List<Template> tList = Util.getProvTmplatesList();
        rsp.setTemplates(tList);
        rsp.setStatus(Util.EBROKERD_STATE_COMPLETE);
        rsp.setRsp(0, "");
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
     */
    @Override
    public Entity getAvailableMachines(Entity req) {
        Entity rsp = new Entity();
        if (System.getProperty(Util.EBROKERD_PROPERTY_TEST_URL) == null) {
            rsp.setRsp(
                0,
                "This interface is not supported by the "
                + Util.getProviderName() + " provider.");
            rsp.setMachines(new ArrayList<Machine>());

        } else {
            List<Machine>mList = Util.listMachines();
            if (mList == null
                    || mList.isEmpty()) {
                rsp.setRsp(0, "No machines available" );
            } else {
                rsp.setCode(0);
                rsp.setMsg("Got available <" + mList.size() + "> machines");
                rsp.setMachines(mList);
            }
        }
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
     */
    @Override
    public Entity getReturnRequests(Entity req) {
        // No reclaim in OpenShift.
        // Yet cleans up machines & requests from local DB
        // However getReturnRequests() is never called from ebrokerd (10.1.0.10) as provider list is hard-coded
        Entity rsp = new Entity();
        List<Request> requestList = new ArrayList<Request>();
        if (log.isTraceEnabled()) {
            log.debug("Start in class ActionImpl in method getReturnRequests with parameters: req: " + req);
        }

        List<Machine> machinesToCheck = req.getMachines();

        Map<String, Machine> machinesToCheckMap = new HashMap<String, Machine>();
        Map<String, Machine> machinesCheckedMap = new HashMap<String, Machine>();
        List<String> terminatingHostList = new ArrayList<String>();
        if (machinesToCheck != null) {
            for (Machine m : machinesToCheck) {
                machinesToCheckMap.put(m.getMachineId(), m);
            }
        }

        Date currentDateTime = new Date();
        long currentDateSecond = currentDateTime.getTime() / 1000;
        long instanceCreationTimeoutSeconds =  Util.INSTANCE_CREATION_TIMEOUT_MINUTES*60;

        if (Util.getConfig().getInstanceCreationTimeout() != null
                && Util.getConfig().getInstanceCreationTimeout().intValue() > 0) {
            instanceCreationTimeoutSeconds = Util.getConfig().getInstanceCreationTimeout().intValue() * 60;
        }

        // load information from DB and OpenShift
        Entity provisionStatusDB = Util.getFromFile();
        Map<String, Machine>mMap = Util.retrieveAllMachines();

        List<Machine> toBeDeletedMachines = new ArrayList<Machine>();

        if (provisionStatusDB != null
                && !	(provisionStatusDB.getReqs() == null || provisionStatusDB.getReqs().isEmpty())) {
            List<Request> reqList = provisionStatusDB.getReqs();
            for (Iterator<Request> iterReq = reqList.listIterator(); iterReq.hasNext();) {
                Request requestInDB = iterReq.next();

                // Sync host status, remove terminated hosts if it is not in the
                // caller list
                List<Machine> mList = requestInDB.getMachines();
                if (! (mList == null || mList.isEmpty())) {
                    for (Iterator<Machine> iterMachine = mList.listIterator(); iterMachine.hasNext();) {
                        Machine m = iterMachine.next();
                        boolean remove = false;

                        Machine inst = mMap.get(m.getMachineId());

                        if (inst != null) {
                            String state = inst.getStatus();
                            if (!state.equals(m.getStatus())) {
                                log.debug("Host <" + m.getMachineId() + "> status changes from <" + m.getStatus()
                                          + "> to <" + state + ">");
                            }
                            m.setStatus(state);
                            // The machine is not in ebrokerd hosts.json
                            // and in creating status for long than 10 minutes
                            if (machinesToCheckMap.get(m.getMachineId()) == null
                                    && !(terminatedState.equals(inst.getStatus()))
                                    && m.getRetId() == null
                                    && m.getLaunchtime() > 0
                                    && currentDateSecond - inst.getLaunchtime() > instanceCreationTimeoutSeconds) {
                                log.warn("Host <" + m.getMachineId() + "> is not added into hosts.json for more than " + instanceCreationTimeoutSeconds/60 + " minutes. Deleting the host from OpenShift.");
                                toBeDeletedMachines.add(m);
                            }

                        } else {
                            // the host is not a valid instance anymore,
                            if (machinesToCheckMap.get(m.getMachineId()) != null) {
                                machinesCheckedMap.put(m.getMachineId(), m);
                                terminatingHostList.add(m.getName());
                                log.debug("Host <" + m.getMachineId() + "> is already deleted and not found in OpenShift.");
                            } else {
                                // remove it from DB
                                log.debug("Host <" + m.getMachineId()
                                          + "> status is <unknown>, remove it from DB. The last status is <"
                                          + m.getStatus() + ">");
                                remove = true;
                            }
                        }

                        // reported terminated hosts to the caller, or remove it
                        // from DB
                        if (terminatedState.equals(m.getStatus())) {
                            if (machinesToCheckMap.get(m.getMachineId()) != null) {
                                machinesCheckedMap.put(m.getMachineId(), m);
                                terminatingHostList.add(m.getName());
                                log.debug("Host <" + m.getMachineId() + "> is terminated.");
                            } else {
                                remove = true;
                            }
                        }

                        if (remove) {
                            log.debug("Host <" + m.getMachineId() + "> is terminated.  Remove it from the DB.");
                            iterMachine.remove();
                        }
                    }
                }

                // remove empty request
                if (mList == null
                        || mList.isEmpty()) {
                    log.debug(
                        "Request <" + requestInDB.getReqId() + "> is empty. Remove it from the DB.");
                    iterReq.remove();
                }
            }

            // check if any hosts missed in DB
            for (Machine m : machinesToCheck) {
                if (machinesCheckedMap.get(m.getMachineId()) != null) {
                    continue;
                }
                Machine inst = mMap.get(m.getMachineId());
                if (inst == null) {
                    log.debug("Host <" + m.getMachineId() + "> does not exist in DB and OpenShift.");
                    // assume the host is terminated
                    terminatingHostList.add(m.getName());
                } else {
                    String state = inst.getStatus();
                    if (alreadyTerminatedStates.contains(state)) {
                        log.debug("Host <" + m.getMachineId() + "> does not exist in DB."
                                  + "OpenShift reports the host is in <" + state + ">.");
                        terminatingHostList.add(m.getName());
                    }
                }
            }
            // Delete Pods with the creation timeout - pending
            if (!(toBeDeletedMachines == null
                    || toBeDeletedMachines.isEmpty())) {
                Util.deleteMachines(req, toBeDeletedMachines, null);
            }

            Util.saveToFile(provisionStatusDB);

            for (String name : terminatingHostList) {
                Request request = new Request();
                request.setVmName(name);
                request.setMachineId(name); /* machineId is equal to name in class Machine in OC */
                requestList.add(request);
            }

        }

        rsp.setStatus(Util.EBROKERD_STATE_COMPLETE);
        rsp.setReqs(requestList);
        rsp.setRsp(0, "Instances marked for termination retrieved successfully");

        return rsp;

    }

}
