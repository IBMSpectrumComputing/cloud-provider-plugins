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
import java.util.Enumeration;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.nio.file.FileSystems;
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

import com.ibm.spectrum.util.StringUtils;
import com.ibm.spectrum.azure.AzureImpl;
import com.ibm.spectrum.azure.IAzure;
import com.ibm.spectrum.model.AzureEntity;
import com.ibm.spectrum.util.AzureUtil;
import com.ibm.spectrum.model.AzureConfig;

/**
* @ClassName: AzureMain
* @Description: The main of Azure host provider
* @author xawangyd
* @date Jan 26, 2016 3:50:09 PM
* @version 1.0
*/
public class AzureMain {
    private static Logger log = LogManager.getLogger(AzureMain.class);
    private static final String LOGGER_NAME = "com.ibm.spectrum";
    private static final String LOG_APPENDER_NAME = "AZURE_FILE_APPEND";

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

        b.append("Invalid operation. For example: ").append("\n")
        .append("java -jar AzureTool.jar [-l or --getAvailableMachines] [Home Dir] [Json File]").append("\n")
        .append("java -jar AzureTool.jar [-t or --getAvailableTemplates] [Home Dir] [Json File]").append("\n")
        .append("java -jar AzureTool.jar [-q or --getReturnRequests] [Home Dir] [Json File]").append("\n")
        .append("java -jar AzureTool.jar [-n or --requestMachines] [Home Dir] [Json File]").append("\n")
        .append("java -jar AzureTool.jar [-r or --requestReturnMachines] [Home Dir] [Json File]").append("\n")
        .append("java -jar AzureTool.jar [-s or --getRequestStatus] [Home Dir] [Json File]");

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
    * @Description: call Azure method
    * @param @param jsonPath
    * @param @param mName
    * @param @return
    * @return String
    * @throws
     */
    public static Integer call(File jf, String mName) {
        IAzure Azure = new AzureImpl();
        Integer code = 1;
        AzureEntity req = null;

        try {
            if (null == jf) {
                req = new AzureEntity();
            } else {
                req = AzureUtil.toObject(jf, AzureEntity.class);
            }

            log.info("Call method: [" + mName + "] begin, request: " + req);

            Method m = Azure.getClass().getMethod(mName, AzureEntity.class);
            AzureEntity rsp = (AzureEntity)m.invoke(Azure, req);

            log.info("Call method: [" + mName + "] end, response: " + rsp);

            code  = rsp.getCode();
            String jsonTxt = AzureUtil.toJsonTxt(rsp);
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
    * @param @param args
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
        String lsfLogDir = "";
        String logFileName = "";
        String providerName = "";
        File jf = null;
        File confDir = null;
        File logDir = null;
        File workDir = null;
        File cfgFile = null;
        File logFile = null;
        InetAddress ip;
        String hostname;
        Path path;
        UserPrincipal owner;

        try {

            AzureUtil.setHomeDir(dir);
            providerName = System.getenv("PROVIDER_NAME");
            if(StringUtils.isNullOrEmpty(providerName)) {
                log.error("Environment variable PROVIDER_NAME is not set. Using Azure");
                providerName = "Azure";
            }
            AzureUtil.setProviderName(providerName);

            lsfConfDir = System.getenv("LSF_ENVDIR");

            if(StringUtils.isNullOrEmpty(lsfConfDir)) {
                log.error("Environment variable LSF_ENVDIR is not set.");
                System.exit(exitCode);
            }

            path = Paths.get(lsfConfDir+"/lsf.conf");
            owner = Files.getOwner(path);

            lsfConfDir = System.getenv("PRO_CONF_DIR");

            if(StringUtils.isNullOrEmpty(lsfConfDir)) {
                log.error("Environment variable PRO_CONF_DIR is not set.");
                System.exit(exitCode);
            }

            confDir = new File(lsfConfDir);

            // if the conf directory does not exist, exit
            if (!confDir.exists()) {
                log.error(providerName+" provider configuration directory: "+lsfConfDir+" does not exist.");
                System.exit(exitCode);
            }
            AzureUtil.setConfDir(lsfConfDir);

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
            if(StringUtils.isNullOrEmpty(lsfWorkDir)) {
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
            if (StringUtils.isNullOrEmpty(mName)) {
                showHelp();
                System.exit(exitCode);
            }

            workDir = new File(lsfWorkDir);

            // if the work directory does not exist, exit
            if (!workDir.exists()) {
                log.error("LSF work directory: "+lsfWorkDir+" does not exist.");
                System.exit(exitCode);
            }
            AzureUtil.setWorkDir(lsfWorkDir);

            String statusFileName = providerName+ "-db.json";
            AzureUtil.setProvStatusFile(statusFileName);

            AzureConfig cfg = new AzureConfig();
            cfgFile = new File(lsfConfDir + "/conf/azureprov_config.json");
            if (!cfgFile.exists()) {
                log.error(providerName+" provider configuration file azureprov_config.json does not exist.");
                System.exit(exitCode);
            }

            cfg = AzureUtil.toObject(cfgFile, AzureConfig.class);

            if (null == cfg) {
                log.error("Configuration file azureprov_config.json is not a valid JSON format file.");
                System.exit(exitCode);
            }

            if(!StringUtils.isNullOrEmpty(cfg.getLogLevel())) {
                setLogLevelWithParameter(cfg.getLogLevel());
            }

            AzureUtil.setConfig(cfg);
            log.debug("Configuration " + cfg.toString());

            exitCode = call(jf, mName);
            System.exit(exitCode);
        } catch(Throwable e) {
            exitCode = 1;
            log.error("Call Azure tool error.", e);
            System.exit(exitCode);
        }

    }

    private static void setLogLevelWithParameter(String logLevel) {
        if (log.isTraceEnabled()) {
            log.trace("Start in class AzureMain in method setLogLevelWithParameter with parameters: logLevel: {}"
                      + new Object[] { logLevel });
        }
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig(LOGGER_NAME);

        boolean logLevelRecognized = true;
        if ("DEBUG".equalsIgnoreCase(logLevel)) {
            loggerConfig.setLevel(Level.DEBUG);
        } else if ("TRACE".equalsIgnoreCase(logLevel)) {
            loggerConfig.setLevel(Level.TRACE);
        } else if ("INFO".equalsIgnoreCase(logLevel)) {
            loggerConfig.setLevel(Level.INFO);
        } else if ("WARN".equalsIgnoreCase(logLevel)) {
            loggerConfig.setLevel(Level.WARN);
        } else if ("ERROR".equalsIgnoreCase(logLevel)) {
            loggerConfig.setLevel(Level.ERROR);
        } else if ("FATAL".equalsIgnoreCase(logLevel)) {
            loggerConfig.setLevel(Level.FATAL);
        } else {
            logLevelRecognized = false;
        }

        if (logLevelRecognized) {
            log.info("Log level is set to: " + logLevel + "<br/>");
            ctx.updateLoggers();
        } else {
            log.warn("logLevel parameter '" + logLevel + "' level not recognized. Using the default instead "+loggerConfig.getLevel());
        }
    }

}
