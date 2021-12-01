#!/usr/bin/python

# Copyright International Business Machines Corp, 2020
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

# -*- coding: utf-8 -*-

import json
import urllib2, urlparse, base64, copy
from Log import Log
import pdb
import time
import datetime

class RequestWithMethod(urllib2.Request):
  def __init__(self, *args, **kwargs):
    self._method = kwargs.pop('method', None)
    urllib2.Request.__init__(self, *args, **kwargs)

  def get_method(self):
    return self._method if self._method else urllib2.Request.get_method(self)

def api_request(url, data = None, token = None, method = None, ret_token = False):
    if method: req = RequestWithMethod(url, data, method = method)
    else: req = RequestWithMethod(url, data)
    if data: req.add_header('Content-Type', 'application/json')
    if token: req.add_header('X-Auth-Token', token)
    Log.logger.debug('Sending %s request to %s', req.get_method(), req.get_full_url())
    Log.logger.debug('Headers: %s', str(req.headers))
    Log.logger.debug('Data: %s', req.get_data())
    try:
        resp = urllib2.urlopen(req, timeout = 30)
    except urllib2.HTTPError as ex:
        ex.msg = '%s (%s)' % (ex.msg, ex.read())
        raise
    data = resp.read()
    Log.logger.debug('Got response from %s: %d %s', resp.geturl(), resp.getcode(), resp.msg)
    Log.logger.debug('Headers: %s', str(resp.headers.dict))
    Log.logger.debug('Data: %s', data)
    if data: data = json.loads(data)
    return (resp.headers.get('X-Subject-Token'), data) if ret_token else data

class Token:
    def __init__(self, cred):
        self.cred = cred
        self.errmsg = ''
        self.api_ver = 3
        if self.cred['AuthURL'].endswith('/v2.0'): self.api_ver = 2
        self.token = ''
        self.tenant_id = ''
        self.nova_url = ''
        self.neutron_url = ''
        self.cinder_url = ''
        self.glance_url = ''
        self.keystone_url = ''
        if self.api_ver == 2: self.auth_v2()
        else: self.auth_v3()

    def auth_v2(self):
        auth_url = self.cred['AuthURL'] + '/tokens'
        auth_data = json.dumps(
{
    "auth": {
        "tenantName": self.cred['ProjectID'],
        "passwordCredentials": {
            "username": self.cred['Username'],
            "password": self.cred['APIKey']
        }
    }
})
        try:
            resp = api_request(auth_url, auth_data)
            self.token = resp['access']['token']['id']
            for service in resp['access']['serviceCatalog']:
                if service['name'] == 'nova': self.nova_url = service['endpoints'][0]['publicURL']
                elif service['name'] == 'neutron': self.neutron_url = service['endpoints'][0]['publicURL']
                elif service['name'] == 'cinder': self.cinder_url = service['endpoints'][0]['publicURL']
                elif service['name'] == 'glance': self.glance_url = service['endpoints'][0]['publicURL']
                elif service['name'] == 'keystone': self.keystone_url = service['endpoints'][0]['publicURL']
        except Exception, ex:
            Log.logger.error('Failed to show details for the Identity API. Exception: %s', str(ex))
            self.errmsg = str(ex)
            return

    def auth_v3(self):
        auth_url = self.cred['AuthURL'] + '/auth/tokens'
        auth_data = json.dumps(
{
  "auth": {
    "scope": {
      "project": {
        "domain": {
          "id": self.cred['ProjectDomainID']
        },
        "name": self.cred['ProjectID']
      }
    },
    "identity": {
      "password": {
        "user": {
          "domain": {
            "id": self.cred['UserDomainID']
          },
          "password": self.cred['APIKey'],
          "name": self.cred['Username']
        }
      },
      "methods": [
        "password"
      ]
    }
  }
})
        try:
            self.token, resp = api_request(auth_url, auth_data, ret_token = True)
            for catalog in resp['token']['catalog']:
                public_url = ''
                for endpoint in catalog['endpoints']:
                    if endpoint['interface'] == 'public': public_url = endpoint['url']
                if catalog['name'] == 'nova': self.nova_url = public_url
                elif catalog['name'] == 'neutron': self.neutron_url = public_url
                elif catalog['name'] == 'cinder': self.cinder_url = public_url
                elif catalog['name'] == 'glance': self.glance_url = public_url
                elif catalog['name'] == 'keystone': self.keystone_url = public_url
        except Exception, ex:
            Log.logger.error('Failed to show details for the Identity API. Exception: %s', str(ex))
            self.errmsg = str(ex)
            return


