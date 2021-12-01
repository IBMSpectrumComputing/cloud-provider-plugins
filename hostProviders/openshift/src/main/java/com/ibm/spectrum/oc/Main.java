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


package com.ibm.spectrum.oc;

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

import com.ibm.spectrum.oc.action.ActionImpl;
import com.ibm.spectrum.oc.action.IAction;
import com.ibm.spectrum.oc.model.Entity;
import  com.ibm.spectrum.oc.util.Util;

public class Main {
    private static Logger log = LogManager.getLogger(Main.class);

    private static final String LOGGER_NAME = "com.ibm.spectrum.oc";

    private static final String LOG_APPENDER_NAME = "OPENSHIFT_FILE_APPEND";

    private static final int PATH_IS_NOT_SET = -1;
    private static final int PATH_DOES_NOT_EXIST = -2;

    private static final String LSF_CONF_FILE_NAME = "lsf.conf";

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
        .append("java -jar ocTool.jar [-l or --getAvailableMachines] [Home Dir] [Json File]").append("\n")
        .append("java -jar ocTool.jar [-t or --getAvailableTemplates] [Home Dir] [Json File]").append("\n")
        .append("java -jar ocTool.jar [-q or --getReturnRequests] [Home Dir] [Json File]").append("\n")
        .append("java -jar ocTool.jar [-n or --requestMachines] [Home Dir] [Json File]").append("\n")
        .append("java -jar ocTool.jar [-r or --requestReturnMachines] [Home Dir] [Json File]").append("\n")
        .append("java -jar ocTool.jar [-s or --getRequestStatus] [Home Dir] [Json File]");

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
     * @Description: call  method
     * @param @param jsonPath
     * @param @param mName
     * @param @return
     * @return String
     * @throws
     */
    public static Integer call(File jf, String mName) {
        IAction action = new ActionImpl();
        Integer code = 1;
        Entity req = null;

        try {
            if (null == jf) {
                req = new Entity();
            } else {
                req = Util.toObject(jf, Entity.class);
            }

            log.info("Call method: [" + mName + "] begin, request: " + req);

            Method m = action.getClass().getMethod(mName, Entity.class);
            Entity rsp = (Entity)m.invoke(action, req);

            log.info("Call method: [" + mName + "] end, response: " + rsp);

            code  = rsp.getCode();
            String jsonTxt = Util.toJsonTxt(rsp);
            System.out.println(jsonTxt);
        } catch(Exception e) {
            log.error("Call service method error: " + mName, e);
        }

        return code;
    }

