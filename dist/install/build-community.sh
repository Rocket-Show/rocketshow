#!/bin/bash
#
# Script to build the Rocket Show community image on a build server.
# The image is built in the current directory, which needs a lot of free space.
# The resulting ZIP is written to /root.
#

set -euo pipefail

echo "Building the community image in: $(pwd)"

# ---- UNMOUNT AND DETACH ALL DEVICES ----
for m in $(mount | grep '^/dev/loop' | awk '{print $3}'); do
    sudo umount "$m" 2>/dev/null || true
done

for l in $(losetup -a | cut -d: -f1); do
    sudo losetup -d "$l" 2>/dev/null || true
done

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
