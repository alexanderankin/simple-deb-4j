package deb.simple.gen_schema;

import com.github.victools.jsonschema.generator.*;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import com.github.victools.jsonschema.module.jackson.JacksonSchemaModule;
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationModule;
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationOption;
import deb.simple.build_deb.DebPackageConfig;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.core.util.Separators;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * the result is not currently acceptable, hopefully one day it will be
 */
@Slf4j
public class GenSchema {
    static final TypeReference<LinkedHashMap<String, Object>> LINKED_HASH_MAP_TYPE_REFERENCE = new TypeReference<>() {
    };

    @SneakyThrows
    public static void main(String[] args) {
        var configBuilder = new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                .with(new JacksonSchemaModule(
                        JacksonOption.ALWAYS_REF_SUBTYPES
                ))
                .with(new JakartaValidationModule(
                        JakartaValidationOption.INCLUDE_PATTERN_EXPRESSIONS,
                        JakartaValidationOption.NOT_NULLABLE_FIELD_IS_REQUIRED
                ))
                .with(Option.DEFINITIONS_FOR_MEMBER_SUPERTYPES)
                .with(Option.MAP_VALUES_AS_ADDITIONAL_PROPERTIES);

        configBuilder.forTypesInGeneral()
                .withCustomDefinitionProvider((javaType, context) -> {
                    // if (!javaType.getErasedType().getSimpleName().equals("Map") && Map.class.isAssignableFrom(javaType.getErasedType())) {
                    if (javaType.getErasedType() != Map.class && Map.class.isAssignableFrom(javaType.getErasedType())) {
                        var jsonMap = context.getTypeContext().resolve(Map.class, String.class, Object.class);
                        return new CustomDefinition(context.createDefinition(jsonMap), true);
                    }
                    return null;
                });


        SchemaGeneratorConfig config = configBuilder
                .build();

        SchemaGenerator generator = new SchemaGenerator(config);
        ObjectMapper objectMapper = generator.getConfig().getObjectMapper();

        var curDir = Path.of(System.getProperty("user.dir"));
        var schemaFile = curDir.resolve(Path.of("src", "public", "simple-deb-config-schema.json"));
        log.info("writing to schemaFile: {}", schemaFile);


        Class<DebPackageConfig> configClass = DebPackageConfig.class;
        var configSchema = objectMapper.treeToValue(generator.generateSchema(configClass), LINKED_HASH_MAP_TYPE_REFERENCE);

        for (var key : List.of("required", "properties", "$defs"))
            configSchema.put(key, configSchema.remove(key));

        var configSchemaString = objectMapper.writer()
                .with(new DefaultPrettyPrinter()
                        .withSeparators(
                                Separators.createDefaultInstance()
                                        .withObjectEntrySpacing(Separators.Spacing.AFTER)
                                        .withArrayElementSpacing(Separators.Spacing.AFTER)
                                        .withObjectNameValueSpacing(Separators.Spacing.AFTER)
                        ))
                .writeValueAsString(configSchema);

        Files.writeString(schemaFile, configSchemaString);
    }
}
