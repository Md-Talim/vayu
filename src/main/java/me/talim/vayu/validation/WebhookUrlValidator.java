package me.talim.vayu.validation;

import java.net.URI;
import java.net.URISyntaxException;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class WebhookUrlValidator implements ConstraintValidator<ValidWebhookUrl, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return false;
        }

        try {
            URI uri = new URI(value);
            boolean validScheme =
                "http".equalsIgnoreCase(uri.getScheme()) ||
                "https".equalsIgnoreCase(uri.getScheme());

            return validScheme && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (URISyntaxException exception) {
            return false;
        }
    }
}
