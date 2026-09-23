package net.microfalx.service.api;

import net.microfalx.lang.Initializable;

/**
 * A wrapper interface for the SLF4J logger which supports quiet mode.
 */
public interface Logger extends org.slf4j.Logger {

    /**
     * Returns a logger instance for the given class.
     *
     * @param clazz the class
     * @return a non-null instance
     */
    static Logger get(Class<?> clazz) {
        Logger logger = ServiceLocator.load(Logger.class);
        ((Initializable) logger).initialize(clazz);
        return logger;
    }
}
