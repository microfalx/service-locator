package net.microfalx.service.core;

import net.microfalx.service.api.Service;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;

public class ServiceProxyImpl implements Service {

    private final Object service;

    ServiceProxyImpl(Object service) {
        requireNonNull(service);
        this.service = service;
    }
}
