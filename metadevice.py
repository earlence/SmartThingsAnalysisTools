#!/usr/bin/python

# SmartThingsAnalysisTools Copyright 2016 Regents of the University of Michigan
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy
# of the License at http://www.apache.org/licenses/LICENSE-2.0


# this will auto create device handlers with a single capability

import urllib
import urllib2
import time

createdevicetypeurl = 'https://graph.api.smartthings.com/ide/device/save'

# to get a cookie, observe network traffic in the browser when logged in to graph.api.smartthings.com
# and copy a cookie from an HTTP header. Paste it as a single line into cookie.txt
cookiepath = 'D:\\SamsungSmartApps\\crawler_input\\cookie.txt' # replace with a path to your cookie

req_createdevice = {
  'id':'',
  'name':'',
  'namespace':'com.earlence.autocreate',
  'author':'EarlenceFernandes',
  'apacheLicense':'',
  'apacheLicense':'true',
  'capabilityId.97':'',
  'capabilityId.150':'',
  'capabilityId.79':'',
  'capabilityId.75':'',
  'capabilityId.153':'',
  'capabilityId.145':'',
  'capabilityId.144':'',
  'capabilityId.148':'',
  'capabilityId.171':'',
  'capabilityId.83':'',
  'capabilityId.76':'',
  'capabilityId.76':'',
  'capabilityId.159':'',
  'capabilityId.159':'',
  'capabilityId.137':'',
  'capabilityId.173':'',
  'capabilityId.172':'',
  'capabilityId.177':'',
  'capabilityId.56':'',
  'capabilityId.136':'',
  'capabilityId.139':'',
  'capabilityId.141':'',
  'capabilityId.94':'',
  'capabilityId.147':'',
  'capabilityId.160':'',
  'capabilityId.135':'',
  'capabilityId.77':'',
  'capabilityId.146':'',
  'capabilityId.175':'',
  'capabilityId.82':'',
  'capabilityId.138':'',
  'capabilityId.78':'',
  'capabilityId.98':'',
  'capabilityId.58':'',
  'capabilityId.152':'',
  'capabilityId.149':'',
  'capabilityId.142':'',
  'capabilityId.156':'',
  'capabilityId.143':'',
  'capabilityId.162':'',
  'capabilityId.161':'',
  'capabilityId.157':'',
  'capabilityId.74':'',
  'capabilityId.93':'',
  'capabilityId.57':'',
  'capabilityId.158':'',
  'capabilityId.99':'',
  'capabilityId.163':'',
  'capabilityId.166':'',
  'capabilityId.168':'',
  'capabilityId.165':'',
  'capabilityId.167':'',
  'capabilityId.176':'',
  'capabilityId.169':'',
  'capabilityId.92':'',
  'capabilityId.91':'',
  'capabilityId.164':'',
  'capabilityId.170':'',
  'capabilityId.178':'',
  'capabilityId.140':'',
  'capabilityId.179':'',
  'capabilityId.180':'',
  'capabilityId.80':'',
  'capabilityId.181':'',
  'attributes':'',
  'commands':'',
  'fingerprints.0.endpointId':'',
  'fingerprints.0.profileId':'',
  'fingerprints.0.deviceId':'',
  'fingerprints.0.deviceVersion':'',
  'fingerprints.0.inClusters':'',
  'fingerprints.0.outClusters':'',
  'fingerprints.0.noneClusters':'',
  'deviceTypeSettings.name':'',
  'deviceTypeSettings.value':'',
  'deviceTypeSettings.name':'',
  'deviceTypeSettings.value':'',
  'displayName':'',
  'displayLink':'',
  'create':'Create'
}

capids = [  'capabilityId.97',
  'capabilityId.150',
  'capabilityId.79',
  'capabilityId.75',
  'capabilityId.153',
  'capabilityId.145',
  'capabilityId.144',
  'capabilityId.148',
  'capabilityId.171',
  'capabilityId.83',
  'capabilityId.76',
  'capabilityId.76',
  'capabilityId.159',
  'capabilityId.159',
  'capabilityId.137',
  'capabilityId.173',
  'capabilityId.172',
  'capabilityId.177',
  'capabilityId.56',
  'capabilityId.136',
  'capabilityId.139',
  'capabilityId.141',
  'capabilityId.94',
  'capabilityId.147',
  'capabilityId.160',
  'capabilityId.135',
  'capabilityId.77',
  'capabilityId.146',
  'capabilityId.175',
  'capabilityId.82',
  'capabilityId.138',
  'capabilityId.78',
  'capabilityId.98',
  'capabilityId.58',
  'capabilityId.152',
  'capabilityId.149',
  'capabilityId.142',
  'capabilityId.156',
  'capabilityId.143',
  'capabilityId.162',
  'capabilityId.161',
  'capabilityId.157',
  'capabilityId.74',
  'capabilityId.93',
  'capabilityId.57',
  'capabilityId.158',
  'capabilityId.99',
  'capabilityId.163',
  'capabilityId.166',
  'capabilityId.168',
  'capabilityId.165',
  'capabilityId.167',
  'capabilityId.176',
  'capabilityId.169',
  'capabilityId.92',
  'capabilityId.91',
  'capabilityId.164',
  'capabilityId.170',
  'capabilityId.178',
  'capabilityId.140',
  'capabilityId.179',
  'capabilityId.180',
  'capabilityId.80',
  'capabilityId.181'
]

def main():
  cookieset = open(cookiepath, 'r')
  cookiedata = cookieset.read()

  prev_capid = ''
  for cap in capids:

    req_createdevice['name'] = 'device' + '.' + cap
    if prev_capid != '':
      req_createdevice[prev_capid] = ''

    req_createdevice[cap] = 'on'
    prev_capid = cap
    
    encodeddata = urllib.urlencode(req_createdevice)
    #print cookieset.read()
    headers = { #'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8',
                #'Referer': 'https://graph.api.smartthings.com/ide/device/create',
                #'Origin': 'Origin: https://graph.api.smartthings.com',
                #'Accept-Encoding': 'gzip, deflate',
                #'Accept-Language': 'en-US,en;q=0.8',
                #'Cache-Control': 'max-age=0',
                #'Connection':'keep-alive',
                'Content-Length': str(len(encodeddata)),
                'Content-Type': 'application/x-www-form-urlencoded',
                'Cookie': cookiedata,
                #'User-Agent': 'Mozilla/5.0 (Windows NT 6.3; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/43.0.2357.134 Safari/537.36'
              }
    req = urllib2.Request(createdevicetypeurl, encodeddata, headers)
    print req.get_full_url()
    print req.get_method()
    print req.header_items()
    print req.get_data()  # list lots of other stuff in Request

    try:
      resp = urllib2.urlopen(req)
      print resp.info()
    except urllib2.HTTPError, err:
      print err.read()

    print "sleeping 6 seconds..."
    time.sleep(6)
  
  

if __name__ == "__main__":
  main()
