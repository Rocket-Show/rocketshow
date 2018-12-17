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

#### Image building

This script is used to build the image (may take about 45 minutes). Preparation should be done according to the readme in the GIT repo.

```shell
git clone https://github.com/RPi-distro/pi-gen.git
cd pi-gen
git checkout tags/2018-03-13-raspbian-stretch

echo "IMG_NAME='RocketShow'" > config

touch ./stage3/SKIP ./stage4/SKIP ./stage5/SKIP
rm stage4/EXPORT* stage5/EXPORT*

# Enhance stage2 with rocketshow
mkdir ./stage2/99-rocket-show

cat <<'EOF' >./stage2/99-rocket-show/00-run-chroot.sh
#!/bin/bash
#
cd /tmp
wget https://rocketshow.net/install/script/install.sh
chmod +x install.sh
./install.sh
rm -rf install.sh
EOF

chmod +x ./stage2/99-rocket-show/00-run-chroot.sh

./build.sh
```

#### Update process
- Add the release notes in update/currentversion2.xml and build the war ("mvn install")
- Copy seed directory directory.tar.gz to rocketshow.net/install, if updated
- Copy target/rocketshow.jar to rocketshow.net/update/rocketshow.jar
- Copy install/install.sh to rocketshow.net/install/script/install.sh, if updated
- Copy update/currentversion2.xml to rocketshow.net/update/currentversion2.xml
- Copy update/before.sh, update/after.sh to rocketshow.net/update/xy.sh, if updated
- Copy the new complete image to rocketshow.net/install/images and change the file latest.php to link the new version
- GIT merge DEV branch to MASTER
- GIT tag with the current version
- Switch to DEV and update POM and update/currentversion2.xml versions
- Set a new version in the file WebContent/index.jsp -> url=app?v=x.y to prevent Chrome from caching the app's index.html.

#### Application
The built application should be uploaded to rocketshow.net/update and be named "rocketshow.jar". The file "currentversion2.xml" can be modified accordingly.

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