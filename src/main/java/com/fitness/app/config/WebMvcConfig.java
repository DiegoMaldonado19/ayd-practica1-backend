package com.fitness.app.config;

import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Query parameters that carry an enum are written in lower case in the contract:
 * "Todos aceptan format=json|csv" and "group_by=week|month" (03-API-REST §3.10).
 * Spring MVC binds them with Enum.valueOf, which is case sensitive, so those
 * documented values were answered with a 500.
 *
 * ApplicationConversionService is the same set of converters Boot already uses to
 * bind application.yml -that is why gym.freeze.max-days-per-cycle reaches
 * maxDaysPerCycle-, and its LenientStringToEnumConverterFactory is exactly the
 * case-insensitive binding this needs. A value that matches no constant still
 * throws, and GlobalExceptionHandler turns that into the 400 the contract asks for.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer
{
    @Override
    public void addFormatters(FormatterRegistry registry)
    {
        ApplicationConversionService.addApplicationConverters(registry);
    }
}
