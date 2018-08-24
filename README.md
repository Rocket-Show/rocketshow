# Rocket Show
A tool to automate your DMX and videoshow.

## For developers
### Development
#### Build
1. The Java web archive (WAR) can be built using this command: `mvn install`
2. Start API server: `docker-compose down && docker-compose up`
3. Start web frontend server: `cd src/main/webapp && npx ng serve --base-href /`

Verify web api works: http://localhost:8080/api/system/state \
Open web application: http://localhost:4200

#### Troubleshooting tomcat issues during development
Login to docker container and inspect logs:
`$ docker-compose down && docker-compose up` \
`$ docker exec -it rocketshow_tomcat_1 /bin/bash` \
`# cat logs/localhost*` 

Open tomcat manager in browser: http://localhost:8080/manager/ \
Username tomcat, password 1234

### Deployment
#### Seed directory
The seed directory structure /install/rocketshow can be packed on a mac with this commands (assuming you're currently in the install directory):
```shell
COPYFILE_DISABLE=true tar -c --exclude='.DS_Store' -zf directory.tar.gz rocketshow
```

The resulting directory.tar.gz should be uploaded to rocketshow.net/install.

#### Application
The built application should be uploaded to rocketshow.net/update and be named "current.war". The file "currentversion.xml" can be modified accordingly.

#### Installation on a Raspberry Pi
These commands can be used to install Rocket Show on a Raspberry Pi with Raspbian Lite installed:
```shell
sudo su - root
cd /tmp
wget https://rocketshow.net/install/script/install.sh
chmod +x install.sh
./install.sh
rm -rf install.sh
```