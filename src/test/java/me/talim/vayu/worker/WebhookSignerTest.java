package me.talim.vayu.worker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WebhookSignerTest {

    private final WebhookSigner signer = new WebhookSigner();

    @Test
    void signsTheTimestampAndPayload() {
        String signature = signer.sign("secret", "1700000000", "{\"amount\":100}");

        assertThat(signature)
                .isEqualTo("3870328946388ac07506ef4c276d255276ce6d1b96bb79696b8f219699f6c006");
    }

    @Test
    void changingThePayloadChangesTheSignature() {
        String first = signer.sign("secret", "1700000000", "{\"amount\":100}");
        String second = signer.sign("secret", "1700000000", "{\"amount\":101}");

        assertThat(first).isNotEqualTo(second);
    }
}
