package net.microfalx.service.api;

/**
 * A proxy for a service.
 */
public interface ServiceProxy extends Service {

    /**
     * Returns the service instance.
     *
     * @return a non-null instance
     */
    Object getDelegate();
}
