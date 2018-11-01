package com.ascargon.rocketshow.video;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import javax.xml.bind.annotation.XmlTransient;

import org.apache.log4j.Logger;

import com.ascargon.rocketshow.Manager;
import com.ascargon.rocketshow.composition.File;

public class VideoFile extends File {

    final static Logger logger = Logger.getLogger(VideoFile.class);

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
