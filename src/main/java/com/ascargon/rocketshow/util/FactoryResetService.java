package com.ascargon.rocketshow.util;

import org.springframework.stereotype.Service;

@Service
public interface FactoryResetService {

    void reset() throws Exception;

}
