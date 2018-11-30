package com.ascargon.rocketshow.util;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class OperatingSystemInformation {

    public enum Type {
        UNKNOWN, WINDOWS, OS_X, LINUX, HP_UNIX, SOLARIs, SUN_OS
    }

    public enum SubType {
        UNKNOWN, RASPBIAN, DEBIAN, UBUNTU
    }

    public enum ArchitectureType {
        UNKNOWN, T32, T64
    }

    private Type type = Type.UNKNOWN;
    private SubType subType = SubType.UNKNOWN;
    private ArchitectureType architectureType = ArchitectureType.UNKNOWN;

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public SubType getSubType() {
        return subType;
    }

    public void setSubType(SubType subType) {
        this.subType = subType;
    }

    public ArchitectureType getArchitectureType() {
        return architectureType;
    }

    public void setArchitectureType(ArchitectureType architectureType) {
        this.architectureType = architectureType;
    }

}
