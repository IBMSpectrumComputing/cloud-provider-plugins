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


package com.ibm.spectrum.cyclecloud.service;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ibm.spectrum.cyclecloud.constant.ProvConst;
import com.ibm.spectrum.cyclecloud.enums.ApiCode;
import com.ibm.spectrum.cyclecloud.enums.CCNodeState;
import com.ibm.spectrum.cyclecloud.enums.NodeResult;
import com.ibm.spectrum.cyclecloud.enums.NodeStatus;
import com.ibm.spectrum.cyclecloud.enums.ProvCode;
import com.ibm.spectrum.cyclecloud.enums.ProvStatus;
import com.ibm.spectrum.cyclecloud.model.ProvApi;
import com.ibm.spectrum.cyclecloud.model.ProvException;
import com.ibm.spectrum.cyclecloud.model.ProvNode;
import com.ibm.spectrum.cyclecloud.model.ProvNodeCreation;
import com.ibm.spectrum.cyclecloud.model.ProvNodes;
import com.ibm.spectrum.cyclecloud.model.ProvReq;
import com.ibm.spectrum.cyclecloud.model.ProvReqs;
import com.ibm.spectrum.cyclecloud.model.ProvResult;
import com.ibm.spectrum.cyclecloud.model.ProvTemplate;
import com.ibm.spectrum.cyclecloud.model.cc.Azure;
import com.ibm.spectrum.cyclecloud.model.cc.CCBucket;
import com.ibm.spectrum.cyclecloud.model.cc.CCCluster;
import com.ibm.spectrum.cyclecloud.model.cc.CCNode;
import com.ibm.spectrum.cyclecloud.model.cc.CCNodeArray;
import com.ibm.spectrum.cyclecloud.model.cc.CCNodeArrays;
import com.ibm.spectrum.cyclecloud.model.cc.CCNodes;
import com.ibm.spectrum.cyclecloud.model.cc.CCVirtMachine;
import com.ibm.spectrum.cyclecloud.model.cc.LSFConfig;
import com.ibm.spectrum.cyclecloud.model.cc.NodeAttribute;
import com.ibm.spectrum.cyclecloud.model.cc.NodeConfig;
import com.ibm.spectrum.cyclecloud.model.cc.NodeCreation;
import com.ibm.spectrum.cyclecloud.model.cc.NodeDefinition;
import com.ibm.spectrum.cyclecloud.model.cc.NodeMgtReq;
import com.ibm.spectrum.cyclecloud.model.cc.NodeSet;
import com.ibm.spectrum.cyclecloud.util.ProvUtil;

import okhttp3.Response;

/**
* @Class Name : ProvService
* @Description: Cycle Cloud Provider Service
* @Author     : Yudong (Dom) Wang
* @Date       : 2019-5-29 11:27:22
* @Version    : V1.0
*/
public class ProvService {
    private static Logger log = LogManager.getLogger(ProvService.class);

    /**
    *
    * @Title      : getAvailableNodes
    * @Description: Retrieve information about the available nodes
    * @Param      : @param json
    * @Param      : @return
    * @Return     : ProvResult
    * @Throws     :
     */
    public ProvResult getAvailableNodes(String json) {
        return new ProvResult(ProvCode.UNSUPPORTED_API);
    }

    /**
    *
    * @Title      : putAttr
    * @Description: Put attribute for template
    * @Param      : @param attr
    * @Param      : @param key
    * @Param      : @param type
    * @Param      : @param value
    * @Return     : void
    * @Throws     :
     */
    private void putAttr(Map<String, List<String>> attr, String key, String type, Object value) {
        if (null == value || StringUtils.isEmpty(type)) {
            return;
        }
        if (null == attr || StringUtils.isEmpty(key)) {
            return;
        }
        if (attr.containsKey(key)) {
            return;
        }

        List<String> values = new ArrayList<String>();
        values.add(type);
        values.add(String.valueOf(value));
        attr.put(key, values);
    }

