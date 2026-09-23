package net.microfalx.service.core;

import net.microfalx.service.api.Service;

public interface Test2Service extends Service {

    static Test2Service getInstance() {
        return Service.lookup(Test2Service.class);
    }
}
