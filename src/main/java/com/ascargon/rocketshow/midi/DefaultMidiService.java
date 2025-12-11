package com.ascargon.rocketshow.midi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import purejavacomm.CommPort;
import purejavacomm.CommPortIdentifier;
import purejavacomm.SerialPort;

import javax.annotation.PreDestroy;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import java.util.*;

@Service
public class DefaultMidiService implements MidiService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultMidiService.class);

    private final Map<String, SerialPort> openMidiSerialDevices = new HashMap<>();

    private boolean isDeviceAllowed(String name) {
        return !(name.equals("Real Time Sequencer") || name.equals("Gervill"));
    }

    private boolean midiDeviceHasDirection(MidiDevice midiDevice, MidiDirection midiDirection) {
        if (!isDeviceAllowed(midiDevice.getDeviceInfo().getName())) {
            return false;
        }

        return ((midiDirection == MidiDirection.IN && midiDevice.getMaxTransmitters() != 0)
                || (midiDirection == MidiDirection.OUT && midiDevice.getMaxReceivers() != 0));
    }

    @Override
    public MidiDevice getHardwareMidiDevice(com.ascargon.rocketshow.midi.MidiDevice midiDevice,
                                            MidiDirection midiDirection) throws MidiUnavailableException {

        // Get a hardware device for a given MIDI device
        MidiDevice.Info[] midiDeviceInfos = MidiSystem.getMidiDeviceInfo();

        if (midiDevice != null) {
            return null;
        }

        // Search for a device with same id and name
        if (midiDeviceInfos.length > midiDevice.getId()) {
            if (midiDeviceInfos[midiDevice.getId()].getName().equals(midiDevice.getName())) {
                MidiDevice hardwareMidiDevice = MidiSystem.getMidiDevice(midiDeviceInfos[midiDevice.getId()]);

                if (midiDeviceHasDirection(hardwareMidiDevice, midiDirection)) {
                    logger.debug("Found MIDI device with same ID and name");
                    return hardwareMidiDevice;
                }
            }
        }

        // Search for a device with the same name
        for (MidiDevice.Info midiDeviceInfo : midiDeviceInfos) {
            if (midiDeviceInfo.getName().equals(midiDevice.getName())) {
                MidiDevice hardwareMidiDevice = MidiSystem.getMidiDevice(midiDeviceInfo);

                if (midiDeviceHasDirection(hardwareMidiDevice, midiDirection)) {
                    logger.debug("Found MIDI device with same name");
                    return hardwareMidiDevice;
                }
            }
        }

        // Search for a device with the same id
        if (midiDeviceInfos.length > midiDevice.getId()) {
            MidiDevice hardwareMidiDevice = MidiSystem.getMidiDevice(midiDeviceInfos[midiDevice.getId()]);

            if (midiDeviceHasDirection(hardwareMidiDevice, midiDirection)) {
                logger.debug("Found MIDI device with same ID");
                return hardwareMidiDevice;
            }
        }

        logger.trace("No MIDI device found");
        return null;
    }

    @Override
    public synchronized SerialPort getHardwareMidiSerialDevice(com.ascargon.rocketshow.midi.MidiDevice midiDevice,
                                                               MidiDirection midiDirection) {

        logger.trace("Search for a serial MIDI device with name '" + midiDevice.getName() + "'...");

        // Return the already opened device, if available
        SerialPort openSerialPort = openMidiSerialDevices.get(midiDevice.getName());
        if (openSerialPort != null) {
            return openSerialPort;
        }

        try {
            CommPortIdentifier portId = CommPortIdentifier.getPortIdentifier(midiDevice.getName());

            if (portId.isCurrentlyOwned()) {
                logger.error("Could not open MIDI serial port, because it is already in use.");
                return null;
            }

            logger.info("Open MIDI serial port...");

            CommPort commPort = portId.open("MidiSerialApp", 2000);
            if (commPort instanceof SerialPort serialPort) {
                serialPort.setSerialPortParams(
                        31250, // MIDI baud rate
                        SerialPort.DATABITS_8,
                        SerialPort.STOPBITS_1,
                        SerialPort.PARITY_NONE
                );

                serialPort.notifyOnDataAvailable(true);
                serialPort.notifyOnOutputEmpty(true);
                serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);

                logger.info("MIDI serial port opened. Ready to receive and send MIDI data.");
                openMidiSerialDevices.put(midiDevice.getName(), serialPort);

                return serialPort;
            }
        } catch (Exception e) {
            logger.error("Could not open MIDI serial port", e);
        }

        return null;
    }

    @Override
    public List<com.ascargon.rocketshow.midi.MidiDevice> getMidiDevices(MidiDirection midiDirection)
            throws MidiUnavailableException {

        // Get all available MIDI devices
        MidiDevice.Info[] midiDeviceInfos = MidiSystem.getMidiDeviceInfo();
        List<com.ascargon.rocketshow.midi.MidiDevice> midiDeviceList = new ArrayList<>();

        for (int i = 0; i < midiDeviceInfos.length; i++) {
            MidiDevice hardwareMidiDevice = MidiSystem.getMidiDevice(midiDeviceInfos[i]);

            // Filter the direction and hide system devices
            if (midiDeviceHasDirection(hardwareMidiDevice, midiDirection)) {
                com.ascargon.rocketshow.midi.MidiDevice midiDevice = new com.ascargon.rocketshow.midi.MidiDevice();
                midiDevice.setId(i);
                midiDevice.setName(midiDeviceInfos[i].getName());
                midiDevice.setVendor(midiDeviceInfos[i].getVendor());
                midiDevice.setDescription(midiDeviceInfos[i].getDescription());
                midiDeviceList.add(midiDevice);
            }
        }

        // Add all available serial ports
        int index = midiDeviceList.size();
        Enumeration<CommPortIdentifier> portList = CommPortIdentifier.getPortIdentifiers();

        while (portList.hasMoreElements()) {
            CommPortIdentifier portId = portList.nextElement();
            if (portId.getPortType() == CommPortIdentifier.PORT_SERIAL) {
                com.ascargon.rocketshow.midi.MidiDevice midiDevice = new com.ascargon.rocketshow.midi.MidiDevice();
                midiDevice.setSerialPort(true);
                midiDevice.setId(index);
                midiDevice.setName(portId.getName());
                midiDevice.setDescription("Serial port");
                midiDeviceList.add(midiDevice);

                index++;
            }
        }

        return midiDeviceList;
    }


    @PreDestroy
    private void close() {
        for (Map.Entry<String, SerialPort> entry : openMidiSerialDevices.entrySet()) {
            SerialPort port = entry.getValue();
            port.close();
        }
    }

}
