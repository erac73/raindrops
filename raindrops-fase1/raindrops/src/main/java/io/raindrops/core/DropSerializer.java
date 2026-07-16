package io.raindrops.core;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.IOException;
import java.math.BigInteger;
import java.util.HexFormat;
import java.util.List;

/**
 * Serializador de drops a formato JSON para transporte y almacenamiento.
 *
 * <p>Utiliza Jackson ObjectMapper con modulos personalizados para:
 * <ul>
 *   <li>Serializar campos {@code byte[]} como cadenas hexadecimales.</li>
 *   <li>Serializar {@code BigInteger} (coordenada y) como cadenas hexadecimales.</li>
 *   <li>Deserializar objetos {@link Drop} desde JSON plano.</li>
 * </ul>
 *
 * <p>Todos los metodos estaticos son thread-safe (el {@link ObjectMapper} subyacente
 * es thread-safe una vez configurado).
 *
 * <p>Formato JSON de un Drop:
 * <pre>{@code
 * {
 *   "id": "a1b2c3...",
 *   "x": 1,
 *   "y": "deadbeef...",
 *   "mac": "1234abcd...",
 *   "ttl": 1700000000
 * }
 * }</pre>
 */
public final class DropSerializer {

    private static final ObjectMapper MAPPER = createMapper();
    private static final HexFormat HEX = HexFormat.of();

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();

        SimpleModule module = new SimpleModule("DropModule");
        module.addSerializer(byte[].class, new JsonSerializer<byte[]>() {
            @Override
            public void serialize(byte[] value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                gen.writeString(HEX.formatHex(value));
            }
        });
        module.addSerializer(BigInteger.class, new JsonSerializer<BigInteger>() {
            @Override
            public void serialize(BigInteger value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                gen.writeString(value.toString(16));
            }
        });
        module.addDeserializer(Drop.class, new JsonDeserializer<Drop>() {
            @Override
            public Drop deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                JsonNode node = p.getCodec().readTree(p);
                byte[] id = HEX.parseHex(node.get("id").asText());
                int x = node.get("x").asInt();
                BigInteger y = new BigInteger(node.get("y").asText(), 16);
                byte[] mac = HEX.parseHex(node.get("mac").asText());
                long ttl = node.get("ttl").asLong();
                return new Drop(id, x, y, mac, ttl);
            }
        });
        mapper.registerModule(module);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    /**
     * Serializa un drop a una cadena JSON.
     *
     * @param drop  El drop a serializar.
     * @return      Representacion JSON del drop.
     * @throws RuntimeException si ocurre un error durante la serializacion.
     */
    public static String toJson(Drop drop) {
        try {
            return MAPPER.writeValueAsString(drop);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializing drop", e);
        }
    }

    /**
     * Deserializa un drop desde una cadena JSON.
     *
     * @param json  Cadena JSON que representa un drop.
     * @return      El objeto Drop deserializado.
     * @throws RuntimeException si ocurre un error durante la deserializacion.
     */
    public static Drop fromJson(String json) {
        try {
            return MAPPER.readValue(json, Drop.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error deserializing drop", e);
        }
    }

    /**
     * Serializa una lista de drops a una cadena JSON (array).
     *
     * @param drops  Lista de drops a serializar.
     * @return       Representacion JSON del array de drops.
     * @throws RuntimeException si ocurre un error durante la serializacion.
     */
    public static String toJson(List<Drop> drops) {
        try {
            return MAPPER.writeValueAsString(drops);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializing drop list", e);
        }
    }

    /**
     * Deserializa una lista de drops desde una cadena JSON (array).
     *
     * @param json  Cadena JSON que representa un array de drops.
     * @return      Lista de objetos Drop deserializados.
     * @throws RuntimeException si ocurre un error durante la deserializacion.
     */
    public static List<Drop> fromJsonList(String json) {
        try {
            JavaType type = MAPPER.getTypeFactory().constructCollectionType(List.class, Drop.class);
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error deserializing drop list", e);
        }
    }

    private DropSerializer() {
    }
}
