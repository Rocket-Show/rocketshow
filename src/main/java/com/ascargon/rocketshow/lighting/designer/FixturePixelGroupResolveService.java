package com.ascargon.rocketshow.lighting.designer;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface FixturePixelGroupResolveService {

    List<CachedFixturePixel> resolveGroupPixels(
            FixtureMatrixPixelGroup group,
            List<CachedFixturePixel> allPixels
    );

    List<CachedFixturePixel> getPixelsForKeyOrGroup(
            FixtureMatrix matrix,
            List<CachedFixturePixel> allPixels,
            String keyOrGroup
    );

}