    /**
    *
    * @Title      : nodeConfig
    * @Description: To get CC node configuration
    * @Param      : @param tpl
    * @Param      : @param pnc
    * @Param      : @return
    * @Return     : NodeConfig
    * @Throws     :
     */
    private NodeConfig nodeConfig(ProvTemplate tpl, ProvNodeCreation pnc) {
        LSFConfig lsfcfg = new LSFConfig(tpl);

        // The custom ENV will be set on Cycle Cloud for LSF user data
        Map<String, Object> customEnv = new LinkedHashMap<String, Object>();
        ProvUtil.put(customEnv, "clustername", System.getProperty("clusterName"));
        ProvUtil.put(customEnv, "providerName", System.getenv(ProvConst.PROV_NAME));
        ProvUtil.put(customEnv, ProvConst.TEMPLATE_ID, tpl.getTplId());
        ProvUtil.put(customEnv, ProvConst.RC_ACCOUNT, pnc.getRcAccount());

        Map<String, Object> userData = pnc.getUserData();
        if (MapUtils.isNotEmpty(userData)) {
            customEnv.putAll(userData);
        }
        String udStr = tpl.getUserData();
        if (StringUtils.isNotEmpty(udStr)) {
            for (String uds : udStr.split(";")) {
                String[] udary = uds.split("=", 2);
                ProvUtil.put(customEnv, udary[0], udary[1]);
            }
        }

        String envNames = ProvUtil.collectionToStr(customEnv.keySet());
        lsfcfg.setCustomEnvNames(envNames);
        lsfcfg.setCustomEnv(customEnv);

        NodeConfig config = new NodeConfig();
        config.setLsf(lsfcfg);
        return config;
    }

    /**
    *
    * @Title      : setTemplate
    * @Description: Set template
    * @Param      : @param tpl
    * @Param      : @param ary
    * @Param      : @param bucket
    * @Return     : void
    * @Throws     :
     */
    private void setTemplate(ProvTemplate tpl, CCNodeArrays ary, CCBucket bucket) {
        CCNodeArray nary = ary.getNarray();
        String tplId = ary.getName() + bucket.getDefinition().getMachineType();
        tplId = StringUtils.replace(tplId, "_", "").toLowerCase();

        tpl.setTplId(tplId);
        tpl.setMaxNum(bucket.getMaxCount());
        tpl.setAvailableNum(bucket.getMaxCount());
        tpl.setMtype(bucket.getDefinition().getMachineType());
        tpl.setNodeArray(ary.getName());
        tpl.setKeyPairLocation(nary.getKeypairLocation());
        tpl.setSubnetId(nary.getSubnetId());
        tpl.setPriority(ProvUtil.intValue(nary.getPriority()));
        tpl.setInitSpecs(nary.getClusterInitSpecs());

        LSFConfig lsfcfg = nary.getConfig().getLsf();
        if (null != lsfcfg) {
            tpl.setLsfEnvDir(lsfcfg.getLocalEtc());
            tpl.setLsfTop(lsfcfg.getLsfTop());
            tpl.setAutoScale(lsfcfg.getAutoScale());
            tpl.setHeadless(lsfcfg.getHeadless());
            tpl.setSkipModify(lsfcfg.getSkipModify());
            tpl.setNumPGroups(lsfcfg.getNumPGroups());
        }

        Azure azure = nary.getAzure();
        if (null != azure && null != azure.getMaxScalesetSize()) {
            tpl.setMaxNum(Math.min(bucket.getMaxCount(), azure.getMaxScalesetSize()));
        }

        HashMap<String, List<String>> tattr = tpl.getAttributes();
        putAttr(tattr, ProvConst.ZONE, String.class.getSimpleName(), nary.getRegion());

        if (null != bucket.getVm()) {
            CCVirtMachine vm = bucket.getVm();
            putAttr(tattr, "mem", ProvConst.NUMERIC, vm.getMemory());
            putAttr(tattr, "ncpus", ProvConst.NUMERIC, vm.getVcpuCount());
            putAttr(tattr, "ncores", ProvConst.NUMERIC, vm.getVcpuCount());
        }
    }

    /**
    *
    * @Title      : bucket
    * @Description: Match the machine type in template to get bucket
    * @Param      : @param buckets
    * @Param      : @param tpl
    * @Param      : @return
    * @Return     : CCBucket
    * @Throws     :
     */
    private CCBucket bucket(List<CCBucket> buckets, ProvTemplate tpl) {
        CCBucket bucket = null;
        if (CollectionUtils.isEmpty(buckets)) {
            return bucket;
        }

        bucket = buckets.get(0);
        if (StringUtils.isBlank(tpl.getMtype())) {
            return bucket;
        }

        String mtype = StringUtils.EMPTY;
        for (CCBucket bkt : buckets) {
            if (null == bkt.getDefinition()) {
                continue;
            }

            mtype = bkt.getDefinition().getMachineType();
            if (StringUtils.isBlank(mtype)) {
                continue;
            }

            if (mtype.equalsIgnoreCase(tpl.getMtype())) {
                bucket = bkt;
                break;
            }
        }
        return bucket;
    }

