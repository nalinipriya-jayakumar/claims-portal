package com.redrivermutual.claims.adapter.in.web;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Strips leading and trailing whitespace from every JSON string, and treats a string that is
 * empty after stripping as absent. So {@code " Jane "} arrives as {@code "Jane"}, a blank required
 * field is reported as {@code REQUIRED} by the contract's {@code required} list, and a blank
 * optional field (phone) is simply not provided.
 */
@Component
public class TrimmingStringModule extends SimpleModule {

    public TrimmingStringModule() {
        super("trimming-strings");
        addDeserializer(String.class, new StdScalarDeserializer<>(String.class) {
            @Override
            public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                String value = StringDeserializer.instance.deserialize(parser, context);
                if (value == null) {
                    return null;
                }
                String stripped = value.strip();
                return stripped.isEmpty() ? null : stripped;
            }
        });
    }
}
