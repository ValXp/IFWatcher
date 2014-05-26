#!/bin/bash

./gradlew assembleRelease
version=`cat app/build.gradle  | grep versionCode | sed "s/.*versionCode \(.*\)/\1/g"`
rm -f version.txt
wget "http://valxp.net/IFWatcher/version.txt"
currentVersion=`cat version.txt`
echo "Uploading version to $version. Version on server is $currentVersion"
echo "Upload ? (yes/no)"
ret=`read`
if [ ! $ret == "yes" ]; then
    echo "Cancelled"
    exit 1
fi 
echo "$version" > version.txt
cp app/build/apk/app-release.apk IFWatcher.apk
scp IFWatcher.apk version.txt root@valxp.net:./http/IFWatcher/
rm version.txt IFWatcher.apk
echo "Done"
