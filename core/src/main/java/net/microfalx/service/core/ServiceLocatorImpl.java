package net.microfalx.service.core;

import net.microfalx.lang.AnnotationUtils;
import net.microfalx.lang.ClassUtils;
import net.microfalx.lang.Initializable;
import net.microfalx.lang.Releasable;
import net.microfalx.lang.annotation.DependsOn;
import net.microfalx.service.api.*;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ClassUtils.isSubClassOf;

/**
 * A factory which provides implementations of services.
 * <p>
 * The factory uses the JDK {@link ServiceLoader} and {@link ClassUtils#resolveProviderInstances(Class)}
 * to discover both the implementations of services and the implementations of {@link Service.Listener}.
 */
public class ServiceLocatorImpl extends ServiceLocator implements Initializable {

    private static final Logger LOGGER = Logger.get(ServiceLocator.class);

    private final Map<Class<?>, Service> services = new ConcurrentHashMap<>();
    private final Map<Class<?>, Service> serviceImplementations = new ConcurrentHashMap<>();
    private final Map<Class<?>, WeakReference<Service>> serviceProxies = new ConcurrentHashMap<>();
    private final Map<Class<?>, ServiceStatistics<?>> serviceStatistics = new ConcurrentHashMap<>();
    private final List<Service.Listener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean listenersLoaded = new AtomicBoolean(false);

    private static final AtomicBoolean quiet = new AtomicBoolean(false);
    final net.microfalx.lang.Logger quietLogger = net.microfalx.lang.Logger.create();

    public Collection<Service> getServices() {
        return serviceImplementations.values().stream()
                .filter(Objects::nonNull).toList();
    }

    public Collection<Service> getServiceProxies() {
        return serviceProxies.values().stream().map(Reference::get)
                .filter(Objects::nonNull).toList();
    }

    @SuppressWarnings("unchecked")
    public <S extends Service> S lookup(Class<S> serviceClass) {
        requireNonNull(serviceClass);
        synchronized (ServiceLocatorImpl.class) {
            S service = (S) services.get(serviceClass);
            if (service == null) {
                LOGGER.debug("Loading service {}", ClassUtils.getName(serviceClass));
                service = ServiceLocator.load(serviceClass);
                register(service);
            }
            return service;
        }
    }

    @Override
    public void initialize(Object... objects) {
        // empty for now
    }

    public void shutdown() {
        synchronized (ServiceLocatorImpl.class) {
            LOGGER.debug("Shutting down services");
            Collection<Service> loadedServices = getServices();
            loadedServices.forEach(service -> {
                stopService(service);
                notifyStopped(service);
            });
            loadedServices.forEach(ServiceLocatorImpl::destroyService);
            services.clear();
            serviceImplementations.clear();
            serviceStatistics.clear();
        }
    }

    @SuppressWarnings("unchecked")
    public <S extends Service> void shutdown(Class<S> serviceClass) {
        requireNonNull(serviceClass);
        synchronized (ServiceLocatorImpl.class) {
            LOGGER.debug("Shutting down service {}", ClassUtils.getName(serviceClass));
            S service = (S) services.get(serviceClass);
            if (service != null) {
                Class<S> implementationClass = (Class<S>) service.getClass();
                getServiceInterfaces(service).forEach(services::remove);
                serviceImplementations.remove(implementationClass);
                try {
                    if (service instanceof Releasable) {
                        try {
                            ((Releasable) service).release();
                        } catch (Exception e) {
                            LOGGER.atWarn().setCause(e).log("Error while releasing service {}", ClassUtils.getName(serviceClass));
                        }
                    }
                    stopService(service);
                    notifyStopped(service);
                } catch (Exception e) {
                    LOGGER.atWarn().setCause(e).log("Error while shutting down service {}", ClassUtils.getName(serviceClass));
                }
                serviceStatistics.values().removeIf(statistics -> isSubClassOf(statistics.getService(), implementationClass));
            }
        }
    }

    public <S extends Service> boolean isLoaded(Class<S> serviceClass) {
        requireNonNull(serviceClass);
        return services.containsKey(serviceClass);
    }

    @SuppressWarnings("unchecked")
    public <S extends Service> void register(S service) {
        requireNonNull(service);
        loadDependencies(service.getClass());
        synchronized (ServiceLocatorImpl.class) {
            loadListeners();
            Collection<Class<?>> serviceInterfaces = getServiceInterfaces(service);
            if (!serviceInterfaces.isEmpty()) {
                serviceInterfaces.forEach(sc -> services.put(sc, service));
            } else {
                services.put(service.getClass(), service);
            }
            initialize(service, (Class<S>) service.getClass());
            if (service instanceof ServiceProxy) {
                Class<?> serviceClass = getRealServiceClass(service);
                serviceProxies.put(serviceClass, new WeakReference<>(service));
            } else {
                serviceImplementations.put(service.getClass(), service);
            }
            serviceStatistics.computeIfPresent(service.getClass(),
                    (cls, statistics) -> statistics.getService() == service ? statistics : null);
        }
    }

    public <S extends Service> Service.Statistics<S> getStatistics(S service) {
        return doGetStatistics(service);
    }

    public Collection<Service.Statistics<?>> getStatistics() {
        return List.copyOf(serviceStatistics.values());
    }