    /**
    *
    * @Title      : nodeArray
    * @Description: Match the node array name in template to get node array
    * @Param      : @param nodeArrays
    * @Param      : @param tpl
    * @Param      : @param tidx
    * @Param      : @return
    * @Return     : CCNodeArrays
    * @Throws     :
     */
    private CCNodeArrays nodeArray(List<CCNodeArrays> nodeArrays, ProvTemplate tpl, int tidx) {
        CCNodeArrays nary = null;
        if (CollectionUtils.isEmpty(nodeArrays)) {
            return nary;
        }

        nary = nodeArrays.get(tidx);
        if (StringUtils.isBlank(tpl.getNodeArray())) {
            return nary;
        }

        for (CCNodeArrays nodeArray : nodeArrays) {
            if (StringUtils.isBlank(nodeArray.getName())) {
                continue;
            }

            if (nodeArray.getName().equalsIgnoreCase(tpl.getNodeArray())) {
                nary = nodeArray;
                break;
            }
        }
        return nary;
    }

    /**
    *
    * @Title      : templates
    * @Description: Get templates from local file
    * @Param      : @return
    * @Param      : @throws ProvException
    * @Return     : List<ProvTemplate>
    * @Throws     :
     */
    private List<ProvTemplate> templates() throws ProvException {
        File jf = new File(ProvUtil.getConfDir() + ProvConst.JSON_CC_TPL);
        if (!jf.exists()) {
            throw new ProvException(ProvCode.NOT_EXIST_FILE, jf.getPath());
        }

        ProvResult result = ProvUtil.toObject(jf, ProvResult.class);
        if (null == result || CollectionUtils.isEmpty(result.getTemplates())) {
            throw new ProvException(ProvCode.BAD_JSON_FILE, jf.getPath());
        }

        ProvApi api = ProvUtil.getApi(ApiCode.CLUSTER_QUERY);
        Response rep = null;
        try {
            rep = ProvUtil.httpGet(api.getUrl());
        } catch(ProvException e) {
            log.error("Failed to get cluster information from the cycle cloud. Some parameters cannot be refreshed to the default template.");
            return result.getTemplates();
        }

        String body = ProvUtil.verify(rep);
        CCCluster cluster = ProvUtil.toObject(body, CCCluster.class);
        if (null == cluster) {
            throw new ProvException(ProvStatus.COMPLETE_WITH_ERR, ProvCode.UNRECOGNIZED_CC_RESPONSE);
        }

        if (CollectionUtils.isEmpty(cluster.getNodeArrays())) {
            return result.getTemplates();
        }

        int tidx = 0;
        int tsize = cluster.getNodeArrays().size();
        for (ProvTemplate tpl : result.getTemplates()) {
            CCNodeArrays ary = this.nodeArray(cluster.getNodeArrays(), tpl, tidx);

            tidx++;
            if (tidx >= tsize) {
                tidx = 0;
            }

            List<CCBucket> buckets = ary.getBuckets();
            if (CollectionUtils.isEmpty(buckets)) {
                continue;
            }

            CCBucket bucket = this.bucket(buckets, tpl);
            this.setTemplate(tpl, ary, bucket);
        }
        return result.getTemplates();
    }

    /**
    *
    * @Title      : getAvailableTemplates
    * @Description: Retrieve information about the available templates
    * @Param      : @param json
    * @Param      : @return
    * @Param      : @throws ProvException
    * @Return     : ProvResult
    * @Throws     :
     */
    public ProvResult getAvailableTemplates(String json) throws ProvException {
        ProvResult result = new ProvResult();
        List<ProvTemplate> tpls = this.templates();
        for (ProvTemplate tpl : tpls) {
            tpl.hide();
        }
        result.setTemplates(tpls);
        return result;
    }

