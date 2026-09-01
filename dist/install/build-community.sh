#!/bin/bash
#
# Script to build the Rocket Show community image on a build server.
# The image is built in the current directory, which needs a lot of free space.
# The resulting ZIP is written to /root.
#

set -euo pipefail

echo "Building the community image in: $(pwd)"

# ---- PREPARE BUILD ENV ----
echo "Deleting previous build directory..."
rm -rf build
mkdir build
cd build

git clone https://github.com/RPi-distro/pi-gen.git
cd pi-gen
git checkout tags/2025-12-04-raspios-trixie-arm64

# pi-gen config
cat > config <<'EOF'
IMG_NAME='RocketShow'
EOF

touch ./stage3/SKIP ./stage4/SKIP ./stage5/SKIP
rm stage4/EXPORT* stage5/EXPORT*

# ---- MAKE THE IMAGE EXPORT ROBUST ----
# ensure_loopdev_partitions in the stock prerun.sh only creates device nodes
# for the partitions lsblk already reports, it never triggers a scan itself.
# If the kernel did not scan the table of the freshly attached loop device,
# the build dies with "mkdosfs: unable to open /dev/loop0p1". Force the scan
# in that case. This is a no-op whenever the stock code already worked.
cat > wait-for-partitions.snippet <<'EOF'

if [ ! -b "${LOOP_DEV}p1" ] || [ ! -b "${LOOP_DEV}p2" ]; then
	cnt=0
	until [ -b "${LOOP_DEV}p1" ] && [ -b "${LOOP_DEV}p2" ]; do
		if [ $cnt -lt 10 ]; then
			cnt=$((cnt + 1))
			echo "Scanning the partitions of ${LOOP_DEV}..."
			partx -a "${LOOP_DEV}" 2>/dev/null || true
			ensure_loopdev_partitions "${LOOP_DEV}"
			sleep 2
		else
			echo "ERROR: no partitions found on ${LOOP_DEV}; exiting"
			exit 1
		fi
	done
fi

EOF

if ! grep -q '^ensure_loopdev_partitions ' ./export-image/prerun.sh; then
    echo "ERROR: cannot patch export-image/prerun.sh, anchor not found" >&2
    exit 1
fi
sed -i '/^ensure_loopdev_partitions /r wait-for-partitions.snippet' ./export-image/prerun.sh
rm -f wait-for-partitions.snippet

# Enhance stage2 with rocketshow
mkdir ./stage2/99-rocket-show

# Script to run outside chroot
cat > ./stage2/99-rocket-show/00-run.sh <<'EOF'
#!/bin/bash -e

# Add some files for the script inside chroot afterwards
install -d "${ROOTFS_DIR}/root"
install -m 0644 /root/rocketshow.jar "${ROOTFS_DIR}/root/rocketshow.jar"
install -m 0644 /root/black.jpg "${ROOTFS_DIR}/root/black.jpg"
install -m 0644 /root/designer_template.json "${ROOTFS_DIR}/root/designer_template.json"
install -m 0644 /root/defaults.tar.gz "${ROOTFS_DIR}/root/defaults.tar.gz"
EOF
chmod +x ./stage2/99-rocket-show/00-run.sh

# Script to run inside chroot (setup rocket show)
cp /root/raspbian-community.sh ./stage2/99-rocket-show/00-run-chroot.sh
chmod +x ./stage2/99-rocket-show/00-run-chroot.sh

# ---- BUILD ----
./build.sh

cd work/RocketShow/export-image

FINAL_IMG="$(date '+%Y-%m-%d')-RocketShow-community.img"

# Find the .img produced by pi-gen; the suffix varies across pi-gen versions.
DATE_PREFIX="$(date '+%Y-%m-%d')"
shopt -s nullglob
BUILT_IMGS=( "${DATE_PREFIX}-RocketShow"*.img )
shopt -u nullglob
if [[ ${#BUILT_IMGS[@]} -eq 0 ]]; then
    echo "ERROR: No .img file found in $(pwd) after build" >&2
    exit 1
fi
[[ "${BUILT_IMGS[0]}" != "$FINAL_IMG" ]] && mv "${BUILT_IMGS[0]}" "$FINAL_IMG"
IMG_FILE="$FINAL_IMG"

# zip the image
echo "ZIP the image..."
zip "$(date '+%Y-%m-%d')-RocketShow-community.zip" "${IMG_FILE}"

mv "$(date '+%Y-%m-%d')-RocketShow-community.zip" /root/
