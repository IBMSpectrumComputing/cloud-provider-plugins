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

package com.ibm.spectrum.cyclecloud.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.spectrum.cyclecloud.constant.ProvConst;
import com.ibm.spectrum.cyclecloud.enums.ApiCode;
import com.ibm.spectrum.cyclecloud.enums.HttpMethod;
import com.ibm.spectrum.cyclecloud.enums.ProvCode;
import com.ibm.spectrum.cyclecloud.enums.ProvStatus;
import com.ibm.spectrum.cyclecloud.model.ProvApi;
import com.ibm.spectrum.cyclecloud.model.ProvApis;
import com.ibm.spectrum.cyclecloud.model.ProvConfig;
import com.ibm.spectrum.cyclecloud.model.ProvException;
import com.ibm.spectrum.cyclecloud.model.ProvMsg;
import com.ibm.spectrum.cyclecloud.model.ProvMsgs;
import com.ibm.spectrum.cyclecloud.model.ProvNode;
import com.ibm.spectrum.cyclecloud.model.ProvReq;
import com.ibm.spectrum.cyclecloud.model.ProvReqs;
import com.ibm.spectrum.cyclecloud.model.ProvResult;
import com.ibm.spectrum.cyclecloud.model.cc.CCStatus;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
* @Class Name : ProvUtil
* @Description: Cycle Cloud Provider Utilities
* @Author     : Yudong (Dom) Wang
* @Date       : 2019-5-29 11:27:22
* @Version    : V1.0
*/
public final class ProvUtil {
    private static Logger log = LogManager.getLogger(ProvUtil.class);