    /**
    *
    * @Title      : getReturnRequests
    * @Description: Retrieve the nodes that the host provider wants them back
    * @Param      : @param json
    * @Param      : @return
    * @Param      : @throws ProvException
    * @Return     : ProvResult
    * @Throws     :
     */
    public ProvResult getReturnRequests(String json) throws ProvException {
        if (StringUtils.isEmpty(json)) {
            throw new ProvException(ProvCode.EMPTY);
        }
        ProvNodes nodes = ProvUtil.toObject(json, ProvNodes.class);
        if (null == nodes) {
            throw new ProvException(ProvCode.UNRECOGNIZED_JSON, json);
        }

        List<ProvReq> reqs = new ArrayList<ProvReq>();
        ProvResult result = new ProvResult(ProvCode.OK);
        result.setReqs(reqs);
        if (CollectionUtils.isEmpty(nodes.getNodes())) {
            return result;
        }

        ProvApi api = ProvUtil.getApi(ApiCode.NODE_QUERY_ALL);
        Response rep = ProvUtil.httpGet(api.getUrl());
        String body = ProvUtil.verify(rep);
        CCNodes cnodes = ProvUtil.toObject(body, CCNodes.class);
        if (null == cnodes) {
            throw new ProvException(ProvStatus.COMPLETE_WITH_ERR, ProvCode.UNRECOGNIZED_CC_RESPONSE);
        }

        Map<String, CCNode> cnodeMap = new LinkedHashMap<String, CCNode>();
        for (CCNode cnode : cnodes.getNodes()) {
            cnodeMap.put(cnode.getNodeId(), cnode);
        }

        List<ProvNode> tnodes = new ArrayList<ProvNode>();
        for (ProvNode pnode : nodes.getNodes()) {
            ProvReq req = new ProvReq();
            req.setNodeId(pnode.getNodeId());
            req.setNodeName(pnode.getName());

            // The node does not exist in Cycle Cloud
            CCNode cnode = cnodeMap.get(pnode.getNodeId());
            if (null == cnode) {
                reqs.add(req);
                continue;
            }

            // The node has been terminated from Cycle Cloud
            if (CCNodeState.isTerminated(cnode.getState())) {
                reqs.add(req);
                continue;
            }

            // Will terminate the failed node
            if (CCNodeState.isFailed(cnode.getState())) {
                tnodes.add(pnode);
            }
        }

        // Terminate the failed node
        this.terminate(tnodes);
        return result;
    }

    /**
    *
    * @Title      : getNodes
    * @Description: Get nodes by request ID
    * @Param      : @param reqId
    * @Param      : @return
    * @Param      : @throws ProvException
    * @Return     : CCNodes
    * @Throws     :
     */
    private CCNodes getNodes(String reqId) throws ProvException {
        ProvApi api = ProvUtil.getApi(ApiCode.NODE_QUERY_BY_REQ_ID, reqId);
        Response rep = ProvUtil.httpGet(api.getUrl());
        String body = ProvUtil.verify(rep);
        CCNodes nodes = ProvUtil.toObject(body, CCNodes.class);
        if (null == nodes) {
            throw new ProvException(ProvStatus.COMPLETE_WITH_ERR, ProvCode.UNRECOGNIZED_CC_RESPONSE);
        }
        return nodes;
    }

