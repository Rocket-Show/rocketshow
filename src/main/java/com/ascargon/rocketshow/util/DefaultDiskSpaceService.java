package com.ascargon.rocketshow.util;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Returns the used and available disk space.
 *
 * @author Moritz A. Vieli
 */
@Service
public class DefaultDiskSpaceService implements DiskSpaceService {

    private final DeviceInformationService deviceInformationService;

    public DefaultDiskSpaceService(
            DeviceInformationService deviceInformationService
    ) {
        this.deviceInformationService = deviceInformationService;
    }

    @Override
    public DiskSpace get() throws Exception {
        DiskSpace diskSpace = new DiskSpace();
        String root = "/";

        if (deviceInformationService.getDeviceInformation().isAvailable()) {
            root = "/data";
        }

        // Get the used and available space in MB
        ProcessBuilder pb = new ProcessBuilder("df", "-BMB", root);
        Process process = pb.start();
        BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;

        // Loop over each line
        while ((line = br.readLine()) != null) {
            line = line.replaceAll("\t", " ");
            line = line.replaceAll(" +", " ");
            line = line.replaceAll("MB", "");

            String[] parts = line.split(" ");

            // skip header line
            if (parts.length < 6 || "Used".equalsIgnoreCase(parts[2])) {
                continue;
            }

            String usedMB = parts[2];
            String availableMB = parts[3];
            String drive = parts[5];

            if (root.equals(drive)) {
                diskSpace.setUsedMB(Double.parseDouble(usedMB));
                diskSpace.setAvailableMB(Double.parseDouble(availableMB));
            }
        }

        return diskSpace;
    }

}
