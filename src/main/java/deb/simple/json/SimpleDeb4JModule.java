package deb.simple.json;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.module.SimpleModule;

public class SimpleDeb4JModule extends SimpleModule {
    public SimpleDeb4JModule() {
        super("simple-deb4j-module");
        addDeserializer(Integer.class, HexIntegerDeserializer.INSTANCE);
        addDeserializer(Integer.TYPE, HexIntegerDeserializer.INSTANCE);
    }

    static class HexIntegerDeserializer extends ValueDeserializer<Integer> {
        static final HexIntegerDeserializer INSTANCE = new HexIntegerDeserializer();

        @Override
        public Integer deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
            String s = p.getValueAsString();
            if (s == null) return p.getIntValue();

            return Integer.decode(s); // handles "0x755", "#755", decimal, negative
        }
    }
}
