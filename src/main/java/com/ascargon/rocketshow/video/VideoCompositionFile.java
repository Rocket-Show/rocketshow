package com.ascargon.rocketshow.video;

import javax.xml.bind.annotation.XmlTransient;

import com.ascargon.rocketshow.Manager;
import com.ascargon.rocketshow.composition.CompositionFile;

public class VideoCompositionFile extends CompositionFile {

    public final static String VIDEO_PATH = "video/";

    @XmlTransient
    public String getPath() {
        return Manager.BASE_PATH + MEDIA_PATH + VIDEO_PATH + getName();
    }

    @XmlTransient
    public int getFullOffsetMillis() {
        return this.getOffsetMillis() + this.getManager().getSettings().getOffsetMillisVideo();
    }

    public FileType getType() {
        return FileType.VIDEO;
    }

}
