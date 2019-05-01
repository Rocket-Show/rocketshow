#!/bin/bash
#
# Start Rocket Show.
#

if [ "$(uname)" == "Darwin" ]; then
  # Start olad in background, if available, on Mac OS
  olad &
fi

java -Xmx512m -jar rocketshow.jar