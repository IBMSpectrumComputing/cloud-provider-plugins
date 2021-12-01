/*
 * Copyright IBM Corporation 2019 U.S. Government Users Restricted Rights -
 * Use, duplication or disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
 * IBM, the IBM logo and ibm.com are trademarks of International Business Machines Corp.,
 * registered in many jurisdictions worldwide. Other product and service names might be
 * trademarks of IBM or other companies. A current list of IBM trademarks is available on
 * the Web at "Copyright and trademark information" at www.ibm.com/legal/copytrade.shtml.
 *
 */

package com.ibm.spectrum;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ibm.spectrum.cyclecloud.enums.ProvCode;
import com.ibm.spectrum.cyclecloud.enums.ProvOption;
import com.ibm.spectrum.cyclecloud.model.ProvException;
import com.ibm.spectrum.cyclecloud.model.ProvResult;
import com.ibm.spectrum.cyclecloud.service.ProvService;
import com.ibm.spectrum.cyclecloud.util.ProvUtil;

/**
* @Class Name : CycleCloudMain
* @Description: Cycle Cloud Provider Main
* @Author     : Yudong (Dom) Wang
* @Date       : 2019-5-29 11:27:22
* @Version    : V1.0
*/
public class CycleCloudMain {
    private static Logger log = LogManager.getLogger(CycleCloudMain.class);

    /**
    *
    * @Title      : perform
    * @Description: Perform service API
    * @Param      : @param option
    * @Param      : @param json
    * @Param      : @throws ProvException
    * @Return     : void
    * @Throws     :
     */
    private static void perform(String option, String json) throws ProvException {
        String methodName = ProvOption.method(option);
        if (StringUtils.isEmpty(methodName)) {
            ProvUtil.exit(ProvCode.BAD_OPTION, ProvUtil.getUsage());
        }
        if (ProvOption.OPT_USAGE.getMethodName().equals(methodName)) {
            ProvUtil.exit(ProvCode.OK, ProvUtil.getUsage());
        }

        ProvUtil.init();

        try {
            log.info("Invoke method '{}':\n{}", methodName, json);
            ProvService service = new ProvService();
            Method method = service.getClass().getMethod(methodName, String.class);
            ProvResult result = (ProvResult) method.invoke(service, json);
            ProvUtil.result(result);
        } catch(InvocationTargetException e) {
            if (e.getTargetException() instanceof ProvException) {
                throw (ProvException) e.getTargetException();
            }
            throw new ProvException(ProvCode.ERR_EXEC_API, methodName, e.getMessage());
        } catch(Exception e) {
            throw new ProvException(ProvCode.ERR_EXEC_API, methodName, e.getMessage());
        }
    }

    /**
    * @Title      : main
    * @Description: Provider main method
    * @Param      : @param args
    * @Return     : void
    * @Throws     :
    */
    public static void main(String[] args) {
        // Invalid option
        if (null == args || args.length < 1 || StringUtils.isBlank(args[0])) {
            ProvUtil.exit(ProvCode.BAD_OPTION, ProvUtil.getUsage());
        }

        String option = args[0];
        String json = StringUtils.EMPTY;
        try {
            if (args.length > 1) {
                // Read input json file
                json = ProvUtil.readFile(args[1]);
            }
            perform(option, json);
        } catch(ProvException e) {
            ProvUtil.result(e);
        }
    }

}
