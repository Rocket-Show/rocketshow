package com.ascargon.rocketshow.settings;

import org.springframework.stereotype.Service;

@Service
public interface SettingsUpdateSystemService {

    void update(Settings settings);

}
