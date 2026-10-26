package me.talim.vayu.worker;

import java.net.http.HttpClient;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(WorkerProperties.class)
public class WorkerConfig {

    @Bean
    ThreadPoolTaskExecutor deliveryExecutor(WorkerProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.concurrency());
        executor.setMaxPoolSize(properties.concurrency());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix("delivery-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    @Bean
    MeterBinder deliveryExecutorMetrics(
            @Qualifier("deliveryExecutor") ThreadPoolTaskExecutor executor
    ) {
        return registry -> {
            Gauge.builder("vayu.worker.executor.active", executor,
                            ThreadPoolTaskExecutor::getActiveCount)
                    .description("Delivery executor threads currently running tasks")
                    .register(registry);

            Gauge.builder("vayu.worker.executor.queue.depth", executor,
                            e -> e.getThreadPoolExecutor().getQueue().size())
                    .description("Deliveries waiting in the executor queue")
                    .register(registry);

            Gauge.builder("vayu.worker.executor.queue.remaining", executor,
                            e -> e.getThreadPoolExecutor().getQueue().remainingCapacity())
                    .description("Remaining capacity in the delivery executor queue")
                    .register(registry);
        };
    }

    @Bean
    RestClient deliveryRestClient(WorkerProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}