class NovaClient:
    def __init__(self, token):
        self.token = token.token
        self.nova_url = token.nova_url
        self.nova_image_url = self.nova_url + '/images'
        self.nova_flavor_url = self.nova_url + '/flavors'
        self.nova_volume_url = self.nova_url + '/os-volumes'
        self.nova_network_url = self.nova_url + '/os-networks'
        self.nova_server_url = self.nova_url + '/servers'

    def get_image_id(self, imageName):
        data = api_request(self.nova_image_url + '?name=' + imageName, token = self.token)
        for image in data['images']:
            if image['name'] == imageName:
                return image['id']
        return ''

    def get_flavor_id(self, flavorName):
        data = api_request(self.nova_flavor_url, token = self.token)
        for flavor in data['flavors']:
            if flavor['name'] == flavorName:
                return flavor['id']
        return ''

    def get_volume_id(self, volumeName):
        data = api_request(self.nova_volume_url, token = self.token)
        for volume in data['volumes']:
            if volume['displayName'] == volumeName:
                return volume['id']
        return ''

    def get_network_id(self, networkName):
        data = api_request(self.nova_network_url, token = self.token)
        for network in data['networks']:
            if network['label'] == networkName:
                return network['id']
        return ''

    def create_server(self, name, imageRef, flavorRef, portId, userData = None, files = None, volumeIds = None,
                        keyName = None, securityGroups = None, metaData = None, availabilityZone = None, adminPass = None,
                        additionalPara = None):
        if additionalPara: post_data = copy.deepcopy(additionalPara)
        else: post_data = {}
        post_data.setdefault('server', {})
        post_data['server'].update(name = name, imageRef = imageRef, flavorRef = flavorRef, config_drive = True)
        post_data['server'].setdefault('networks', [])
        post_data['server']['networks'].append({'port': portId})
        if userData: post_data['server']['user_data'] = userData
        if files:
            post_data['server'].setdefault('personality', [])
            post_data['server']['personality'] += [{'path': fpath, 'contents': fcontent} for fpath, fcontent in files]
        #if volumeIds: post_data['server']['block_device_mapping'] = [{'volume_id': vid} for vid in volumeIds]
        if volumeIds:
            post_data['server']['block_device_mapping_v2'] =\
                        [{'source_type': 'image', 'delete_on_termination': True, 'boot_index': 0, 'uuid': imageRef}] +\
                        [{'source_type': 'volume', 'destination_type': 'volume', 'uuid': vid} for vid in volumeIds]
        if keyName: post_data['server']['key_name'] = keyName
        if securityGroups:
            post_data['server'].setdefault('security_groups', [])
            post_data['server']['security_groups'] += securityGroups
        if metaData:
            post_data['server'].setdefault('metadata', {})
            post_data['server']['metadata'].update(metaData)
        if availabilityZone: post_data['server']['availability_zone'] = availabilityZone
        if adminPass: post_data['server']['adminPass'] = adminPass
        post_data = json.dumps(post_data)
        data = api_request(self.nova_server_url, data = post_data, token = self.token)
        return data['server']['id']

    def delete_server(self, id):
        try:
            data = api_request(self.nova_server_url + '/' + id, method = 'DELETE', token = self.token)
        except urllib2.HTTPError as ex:
            if ex.code != 404: raise

    def get_server_details(self, id):
        details = {}
        try:
            data = api_request(self.nova_server_url + '/' + id, token = self.token)
        except urllib2.HTTPError as ex:
            if ex.code == 404: 
                details['status'] = 'deleted'
                return details            
            raise
        else:            
            status = data['server']['status']
            task_state = data['server']['OS-EXT-STS:task_state']
            if status == 'DELETED': details['status'] = 'deleted'
            elif status == 'BUILD': details['status']= 'building'
            elif status == 'ERROR': details['status'] = 'error'
            elif task_state == 'deleting': details['status'] = 'deleting'
            else: details['status'] = 'active'
            
            if data['server']['addresses']:
                if data['server']['addresses'].has_key('private'):
                    if data['server']['addresses']['private']:
                        details['privateIpAddr'] = data['server']['addresses']['private'][0]['addr']
                if data['server']['addresses'].has_key('public'):
                    if data['server']['addresses']['public']:
                        details['publicIpAddr'] = data['server']['addresses']['public'][0]['addr']                        
            if data['server'].has_key('OS-SRV-USG:launched_at'):
                if data['server']['OS-SRV-USG:launched_at']:
                    details['launchTime'] = time.mktime(datetime.datetime.strptime(data['server']['OS-SRV-USG:launched_at'], "%Y-%m-%dT%H:%M:%S.%f").timetuple())  
        return details

    def list_server_ports(self, id):
        data = api_request(self.nova_server_url + '/' + id + '/os-interface', token = self.token)
        return [interface['port_id'] for interface in data['interfaceAttachments']]


