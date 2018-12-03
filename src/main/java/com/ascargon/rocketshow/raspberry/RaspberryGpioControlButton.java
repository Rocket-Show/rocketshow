package com.ascargon.rocketshow.raspberry;

import com.pi4j.io.gpio.GpioPinDigitalInput;

public class RaspberryGpioControlButton {

    private int cyclesHigh = 0;

    private GpioPinDigitalInput button;

    private RaspberryGpioControl raspberryGpioControl;

    public int getCyclesHigh() {
        return cyclesHigh;
    }

    public void setCyclesHigh(int cyclesHigh) {
        this.cyclesHigh = cyclesHigh;
    }

    public GpioPinDigitalInput getButton() {
        return button;
    }

    public void setButton(GpioPinDigitalInput button) {
        this.button = button;
    }

    public RaspberryGpioControl getRaspberryGpioControl() {
        return raspberryGpioControl;
    }

    public void setRaspberryGpioControl(RaspberryGpioControl raspberryGpioControl) {
        this.raspberryGpioControl = raspberryGpioControl;
    }

}