    /**
    *
    * @Title      : createNode
    * @Description: Create nodes from the cycle cloud
    * @Param      : @param json
    * @Param      : @return
    * @Param      : @throws ProvException
    * @Return     : ProvResult
    * @Throws     :
     */
    public ProvResult createNode(String json) throws ProvException {
        if (StringUtils.isEmpty(json)) {
            throw new ProvException(ProvCode.EMPTY);
        }
        ProvNodeCreation pnc = ProvUtil.toObject(json, ProvNodeCreation.class);
        if (null == pnc) {
            throw new ProvException(ProvCode.UNRECOGNIZED_JSON, json);
        }
        if (null == pnc.getTemplate()) {
            throw new ProvException(ProvCode.EMPTY);
        }

        ProvTemplate tpl = pnc.getTemplate();
        Integer mcount = tpl.getMcount();
        if (null == mcount || mcount < 1) {
            throw new ProvException(ProvCode.BAD_NODE_COUNT);
        }

        // Find template
        ProvTemplate targetTpl = null;
        List<ProvTemplate> tpls = this.templates();
        for (ProvTemplate pt : tpls) {
            if (tpl.getTplId().equals(pt.getTplId())) {
                targetTpl = pt;
                break;
            }
        }
        if (null == targetTpl) {
            throw new ProvException(ProvCode.NOT_EXIST_TEMPLATE, tpl.getTplId());
        }

        NodeSet set = new NodeSet();
        set.setNodeArray(targetTpl.getNodeArray());
        set.setDefinition(new NodeDefinition(targetTpl.getMtype()));
        set.setpGrpId(targetTpl.getpGrpName());
        set.setCount(mcount);

        // Add tags for the node
        String rcAccount = pnc.getRcAccount();
        Map<String, Object> tags = new LinkedHashMap<String, Object>();
        ProvUtil.put(tags, "rc_account", rcAccount);
        String tagStr = targetTpl.getTags();
        if (StringUtils.isNotEmpty(tagStr)) {
            for (String atag : tagStr.split(";")) {
                String[] tagAry = atag.split("=", 2);
                ProvUtil.put(tags, tagAry[0], tagAry[1]);
            }
        }

        NodeConfig config = this.nodeConfig(targetTpl, pnc);
        NodeAttribute attr = new NodeAttribute(targetTpl);
        attr.setTags(tags);
        attr.setConfig(config);
        attr.setRegion(ProvUtil.getConfig().getRegion());
        set.setAttributes(attr);

        List<NodeSet> sets = new ArrayList<NodeSet>();
        sets.add(set);

        NodeCreation creation = new NodeCreation(sets);
        String reqId = creation.getReqId();

        // Create nodes through CC API
        ProvApi api = ProvUtil.getApi(ApiCode.NODE_CREATION);
        Response rep = ProvUtil.httpPost(api.getUrl(), creation.toString());
        ProvUtil.verify(rep);

        // Get the node list from CC
        CCNodes nodes = this.getNodes(reqId);
        ProvReq req = new ProvReq(reqId, nodes.toProvNodes(rcAccount, targetTpl.getTplId()));
        req.setRcAccount(rcAccount);
        ProvUtil.saveProvData(req);
        return new ProvResult(ProvCode.OK, ProvStatus.RUNNING, reqId);
    }

    /**
    *
    * @Title      : terminate
    * @Description: Terminate nodes
    * @Param      : @param nodes
    * @Param      : @return
    * @Param      : @throws ProvException
    * @Return     : String
    * @Throws     :
     */
    private String terminate(List<ProvNode> nodes) throws ProvException {
        if (CollectionUtils.isEmpty(nodes)) {
            return StringUtils.EMPTY;
        }
        List<String> nodeIds = new ArrayList<String>();
        for (ProvNode node : nodes) {
            nodeIds.add(node.getNodeId());
        }

        NodeMgtReq req = new NodeMgtReq(nodeIds);
        String reqId = req.getReqId();

        try {
            ProvApi api = ProvUtil.getApi(ApiCode.NODE_TERMINATION);
            Response rep = ProvUtil.httpPost(api.getUrl(), req.toString());
            ProvUtil.verify(rep);
        } catch(ProvException e) {
            throw e;
        }

        return reqId;
    }

    /**
    *
    * @Title      : terminateNode
    * @Description: Return nodes back to the cycle cloud.
    * @Param      : @param json
    * @Param      : @return
    * @Param      : @throws ProvException
    * @Return     : ProvResult
    * @Throws     :
     */
    public ProvResult terminateNode(String json) throws ProvException {
        if (StringUtils.isEmpty(json)) {
            throw new ProvException(ProvCode.EMPTY);
        }
        ProvNodes nodes = ProvUtil.toObject(json, ProvNodes.class);
        if (null == nodes) {
            throw new ProvException(ProvCode.UNRECOGNIZED_JSON, json);
        }
        if (CollectionUtils.isEmpty(nodes.getNodes())) {
            throw new ProvException(ProvCode.EMPTY);
        }

        String reqId = this.terminate(nodes.getNodes());

        Map<String, ProvNode> pnodes = ProvUtil.createdNodes();
        ProvReq req = new ProvReq(reqId, nodes.getNodes());
        req.updateNodes(pnodes);
        ProvUtil.saveProvData(req);
        return new ProvResult(ProvCode.OK, ProvStatus.COMPLETE, reqId);
    }

