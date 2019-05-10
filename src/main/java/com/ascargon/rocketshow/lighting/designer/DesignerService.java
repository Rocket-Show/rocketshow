package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.composition.CompositionPlayer;
import org.freedesktop.gstreamer.Pipeline;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public interface DesignerService {

    Project getProjectByCompositionName(String compositionName);

    void load(CompositionPlayer compositionPlayer, Project project, Pipeline pipeline);

    void play();

    void pause();

    void seek(long positionMillis);

    void close();

    long getPositionMillis();

}
