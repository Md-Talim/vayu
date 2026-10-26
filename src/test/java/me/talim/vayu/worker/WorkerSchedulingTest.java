package me.talim.vayu.worker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import me.talim.vayu.support.PostgresIntegrationTest;

@TestPropertySource(properties = {
        "vayu.worker.enabled=true",
        "vayu.worker.poll-interval=1h",
        "vayu.worker.reaper.enabled=false"
})
class WorkerSchedulingTest extends PostgresIntegrationTest {

    @Autowired
    private DeliveryPoller poller;

    @Autowired
    private DeliveryReaperTrigger reaperTrigger;

    @Test
    void scheduledWorkerBeansStart() {
        assertThat(poller).isNotNull();
        assertThat(reaperTrigger).isNotNull();
    }
}