class GlanceClient:
    def __init__(self, token):
        self.token = token.token
        self.glance_url = token.glance_url

    def get_image_id(self, imageName):
        data = api_request(self.glance_url + '/v2/images?name=' + imageName, token = self.token)
        for image in data['images']:
            if image['name'] == imageName:
                return image['id']
        return ''

class CinderClient:
    def __init__(self, token):
        self.token = token.token
        self.cinder_url = token.cinder_url

class NeutronClient:
    def __init__(self, token):
        self.token = token.token
        self.neutron_url = token.neutron_url
        self.neutron_network_url = urlparse.urljoin(self.neutron_url, '/v2.0/networks')
        self.neutron_port_url = urlparse.urljoin(self.neutron_url, '/v2.0/ports')
        self.neutron_security_group_url = urlparse.urljoin(self.neutron_url, '/v2.0/security-groups')

    def get_network_id(self, networkName):
        data = api_request(self.neutron_network_url + '.json?fields=id&fields=name&name=' + networkName, token = self.token)
        for network in data['networks']:
            if network['name'] == networkName:
                return network['id']
        return ''

    def get_security_group_ids(self, security_group_names):
        tmp_security_group_url = self.neutron_security_group_url + '.json?fields=id&fields=name'
        security_group_ids = []
        #list all security groups
        #for security_group_name in security_group_names:
        #    tmp_security_group_url = tmp_security_group_url + '&name=' + security_group_name['name']
        data = api_request(tmp_security_group_url, token = self.token)
        for security_group_name in security_group_names:
            found = 0
            for security_group in data['security_groups']:
                if security_group['name'] == security_group_name['name']:
                    found = 1
                    security_group_ids.append(security_group['id'])
            if found == 0:
                Log.logger.warn("Failed to get id of security group: %s", security_group_name);
        return security_group_ids

    def create_port(self, networkId, security_group_ids):
        post_data = {'port': {'network_id': networkId, 'admin_state_up': True}}
        #post_data['port']['port_security_enabled'] = True
        if security_group_ids and len(security_group_ids) > 0:
            post_data['port']['security_groups'] = security_group_ids
            Log.logger.debug("security_group_ids: %s", security_group_ids)
        else:
            Log.logger.warn("Failed to retrieve security_group_ids. Creating port without security group.")
        post_data = json.dumps(post_data)
        data = api_request(self.neutron_port_url, data = post_data, token = self.token)
        return data['port']['fixed_ips'][0]['ip_address'], data['port']['id']

    def delete_port(self, portId):
        try:
            data = api_request(self.neutron_port_url + '/' + portId, method = 'DELETE', token = self.token)
        except urllib2.HTTPError as ex:
            if ex.code != 404: raise

    def update_port(self, port_id, attrs):
        port_data = json.dumps({'port': attrs})
        data = api_request(self.neutron_port_url + '/' + port_id, data = port_data, method = 'PUT', token = self.token)