    private static ObjectMapper mapper = new ObjectMapper();
    static {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    /**
     * Cycle cloud configuration
     */
    private static ProvConfig config = null;

    /**
     * HTTP client
     */
    private static OkHttpClient httpClient = null;

    /**
     * Cycle cloud REST API list
     */
    private static Map<Integer, ProvApi> restApis = null;

    /**
     * Provider error message
     */
    private static Map<Integer, String> messages = null;

    /**
     * Provider usage
     */
    private static String usage = "";

    /**
     * Work directory
     */
    private static String dataDir = "";

    /**
     * Configuration directory
     */
    private static String confDir = "";

    /**
    * @Title      : ProvUtil
    * @Description: constructor
    * @Param      :
    */
    private ProvUtil() {
    }

    public static String getDataDir() {
        return dataDir;
    }

    public static String getConfDir() {
        return confDir;
    }

    public static File getDataFile() {
        String provName = System.getenv(ProvConst.PROV_NAME);
        File dbFile = new File(getDataDir() + provName + ProvConst.JSON_CC_DATA);
        return dbFile;
    }

    /**
    *
    * @Title      : getHostVerifier
    * @Description: Get host name verifier
    * @Param      : @return
    * @Return     : HostnameVerifier
    * @Throws     :
     */
    private static HostnameVerifier getHostVerifier() {
        HostnameVerifier hostnameVerifier = new HostnameVerifier() {
            public boolean verify(String s, SSLSession sslSession) {
                return true;
            }
        };
        return hostnameVerifier;
    }

    /**
    *
    * @Title      : getTrustManager
    * @Description: Get trust manager
    * @Param      : @return
    * @Return     : X509TrustManager
    * @Throws     :
     */
    private static X509TrustManager getTrustManager() {
        X509TrustManager tm = new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[] {};
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            }

            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            }
        };
        return tm;
    }

    /**
    *
    * @Title      : getSSLSocketFactory
    * @Description: Get SSL socket factory
    * @Param      : @return
    * @Return     : SSLSocketFactory
    * @Throws     :
     */
    private static SSLSocketFactory getSSLSocketFactory() {
        try {
            SSLContext ctx = SSLContext.getInstance("SSL");
            ctx.init(null, new TrustManager[] { getTrustManager() }, new SecureRandom());
            return ctx.getSocketFactory();
        } catch(Exception e) {
            log.error("Failed to get SSL socket factory.", e);
        }
        return null;
    }

    /**
    *
    * @Title      : getClient
    * @Description: Get http client
    * @Param      : @return
    * @Return     : OkHttpClient
    * @Throws     :
     */
    private static OkHttpClient getClient() {
        if (null == httpClient) {
            httpClient = new OkHttpClient()
            .newBuilder()
            .connectTimeout(ProvConst.THIRTY_SECONDS, TimeUnit.SECONDS)
            .readTimeout(ProvConst.THIRTY_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(ProvConst.THIRTY_SECONDS, TimeUnit.SECONDS)
            .sslSocketFactory(getSSLSocketFactory(), getTrustManager())
            .hostnameVerifier(getHostVerifier())
            .addInterceptor(new AuthInterceptor()).build();
        }
        return httpClient;
    }

    /**
    *
    * @Title      : getServerUrl
    * @Description: Get server url
    * @Param      : @param url
    * @Param      : @return
    * @Return     : String
    * @Throws     :
     */
    private static String getServerUrl(String url) {
        return getConfig().getServer() + url;
    }

    /**
    *
    * @Title      : getConfig
    * @Description: Get cycle cloud configuration
    * @Param      : @return
    * @Return     : ProvConfig
    * @Throws     :
     */
    public static ProvConfig getConfig() {
        if (null == config) {
            config = ProvUtil.toObject(new File(confDir + ProvConst.JSON_CC_CONF), ProvConfig.class);
        }
        return config;
    }

    /**
    *
    * @Title      : getApi
    * @Description: Get REST API
    * @Param      : @param api
    * @Param      : @return
    * @Param      : @throws ProvException
    * @Return     : ProvApi
    * @Throws     :
     */
    public static ProvApi getApi(ApiCode api) throws ProvException {
        if (null == restApis) {
            String json = StringUtils.EMPTY;
            try {
                InputStream is = ProvApi.class.getClassLoader().getResourceAsStream(ProvConst.JSON_CC_API);
                json = IOUtils.toString(is, ProvConst.UTF_8);
                close(is);
            } catch(IOException e) {
                log.error("Failed to read file: " + ProvConst.JSON_CC_API, e);
                throw new ProvException(ProvCode.ERR_READ_FILE, ProvConst.JSON_CC_API);
            }
            ProvApis apis = ProvUtil.toObject(json, ProvApis.class);
            restApis = apis.getApis();
        }

        if (null == restApis || !restApis.containsKey(api.value())) {
            log.warn("Can not find the API with code {}", api.value());
            throw new ProvException(ProvCode.NOT_FOUND_API, api.value());
        }

        ProvApi restApi = restApis.get(api.value());
        restApi.setUrl(StringUtils.replace(restApi.getUrl(), ProvConst.PATH_CLUSTER, getConfig().getCluster()));
        return restApi;
    }

    /**
    *
    * @Title      : getApi
    * @Description: Get API and replace ID for URL
    * @Param      : @param api
    * @Param      : @param id
    * @Param      : @return
    * @Param      : @throws ProvException
    * @Return     : ProvApi
    * @Throws     :
     */
    public static ProvApi getApi(ApiCode api, String id) throws ProvException {
        ProvApi restApi = getApi(api);
        restApi.setUrl(StringUtils.replace(restApi.getUrl(), ProvConst.PATH_ID, id));
        return restApi;
    }

    /**
    *
    * @Title      : strValue
    * @Description: To string value
    * @Param      : @param value
    * @Param      : @return
    * @Return     : String
    * @Throws     :
     */
    public static String strValue(Object value) {
        if (null == value) {
            return null;
        }
        return String.valueOf(value);
    }

    /**
    *
    * @Title      : intValue
    * @Description: To integer value
    * @Param      : @param value
    * @Param      : @return
    * @Return     : Integer
    * @Throws     :
     */
    public static Integer intValue(Object value) {
        if (null == value) {
            return null;
        }
        String strValue = strValue(value);
        if (!StringUtils.isNumeric(strValue)) {
            return null;
        }
        return Integer.valueOf(strValue);
    }

    /**
    *
    * @Title      : boolValue
    * @Description: To boolean value
    * @Param      : @param value
    * @Param      : @return
    * @Return     : Boolean
    * @Throws     :
     */
    public static Boolean boolValue(Object value) {
        if (null == value) {
            return null;
        }
        return BooleanUtils.toBoolean(strValue(value));
    }

    /**
    *
    * @Title      : getMsg
    * @Description: Get provider message
    * @Param      : @param code
    * @Param      : @return
    * @Return     : ProvMsg
    * @Throws     :
     */
    public static ProvMsg getMsg(Integer code) {
        ProvMsg msg = new ProvMsg();
        if (null == messages) {
            String json = StringUtils.EMPTY;
            try {
                InputStream is = ProvUtil.class.getClassLoader().getResourceAsStream(ProvConst.JSON_CC_MSG);
                json = IOUtils.toString(is, ProvConst.UTF_8);
                close(is);
            } catch(IOException e) {
                log.error("Failed to read file: " + ProvConst.JSON_CC_MSG, e);
                return msg;
            }
            ProvMsgs msgs = toObject(json, ProvMsgs.class);
            messages = msgs.getMsgs();
        }

        if (null == messages || !messages.containsKey(code)) {
            log.warn("Can not find the message with code {}", code);
            return msg;
        }

        msg.setCode(code);
        msg.setMsg(messages.get(code));
        return msg;
    }

    /**
    *
    * @Title      : tojsonStr
    * @Description: Write object as json string
    * @Param      : @param instance
    * @Param      : @return
    * @Return     : String
    * @Throws     :
     */
    public static <T> String toJsonStr(T instance) {
        if (null == instance) {
            return StringUtils.EMPTY;
        }

        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(instance);
        } catch(Exception e) {
            log.error("Failed to write object [" + instance + "] as json string.", e);
        }
        return StringUtils.EMPTY;
    }

    /**
    *
    * @Title      : toJsonFile
    * @Description: Write object as json file
    * @Param      : @param jf
    * @Param      : @param instance
    * @Return     : void
    * @Throws     :
     */
    public static <T> void toJsonFile(File jf, T instance) {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(jf, instance);
        } catch(Exception e) {
            log.error("Failed to write object [" + instance + "] as json file [" + jf.getName() + "]", e);
        }
    }

    /**
    *
    * @Title      : toOject
    * @Description: Read json string as object
    * @Param      : @param json
    * @Param      : @param clas
    * @Param      : @return
    * @Return     : T
    * @Throws     :
     */
    public static <T> T toObject(String json, Class<T> clas) {
        if (null == json || null == clas) {
            return null;
        }

        try {
            return (T) mapper.readValue(json, clas);
        } catch(Exception e) {
            log.error("Failed to read json [" + json + "] as object.", e);
        }
        return null;
    }

    /**
    *
    * @Title      : toOject
    * @Description: Read input stream as object
    * @Param      : @param is
    * @Param      : @param clas
    * @Param      : @return
    * @Return     : T
    * @Throws     :
     */
    public static <T> T toObject(InputStream is, Class<T> clas) {
        if (null == is || null == clas) {
            return null;
        }

        try {
            return (T) mapper.readValue(is, clas);
        } catch(Exception e) {
            log.error("Failed to read input stream as object.", e);
        }
        return null;
    }

    /**
    *
    * @Title      : toOject
    * @Description: Read json file as object
    * @Param      : @param jf
    * @Param      : @param clas
    * @Param      : @return
    * @Return     : T
    * @Throws     :
     */
    public static <T> T toObject(File jf, Class<T> clas) {
        if (null == jf || null == clas) {
            return null;
        }

        if (!jf.exists()) {
            log.warn("The json file [{}] does not exist.", jf.getName());
            return null;
        }

        try {
            return (T) mapper.readValue(jf, clas);
        } catch(Exception e) {
            log.error("Failed to read json file [" + jf.getName() + "] as object.", e);
        }
        return null;
    }

    /**
    *
    * @Title      : close
    * @Description: Close input stream
    * @Param      : @param is
    * @Return     : void
    * @Throws     :
     */
    public static void close(InputStream is) {
        if (null == is) {
            return;
        }

        try {
            is.close();
        } catch(IOException e) {
            log.error("Failed to close input stream.", e);
        }
    }

    /**
    *
    * @Title      : randomID
    * @Description: Random ID
    * @Param      : @return
    * @Return     : String
    * @Throws     :
     */
    public static String randomID() {
        String randomID = UUID.randomUUID().toString();
        randomID = StringUtils.replace(randomID, "-", "");
        return randomID + "-" + System.currentTimeMillis();
    }

    /**
    *
    * @Title      : httpPost
    * @Description: HTTP post request
    * @Param      : @param url
    * @Param      : @param json
    * @Param      : @return
    * @Param      : @throws ProvException
    * @Return     : Response
    * @Throws     :
     */
    public static Response httpPost(String url, String json) throws ProvException {
        Response rep = null;
        RequestBody body = RequestBody.create(ProvConst.JSON, json);
        Request req = new Request.Builder().url(getServerUrl(url)).post(body).build();

        log.info("POST {}\n{}", url, json);
        try {
            rep = getClient().newCall(req).execute();
        } catch(Exception e) {
            log.error("HTTP post request failed: \n" + url + "\n" + json, e);
            throw new ProvException(ProvCode.ERR_CC_REQUEST, HttpMethod.POST.name(), url, json);
        }

        return rep;
    }

    /**
    *
    * @Title      : httpGet
    * @Description: HTTP get request
    * @Param      : @param url
    * @Param      : @return
    * @Param      : @throws ProvException
    * @Return     : Response
    * @Throws     :
     */
    public static Response httpGet(String url) throws ProvException {
        Response rep = null;
        Request req = new Request.Builder().url(getServerUrl(url)).get().build();

        log.info("GET {}", url);
        try {
            rep = getClient().newCall(req).execute();
        } catch(Exception e) {
            log.error("HTTP get request failed: \n" + url, e);
            throw new ProvException(ProvCode.ERR_CC_REQUEST, HttpMethod.GET.name(), url, "");
        }

        return rep;
    }

    /**
    *
    * @Title      : result
    * @Description: Get response result
    * @Param      : @param rep
    * @Param      : @return
    * @Param      : @throws ProvException
    * @Return     : String
    * @Throws     :
     */
    public static String verify(Response rep) throws ProvException {
        if (null == rep) {
            throw new ProvException(ProvCode.ERR_CC_RESPONSE);
        }

        String body = StringUtils.EMPTY;
        try {
            body = rep.body().string();
            log.info("Response body:\n{}", body);
        } catch(IOException e) {
            log.error("Failed to get response from cycle cloud.", e);
            throw new ProvException(ProvCode.ERR_CC_RESPONSE);
        }

        // HTTP response failed
        if (!rep.isSuccessful()) {
            CCStatus cs = toObject(body, CCStatus.class);
            if (null == cs) {
                throw new ProvException(ProvCode.UNRECOGNIZED_CC_RESPONSE);
            }
            throw new ProvException(cs.getCode(), cs.getMsg(), ProvStatus.WARN.value());
        }
        return body;
    }

    /**
    *
    * @Title      : isCreationReq
    * @Description: Check if it is creation request
    * @Param      : @param reqId
    * @Param      : @return
    * @Return     : boolean
    * @Throws     :
     */
    public static boolean isCreationReq(String reqId) {
        if (StringUtils.startsWith(reqId, ProvConst.REQ_ID_CREATE)) {
            return true;
        }
        return false;
    }

    /**
    *
    * @Title      : isTerminationReq
    * @Description: Check if it is termination request
    * @Param      : @param reqId
    * @Param      : @return
    * @Return     : boolean
    * @Throws     :
     */
    public static boolean isTerminationReq(String reqId) {
        if (StringUtils.startsWith(reqId, ProvConst.REQ_ID_DELETE)) {
            return true;
        }
        return false;
    }

    /**
    *
    * @Title      : getUsage
    * @Description: Get usage
    * @Param      : @return
    * @Return     : String
    * @Throws     :
     */
    public static String getUsage() {
        if (StringUtils.isNotEmpty(usage)) {
            return usage;
        }

        InputStream is = ProvUtil.class.getClassLoader().getResourceAsStream(ProvConst.PROV_USAGE);
        try {
            usage = IOUtils.toString(is, ProvConst.UTF_8);
        } catch(Exception e) {
            log.error("Failed to read file: " + ProvConst.PROV_USAGE, e);
        } finally {
            close(is);
        }

        return usage;
    }

    /**
    *
    * @Title      : exit
    * @Description: Exit with status
    * @Param      : @param pc
    * @Param      : @param messages
    * @Return     : void
    * @Throws     :
     */
    public static void exit(ProvCode pc, String... messages) {
        if (!ProvCode.OK.equals(pc)) {
            System.out.println(pc.message());
            log.error(pc.message());
        }

        if (null != messages) {
            for (String msg : messages) {
                System.out.println(msg);
            }
        }

        System.exit(pc.value());
    }

    /**
    *
    * @Title      : result
    * @Description: Print result and exit
    * @Param      : @param pr
    * @Return     : void
    * @Throws     :
     */
    public static void result(ProvResult pr) {
        String json = pr.toString();
        System.out.println(json);

        Integer status = pr.getCode();
        if (null == status) {
            status = 0;
        }

        if (0 != status.intValue()) {
            log.error("Result:\n{}", json);
        } else {
            log.info("Result:\n{}", json);
        }

        System.exit(status);
    }

    /**
    *
    * @Title      : result
    * @Description: Print result and exit
    * @Param      : @param pe
    * @Return     : void
    * @Throws     :
     */
    public static void result(ProvException pe) {
        ProvResult pr = new ProvResult(pe);
        result(pr);
    }

    /**
    *
    * @Title      : readFile
    * @Description: Read file
    * @Param      : @param pathName
    * @Param      : @return
    * @Param      : @throws ProvException
    * @Return     : String
    * @Throws     :
     */
    public static String readFile(String pathName) throws ProvException {
        String content = StringUtils.EMPTY;
        File file = new File(pathName);
        if (!file.exists()) {
            throw new ProvException(ProvCode.NOT_EXIST_FILE, pathName);
        }
        try {
            content = FileUtils.readFileToString(file, ProvConst.UTF_8);
        } catch(IOException e) {
            log.error("Failed to read file: " + pathName, e);
            throw new ProvException(ProvCode.ERR_READ_FILE, pathName);
        }
        return content;
    }

    /**
    *
    * @Title      : init
    * @Description: Initialize variables
    * @Param      : @throws ProvException
    * @Return     : void
    * @Throws     :
     */
    public static void init() throws ProvException {
        if (StringUtils.isEmpty(confDir)) {
            confDir = System.getenv(ProvConst.CONF_DIR);
            if (StringUtils.isEmpty(confDir)) {
                throw new ProvException(ProvCode.NULL_CONF_DIR, ProvConst.CONF_DIR);
            }
            confDir += File.separatorChar + ProvConst.CONF + File.separatorChar;
        }

        if (StringUtils.isEmpty(dataDir)) {
            dataDir = System.getenv(ProvConst.DATA_DIR);
            if (StringUtils.isEmpty(dataDir)) {
                throw new ProvException(ProvCode.NULL_DATA_DIR, ProvConst.DATA_DIR);
            }
            dataDir += File.separatorChar;
        }

        // Check if the configuration file exists
        File jf = new File(confDir + ProvConst.JSON_CC_CONF);
        if (!jf.exists()) {
            throw new ProvException(ProvCode.NOT_EXIST_FILE, jf.getPath());
        }

        setLogLevel();
    }

    /**
    *
    * @Title      : setLogLevel
    * @Description: Set log level
    * @Param      : @param levelName
    * @Return     : void
    * @Throws     :
     */
    public static void setLogLevel() {
        ProvConfig pc = getConfig();
        if (null == pc) {
            return;
        }

        String levelName = pc.getLogLevel();
        if (StringUtils.isBlank(levelName)) {
            log.warn("Empty log level specified by 'LogLevel'.");
            return;
        }

        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        LoggerConfig config = ctx.getConfiguration().getLoggerConfig(ProvConst.CC_LOG_PATH);
        Level level = Level.getLevel(levelName.toUpperCase());
        if (null == level) {
            log.warn("The log level '{}' is not recognized. Using the default '{}' instead.", levelName, config.getLevel());
            return;
        }

        config.setLevel(level);
        ctx.updateLoggers();
        log.debug("Log level is set to '{}'", levelName);
    }

    /**
    *
    * @Title      : collectionToStr
    * @Description: Collection to string
    * @Param      : @param collection
    * @Param      : @return
    * @Return     : String
    * @Throws     :
     */
    public static String collectionToStr(Collection<?> collection) {
        String cstr = collection.toString();
        cstr = StringUtils.remove(cstr, '[');
        cstr = StringUtils.remove(cstr, ']');
        cstr = StringUtils.remove(cstr, ',');
        return cstr;
    }

    /**
    *
    * @Title      : terminationTimeout
    * @Description: Termination timeout
    * @Param      : @param reqId
    * @Param      : @return
    * @Return     : boolean
    * @Throws     :
     */
    public static boolean terminationTimeout(String reqId) {
        Long launchTime = launchTime(reqId);
        if (null == launchTime) {
            return false;
        }

        Long timeout = ProvUtil.getConfig().getTerminationTimeout();
        long now = System.currentTimeMillis();
        if (now - launchTime >= timeout) {
            return true;
        }

        return false;
    }

    /**
    *
    * @Title      : creationTimeout
    * @Description: Creation timeout
    * @Param      : @param reqId
    * @Param      : @return
    * @Return     : boolean
    * @Throws     :
     */
    public static boolean creationTimeout(String reqId) {
        Long launchTime = launchTime(reqId);
        if (null == launchTime) {
            return false;
        }

        Long timeout = ProvUtil.getConfig().getCreationTimeout();
        long now = System.currentTimeMillis();
        if (now - launchTime >= timeout) {
            return true;
        }

        return false;
    }

    /**
    *
    * @Title      : put
    * @Description: Put key/value to map
    * @Param      : @param map
    * @Param      : @param key
    * @Param      : @param value
    * @Return     : void
    * @Throws     :
     */
    public static void put(Map<String, Object> map, String key, Object value) {
        if (null == map || StringUtils.isBlank(key) || null == value) {
            return;
        }
        map.put(key, value);
    }

    /**
    *
    * @Title      : fieldNames
    * @Description: Get field names
    * @Param      : @param clas
    * @Param      : @return
    * @Return     : List<String>
    * @Throws     :
     */
    public static List<String> fieldNames(Class<?> clas) {
        List<String> names = new ArrayList<String>();
        if (null == clas) {
            return names;
        }
        for (Field field : clas.getDeclaredFields()) {
            JsonProperty jp = field.getAnnotation(JsonProperty.class);
            if (null == jp) {
                names.add(field.getName());
                continue;
            }
            names.add(jp.value());
        }
        return names;
    }

    /**
    *
    * @Title      : exec
    * @Description: Execute command
    * @Param      : @param cmd
    * @Param      : @return
    * @Return     : String
    * @Throws     :
     */
    public static String exec(String cmd) {
        String retStr = StringUtils.EMPTY;
        try {
            Process ps = Runtime.getRuntime().exec(cmd);
            retStr = IOUtils.toString(ps.getInputStream(), ProvConst.UTF_8);
        } catch(Exception e) {
            log.error("Failed to execute command: " + cmd, e);
        }
        return retStr;
    }

    /**
    *
    * @Title      : hostName
    * @Description: Get host name
    * @Param      : @param addr
    * @Param      : @return
    * @Return     : String
    * @Throws     :
     */
    public static String hostName(String addr) {
        if (StringUtils.isEmpty(addr)) {
            return addr;
        }

        String retStr = exec(ProvConst.CMD_GETENT + addr);
        if (StringUtils.isBlank(retStr)) {
            return addr;
        }

        retStr = StringUtils.trim(retStr);
        retStr = retStr.replaceAll("\\s{1,}", " ");
        String[] addrs = retStr.split(" ");
        if (addrs.length > 1) {
            return StringUtils.lowerCase(addrs[1]);
        }

        return addr;
    }

    /**
    *
    * @Title      : launchTime
    * @Description: Get launch time
    * @Param      : @param reqId
    * @Param      : @return
    * @Return     : Long
    * @Throws     :
     */
    public static Long launchTime(String reqId) {
        Long launchTime = null;
        String timeStr = StringUtils.substringAfterLast(reqId, "-");
        if (StringUtils.isNotBlank(timeStr) && StringUtils.isNumeric(timeStr)) {
            launchTime = Long.valueOf(timeStr);
        }
        return launchTime;
    }

    /**
    *
    * @Title      : isExpired
    * @Description: Check if the provider data expired
    * @Param      : @param reqId
    * @Param      : @return
    * @Return     : boolean
    * @Throws     :
     */
    public static boolean isExpired(String reqId) {
        Long launchTime = launchTime(reqId);
        if (null == launchTime) {
            return false;
        }

        Long now = System.currentTimeMillis();
        if (now - launchTime >= ProvConst.ONE_DAY) {
            return true;
        }

        return false;
    }

    /**
    *
    * @Title      : getProvData
    * @Description: Get request data by ID
    * @Param      : @param reqId
    * @Param      : @return
    * @Return     : ProvReq
    * @Throws     :
     */
    public static ProvReq getProvData(String reqId) {
        ProvReq targetReq = null;
        File df = getDataFile();
        ProvReqs reqs = ProvUtil.toObject(df, ProvReqs.class);
        if (null == reqs || CollectionUtils.isEmpty(reqs.getReqs())) {
            return targetReq;
        }

        for (ProvReq req : reqs.getReqs()) {
            if (reqId.equals(req.getReqId())) {
                targetReq = req;
                break;
            }
        }
        return targetReq;
    }

    /**
    *
    * @Title      : saveProvData
    * @Description: Save request data
    * @Param      : @param req
    * @Return     : void
    * @Throws     :
     */
    public static void saveProvData(ProvReq req) {
        if (null == req) {
            return;
        }

        File df = getDataFile();
        ProvReqs reqs = ProvUtil.toObject(df, ProvReqs.class);
        if (null == reqs) {
            reqs = new ProvReqs();
        }

        List<ProvReq> reqList = new ArrayList<ProvReq>();
        reqList.add(req);

        for (ProvReq rq : reqs.getReqs()) {
            // Ignore the expired data
            if (isExpired(rq.getReqId())) {
                continue;
            }
            reqList.add(rq);
        }

        reqs.setReqs(reqList);
        ProvUtil.toJsonFile(df, reqs);
    }

    /**
    *
    * @Title      : updateProvData
    * @Description: Update request data
    * @Param      : @param req
    * @Return     : void
    * @Throws     :
     */
    public static void updateProvData(ProvReq req) {
        if (null == req) {
            return;
        }

        File df = getDataFile();
        ProvReqs reqs = ProvUtil.toObject(df, ProvReqs.class);
        if (null == reqs) {
            reqs = new ProvReqs();
        }

        ProvReq targetReq = null;
        for (ProvReq rq : reqs.getReqs()) {
            if (req.getReqId().equals(rq.getReqId())) {
                targetReq = rq;
                break;
            }
        }

        if (null == targetReq) {
            return;
        }

        targetReq.updateRequest(req);
        ProvUtil.toJsonFile(df, reqs);
    }

    /**
    *
    * @Title      : createdNodes
    * @Description: Get created nodes
    * @Param      : @return
    * @Return     : Map<String,ProvNode>
    * @Throws     :
     */
    public static Map<String, ProvNode> createdNodes() {
        File df = getDataFile();
        ProvReqs reqs = ProvUtil.toObject(df, ProvReqs.class);
        if (null == reqs) {
            reqs = new ProvReqs();
        }

        Map<String, ProvNode> pnodes = new LinkedHashMap<String, ProvNode>();
        for (ProvReq rq : reqs.getReqs()) {
            if (!isCreationReq(rq.getReqId())) {
                continue;
            }
            if (CollectionUtils.isEmpty(rq.getNodes())) {
                continue;
            }
            for (ProvNode pnode : rq.getNodes()) {
                pnodes.put(pnode.getNodeId(), pnode);
            }
        }
        return pnodes;
    }

    /**
    *
    * @Title      : terminatedNodes
    * @Description: Get terminated nodes
    * @Param      : @return
    * @Return     : Map<String,ProvNode>
    * @Throws     :
     */
    public static Map<String, ProvNode> terminatedNodes() {
        File df = getDataFile();
        ProvReqs reqs = ProvUtil.toObject(df, ProvReqs.class);
        if (null == reqs) {
            reqs = new ProvReqs();
        }

        Map<String, ProvNode> pnodes = new LinkedHashMap<String, ProvNode>();
        for (ProvReq rq : reqs.getReqs()) {
            if (!isTerminationReq(rq.getReqId())) {
                continue;
            }
            if (CollectionUtils.isEmpty(rq.getNodes())) {
                continue;
            }
            for (ProvNode pnode : rq.getNodes()) {
                pnodes.put(pnode.getNodeId(), pnode);
            }
        }
        return pnodes;
    }

}