    public <S extends Service> void report(S service, Service.Metric metric, long value) {
        requireNonNull(service);
        requireNonNull(metric);
        doGetStatistics(service).apply(metric, value);
        notifyEvent(service, metric, value);
    }

    public void addListener(Service.Listener listener) {
        requireNonNull(listener);
        listeners.add(listener);
    }

    public void removeListener(Service.Listener listener) {
        requireNonNull(listener);
        listeners.remove(listener);
    }

    public Collection<Service.Listener> getListeners() {
        loadListeners();
        return List.copyOf(listeners);
    }

    public boolean isQuiet() {
        return quiet.get();
    }

    public void setQuiet(boolean value) {
        quiet.set(value);
    }


    public String getLog() {
        return quietLogger.getOutput();
    }

    public <S extends Service> Object getRealService(S service) {
        if (service instanceof ServiceProxy) {
            return ((ServiceProxy) service).getDelegate();
        } else {
            return service;
        }
    }

    private void loadListeners() {
        if (listenersLoaded.compareAndSet(false, true)) {
            ServiceLoader.load(Service.Listener.class).forEach(listeners::add);
            listeners.addAll(ClassUtils.resolveProviderInstances(Service.Listener.class));
        }
    }

    private void notifyStarted(Service service) {
        for (Service.Listener listener : listeners) {
            try {
                listener.onServiceStarted(service);
            } catch (Exception e) {
                LOGGER.atWarn().setCause(e).log("Failed to notify listener {} that service {} started",
                        ClassUtils.getName(listener), ClassUtils.getName(service));
            }
        }
    }

    private void notifyStopped(Service service) {
        for (Service.Listener listener : listeners) {
            try {
                listener.onServiceStopped(service);
            } catch (Exception e) {
                LOGGER.atWarn().setCause(e).log("Failed to notify listener {} that service {} stopped",
                        ClassUtils.getName(listener), ClassUtils.getName(service));
            }
        }
    }

    private void notifyEvent(Service service, Service.Metric metric, long value) {
        for (Service.Listener listener : listeners) {
            try {
                listener.onServiceEvent(service, metric, value);
            } catch (Exception e) {
                LOGGER.atWarn().setCause(e).log("Failed to notify listener {} about event {} for service {}",
                        ClassUtils.getName(listener), metric, ClassUtils.getName(service));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <S extends Service> ServiceStatistics<S> doGetStatistics(S service) {
        requireNonNull(service);
        Class<?> serviceClass = getRealServiceClass(service);
        return (ServiceStatistics<S>) serviceStatistics.computeIfAbsent(serviceClass,
                cls -> new ServiceStatistics<>(service));
    }


    static void startService(Object service) {
        if (service instanceof Service.Lifecycle) {
            try {
                ((Service.Lifecycle) service).start();
            } catch (Exception e) {
                LOGGER.atError().setCause(e).log("Failed to stop service {}",
                        ClassUtils.getName(service));
            }
        }
    }

    static void stopService(Object service) {
        if (service instanceof Service.Lifecycle) {
            try {
                ((Service.Lifecycle) service).stop();
            } catch (Exception e) {
                LOGGER.atWarn().setCause(e).log("Failed to stop service {}",
                        ClassUtils.getName(service));
            }
        }
    }

    static void destroyService(Object service) {
        if (service instanceof Releasable) {
            try {
                ((Releasable) service).release();
            } catch (Exception e) {
                LOGGER.atWarn().setCause(e).log("Failed to release service {}",
                        ClassUtils.getName(service));
            }
        }
    }

    private static <S extends Service> Collection<Class<?>> getServiceInterfaces(S service) {
        return ClassUtils.getInterfaces(service.getClass()).stream()
                .filter(Service.class::isAssignableFrom)
                .filter(sc -> sc != Service.class).toList();
    }

    private void initShutdown() {
        if (initialized.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new ServiceLocatorThread());
        }
    }

    private <S extends Service> void initialize(S service, Class<S> serviceClass) {
        if (!isSubClassOf(service, serviceClass)) {
            throw new ServiceException("The service " + ClassUtils.getName(service) + " is not a subclass of "
                    + ClassUtils.getName(serviceClass));
        }
        // we do not initialize external services (like Spring beans) as they are initialized by the framework
        if (service instanceof ServiceProxy) return;
        if (service instanceof Initializable) ((Initializable) service).initialize();
        startService(service);
        notifyStarted(service);
    }

    @SuppressWarnings("unchecked")
    private <S extends Service> void loadDependencies(Class<S> serviceClass) {
        DependsOn dependsOnAnnot = AnnotationUtils.getAnnotation(serviceClass, DependsOn.class);
        if (dependsOnAnnot == null) return;
        for (Class<?> clazz : dependsOnAnnot.classes()) {
            if (ClassUtils.isSubClassOf(clazz, Service.class)) {
                lookup((Class<? extends Service>) clazz);
            } else {
                throw new ServiceException("The class " + ClassUtils.getName(clazz) + " is not a subclass of "
                        + ClassUtils.getName(Service.class));
            }
        }
    }

    private static class ServiceLocatorThread extends Thread {

        @Override
        public void run() {
            ServiceLocator.getInstance().shutdown();
            super.run();
        }
    }


}