    private static int isPathValid (String path) {
        if(path == null
                || path.isEmpty())  {
            return PATH_IS_NOT_SET;
        }

        File pathFile = new File(path);
        if (! pathFile.exists())  {
            return PATH_DOES_NOT_EXIST;
        }

        return 0;
    }
    private static String getPathPropertyValue (String propertyName, boolean isFatal) {
        String path = System.getProperty(propertyName);
        if (path == null ) {
            path = System.getenv(propertyName);
        }

        int result = isPathValid(path);
        if(result == PATH_IS_NOT_SET)  {
            if (isFatal) {
                log.error("Environment variable " + propertyName + " is not set.");
            }
            return null;
        } else if (result == PATH_DOES_NOT_EXIST) {
            if (isFatal) {
                log.error("Environment variable "+ propertyName +" path: " + path + " does not exist.");
            }
            return null;
        }
        log.info("Environment variable " + propertyName + ": " + path);

        return path;
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
        String lsfConfDir = null;
        String provConfDir = null;
        String provConfTopDir = null;
        String provWorkDir = null;
        String providerName = null;


        Path path;
        UserPrincipal owner;
        Integer exitCode = 1;

        if (args.length < 3) {
            showHelp();
            System.exit(exitCode);
        }
        String opt = args[0];
        String homeDir = args[1];
        String inputJson = args[2];

        String mName = getMethodName(opt);
        if (mName == null || mName.isEmpty()) {
            showHelp();
            System.exit(exitCode);
        }
        if (isPathValid(homeDir) < 0) {
            log.error("Home directory does not exist: " + homeDir);
            System.exit(-1);
        }
        File jf = new File(inputJson);
        if (!jf.exists())  {
            log.error("Input JSON file does not exist: " + inputJson);
            System.exit(-1);
        }

        try {
            if (log.isTraceEnabled()) {
                log.trace("Start in class Main in method main with parameters: args"
                          + new Object[] { args });
            }

            providerName = System.getProperty(Util.EBROKERD_ENV_PROVIDIER_NAME);
            if(providerName == null ) {
                providerName = System.getenv(Util.EBROKERD_ENV_PROVIDIER_NAME);
            }
            if(providerName == null || providerName.isEmpty()) {
                log.error("Environment variable  " + Util.EBROKERD_ENV_PROVIDIER_NAME +" is not set. Using " + Util.OPENSHIFT_API_PROVIDER);
                providerName = Util.OPENSHIFT_API_PROVIDER;
            }
            String statusFileName = providerName+ "-db.json";

            lsfConfDir = getPathPropertyValue(Util.EBROKERD_ENV_LSF_ENVDIR, true);
            if (lsfConfDir == null) {
                System.exit(-1);
            }
            Util.setLsfConfDir(lsfConfDir);
            provConfTopDir = getPathPropertyValue(Util.EBROKERD_ENV_PROV_CONF_DIR, false);
            if (provConfTopDir == null) {
                provConfDir = lsfConfDir + "/resource_connector/openshift/conf";
                if (isPathValid(provConfDir) < 0) {
                    log.error("Drectory does not exist: " + provConfDir);
                    System.exit(-1);
                }
                System.exit(-1);
            } else {
                if (provConfTopDir.indexOf("openshift/conf") < 0) { // ebrokerd sets this env var without "conf" subdir
                    provConfDir = provConfTopDir + "/conf";
                } else {
                    provConfDir = provConfTopDir;
                }
            }
            provWorkDir = getPathPropertyValue(Util.EBROKERD_ENV_PROV_WORK_DIR, true);
            if (provWorkDir == null) {
                System.exit(-1);
            }

            path = Paths.get(lsfConfDir+"/" + LSF_CONF_FILE_NAME);
            owner = Files.getOwner(path);

            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration config = ctx.getConfiguration();
            LoggerConfig loggerConfig = config.getLoggerConfig(LOGGER_NAME);
            Appender fileAppender = loggerConfig.getAppenders().get(LOG_APPENDER_NAME);
            if( fileAppender != null && fileAppender instanceof RollingFileAppender) {
                RollingFileAppender rollingFileAppender = (RollingFileAppender) fileAppender;
            }

            if (Util.setProvConfDir(provConfDir) < 0) {
                System.exit(-1);
            }

            if (System.getProperty(Util.EBROKERD_PROPERTY_LSF_TOP_DIR) != null) {
                Util.setLsfTopDir(System.getProperty(Util.EBROKERD_PROPERTY_LSF_TOP_DIR));
            }
            if (System.getProperty(Util.EBROKERD_PROPERTY_LSF_HOSTS_FILE) != null) {
                Util.setLsf_hosts_file_path(System.getProperty(Util.EBROKERD_PROPERTY_LSF_HOSTS_FILE));
            }
            if (System.getProperty(Util.EBROKERD_PROPERTY_TEMP_HOSTS_FILE) != null) {
                Util.setTemp_hosts_file_path(System.getProperty(Util.EBROKERD_PROPERTY_TEMP_HOSTS_FILE));
            }
            if (System.getProperty(Util.EBROKERD_PROPERTY_ENABLE_EGO) != null) {
                Util.setEnable_ego(System.getProperty(Util.EBROKERD_PROPERTY_ENABLE_EGO));
            }

            Util.setProvWorkDir(provWorkDir);
            Util.setProvStatusFile(statusFileName);
            Util.setProviderName(providerName);
            Util.setHomeDir(homeDir);

            setLogLevelWithParameter(Util.getConfig().getLogLevel());

            exitCode = call(jf, mName);
            System.exit(exitCode);
        } catch(Throwable e) {
            log.error("Call OpenShift tool error.", e);
            System.exit(-1);
        }

    }

    private static void setLogLevelWithParameter(String logLevel) {
        if (logLevel == null
                || logLevel.isEmpty()) {
            return;
        }
        if (log.isTraceEnabled()) {
            log.trace("Start in class Main in method setLogLevelWithParameter with parameters: logLevel: {}"
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
            log.warn("logLevel parameter '" + logLevel + "' level is not recognized. Using the default level instead "+loggerConfig.getLevel());
        }
    }

}
