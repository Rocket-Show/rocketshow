package com.ascargon.rocketshow;

import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class Capabilities {

    private boolean ola = true;
    private boolean gstreamer = true;

    public boolean isOla() {
        return ola;
    }

    public void setOla(boolean ola) {
        this.ola = ola;
    }

    public boolean isGstreamer() {
        return gstreamer;
    }

    public void setGstreamer(boolean gstreamer) {
        this.gstreamer = gstreamer;
    }

}
