package me.talim.vayu.validation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Documented
@Constraint(validatedBy = WebhookUrlValidator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ FIELD, PARAMETER, ANNOTATION_TYPE })
public @interface ValidWebhookUrl {
    String message() default "url must be valid HTTP or HTTPS URL";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
