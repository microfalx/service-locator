package net.microfalx.service.api;

import net.microfalx.lang.ClassUtils;
import net.microfalx.lang.Initializable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;

/**
 * A factory which provides implementations of services.
 * <p>
 * The factory uses the JDK {@link ServiceLoader} and {@link ClassUtils#resolveProviderInstances(Class)}
 * to discover both the implementations of services and the implementations of {@link Service.Listener}.
 */
public abstract class ServiceLocator {

    private static final Logger LOGGER = Logger.get(ServiceLocator.class);

    static ThreadLocal<ServiceLocator> CURRENT = new ThreadLocal<>();
    static volatile ServiceLocator INSTANCE;

    /**
     * Returns the service locator for the current thread. If no service locator is set for the current thread,
     * the default service locator is returned.
     *
     * @return a non-null instance
     */
    public static ServiceLocator current() {
        ServiceLocator serviceLocator = CURRENT.get();
        return serviceLocator != null ? serviceLocator : getInstance();
    }

    /**
     * Attaches a service locator to the current thread.
     *
     * @param serviceLocator the new service locator
     */
    public static void set(ServiceLocator serviceLocator) {
        requireNonNull(serviceLocator);
        CURRENT.set(serviceLocator);
    }

    /**
     * Removes the service locator from the current thread.
     */
    public static void reset() {
        CURRENT.remove();
    }

