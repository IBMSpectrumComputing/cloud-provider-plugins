# Copyright International Business Machines Corp, 2025
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#!/usr/bin/env python3
import boto3
import json
import os
import sys
from datetime import datetime, timezone, timedelta

def main():
    profile_name = os.getenv('AWS_FEDERATED_PROFILE', 'aws-federated-profile')
    
    # TODO: Automate `aws sso login --profile $AWS_FEDERATED_PROFILE` 
    
    try:
        session = boto3.Session(profile_name=profile_name)
        creds = session.get_credentials().get_frozen_credentials()
        
        # Output in exact format ConfigManager needs
        output = {
            "AccessKeyId": creds.access_key,
            "SecretAccessKey": creds.secret_key, 
            "SessionToken": creds.token,
            # SSO doesn't always expose expiration, so default to 1 hour
            "Expiration": (datetime.now(timezone.utc) + timedelta(hours=1)).isoformat()
        }
        
        print(json.dumps(output))
        
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == '__main__':
    main()