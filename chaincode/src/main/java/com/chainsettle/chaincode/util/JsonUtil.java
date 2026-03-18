package com.chainsettle.chaincode.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.hyperledger.fabric.shim.ChaincodeException;

public final class JsonUtil {
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private JsonUtil() {
    }

    public static String toJson(final Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (IOException exception) {
            throw new ChaincodeException("Unable to serialize object", "JSON_SERIALIZATION_ERROR");
        }
    }

    public static <T> T fromJson(final String value, final Class<T> targetType) {
        try {
            return MAPPER.readValue(value, targetType);
        } catch (IOException exception) {
            throw new ChaincodeException("Unable to deserialize JSON into " + targetType.getSimpleName(),
                "JSON_DESERIALIZATION_ERROR");
        }
    }

    public static Map<String, Object> readMap(final String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return MAPPER.readValue(value, new TypeReference<>() {
            });
        } catch (IOException exception) {
            throw new ChaincodeException("Unable to deserialize metadata JSON", "JSON_DESERIALIZATION_ERROR");
        }
    }

    public static <T> List<T> readList(final String value, final Class<T> targetType) {
        try {
            return MAPPER.readValue(
                value,
                MAPPER.getTypeFactory().constructCollectionType(List.class, targetType)
            );
        } catch (IOException exception) {
            throw new ChaincodeException("Unable to deserialize JSON array", "JSON_DESERIALIZATION_ERROR");
        }
    }
}

