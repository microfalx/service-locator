package net.microfalx.service.core;

import net.microfalx.service.api.Logger;
import net.microfalx.service.api.ServiceLocator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoggerImplTest {

    private boolean quiet;
    private ServiceLocatorImpl serviceLocator;

    @BeforeEach
    void setUp() {
        serviceLocator = new ServiceLocatorImpl();
        ServiceLocator.set(serviceLocator);
        quiet = serviceLocator.isQuiet();
        serviceLocator.setQuiet(false);
        serviceLocator.quietLogger.clear();
    }

    @AfterEach
    void tearDown() {
        serviceLocator.quietLogger.clear();
        serviceLocator.setQuiet(quiet);
    }

    @Test
    void forwardsAndAppendsWhenNotQuiet() {
        org.slf4j.Logger delegate = createLogger();
        Logger logger = new LoggerImpl(delegate);

        logger.info("message");
        assertEquals("", serviceLocator.getLog());
    }

    @Test
    void appendsButDoesNotForwardWhenQuiet() {
        org.slf4j.Logger delegate = createLogger();
        Logger logger = new LoggerImpl(delegate);
        serviceLocator.setQuiet(true);

        logger.info("quiet-message");
        assertEquals("Logger : quiet-message", serviceLocator.getLog());
    }

    @Test
    void formattedMessageIsAppendedAsRenderedText() {
        org.slf4j.Logger delegate = createLogger();
        Logger logger = new LoggerImpl(delegate);
        serviceLocator.setQuiet(true);

        logger.warn("value {} {}", 10, 20);
        assertEquals("Logger : value 10 20", serviceLocator.getLog());
    }

    @Test
    void markerOverloadForwardsAndAppendsRenderedText() {
        org.slf4j.Logger delegate = createLogger();
        Logger logger = new LoggerImpl(delegate);
        Marker marker = MarkerFactory.getMarker("test");
        serviceLocator.setQuiet(true);
        logger.error(marker, "failed {}", "task");
        assertEquals("Logger : failed task", serviceLocator.getLog());
    }

    @Test
    void enabledChecksRespectQuietMode() {
        org.slf4j.Logger delegate = createLogger();
        when(delegate.isDebugEnabled()).thenReturn(true);
        Logger logger = new LoggerImpl(delegate);

        serviceLocator.setQuiet(true);
        assertFalse(logger.isDebugEnabled());

        serviceLocator.setQuiet(false);
        assertTrue(logger.isDebugEnabled());
    }

    private org.slf4j.Logger createLogger() {
        org.slf4j.Logger delegate = mock(org.slf4j.Logger.class);
        when(delegate.getName()).thenReturn("Logger");
        when(delegate.isInfoEnabled()).thenReturn(true);
        when(delegate.isWarnEnabled()).thenReturn(true);
        when(delegate.isErrorEnabled()).thenReturn(true);
        return delegate;
    }
}