    /**
    *
    * @Title      : nodeCreationStatus
    * @Description: Node creation status
    * @Param      : @param nodes
    * @Param      : @param req
    * @Param      : @return
    * @Return     : List<ProvNode>
    * @Throws     :
     */
    private List<ProvNode> nodeCreationStatus(List<CCNode> nodes, ProvReq req) {
        String reqId = req.getReqId();
        Long launchTime = ProvUtil.launchTime(reqId);
        if (null == launchTime) {
            launchTime = 0l;
        }
        launchTime = launchTime / ProvConst.ONE_SECOND;

        ProvReq rq = ProvUtil.getProvData(reqId);
        Map<String, ProvNode> dbNodes = new LinkedHashMap<String, ProvNode>();
        if (null != rq) {
            req.setRcAccount(rq.getRcAccount());
            if (CollectionUtils.isNotEmpty(rq.getNodes())) {
                for (ProvNode pnode : rq.getNodes()) {
                    pnode.setLaunchTime(launchTime);
                    dbNodes.put(pnode.getNodeId(), pnode);
                }
            }
        }

        // The request machines does not exist in CC
        List<ProvNode> pnodes = new ArrayList<ProvNode>();
        if (CollectionUtils.isEmpty(nodes)) {
            req.setRequest(ProvCode.NOT_EXIST_NODE);
            for (ProvNode pnode : dbNodes.values()) {
                pnode.setNode(NodeResult.FAILED, NodeStatus.DELETED);
                pnodes.add(pnode);
            }

            // Update provider data to DB file
            req.setNodes(pnodes);
            ProvUtil.updateProvData(req);
            return pnodes;
        }

        boolean timeout = ProvUtil.creationTimeout(reqId);
        String nodeState = StringUtils.EMPTY;
        // Termination node list
        List<ProvNode> tnodes = new ArrayList<ProvNode>();
        for (CCNode node : nodes) {
            ProvNode pnode = new ProvNode(node);
            pnode.setLaunchTime(launchTime);
            pnodes.add(pnode);
            dbNodes.remove(pnode.getNodeId());
            nodeState = node.getState();

            // Failed
            if (CCNodeState.isFailed(nodeState)) {
                pnode.setNode(NodeResult.FAILED, NodeStatus.ERROR, ProvCode.ERR_NODE_CREATION);
                if (!ProvStatus.RUNNING.value().equals(req.getStatus())) {
                    req.setRequest(ProvCode.ERR_CC_NODE);
                }
                tnodes.add(pnode);
                continue;
            }

            // Started
            if (CCNodeState.isStarted(nodeState)) {
                // PrivateIp and NodeId are ready
                if (StringUtils.isNotEmpty(node.getPrivateIp()) && StringUtils.isNotEmpty(node.getNodeId())) {
                    pnode.setNode(NodeResult.SUCCEED, NodeStatus.ACTIVE);
                    continue;
                }
            }

            // Node creation timeout
            if (timeout) {
                pnode.setNode(NodeResult.FAILED, NodeStatus.CREATION_TIMEOUT, ProvCode.CREATION_TIMEOUT);
                tnodes.add(pnode);
                continue;
            }

            // Executing
            pnode.setNode(NodeResult.EXECUTING, NodeStatus.BUILDING);
            req.setStatus(ProvStatus.RUNNING.value());
        }

        // Some machines do not exist in CC
        if (MapUtils.isNotEmpty(dbNodes)) {
            if (!ProvStatus.RUNNING.value().equals(req.getStatus())) {
                req.setRequest(ProvCode.NOT_EXIST_NODE);
            }
            for (ProvNode pnode : dbNodes.values()) {
                pnode.setNode(NodeResult.FAILED, NodeStatus.DELETED);
                pnodes.add(pnode);
            }
        }

        // Creation request timeout
        if (timeout) {
            req.setRequest(ProvCode.CREATION_TIMEOUT);
        }

        // Terminate the nodes in failed or timeout state
        try {
            this.terminate(tnodes);
        } catch(Exception e) {
            log.error("Failed to terminate nodes.", e);
        }

        // Update provider data to DB file
        req.setNodes(pnodes);
        ProvUtil.updateProvData(req);
        return pnodes;
    }

