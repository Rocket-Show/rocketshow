package com.ascargon.rocketshow;

import com.ascargon.rocketshow.composition.Composition;
import org.springframework.stereotype.Service;

@Service
public interface PlayerService {

    void setComposition(Composition composition, boolean playDefaultCompositionWhenStoppingComposition,
                               boolean forceLoad) throws Exception;

    void setComposition(Composition composition) throws Exception;

    void setCompositionName(String name) throws Exception;

    void setAutoStartNextComposition(boolean autoStartNextComposition);

    void stop(boolean playDefaultComposition);

    void stop();

    Composition.PlayState getPlayState();

    String getCompositionName();

    long getCompositionDurationMillis();

    long getPositionMillis();

}
