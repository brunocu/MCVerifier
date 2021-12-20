#!/bin/bash

maven_evaluate() {
    echo $(mvn help:evaluate -Dexpression=$1 -q -DforceStdout --file ./pom.xml)
}

# get project version
version=$(maven_evaluate "project.version")
echo "Project version is $version"

# get build name
artifact=$(maven_evaluate "project.build.finalName")
echo "Package name will be $artifact"

# set variables
echo "::set-output name=version::$version"
echo "::set-output name=artifact::$artifact"

if [[ $version =~ "SNAPSHOT" ]]; then
    echo "::set-output name=pre-release::true"
else
    echo "::set-output name=pre-release::false"
fi
