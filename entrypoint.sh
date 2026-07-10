#!/bin/sh
set -e
mkdir -p /app/data
chown -R raindrops:raindrops /app/data
exec su -s /bin/sh raindrops -c "exec /opt/java/openjdk/bin/java -jar /app/app.jar"