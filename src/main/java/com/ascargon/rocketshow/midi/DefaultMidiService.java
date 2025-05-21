package com.ascargon.rocketshow.midi;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PreDestroy;
import javax.sound.midi.*;
import javax.sound.midi.MidiDevice;

import com.fazecast.jSerialComm.SerialPort;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

@Service
public class DefaultMidiService implements MidiService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultMidiService.class);

    private List<SerialPort> openMidiSerialDevices = new ArrayList<>();

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
    public SerialPort getHardwareMidiSerialDevice(com.ascargon.rocketshow.midi.MidiDevice midiDevice,
                                                  MidiDirection midiDirection) {

        // The port name is system-wide unique
        SerialPort[] serialPorts = SerialPort.getCommPorts();
        for (SerialPort serialPort : serialPorts) {
            if (serialPort.getSystemPortName().equals(midiDevice.getName())) {
                if (serialPort.isOpen()) {
                    return serialPort;
                }

                logger.trace("Try opening serial port " + serialPort.getSystemPortName());

                serialPort.setComPortParameters(31250, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
                serialPort.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);

                if (!serialPort.openPort()) {
                    logger.trace("Could not open serial port " + serialPort.getSystemPortName() + ". Errorcode: " + serialPort.getLastErrorCode());
                    return null;
                }

                openMidiSerialDevices.add(serialPort);

                logger.trace("Serial port opened. Ready to receive and send MIDI data...");

                return serialPort;
            }
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
        SerialPort[] serialPorts = SerialPort.getCommPorts();
        int index = midiDeviceList.size();
        for (SerialPort serialPort : serialPorts) {
            com.ascargon.rocketshow.midi.MidiDevice midiDevice = new com.ascargon.rocketshow.midi.MidiDevice();
            midiDevice.setSerialPort(true);
            midiDevice.setId(index);
            midiDevice.setName(serialPort.getSystemPortName());
            midiDevice.setDescription(serialPort.getDescriptivePortName());
            midiDeviceList.add(midiDevice);

            index++;
        }

        return midiDeviceList;
    }


    @PreDestroy
    private void close() {
        for (SerialPort openMidiSerialDevice : openMidiSerialDevices) {
            openMidiSerialDevice.closePort();
        }
    }

}
