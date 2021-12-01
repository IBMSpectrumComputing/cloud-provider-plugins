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

package com.ibm.spectrum.azure;

import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.apache.commons.collections.CollectionUtils;
import com.ibm.spectrum.util.StringUtils;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.compute.PowerState;
import com.microsoft.azure.management.compute.VirtualMachine;
import com.ibm.spectrum.constant.AzureConst;
import com.ibm.spectrum.model.AzureEntity;
import com.ibm.spectrum.model.AzureMachine;
import com.ibm.spectrum.model.AzureRequest;
import com.ibm.spectrum.model.AzureTemplate;
import com.ibm.spectrum.model.AzureUserData;
import com.ibm.spectrum.util.AzureUtil;

/**
 * @ClassName: AzureImpl
 * @Description: The implementation of Azure host provider
 * @author xawangyd
 * @date Jan 26, 2016 3:46:14 PM
 * @version 1.0
 */
public class AzureImpl implements IAzure {
    private static Logger log = LogManager.getLogger(AzureImpl.class);

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
     * @see com.ibm.spectrum.Azure.IAzure#getAvailableTemplates(com.ibm.spectrum.model.AzureEntity)
     */
    @Override
    public AzureEntity getAvailableTemplates(AzureEntity req) {
        AzureEntity rsp = new AzureEntity();
        Properties properties = new Properties();
        String userDataStr = "";
        File jf = new File(AzureUtil.getConfDir() + "/conf/azureprov_templates.json");
        if (!jf.exists()) {
            rsp.setRsp(1, "Azure template file azureprov_templates.json does not exist in the "
                       + AzureUtil.getProviderName() + " provider configuration directory.");
            return rsp;
        }

        rsp = AzureUtil.toObject(jf, AzureEntity.class);
        if (null == rsp || CollectionUtils.isEmpty(rsp.getTemplates())) {
            rsp.setRsp(1, "Tempalate file azureprov_templates.json is not a valid JSON format file.");
            return rsp;
        }

        log.debug("The templates: " + rsp);

        /*
        		for (AzureTemplate t : rsp.getTemplates()) {
        			t.hide();
        		}
        */

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
     * @see com.ibm.spectrum.Azure.IAzure#getAvailableMachines(com.ibm.spectrum.model.AzureEntity)
     */
    @Override
    public AzureEntity getAvailableMachines(AzureEntity req) {
        AzureEntity rsp = new AzureEntity();
        rsp.setRsp(0, "This interface is not supported by " + AzureUtil.getProviderName() + " provider.");
        rsp.setMachines(new ArrayList<AzureMachine>());
        return rsp;
    }

    /**
     * (Non Javadoc)
     * <p>
     * Title: getReturnRequests
     * </p>
     * <p>
     * Description: For Azure, we only delete these VM are not in ebrokerd host
     * list which are launched 20 minutes ago.
     * </p>
     *
     * @param req
     * @return
     * @see com.ibm.spectrum.Azure.IAzure#getReturnRequests(com.ibm.spectrum.model.AzureEntity)
     */
    @Override
    public AzureEntity getReturnRequests(AzureEntity req) {
        AzureEntity rsp = new AzureEntity();
        List<AzureRequest> azureRequestList = new ArrayList<AzureRequest>();
        if (log.isDebugEnabled()) {
            log.debug("Start in class AzureImpl in method getReturnRequests with parameters: req: " + req);
        }

        List<AzureMachine> machinesToCheck = req.getMachines();
        Map<String, AzureMachine> machinesToCheckMap = new HashMap<String, AzureMachine>();
        Map<String, AzureMachine> machinesCheckedMap = new HashMap<String, AzureMachine>();
        Map<String, VirtualMachine> instances = null;
        String key = null;

        List<AzureMachine> terminatingHostList = new ArrayList<AzureMachine>();
        if (!CollectionUtils.isEmpty(machinesToCheck)) {
            for (AzureMachine m : machinesToCheck) {
                key = m.mapEbrokerdBackToMahinedId().toLowerCase();
                log.debug("getReturnRequests: machinesToCheck machine key <" + key + ">.");
                machinesToCheckMap.put(key, m);
            }
        }

        // load information from DB and Azure
        AzureEntity provisionStatusDB = AzureUtil.getFromFile();

        instances = AzureUtil.listVM();

        Date currentDateTime = new Date();
        long currentDateSecond = currentDateTime.getTime() / 1000;
        long instanceCreationTimeOutSeconds = AzureConst.INSTANCE_CREATION_TIMEOUT_SECONDS;
        if (AzureUtil.getConfig().getInstanceCreationTimeout() != null
                && AzureUtil.getConfig().getInstanceCreationTimeout().intValue() > 0) {
            instanceCreationTimeOutSeconds = AzureUtil.getConfig().getInstanceCreationTimeout().intValue() * 60;
        }

        // The instance is created but not added into ebrokerd host.json.
        // Use NSTANCE_CREATION_TIMEOUT + 5min
        long instanceClosedRCTimeOutSeconds = instanceCreationTimeOutSeconds + 300;

        if (provisionStatusDB != null && !CollectionUtils.isEmpty(provisionStatusDB.getReqs())) {
            List<AzureRequest> reqList = provisionStatusDB.getReqs();
            for (Iterator<AzureRequest> iterReq = reqList.listIterator(); iterReq.hasNext();) {
                AzureRequest requestInDB = iterReq.next();

                String inReqId = requestInDB.getReqId();
                boolean statusUpdateForReturnMachine = (inReqId.startsWith(AzureConst.RETURN_REQUEST_PREFIX));
                boolean statusUpdateForCreateMachine = !statusUpdateForReturnMachine;

                if (statusUpdateForCreateMachine && (currentDateSecond
                                                     - requestInDB.getTime().longValue() / 1000 < AzureConst.AZURE_QUERY_NEW_CREATED_VM_TIMEOUT)) {
                    continue;
                }
                // Sync host status, remove terminated hosts if it is not in the
                // caller list
                List<AzureMachine> mList = requestInDB.getMachines();

                if (!CollectionUtils.isEmpty(mList)) {
                    for (Iterator<AzureMachine> iterMachine = mList.listIterator(); iterMachine.hasNext();) {
                        AzureMachine m = iterMachine.next();
                        boolean remove = false;
                        key = null;
                        if (StringUtils.isNullOrEmpty(m.getMachineId())) {
                            log.warn("Host id is null:" + m);
                            if (m.getLaunchtime() != null && m.getLaunchtime().longValue() > 0
                                    && currentDateSecond - m.getLaunchtime()
                                    .longValue() > instanceClosedRCTimeOutSeconds) {
                                log.warn("Host id is null. Host <" + m.getName()
                                         + "> is not found in ebrokerd checking list, and it created "+ instanceClosedRCTimeOutSeconds/60 +" minutes ago. currentDateSecond <"
                                         + currentDateSecond + ">. mgetLaunchtime <" + m.getLaunchtime()
                                         + ">. Deleting it...");
                                m.setStatus("NON_EBD_HOST_TIMEOUT_INSTANCE_ID_NULL");
                                AzureUtil.deleteVM(m);
                                remove = true;
                            } else {
                                continue;
                            }
                        } else {
                            key = m.getMachineId().toLowerCase();
                        }

                        if (key != null && instances != null && instances.size() > 0) {
                            VirtualMachine inst = null;

                            inst = instances.get(key);

                            if (inst == null) {
                                // the host is not a valid instance anymore,
                                // remove it from DB
                                log.debug(
                                    "Host <" + m.getName() + "> status is not found on Azure, remove it from DB.");
                                m.setStatus("not_found_on_azure");
                                if (machinesToCheckMap.get(key) != null) {
                                    machinesCheckedMap.put(key, m);
                                    terminatingHostList.add(m);
                                    log.debug("Host <" + m.getMachineId() + "> is terminating.");
                                } else {
                                    remove = true;
                                }
                            } else {
                                // if the host is not found in ebrokerd check list(host.json)
                                // and has been launched 10 minutes ago, delete it
                                if (machinesToCheckMap.get(key) == null && m.getLaunchtime() != null
                                        && m.getLaunchtime().longValue() > 0
                                        && (currentDateSecond - m.getLaunchtime()
                                            .longValue() > instanceClosedRCTimeOutSeconds)) {
                                    // if the host is not in Deleting
                                    // state(maybe deleted by
                                    // requestReturnMachines interface)
                                    if (!inst.provisioningState().equals("Deleting")) {
                                        log.warn("Host <" + m.getName()
                                                 + "> is not found in ebrokerd checking list, and it created "+ instanceClosedRCTimeOutSeconds/60 +" minutes ago. currentDateSecond <"
                                                 + currentDateSecond + ">. mgetLaunchtime <" + m.getLaunchtime()
                                                 + ">. Deleting it...");
                                        AzureUtil.deleteVM(m);
                                        m.setStatus("NON_EBD_HOST_TIMEOUT");
                                    }
                                }
                            }
                        }

                        if (remove) {
                            log.debug("Host <" + m.getMachineId() + "> is not found on Azure. Remove it from the DB.");
                            iterMachine.remove();
                        }
                    }
                }

                // remove empty instance on-demand request
                if (CollectionUtils.isEmpty(mList)) {
                    log.debug("On-demand request <" + requestInDB.getReqId() + "> is empty. Remove it from the DB.");
                    iterReq.remove();
                }
            }

            // check if any hosts missed in DB
            if (!CollectionUtils.isEmpty(machinesToCheck)) {
                for (AzureMachine m : machinesToCheck) {
                    if (m.getMachineId() != null) {
                        key = m.getMachineId().toLowerCase();
                        if (machinesCheckedMap.get(key) == null) {
                            if (instances != null && instances.size() > 0) {
                                VirtualMachine inst = instances.get(key);
                                if (inst == null) {
                                    log.debug("Host <" + m.getMachineId() + "> does not exist in DB and Azure.");
                                    // assume the host is terminated
                                    terminatingHostList.add(m);
                                }
                            }
                        }
                    } else {
                        // Should never go here
                        log.warn("machinesToCheck <" + m + "> machineId is null. ");
                    }
                }

            }

            AzureUtil.saveToFile(provisionStatusDB);

            for (AzureMachine m : terminatingHostList) {
                AzureRequest azureRequest = new AzureRequest();
                azureRequest.setVmName(m.getName());
                m.mapMahinedIdToEbrokderd();
                azureRequest.setMachineId(m.getMachineId());
                azureRequestList.add(azureRequest);
            }

        }

        rsp.setStatus(AzureConst.EBROKERD_STATE_COMPLETE);
        rsp.setReqs(azureRequestList);
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
     * @see com.ibm.spectrum.Azure.IAzure#requestMachines(com.ibm.spectrum.model.AzureEntity)
     */
    @Override
    public AzureEntity requestMachines(AzureEntity req) {
        AzureEntity rsp = new AzureEntity();
        String instanceTagVal = "";

        List<AzureMachine> machines = req.getMachines();
        if (!CollectionUtils.isEmpty(machines)) {
            rsp.setRsp(1, "Input parameter machines is not supported.");
            return rsp;
        }

        AzureTemplate t = req.getTemplate();
        if (null == t || StringUtils.isNullOrEmpty(t.getTemplateId())) {
            rsp.setRsp(1, "Invalid template.");
            return rsp;
        }

        AzureTemplate at = AzureUtil.getTemplateFromFile(t.getTemplateId());
        if (null == at) {
            rsp.setRsp(1, "The template does not exist.");
            return rsp;
        }

        Integer vmNum = t.getVmNumber();
        if (null == vmNum || vmNum < 1) {
            rsp.setRsp(1, "Invalid VM number.");
            return rsp;
        }

        at.setVmNumber(vmNum);

        instanceTagVal = req.getTagValue();
        Collection<AzureMachine> rsv = AzureUtil.createVM(at, instanceTagVal);
        if (null == rsv || CollectionUtils.isEmpty(rsv)) {
            rsp.setRsp(1, "Create VM on " + AzureUtil.getProviderName() + " failed.");
            return rsp;
        }

        String reqId = "req-" + UUID.randomUUID().toString();

        /*
         * get the launch time of this machine
         */
        Date mlaunchdate = new Date();
        // convert Date from dd-MMM-yyyy format to UTC millisec
        Long mlaunchtime = mlaunchdate.getTime();
        // convert to seconds
        if (mlaunchtime > 0) {
            mlaunchtime = mlaunchtime / 1000;
        } else {
            mlaunchtime = 0L;
        }

        List<AzureMachine> mLst = new ArrayList<AzureMachine>();
        for (AzureMachine m : rsv) {
            m.setStatus("begin_creating");
            m.setResult(AzureConst.EBROKERD_MACHINE_RESULT_EXECUTING);
            m.setReqId(reqId);
            m.setTemplate(t.getTemplateId());
            m.setRcAccount(instanceTagVal);

            m.setLaunchtime(mlaunchtime);
            mLst.add(m);
        }

        AzureRequest rq = new AzureRequest();
        rq.setMachines(mLst);
        rq.setReqId(reqId);
        rq.setTime(System.currentTimeMillis());
        rq.setTtl(at.getTtl());
        rq.setTagValue(instanceTagVal);

        AzureUtil.saveToFile(rq);

        rsp.setMsg("Request VM success from " + AzureUtil.getProviderName() + ".");
        rsp.setReqId(reqId);
        AzureUtil.mapAllMachineIdsToErokerd(rq.getMachines());
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
     * @see com.ibm.spectrum.Azure.IAzure#requestReturnMachines(com.ibm.spectrum.model.AzureEntity)
     */
    @Override
    public AzureEntity requestReturnMachines(AzureEntity req) {
        AzureEntity rsp = new AzureEntity();
        List<AzureMachine> machines = req.getMachines();
        if (CollectionUtils.isEmpty(machines)) {
            rsp.setRsp(1, "Invalid input file.");
            return rsp;
        }

        List<String> vmNames = new ArrayList<String>();
        for (AzureMachine m : machines) {
            vmNames.add(m.getName());
        }

        Map<String, AzureMachine> vmMap = AzureUtil.getVMFromFile(vmNames);
        List<AzureMachine> azureMachinesLst = new ArrayList<AzureMachine>(vmMap.values());

        List<AzureMachine> delVmList = AzureUtil.deleteVM(azureMachinesLst);
        String retId = "ret-" + UUID.randomUUID().toString();
        if (delVmList != null) {
            for (AzureMachine avm : delVmList) {
                AzureMachine m = vmMap.get(avm.getMachineId());
                m.setRetId(retId);
                m.setStatus(avm.getStatus());
                m.setResult(avm.getStatus());
            }
        }

        AzureUtil.updateToFile(vmMap);

        rsp.setMsg("Delete VM success.");
        rsp.setReqId(retId);

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
     * @see com.ibm.spectrum.Azure.IAzure#getRequestStatus(com.ibm.spectrum.model.AzureEntity)
     */
    @Override
    public AzureEntity getRequestStatus(AzureEntity req) {
        AzureEntity rsp = new AzureEntity();
        List<AzureRequest> reqLst = req.getReqs();

        if (CollectionUtils.isEmpty(reqLst)) {
            rsp.setRsp(1, "Invalid input file.");
            return rsp;
        }

        for (AzureRequest inReq : reqLst) {
            AzureRequest fReq = AzureUtil.getFromFile(inReq.getReqId());
            if (null == fReq) {
                continue;
            }

            // Update status
            AzureUtil.updateStatus(fReq, inReq);

            // Change to returning format
            List<AzureMachine> mLst = inReq.getMachines();
            if (mLst != null && CollectionUtils.isNotEmpty(mLst)) {
                for (AzureMachine m : mLst) {
                    m.hide();
                }
                AzureUtil.mapAllMachineIdsToErokerd(mLst);
            }
        }

        rsp.setReqs(reqLst);
        return rsp;
    }

}
