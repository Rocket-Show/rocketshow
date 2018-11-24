package com.ascargon.rocketshow.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class DefaultStartMessageDisplayService implements StartMessageDisplayService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultStartMessageDisplayService.class);

    private final Environment environment;

    public DefaultStartMessageDisplayService(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void show() {
        logger.info("*************************************************");
        logger.info("* Rocket Show started. Open http://localhost:" + environment.getProperty("local.server.port") + " with your browser");
        logger.info("*************************************************");
    }

}
