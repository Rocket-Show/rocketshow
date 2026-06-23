package com.ascargon.rocketshow.util;

import org.apache.logging.log4j.core.LogEvent;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface ErrorLogService {

    void add(LogEvent event);

    List<LogEvent> getLastLogs();

}
