package org.camunda.tngp.broker;

import java.io.InputStream;

import org.camunda.tngp.broker.logstreams.LogStreamsComponent;
import org.camunda.tngp.broker.system.SystemContext;
import org.camunda.tngp.broker.system.metrics.MetricsComponent;
import org.camunda.tngp.broker.system.threads.ThreadingComponent;
import org.camunda.tngp.broker.taskqueue.TaskQueueComponent;
import org.camunda.tngp.broker.transport.TransportComponent;

public class Broker implements AutoCloseable
{
    protected final SystemContext brokerContext;

    public Broker(String configFileLocation)
    {
        brokerContext = new SystemContext(configFileLocation);
        start();
    }

    public Broker(InputStream configStream)
    {
        brokerContext = new SystemContext(configStream);
        start();
    }

    protected void start()
    {
        brokerContext.addComponent(new MetricsComponent());
        brokerContext.addComponent(new ThreadingComponent());
        brokerContext.addComponent(new TransportComponent());
        brokerContext.addComponent(new LogStreamsComponent());
        brokerContext.addComponent(new TaskQueueComponent());

        brokerContext.init();
    }

    @Override
    public void close()
    {
        brokerContext.close();
        System.out.println("Broker closed.");
    }
}
