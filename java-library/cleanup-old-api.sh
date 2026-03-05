#!/bin/bash
# Run this script once to delete the old api/ folder and duplicate spring/model/ classes
# that have been replaced by the new spring/ and model/ packages.
#
# Usage (from repo root):
#   bash java-library/cleanup-old-api.sh

BASE="java-library/src/main/java/no/testframework/javalibrary"

echo "Deleting old api/ package (replaced by spring/ and model/)..."
rm -rf "$BASE/api"

echo "Deleting duplicate model classes from spring/model/ (canonical copies are in model/)..."
rm -f "$BASE/spring/model/TestCase.java"
rm -f "$BASE/spring/model/TestCaseResult.java"
rm -f "$BASE/spring/model/TestSuite.java"
rm -f "$BASE/spring/model/TestSuiteResult.java"

echo "Done. Remaining spring/model/ contents:"
ls "$BASE/spring/model/"

echo "Remaining structure:"
find "$BASE" -name "*.java" | sort
