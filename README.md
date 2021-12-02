# resource-connector-plugins
This is the location of cloud provider plugins for [LSF Resource Connector](https://www.ibm.com/docs/en/spectrum-lsf/10.1.0?topic=lsf-resource-connnector). Refer to [LSF Resource Connector](https://www.ibm.com/docs/en/spectrum-lsf/10.1.0?topic=lsf-resource-connnector) how LSF Resource Connector works, and how to configure each cloud providers.

# install
Sample install directory for cloud provider plugins under LSF.
```
LSF_TOP
|-- 10.1
|   `-- resource_connector
|       |-- ibmcloudgen2
|       |   `-- scripts
|       |       |-- getAvailableMachines.sh
|       |       |-- getAvailableTemplates.sh
|       |       |-- getRequestStatus.sh
|       |       |-- getReturnRequests.sh
|       |       |-- requestMachines.sh
|       |       |-- requestReturnMachines.sh
|       |       |-- other_scripts
|       |       `-- example_user_data.sh
|       |-- aws
|       |   |-- lib
|       |   |   |-- AwsTool.jar
|       |   |   `-- other_3rd_jars
|       |   `-- scripts
|       |       |-- getAvailableTemplates.sh
|       |       |-- getRequestStatus.sh
|       |       |-- getReturnRequests.sh
|       |       |-- requestMachines.sh
|       |       |-- requestReturnMachines.sh
|       |       `-- example_user_data.sh
|       `-- other_plugins
`-- conf
    `-- resource_connector
        |-- example_hostProviders.json
        |-- example_policy_config.json
        |-- ibmcloudgen2
        |   `-- conf
        |       |-- ibmcloudgen2_config.json
        |       `-- ibmcloudgen2_templates.json
        |-- aws
        |   `-- conf
        |       |-- awsprov_config.json
        |       |-- awsprov_templates.json
        |       `-- credentials
        `-- other_plugins

```
# Interfaces between ebrokerd
There are 5 shell scripts that must be implemented for a new plugin. The ebrokerd daemon invoke those scripts periodically, with parameter "-f <input.json>". Each script exit with 0 if calling succeed and result will be in the stdOut. Otherwise exit with 1 if calling failed and error message will be in the stdOut.

## getAvailableTemplates.sh

sample input.json
```
{ }
```

sample output json to stdOut
```
{ "templates": [ { "templateId": "Template-VM-1", "maxNumber": 200, "attributes": { "mem": [ "Numeric", "8192" ], "ncpus": [ "Numeric", "1" ], "zone": [ "String", "asiasoutheast" ], "azurehost": [ "Boolean", "1" ], "ncores": [ "Numeric", "1" ], "type": [ "String", "X86_64" ] }, "instanceTags": "group=LSF2"} ] }
```

## requestMachines.sh
sample input.json
```
{ "template": { "templateId": "Template-VM-1", "machineCount": 1 }, "rc_account": "default", "userData": { } }
```
sample output json to stdOut
```
{ "message": "RequestVM success from azure.", "requestId": "c95595c4-0c07-4eb2-8c0c-94f364ac8e76" }
```
## requestReturnMachines.sh
sample input.json
```
{ "machines": [ { "name": "host-10-1-1-36", "machineId": "4fa69d720e06c50a89fb" } ] }
```
sample output json to stdOut
```
{ "message": "Request to terminate instances successful.", "requestId": "5f6a3f07-840e-4fa6-9d72-0e06c50a89fb" }
```

## getRequestStatus.sh

sample input.json
```
{ "requests": [ { "requestId": "7de9425e-6be8-4e50-8dc7-dbcab7ec3102" } ] }
```
sample output json to stdOut
```
{ "requests": [ { "status": "complete", "machines": [ { "machineId": "4e508dc7dbcab7ec3102", "name": "host-10-100-2-118", "result": "succeed", "status": "deallocated", "privateIpAddress": "10.100.2.118", "rcAccount": "default", "message": "", "launchtime": 1494001568 } ], "requestId": "7de9425e-6be8-4e50-8dc7-dbcab7ec3102", "message": "" } ] }
```
Valid value for a request **status**:
1. complete: the request is complete. For create instances request, it means the required number of instances are created. For terminate instance request, it means some instances are all terminated on cloud.
2. running:  the request is still in process.
3. complete_with_error: the request is complete, but some instances are failed. For example, it request to create 10 instances in a request. 9 instances are created successfully, but 1 instance is created failed.

Valid value for an instance **result**:
1. succeed: For create instance request, it means the instance is created  successfully. For terminate instance request, it means the instance is terminated successfully.
2. other: The instances is still in provision or in shutting-down status.

## getReturnRequests.sh

sample input.json
```
{ "machines": [ { "name": "host-10-1-1-36", "machineId": "4fa69d720e06c50a89fb" } ] }
```
sample output json to stdOut
 if there are some instances are terminated by cloud, such as aws spot instances.
```
{ "requests": [ { "machine": "host-10-1-1-36", "machineId": "4fa69d720e06c50a89fb" } ] }
```
Or if no instance is terminated, just response an empty "requests" list:
```
{ "status": "complete", "message": "", "requests": [ ] }
```

# ENV variables
Below environment variables can be used in provider plugin. The ebrokerd daemon sets value before invoke the above scripts.
```
PRO_LSF_TOP    : Top directory where lsf is installed. Same as LSF_TOP in lsf.conf.
PRO_DATA_DIR   : The data file directory, usually, it is <LSF_TOP>/work/<cluster>/resource_connector/.
PRO_LSF_LOGDIR : Where you put the provider log. Same as LSF_LOGDIR in lsf.conf.
PROVIDER_NAME  : The value of "name" in hostProviders.json.
PRO_CONF_DIR   : The value of "confPath" in hostProviders.json.
SCRIPT_OPTIONS : The value of "scriptOpption" in hostProviders.json.
```

# build
Use build.sh to build and package all plugins. As for each plugin, it depend on the plugin source code launguage.


