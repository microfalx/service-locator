package net.microfalx.service.core;

import net.microfalx.service.api.Service;
import net.microfalx.service.api.ServiceLocator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static net.microfalx.service.api.Service.Metric.*;
import static org.junit.jupiter.api.Assertions.*;

class ServiceLocatorImplTest {

    private ServiceLocatorImpl serviceLocator;

    @BeforeEach
    void setup() {
        serviceLocator = new ServiceLocatorImpl();
        ServiceLocator.set(serviceLocator);
        TestServiceListener.reset();
    }

    @Test
    void service1() {
        Test1Service instance = Test1Service.getInstance();
        assertNotNull(instance);
        assertTrue(serviceLocator.isLoaded(Test1Service.class));
    }

    @Test
    void service2() {
        Test2Service instance = Test2Service.getInstance();
        assertNotNull(instance);
        assertTrue(serviceLocator.isLoaded(Test2Service.class));
    }

    @Test
    void register() {
        serviceLocator.register(new Test1ServiceImpl());
        serviceLocator.register(new Test2ServiceImpl());
        assertTrue(serviceLocator.isLoaded(Test1Service.class));
        assertTrue(serviceLocator.isLoaded(Test2Service.class));
    }

    @Test
    void statistics() {
        Test1ServiceImpl test1Service = new Test1ServiceImpl();
        serviceLocator.register(test1Service);
        Service.Statistics<Test1ServiceImpl> statistics = serviceLocator.getStatistics(test1Service);
        assertNotNull(statistics.getService());
    }

    @Test
    void reportCounters() {
        Test1ServiceImpl test1Service = new Test1ServiceImpl();
        serviceLocator.register(test1Service);
        serviceLocator.report(test1Service, SUCCESS);
        serviceLocator.report(test1Service, SUCCESS, 4);
        test1Service.report(WARNING);
        test1Service.report(ERROR, 2);
        test1Service.report(FAILURE);

        Service.Statistics<Test1ServiceImpl> statistics = serviceLocator.getStatistics(test1Service);
        assertEquals(5, statistics.getSuccessCount());
        assertEquals(1, statistics.getWarningCount());
        assertEquals(2, statistics.getErrorCount());
        assertEquals(1, statistics.getFailedCount());
    }

    @Test
    void reportGauges() {
        Test1ServiceImpl test1Service = new Test1ServiceImpl();
        serviceLocator.register(test1Service);
        test1Service.report(MEMORY_USAGE, 2048);
        test1Service.report(MEMORY_USAGE, 1024);
        test1Service.report(THREAD_STARTED);
        test1Service.report(THREAD_STARTED);
        test1Service.report(THREAD_STOPPED);

        Service.Statistics<? extends Service> statistics = test1Service.getStatistics();
        assertEquals(1024, statistics.getMemoryUsage());
        assertEquals(1, statistics.getThreadCount());

        test1Service.report(THREAD_COUNT, 10);
        assertEquals(10, statistics.getThreadCount());
    }

    @Test
    void reportTasks() {
        Test1ServiceImpl test1Service = new Test1ServiceImpl();
        serviceLocator.register(test1Service);
        test1Service.report(TASK_SCHEDULED, 3);
        test1Service.report(TASK_STARTED, 2);

        Service.Statistics<? extends Service> statistics = test1Service.getStatistics();
        assertEquals(1, statistics.getTaskPendingCount());
        assertEquals(2, statistics.getTaskRunningCount());

        test1Service.report(TASK_SUCCEEDED);
        test1Service.report(TASK_FAILED);
        assertEquals(0, statistics.getTaskRunningCount());
        assertEquals(1, statistics.getSuccessCount());
        assertEquals(1, statistics.getFailedCount());

        // counters never go below zero
        test1Service.report(TASK_SUCCEEDED);
        assertEquals(0, statistics.getTaskRunningCount());
    }

    @Test
    void getAllStatistics() {
        serviceLocator.register(new Test1ServiceImpl());
        serviceLocator.register(new Test2ServiceImpl());
        Test1Service.getInstance().report(WARNING);
        assertEquals(1, serviceLocator.getStatistics().size());
    }

    @Test
    void discoveredListenerIsNotified() {
        Test1ServiceImpl test1Service = new Test1ServiceImpl();
        serviceLocator.register(test1Service);
        assertTrue(serviceLocator.getListeners().stream().anyMatch(l -> l instanceof TestServiceListener));
        assertEquals(1, TestServiceListener.started.size());
        assertSame(test1Service, TestServiceListener.started.get(0));

        test1Service.report(SUCCESS, 3);
        assertEquals(1, TestServiceListener.events.size());
        Object[] event = TestServiceListener.events.get(0);
        assertSame(test1Service, event[0]);
        assertEquals(SUCCESS, event[1]);
        assertEquals(3L, event[2]);

        serviceLocator.shutdown(Test1Service.class);
        assertEquals(1, TestServiceListener.stopped.size());
        assertSame(test1Service, TestServiceListener.stopped.get(0));
    }

    @Test
    void globalShutdownNotifiesStopped() {
        Test1ServiceImpl test1Service = new Test1ServiceImpl();
        serviceLocator.register(test1Service);
        serviceLocator.shutdown();
        assertEquals(1, TestServiceListener.stopped.size());
        assertSame(test1Service, TestServiceListener.stopped.get(0));
    }

    @Test
    void manuallyRegisteredListener() {
        List<Service> started = new ArrayList<>();
        Service.Listener listener = new Service.Listener() {
            @Override
            public void onServiceStarted(Service service) {
                started.add(service);
            }
        };
        serviceLocator.addListener(listener);
        try {
            Test1ServiceImpl test1Service = new Test1ServiceImpl();
            serviceLocator.register(test1Service);
            assertEquals(1, started.size());
            assertSame(test1Service, started.get(0));
        } finally {
            serviceLocator.removeListener(listener);
        }
    }

}