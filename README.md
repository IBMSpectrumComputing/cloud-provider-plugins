
# resource-connector-plugins
This is the location of cloud provider plug-ins for the [LSF resource connector](https://www.ibm.com/docs/en/spectrum-lsf/10.1.0?topic=lsf-resource-connnector). For more information on how the LSF resource connector works, and how to configure each cloud provider, refer to [LSF resource connector](https://www.ibm.com/docs/en/spectrum-lsf/10.1.0?topic=lsf-resource-connnector).

# install
The following is a sample installation directory for cloud provider plug-ins under LSF:
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
There are 5 shell scripts that you must implement for a new plug-in. The ebrokerd daemon periodically invokes these scripts with the "-f <input.json>" option. Each script exits with return code 0 if the call succeeds and the result is in stdout. Otherwise, the script exits with return code 1 if the call fails and the error message is in stdout.

## getAvailableTemplates.sh
Parse the template configuration file in <LSF_TOP>/conf/resource_connector/<provider_name>/conf/\<prov\>_templates.json.

The following is an example of content of an input JSON file:
```
{ }
```

The following is an example of content of JSON output to stdout:
```
{
    "templates":[
        {
            "templateId":"Template-VM-1",
            "maxNumber":200,
            "attributes":{
                "mem":[
                    "Numeric",
                    "8192"
                ],
                "ncpus":[
                    "Numeric",
                    "1"
                ],
                "zone":[
                    "String",
                    "asiasoutheast"
                ],
                "azurehost":[
                    "Boolean",
                    "1"
                ],
                "ncores":[
                    "Numeric",
                    "1"
                ],
                "type":[
                    "String",
                    "X86_64"
                ]
            },
            "instanceTags":"group=LSF2"
        }
    ]
}
```

## requestMachines.sh
Request to create instances from the cloud. 

The following is an example of the content of an input JSON file:
```
{
    "template":{
        "templateId":"Template-VM-1",
        "machineCount":1
    },
    "rc_account":"default",
    "userData":{

    }
}
```

The following is an example of JSON output to stdout:
```
{
    "message":"RequestVM succeeded from Azure.",
    "requestId":"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
}
```

## requestReturnMachines.sh
Request to terminate instances on the cloud.

The following is an example of content of an input JSON file:
```
{
    "machines":[
        {
            "name":"host-10-1-1-36",
            "machineId":"xxxxxxxxxxxxxxxxxxxx"
        }
    ]
}
```

The following is an example of example JSON output to stdout:
```
{
    "message":"Request to terminate instances successful.",
    "requestId":"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
}
```

## getRequestStatus.sh
Get the request status for a request. The request can be either a create instances request or a terminate instances request.

The following is an example of the content of an input JSON file:
```
{
    "requests":[
        {
            "requestId":"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
        }
    ]
}
```

The following is an example of JSON output to stdout:
```
{
    "requests":[
        {
            "status":"complete",
            "machines":[
                {
                    "machineId":"xxxxxxxxxxxxxxxxxxxx",
                    "name":"host-10-100-2-118",
                    "result":"succeed",
                    "status":"RUNNING",
                    "privateIpAddress":"10.100.2.118",
                    "rcAccount":"default",
                    "message":"",
                    "launchtime":1494001568
                }
            ],
            "requestId":"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
            "message":""
        }
    ]
}
```

The following are valid values for a request **status**:
1. complete: the request is complete. For create instances request, it means the required number of instances are created. For terminate instance request, it means the related instances are all terminated on cloud.
2. running:  the request is still in process.
3. complete_with_error: the request is complete, but some instances are failed. For example, it request to create 10 instances in a request. 9 instances are created successfully, but 1 instance is created failed.

The following are valid values for an instance **result**:
1. succeed: For create instance request, it means the instance is created  successfully. For terminate instance request, it means the instance is terminated successfully.
2. executing:  The instance is still in provision or in shutting-down status.
3. fail: The instance failed to create or terminate. 

## getReturnRequests.sh
Check whether any of the instances in the input JSON file are terminated.

The following is an example of the content of an input JSON file:
```
{
    "machines":[
        {
            "name":"host-10-1-1-36",
            "machineId":"xxxxxxxxxxxxxxxxxxxx"
        },
        {
            "name":"host-10-1-1-37",
            "machineId":"xxxxxxxxxxxxxxxxxxxx"
        }
    ]
}
```
The following is an example of JSON output to stdout if the cloud terminated any instances, such as AWS spot instances.

```
{
    "requests":[
        {
            "machine":"host-10-1-1-36",
            "machineId":"xxxxxxxxxxxxxxxxxxxx"
        }
    ]
}
```

If there are no terminated instances, the output returns an empty "requests" list:
```
{
    "status":"complete",
    "message":"",
    "requests":[

    ]
}
```

# ENV variables
You can use the following environment variables in provider plug-ins. The ebrokerd daemon sets the variable values before invoking the previously-mentioned scripts.
```
PRO_LSF_TOP    : Top level directory where LSF is installed. This is the same value as LSF_TOP in the lsf.conf file.
PRO_DATA_DIR   : The data file directory. In most cases, this is <LSF_TOP>/work/<cluster>/resource_connector/.
PRO_LSF_LOGDIR : The location of the provider log. This is the same value as LSF_LOGDIR in the lsf.conf file.
PROVIDER_NAME  : The value of "name" in the hostProviders.json file.
PRO_CONF_DIR   : The value of "confPath" in the hostProviders.json file.
SCRIPT_OPTIONS : The value of "scriptOpption" in the hostProviders.json file.
```

# build
Use build.sh to build and package all plug-ins. For each plug-in, this depends on the plug-in source code language.
