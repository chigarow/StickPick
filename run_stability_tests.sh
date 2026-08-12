#!/bin/sh
set -e
i=1
while [ $i -le 10 ]; do
  echo "--- STABILITY TEST RUN $i / 10 ---"
  ./gradlew test --quiet
  i=$((i+1))
done
echo "ALL 10 STABILITY RUNS PASSED SUCCESSFULLY!"
