package com.ascargon.rocketshow.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;

import java.io.Serializable;

public class ErrorLogAppender extends AbstractAppender {

    private final ErrorLogService errorLogService;

    protected ErrorLogAppender(ErrorLogService errorLogService, String name, Filter filter, Layout<? extends Serializable> layout) {
        super(name, filter, layout);
        this.errorLogService = errorLogService;
    }

    @Override
    public void append(LogEvent event) {
        if (event.getLevel().isMoreSpecificThan(Level.ERROR)) {
            errorLogService.add(event.toImmutable());
        }
    }

}
