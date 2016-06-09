# Lagonaki

This is permacoin implementation on top of Scorex framework.


# Run a node
## Ubuntu

Install Oracle Java8 JDK:

`echo "deb http://ppa.launchpad.net/webupd8team/java/ubuntu precise main" | tee -a /etc/apt/sources.list`

`echo "deb-src http://ppa.launchpad.net/webupd8team/java/ubuntu precise main" | tee -a /etc/apt/sources.list`

`apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys EEA14886`

`apt-get update`

`apt-get install oracle-java8-installer`

and agree with license terms


Run Lagonaki node

Download deb package from [releases](https://github.com/ScorexProject/PermaScorex/releases)

Install it

`sudo dpkg -i lagonaki.deb`

Run "lagonaki settings.json".

### Create package

For now it is only possible to create deb package with `sbt debian:packageBin` command

## Other system

Compile code by typing `sbt recompile`
Run a node with `java -jar target/scala-2.11/lagonaki.jar` command

## Docker
 
 To start Lagonaki with the Docker run `docker run -i -p 9085:9085  "scorex/lagonaki:1.2.8"`


## Run a private local network
---

Run one or two peers on the local machine:


* run "sbt recompile" to (re-)build .jar file
* run "sbt startLocal1" to run first local peer binded to 127.0.0.1:9084 . Edit settings in settings-local1.json
   if needed. Access UI via localhost:9085
* run "sbt startLocal2" to run second local peer binded to 127.0.0.2:9088 . Edit settings in settings-local2.json
   if needed. Access UI via localhost:9086
* run "sbt startLocal3" to run second local peer binded to 127.0.0.3:9084 . Edit settings in settings-local2.json
   if needed. Access UI via localhost:9087
* You can run first & second peers simultaneously by running "sbt startLocal"

You can edit folders / other settings in settings.json file before running commands above.