    /**
     * Returns the default service locator.
     *
     * @return a non-null instance
     */
    public static ServiceLocator getInstance() {
        if (INSTANCE == null) {
            synchronized (ServiceLocator.class) {
                if (INSTANCE == null) {
                    INSTANCE = load(ServiceLocator.class);
                    ((Initializable) INSTANCE).initialize();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Looks up a service.
     * <p>
     * The locator uses the Java {@link ServiceLoader} mechanism to load implementations of services and
     * {@link ClassUtils#resolveProviderInstances(Class)}. If a service has already been loaded (and initialized),
     * it will be returned from the cache.
     *
     * @param serviceClass the class of the service to load
     * @param <S>          the type of the service
     * @return an instance of the requested service
     */
    public abstract <S extends Service> S lookup(Class<S> serviceClass);

    /**
     * Shuts down the service locator.
     * <p>
     * This method should be called when the application is shutting down to ensure
     * that all services are properly stopped.
     * <p>
     * For the default service locator, this method is automatically called when the JVM shuts down.
     */
    public abstract void shutdown();

    /**
     * Returns a collection of all loaded services.
     *
     * @return a non-null instance
     */
    public abstract Collection<Service> getServices();

    /**
     * Returns a collection of external services.
     *
     * @return a non-null instance
     */
    public abstract Collection<Service> getServiceProxies();

    /**
     * Checks if a service is loaded.
     *
     * @param serviceClass the class of the service to check
     * @param <S>          the type of the service
     * @return true if the service is loaded, false otherwise
     */
    public abstract <S extends Service> boolean isLoaded(Class<S> serviceClass);

    /**
     * Registers a new service.
     *
     * @param service the service instance
     * @param <S>     the service type
     */
    public abstract <S extends Service> void register(S service);

    /**
     * Returns the statistics collected for a service.
     * <p>
     * The statistics are created on demand, they are updated out of the events reported with
     * {@link #report(Service, Service.Metric)} and they live for as long as the service is registered.
     *
     * @param service the service
     * @param <S>     the service type
     * @return a non-null instance
     */
    public abstract <S extends Service> Service.Statistics<S> getStatistics(S service);

    /**
     * Returns the statistics collected for all registered services, usually used to produce a report.
     *
     * @return a non-null instance
     */
    public abstract Collection<Service.Statistics<?>> getStatistics();

    /**
     * Reports an event about a service.
     * <p>
     * The event is applied to the statistics of the service with a value of one, which increments the counters
     * changed by the event:
     * <pre>
     *     ServiceLocator.report(service, Service.Event.SUCCESS);
     * </pre>
     *
     * @param service the service which reports the event
     * @param metric  the event
     * @param <S>     the service type
     * @see Service#report(Service.Metric)
     */
    public final <S extends Service> void report(S service, Service.Metric metric) {
        report(service, metric, 1);
    }

    /**
     * Reports an event about a service.
     * <p>
     * The value carries how much the event changes the statistics: the amount added to the counters changed by
     * the event or the new value of a gauge (see {@link Service.Metric#isGauge()}):
     * <pre>
     *     ServiceLocator.report(service, Service.Event.MEMORY_USAGE, 1024);
     * </pre>
     * <p>
     * Events can be reported from any thread.
     *
     * @param service the service which reports the event
     * @param metric  the event
     * @param value   the value carried by the event
     * @param <S>     the service type
     * @see Service#report(Service.Metric, long)
     */
    public abstract <S extends Service> void report(S service, Service.Metric metric, long value);

    /**
     * Registers a listener which will be notified about the lifecycle and the events of all services.
     * <p>
     * Most listeners should be discovered automatically, either through the JDK {@link ServiceLoader} or the
     * {@code @Provider} pattern (see {@link Service.Listener}); this method exists for listeners which cannot be
     * discovered this way (for example, listeners created dynamically).
     *
     * @param listener the listener
     * @see #removeListener(Service.Listener)
     */
    public abstract void addListener(Service.Listener listener);

    /**
     * Removes a previously registered listener.
     *
     * @param listener the listener
     * @see #addListener(Service.Listener)
     */
    public abstract void removeListener(Service.Listener listener);

    /**
     * Returns the listeners registered with this locator, discovering them (via the JDK {@link ServiceLoader} and
     * the {@code @Provider} pattern) on the first call.
     *
     * @return a non-null instance
     */
    public abstract Collection<Service.Listener> getListeners();

    /**
     * Returns whether the service locator (and services) is in quiet mode.
     *
     * @return {@code true} if quiet, {@code false} otherwise
     */
    public abstract boolean isQuiet();

    /**
     * Changes whether the service locator (and services) is in quiet mode.
     *
     * @param value {@code true} if quiet, {@code false} otherwise
     */
    public abstract void setQuiet(boolean value);

    /**
     * Returns (and resets) the service log, which is a log of all messages logged while the service locator
     * (and services).
     *
     * @return a non-null instance
     */
    public abstract String getLog();

    /**
     * Returns the reference to the real service implementation class. If the service is a proxy,
     * it will return the underlying service class.
     *
     * @param service the service instance
     * @param <S>     the service type
     * @return the real service implementation class
     */
    public final <S extends Service> Class<?> getRealServiceClass(S service) {
        return getRealService(service).getClass();
    }

    /**
     * Returns the reference to the real service implementation. If the service is a proxy,
     * it will return the underlying service.
     *
     * @param service the service instance
     * @param <S>     the service type
     * @return the real service implementation
     */
    public abstract <S extends Service> Object getRealService(S service);

    /**
     * Loads a service/component implementation for the given type.
     *
     * @param type the class of the service/component to load
     * @param <T>  the type of the service/component
     * @return an instance of the service/component
     */
    public static <T> T load(Class<T> type) {
        Collection<T> services = new ArrayList<>();
        ServiceLoader.load(type).stream().forEach(s -> services.add(s.get()));
        services.addAll(ClassUtils.resolveProviderInstances(type));
        if (services.size() > 1) {
            throw new ServiceException("Multiple implementations located for type " + type.getName()
                    + ": " + services.stream().map(ClassUtils::getName).collect(Collectors.joining(",")));
        } else if (!services.isEmpty()) {
            return services.iterator().next();
        } else {
            throw new ServiceException("A service/component of type " + type.getName() + " could not be found");
        }
    }


}
