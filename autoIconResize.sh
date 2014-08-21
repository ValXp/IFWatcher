#!/bin/bash

if [ "$#" != "1" ] || [ "$1" == "-h" ] ; then
    echo "Usage : $0 source_image"
    exit 1
fi

src="$1"

function makeIcon() {
    folder=app/src/main/res/drawable-$2
    
    echo "$2"
    mkdir -p $folder
    convert $src -resize $1 $folder/ic_launcher.png
}

makeIcon 48x48 mdpi
makeIcon 72x72 hdpi
makeIcon 96x96 xhdpi
makeIcon 144x144 xxhdpi
makeIcon 192x192 xxxhdpi

echo "Done"
