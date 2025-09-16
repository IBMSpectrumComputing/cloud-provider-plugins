#!/bin/bash
# This script:
#    - should be called as requestMachines.sh -f input.json
#    - exit with 0 if calling succeed and result will be in the stdOut
#    - exit with 1 if calling failed and error message will be in the stdOut
#
inJson=$2
scriptDir=`dirname $0`
homeDir="$(cd "$scriptDir" && cd .. && pwd)"

export PYTHONPATH=$scriptDir

python3 $scriptDir/../src/request_return_machines.py $inJson $home