    /**
    *
    * @Title      : nodeTerminationStatus
    * @Description: Node termination status
    * @Param      : @param cnodes
    * @Param      : @param req
    * @Param      : @return
    * @Return     : List<ProvNode>
    * @Throws     :
     */
    private List<ProvNode> nodeTerminationStatus(List<CCNode> cnodes, ProvReq req) {
        String reqId = req.getReqId();
        List<ProvNode> pnodes = new ArrayList<ProvNode>();
        ProvReq rq = ProvUtil.getProvData(reqId);

        // The nodes do not exist in Cycle Cloud
        if (CollectionUtils.isEmpty(cnodes)) {
            req.setRequest(ProvCode.OK);
            if (null == rq || CollectionUtils.isEmpty(rq.getNodes())) {
                return pnodes;
            }

            for (ProvNode pnode : rq.getNodes()) {
                pnode.setNode(NodeResult.SUCCEED, NodeStatus.DELETED);
            }
            pnodes.addAll(rq.getNodes());
            return pnodes;
        }

        Map<String, ProvNode> nodeMap = new LinkedHashMap<String, ProvNode>();
        if (null != rq && CollectionUtils.isNotEmpty(rq.getNodes())) {
            for (ProvNode pnode : rq.getNodes()) {
                nodeMap.put(pnode.getNodeId(), pnode);
            }
        }

        boolean timeout = ProvUtil.terminationTimeout(reqId);
        List<ProvNode> tnodes = new ArrayList<ProvNode>();
        for (CCNode cnode : cnodes) {
            ProvNode pnode = new ProvNode(cnode);

            // Fill with the cached node
            if (nodeMap.containsKey(pnode.getNodeId())) {
                ProvNode node = nodeMap.get(pnode.getNodeId());
                pnode.setNode(node);
            }

            // Termination timeout
            if (timeout) {
                pnode.setNode(NodeResult.FAILED, NodeStatus.TERMINATION_TIMEOUT, ProvCode.TERMINATION_TIMEOUT);
            } // Terminated
            else if (CCNodeState.isTerminated(cnode.getState())) {
                pnode.setNode(NodeResult.SUCCEED, NodeStatus.DELETED);
            } // Failed, will retry to terminate
            else if (CCNodeState.isFailed(cnode.getState())) {
                pnode.setNode(NodeResult.EXECUTING, NodeStatus.DELETING);
                req.setStatus(ProvStatus.RUNNING.value());
                tnodes.add(pnode);
            } // Terminating
            else {
                pnode.setNode(NodeResult.EXECUTING, NodeStatus.DELETING);
                req.setStatus(ProvStatus.RUNNING.value());
            }
            pnodes.add(pnode);
        }

        if (timeout) {
            req.setRequest(ProvCode.TERMINATION_TIMEOUT);
        }

        try {
            // Retry to terminate the failed nodes
            this.terminate(tnodes);
        } catch(Exception e) {
            log.error("Failed to terminate nodes " + tnodes + ", will retry.", e);
        }

        return pnodes;
    }

    /**
    *
    * @Title      : getRequestStatus
    * @Description: Retrieve status of nodes creation and return
    * @Param      : @param json
    * @Param      : @return
    * @Param      : @throws ProvException
    * @Return     : ProvResult
    * @Throws     :
     */
    public ProvResult getRequestStatus(String json) throws ProvException {
        if (StringUtils.isEmpty(json)) {
            throw new ProvException(ProvCode.EMPTY);
        }
        ProvReqs prs = ProvUtil.toObject(json, ProvReqs.class);
        if (null == prs) {
            throw new ProvException(ProvCode.UNRECOGNIZED_JSON, json);
        }
        if (CollectionUtils.isEmpty(prs.getReqs())) {
            throw new ProvException(ProvCode.EMPTY);
        }

        String reqId = StringUtils.EMPTY;
        CCNodes nodes = null;
        List<ProvNode> pnodes = null;
        ProvResult result = new ProvResult();
        result.setReqs(new ArrayList<ProvReq>());

        for (ProvReq pr : prs.getReqs()) {
            reqId = pr.getReqId();
            try {
                nodes = this.getNodes(reqId);
            } catch(ProvException e) {
                result.getReqs().add(new ProvReq(reqId, e.message(), e.status()));
                continue;
            }

            // Set the request and node status
            ProvReq req = new ProvReq(reqId, ProvCode.OK);
            if (ProvUtil.isCreationReq(reqId)) {
                pnodes = this.nodeCreationStatus(nodes.getNodes(), req);
            } else if (ProvUtil.isTerminationReq(reqId)) {
                pnodes = this.nodeTerminationStatus(nodes.getNodes(), req);
            } else {
                req.setRequest(ProvCode.UNSUPPORTED_REQ);
            }
            req.setNodes(pnodes);
            result.getReqs().add(req);
        }

        return result;
    }

}
