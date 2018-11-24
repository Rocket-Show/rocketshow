package com.ascargon.rocketshow;

import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.composition.CompositionPlayer;
import org.springframework.stereotype.Service;

/**
 * Handle the playback of compositions, remote devices, the default composition and compositions as samples.
 */
@Service
public interface PlayerService {

    void play() throws Exception;

    void playAsSample(String compositionName) throws Exception;

    void pause() throws Exception;

    void togglePlay() throws Exception;

    void stop(boolean playDefaultComposition) throws Exception;

    void stop() throws Exception;

    void seek(long positionMillis) throws Exception;

    void setNextComposition() throws Exception;

    void setPreviousComposition() throws Exception;

    void setComposition(Composition composition, boolean playDefaultCompositionWhenStoppingComposition,
                        boolean forceLoad) throws Exception;

    void setComposition(Composition currentComposition) throws Exception;

    void setCompositionName(String name) throws Exception;

    void loadCompositionName(String compositionName) throws Exception;

    CompositionPlayer.PlayState getPlayState();

    String getCompositionName();

    long getCompositionDurationMillis();

    long getPositionMillis();

    void compositionPlayerFinishedPlaying(CompositionPlayer compositionPlayer) throws Exception;

}