class OpenStackClient:
    def __init__(self, cred):
        self.token = Token(cred)
        if not self.token.token:
            self.errmsg = self.token.errmsg
            self.token = None
            return
        self.nova = NovaClient(self.token)
        self.neutron = NeutronClient(self.token)
        self.networks = {}
        self.secgrps = {}
        self.images = {}
        self.flavors = {}
        self.volumes = {}

    def __get_network_id(self, network):
        if not self.networks.has_key(network):
            self.networks[network] = self.neutron.get_network_id(network)
        return self.networks[network]

    def __get_security_group_ids(self, security_group_names):
        security_group_names_key = ''
        for security_group_name in security_group_names:
            security_group_names_key = security_group_names_key + ',' + security_group_name['name']
        if not self.secgrps.has_key(security_group_names_key):
            self.secgrps[security_group_names_key] = self.neutron.get_security_group_ids(security_group_names)
        return self.secgrps[security_group_names_key]

    def __get_image_id(self, image):
        if not self.images.has_key(image):
            self.images[image] = self.nova.get_image_id(image)
        return self.images[image]

    def __get_flavor_id(self, flavor):
        if not self.flavors.has_key(flavor):
            self.flavors[flavor] = self.nova.get_flavor_id(flavor)
        return self.flavors[flavor]

    def __get_volume_id(self, volume):
        if not self.volumes.has_key(volume):
            self.volumes[volume] = self.nova.get_volume_id(volume)
        return self.volumes[volume]

    def create_ip_port(self, network, security_group_names):
        security_group_ids = []
        try:
            network_id = self.__get_network_id(network)
        except Exception, ex:
            errmsg = 'Failed to get network id %s. Exception: %s' % (network, str(ex))
            Log.logger.error(errmsg)
            return ('', '')

        try:
            security_group_ids = self.__get_security_group_ids(security_group_names)
        except Exception, ex:
            errmsg = 'Failed to get ids of security groups:  %s. Exception: %s' % (str(security_group_names), str(ex))
            Log.logger.warn(errmsg)
            security_group_ids = []
            #return ('', '')

        try:
            ip_port = self.neutron.create_port(network_id, security_group_ids)
            return ip_port
        except Exception, ex:
            errmsg = 'Failed to create port. Exception: %s' % (str(ex))
            Log.logger.error(errmsg)
            return ('', '')

    def create_machine(self, name, port, image, flavor, user_data = '', user_script = None, volumes = None,
                        key_name = None, security_groups = None, metadata = None, availability_zone = None, admin_pass = None,
                        additional_para = None):
        Log.logger.debug('Creating machine %s, image=%s, flavor=%s, user_data=%s, user_script=%s, volumes=%s, key_name=%s, security_groups=%s',
                    name, image, flavor, user_data, str(user_script), str(volumes), str(key_name), str(security_groups))
        errmsg = ''

        if user_script:
            try:
                script_content = file(user_script).read()
            except Exception, ex:
                errmsg = 'Failed to load user script %s. Exception: %s' % (user_script, str(ex))
                Log.logger.error(errmsg)
                return {'name': name, 'id': '', 'message': name + ' - ' + errmsg}

        try:
            image_id = self.__get_image_id(image)
            if not image_id: errmsg = 'Image %s not found' % (image)
        except Exception, ex:
            errmsg = 'Failed to list image %s. Exception: %s' % (image, str(ex))
            image_id = ''
        if not image_id:
            Log.logger.error(errmsg)
            return {'name': name, 'id': '', 'message': name + ' - ' + errmsg}

        try:
            flavor_id = self.__get_flavor_id(flavor)
            if not flavor_id: errmsg = 'Flavor %s not found' % (flavor)
        except Exception, ex:
            errmsg = 'Failed to list flavor %s. Exception: %s' % (flavor, str(ex))
            flavor_id = ''
        if not flavor_id:
            Log.logger.error(errmsg)
            return {'name': name, 'id': '', 'message': name + ' - ' + errmsg}

        volume_ids = []
        if volumes:
            for vname in volumes:
                try:
                    volume_id = self.__get_volume_id(vname)
                    if not volume_id: errmsg = 'Volume %s not found' % (vname)
                except Exception, ex:
                    errmsg = 'Failed to list volume %s. Exception: %s' % (vname, str(ex))
                    volume_id = ''
                if not volume_id:
                    Log.logger.error(errmsg)
                    return {'name': name, 'id': '', 'message': name + ' - ' + errmsg}
                volume_ids.append(volume_id)

        try:
            server_id = self.nova.create_server(name, imageRef = image_id, flavorRef = flavor_id, portId = port,
                            userData = base64.b64encode(user_data),
                            files = [('/root/userscript.sh', base64.b64encode(script_content))] if user_script else None,
                            volumeIds = volume_ids, keyName = key_name, securityGroups = security_groups,
                            metaData = metadata, availabilityZone = availability_zone, adminPass = admin_pass,
                            additionalPara = additional_para)
            errmsg = ''
        except Exception, ex:
            errmsg = 'Failed to create server %s. Exception: %s' % (name, str(ex))
            Log.logger.error(errmsg)
            server_id = ''

        return {'name': name, 'id': server_id, 'message': errmsg}

    def delete_machine(self, id):
        errmsg = ''
        try:
            if id: self.nova.delete_server(id)
        except Exception, ex:
            errmsg = 'Failed to delete server %s. Exception: %s' % (id, str(ex))
            Log.logger.error(errmsg)
        return errmsg

    def get_machine_details(self, id):
        server_details = {}
        try:
            server_details = self.nova.get_server_details(id)
        except Exception, ex:
            status = ''
            errmsg = 'Failed to get server details %s. Exception: %s' % (id, str(ex))
            Log.logger.error(errmsg)
        return server_details

    def delete_port(self, port):
        errmsg = ''
        try:
            self.neutron.delete_port(port)
        except Exception, ex:
            errmsg = 'Failed to delete port %s. Exception: %s' % (port, str(ex))
            Log.logger.error(errmsg)
        return errmsg

    def update_machine_dns(self, id, dns_name):
        try:
            port_ids = self.nova.list_server_ports(server_id)
            for port in port_ids: self.neutron.update_port(port, {"dns_name": dns_name})
        except Exception, ex:
            errmsg = 'Failed to set dns name to server %s. Exception: %s' % (dns_name, str(ex))
            Log.logger.error(errmsg)

    def get_volume_id(self, volume):
        volume_id = ''
        try:
            volume_id = self.__get_volume_id(volume)
        except Exception, ex:
            errmsg = 'Failed to list volume %s. Exception: %s' % (volume, str(ex))
            Log.logger.error(errmsg)
        return volume_id
