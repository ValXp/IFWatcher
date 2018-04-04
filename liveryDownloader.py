import json
import urllib
import sys
import os
import time
import xmltodict
# you need to pip install xmltodict

globalConfigServer = ''

liveryDownloadFolder = "./downloadedLiveries"

if not os.path.exists(liveryDownloadFolder):
    os.makedirs(liveryDownloadFolder)

# First retrieve globalConfiguration

configURL = globalConfigServer + "GlobalConfiguration.json"
response = urllib.urlopen(configURL)
globalConfig = json.loads(response.read())

# with the globalConfiguration we can get the URL to the package list and the airplanes
for config in globalConfig["Configuration"]:
    if config["Key"] == "PackagesManifestURL":
        packagesManifestURL = config["Value"]
    if config["Key"] == "AirplanesManifestURL":
        airplanesManifestURL = config["Value"]
    if config["Key"] == "DLCServer":
        liveryServer = config["Value"]

print("Loading AirplanesManifest.json")
response = urllib.urlopen(airplanesManifestURL)
airplanesManifest = json.loads(response.read())

print("Loading PackagesManifest")
response = urllib.urlopen(packagesManifestURL)
packages = xmltodict.parse(response.read())

packageDict  = {}
for manifest in packages["PackagesManifest"]["Packages"]["PackageManifest"]:
    packageDict[manifest["PackageID"]] = manifest["Name"]


# Generating a cleaner AirplanesManifest that doesn't contain a bunch of useless data
print("AirplanesManifest cleanup")
cleanManifest = []
for plane in airplanesManifest:
    newLiveries = []
    for livery in plane["Liveries"]:
        newLiveries.append({ "Name" : livery["Name"], "ID" : livery["ID"] })
    cleanManifest.append({ "Liveries" : newLiveries, "Name" : plane["Name"], "ID" : plane["ID"]})

with open('AirplanesManifest.json', 'w') as manifestFile:
    json.dump(cleanManifest, manifestFile, separators=(',',':'))

downloadErrors = 0
for plane in airplanesManifest:
    if plane["ID"] == "ef677903-f8d3-414f-a190-233b2b855d46": # skipping C172
        continue
    for livery in plane["Liveries"]:
        url = liveryServer + "Aircraft/" + packageDict[plane["ID"]] + "_" + livery["TexturePrefix"] + ".png"
        filename = liveryDownloadFolder + "/" + livery["ID"] + ".png"
        print("Downloading " + livery["Name"] + " for " + plane["Name"] + ". URL: " + url)
        if not os.path.exists(filename):
            try:
                urllib.urlretrieve(url, filename) 
                #time.sleep(1)
            except IOError as error:
                print("Exception downloading livery: ", error)
                downloadErrors = downloadErrors + 1
        else:
            print("Livery already downloaded. Skipping.")

print("Livery download success! Download Errors: " + str(downloadErrors))
