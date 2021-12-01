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


package com.ibm.spectrum;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.UserPrincipal;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import com.ibm.spectrum.constant.GcloudConst;
import com.ibm.spectrum.gcloud.GcloudImpl;
import com.ibm.spectrum.gcloud.IGcloud;
import com.ibm.spectrum.model.GcloudConfig;
import com.ibm.spectrum.model.GcloudEntity;
import com.ibm.spectrum.util.GcloudUtil;

/**
* @ClassName: GcloudMain
* @Description: The main of Google Cloud host provider
* @author zcg
* @date Sep 11, 2017
* @version 1.0
*/
public class GcloudMain {
    private static Logger log = LogManager.getLogger(GcloudMain.class);

    private static final String LOGGER_NAME = "com.ibm.spectrum";

    private static final String LOG_APPENDER_NAME = "GCLOUD_FILE_APPEND";

    /**
    *
    * @Title: showHelp
    * @Description: Show help
    * @param
    * @return void
    * @throws
     */
    public static void showHelp() {
        StringBuilder b = new StringBuilder();

        b.append("Invalid operation. For example:").append("\n")
        .append("java -jar GcloudTool.jar [-l or --getAvailableMachines] [Home Dir] [Json File]").append("\n")
        .append("java -jar GcloudTool.jar [-t or --getAvailableTemplates] [Home Dir] [Json File]").append("\n")
        .append("java -jar GcloudTool.jar [-q or --getReturnRequests] [Home Dir] [Json File]").append("\n")
        .append("java -jar GcloudTool.jar [-n or --requestMachines] [Home Dir] [Json File]").append("\n")
        .append("java -jar GcloudTool.jar [-r or --requestReturnMachines] [Home Dir] [Json File]").append("\n")
        .append("java -jar GcloudTool.jar [-s or --getRequestStatus] [Home Dir] [Json File]");

        log.info(b.toString());

        System.out.println(b.toString());
    }

    /**
    *
    *
    * @Title: getMethodName
    * @Description: get  method name
    * @param @param opt
    * @param @return
    * @return String
    * @throws
     */
    public static String getMethodName(String opt) {
        String mName = "";
        if ("-l".equals(opt) || "--getAvailableMachines".equals(opt)) {
            mName = "getAvailableMachines";
            return mName;
        }

        if ("-t".equals(opt) || "--getAvailableTemplates".equals(opt)) {
            mName = "getAvailableTemplates";
            return mName;
        }

        if ("-q".equals(opt) || "--getReturnRequests".equals(opt)) {
            mName = "getReturnRequests";
            return mName;
        }

        if ("-n".equals(opt) || "--requestMachines".equals(opt)) {
            mName = "requestMachines";
            return mName;
        }

        if ("-r".equals(opt) || "--requestReturnMachines".equals(opt)) {
            mName = "requestReturnMachines";
            return mName;
        }

        if ("-s".equals(opt) || "--getRequestStatus".equals(opt)) {
            mName = "getRequestStatus";
            return mName;
        }

        return mName;
    }

    /**
    *
    * @Title: call
    * @Description: call Gcloud method
    * @param  jsonPath
    * @param  mName
    * @return String
    * @throws
    */
    public static Integer call(File jf, String mName) {
        IGcloud gcloud = new GcloudImpl();
        Integer code = 1;
        GcloudEntity req = null;

        try {
            if (null == jf) {
                req = new GcloudEntity();
            } else {
                req = GcloudUtil.toObject(jf, GcloudEntity.class);
            }

            log.info("Call method: [" + mName + "] begin, request: " + req);

            Method m = gcloud.getClass().getMethod(mName, GcloudEntity.class);
            GcloudEntity rsp = (GcloudEntity)m.invoke(gcloud, req);

            log.info("Call method: [" + mName + "] end, response: " + rsp);

            code  = rsp.getCode();
            String jsonTxt = GcloudUtil.toJsonTxt(rsp);
            System.out.println(jsonTxt);
        } catch(Exception e) {
            log.error("Call service method error: " + mName, e);
        }

        return code;
    }

