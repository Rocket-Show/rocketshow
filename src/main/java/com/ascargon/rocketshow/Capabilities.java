package com.ascargon.rocketshow;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class Capabilities {

    private boolean ola = true;
    private boolean gstreamer = true;

    @XmlElement
    public boolean isOla() {
        return ola;
    }

    public void setOla(boolean ola) {
        this.ola = ola;
    }

    @XmlElement
    public boolean isGstreamer() {
        return gstreamer;
    }

    public void setGstreamer(boolean gstreamer) {
        this.gstreamer = gstreamer;
    }

}
