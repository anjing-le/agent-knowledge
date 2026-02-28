#!/bin/bash
set -e
cd $(dirname $0)

# vars
registry_host="your-registry.example.com"
image_name="general-doc-parser-dev"
image_tag=$1

# check
if [ -z "$image_tag" ]; then
    echo "image_tag is required"
    exit 1
fi

# build
full_image_name=$registry_host/$image_name:$image_tag
echo "building $full_image_name"
docker build -t $full_image_name .

echo "done"