    /**
    *
    * @Title: main
    * @Description: the main call
    * @param args
    * @return void
    * @throws
    */
    public static void main(String[] args) {
        Integer exitCode = 1;
        if (args.length < 2) {
            showHelp();
            System.exit(exitCode);
        }

        String opt = args[0];
        String dir = args[1];
        String lsfConfDir = "";
        String lsfWorkDir = "";
        String providerName = "";
        File jf = null;
        File confDir = null;
        File workDir = null;
        File cfgFile = null;
        Path path;
        UserPrincipal owner;

        try {
            if (log.isTraceEnabled()) {
                log.trace("Start in class GcloudMain in method main with parameters: args"
                          + new Object[] { args });
            }
            GcloudUtil.setHomeDir(dir);
            providerName = System.getenv("PROVIDER_NAME");
            if(StringUtils.isEmpty(providerName)) {
                log.error("Environment variable PROVIDER_NAME is not set. Using gcloud");
                providerName = "gcloud";
            }
            GcloudUtil.setProviderName(providerName);

            lsfConfDir = System.getenv("LSF_ENVDIR");
            log.info("LSF CONF DIR: " + lsfConfDir);
            if(StringUtils.isEmpty(lsfConfDir)) {
                log.error("Environment variable LSF_ENVDIR is not set.");
                System.exit(exitCode);
            }

            path = Paths.get(lsfConfDir+"/lsf.conf");
            owner = Files.getOwner(path);

            lsfConfDir = System.getenv("PRO_CONF_DIR");

            if(StringUtils.isEmpty(lsfConfDir)) {
                log.error("Environment variable PRO_CONF_DIR is not set.");
                System.exit(exitCode);
            }

            confDir = new File(lsfConfDir);

            // if the conf directory does not exist, exit
            if (!confDir.exists()) {
                log.error(providerName+" provider configuration directory: "+lsfConfDir+" does not exist.");
                System.exit(exitCode);
            }
            GcloudUtil.setConfDir(lsfConfDir);


            //Setting the owner of the log file to LSF ADMIN

            // TODO To be confirmed if it is needed to do the extra check : if
            // the current log file owner is different than the LSF Admin, since
            // the scripts calling this JAR are run by the LSF ADMIN.

            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration config = ctx.getConfiguration();
            LoggerConfig loggerConfig = config.getLoggerConfig(LOGGER_NAME);
            Appender fileAppender = loggerConfig.getAppenders().get(LOG_APPENDER_NAME);
            if( fileAppender != null && fileAppender instanceof RollingFileAppender) {
                RollingFileAppender rollingFileAppender = (RollingFileAppender) fileAppender;
                try {
                    path = Paths.get(rollingFileAppender.getFileName());
                    UserPrincipal pathOwner = Files.getOwner(path);
                    if(!owner.getName().equals(pathOwner.getName())) {
                        log.info("Setting the LSF Admin as the owner");
                        Files.setOwner(path, owner);
                    }
                } catch (Exception e) {
                    log.error(
                        "Problem in setting the owner of the log file to the LSF Admin. Will ignore changing the log file owner: "
                        + e.getMessage(), e);
                }
            }

            lsfWorkDir = System.getenv("PRO_DATA_DIR");
            if(StringUtils.isEmpty(lsfWorkDir)) {
                lsfWorkDir =  dir + "/data";
                log.warn("LSF_SHAREDIR is not set. Using "+lsfWorkDir);
            }

            if (args.length >= 3) {
                String jfname = args[2];
                jf = new File(jfname);
                if (!jf.exists()) {
                    log.error("Input json file does not exist: " + jfname);
                    System.exit(exitCode);
                }
            }

            String mName = getMethodName(opt);
            if (StringUtils.isEmpty(mName)) {
                showHelp();
                System.exit(exitCode);
            }

            workDir = new File(lsfWorkDir);

            // if the work directory does not exist, exit
            if (!workDir.exists()) {
                log.error("LSF work directory: "+lsfWorkDir+" does not exist.");
                System.exit(exitCode);
            }
            GcloudUtil.setWorkDir(lsfWorkDir);

            String statusFileName = providerName+ "-db.json";
            GcloudUtil.setProvStatusFile(statusFileName);

            GcloudConfig cfg = new GcloudConfig();
            cfgFile = new File(confDir + "/conf/" + GcloudConst.GOOGLEPROV_CONFIG_FILENAME);
            if (!cfgFile.exists()) {
                log.error(providerName+" provider configuration file "+ GcloudConst.GOOGLEPROV_CONFIG_FILENAME +" does not exist.");
                System.exit(exitCode);
            }

            cfg = GcloudUtil.toObject(cfgFile, GcloudConfig.class);

            if (null == cfg) {
                log.error("Configuration file " + GcloudConst.GOOGLEPROV_CONFIG_FILENAME + " is not a valid JSON format file.");
                System.exit(exitCode);
            }

            if(StringUtils.isNotBlank(cfg.getLogLevel())) {
                setLogLevelWithParameter(cfg.getLogLevel());
            }

            GcloudUtil.setConfig(cfg);
            log.debug("Configuration " + cfg.toString());

            exitCode = call(jf, mName);
            System.exit(exitCode);
        } catch(Throwable e) {
            exitCode = 1;
            log.error("Call Goole Cloud tool error.", e);
            System.exit(exitCode);
        }

    }

    private static void setLogLevelWithParameter(String logLevel) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class GcloudMain in method setLogLevelWithParameter with parameters: logLevel: {}"
                      + new Object[] { logLevel });
        }
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig(LOGGER_NAME);

        boolean logLevelRecognized = true;
        if ("DEBUG".equalsIgnoreCase(logLevel)) {
            loggerConfig.setLevel(Level.DEBUG);
        } else if ("INFO".equalsIgnoreCase(logLevel)) {
            loggerConfig.setLevel(Level.INFO);
        } else if ("WARN".equalsIgnoreCase(logLevel)) {
            loggerConfig.setLevel(Level.WARN);
        } else if ("ERROR".equalsIgnoreCase(logLevel)) {
            loggerConfig.setLevel(Level.ERROR);
        } else if ("FATAL".equalsIgnoreCase(logLevel)) {
            loggerConfig.setLevel(Level.FATAL);
        } else if ("TRACE".equalsIgnoreCase(logLevel)) {
            loggerConfig.setLevel(Level.TRACE);
        } else {
            logLevelRecognized = false;
        }

        if (logLevelRecognized) {
            log.info("Log level is set to: " + logLevel);
            ctx.updateLoggers();
        } else {
            log.warn("logLevel parameter '" + logLevel + "' level not recognized. Using the default instead "+loggerConfig.getLevel());
        }
    }

}
