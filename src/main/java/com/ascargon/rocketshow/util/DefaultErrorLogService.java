package com.ascargon.rocketshow.util;

import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

@Service
public class DefaultErrorLogService implements ErrorLogService {

    private static final int MAX_SIZE = 50;

    // not thread-safe by itself → guard with synchronized
    private final Deque<LogEvent> logList = new ArrayDeque<>();

    public DefaultErrorLogService() {
        // Register the custom error log appender
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        var config = context.getConfiguration();

        ErrorLogAppender appender = new ErrorLogAppender(this,
                "ErrorLogAppender",
                null,
                PatternLayout.createDefaultLayout(config)
        );

        appender.start();

        config.addAppender(appender);

        LoggerConfig rootLogger = config.getRootLogger();
        rootLogger.addAppender(appender, null, null);

        context.updateLoggers();
    }

    @Override
    public synchronized void add(LogEvent event) {
        if (logList.size() >= MAX_SIZE) {
            logList.removeFirst(); // remove oldest
        }
        logList.addLast(event.toImmutable()); // safer: detach from Log4j internals
    }

    public synchronized List<LogEvent> getLastLogs() {
        return new ArrayList<>(logList); // return a copy
    }
}