package net.microfalx.service.core;

import net.microfalx.lang.annotation.DependsOn;
import net.microfalx.lang.annotation.Provider;
import net.microfalx.service.api.Service;

@Provider
@DependsOn(classes = Test1Service.class)
public class Test2ServiceImpl implements Test2Service, Service.Lifecycle {

    @Override
    public void start() {

    }
}
