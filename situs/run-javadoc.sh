#!/bin/bash
cd "$(dirname "$0")"
./gradlew javadoc 2>&1 | tee javadoc-output.txt
