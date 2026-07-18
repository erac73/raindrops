package io.raindrops.storage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.raindrops.core.Drop;
import io.raindrops.core.DropSerializer;
import io.raindrops.core.RainDropsCore;
import io.raindrops.core.RainMap;
import io.raindrops.storage.config.PeerConfig;
import io.raindrops.storage.model.RainMapEntity;
import io.raindrops.storage.repository.RainMapRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Servicio de gestión de RainMaps en el nodo Storage.
 *
 * <p>Responsable del almacenamiento, consulta y construcción de RainMaps.
 * Cada RainMap contiene el índice cifrado de drops y sus metadatos (n, k),
 * y se replica automáticamente a los peers configurados.</p>
 *
 * <p>Este servicio utiliza {@link RainMapRepository} para la persistencia
 * y {@link ReplicationService} para la réplica a nodos peer.</p>
 */
@Service
public class RainMapService {

    private static final Logger log = LoggerFactory.getLogger(RainMapService.class);

    private final RainMapRepository rainMapRepository;
    private final ReplicationService replicationService;
    private final ObjectMapper mapper;
    private final String nodeId;

    /**
     * Constructor del RainMapService.
     *
     * @param rainMapRepository repositorio JPA para la persistencia de RainMaps.
     * @param replicationService servicio de réplica a nodos peer.
     * @param mapper            ObjectMapper compartido para serialización JSON.
     * @param nodeId            identificador único de este nodo Storage.
     */
    public RainMapService(RainMapRepository rainMapRepository,
                          ReplicationService replicationService,
                          ObjectMapper mapper,
                          @Value("${NODE_ID:storage-node}") String nodeId) {
        this.rainMapRepository = rainMapRepository;
        this.replicationService = replicationService;
        this.mapper = mapper;
        this.nodeId = nodeId;
    }

    /**
     * Almacena un RainMap en la base de datos y lo replica a los peers.
     *
     * <p>Si ya existe un RainMap con el mismo ID, se actualiza su contenido.</p>
     *
     * @param rainMapId          identificador único del RainMap.
     * @param encryptedPayload   payload cifrado del RainMap (nonce + ciphertext).
     * @param n                  número total de shares.
     * @param k                  umbral de reconstrucción.
     * @param ciphertextHex      cifrado adicional en hexadecimal (puede ser null).
     * @return el ID del RainMap almacenado.
     */
    @Transactional
    public String storeRainMap(String rainMapId, byte[] encryptedPayload, int n, int k, String ciphertextHex) {
        HexFormat hex = HexFormat.of();
        RainMapEntity entity = rainMapRepository.findByRainMapId(rainMapId).orElse(new RainMapEntity());
        entity.setRainMapId(rainMapId);
        entity.setEncryptedPayloadHex(hex.formatHex(encryptedPayload));
        entity.setN(n);
        entity.setK(k);
        entity.setNodeId(nodeId);
        if (ciphertextHex != null) {
            entity.setCiphertextHex(ciphertextHex);
        }
        entity.setCreatedAt(LocalDateTime.now());
        rainMapRepository.save(entity);
        log.info("Stored RainMap {} (n={}, k={})", rainMapId, n, k);

        replicationService.replicateRainMap(rainMapId, encryptedPayload, n, k, ciphertextHex);

        return rainMapId;
    }

    /**
     * Almacena o actualiza el cifrado adicional de un RainMap existente.
     *
     * @param rainMapId     identificador del RainMap a actualizar.
     * @param ciphertextHex cifrado en formato hexadecimal.
     */
    @Transactional
    public void storeCiphertext(String rainMapId, String ciphertextHex) {
        rainMapRepository.findByRainMapId(rainMapId).ifPresent(entity -> {
            entity.setCiphertextHex(ciphertextHex);
            rainMapRepository.save(entity);
            log.info("Stored ciphertext for RainMap {}", rainMapId);
        });
    }

    /**
     * Obtiene la representación JSON de un RainMap por su ID.
     *
     * @param rainMapId identificador del RainMap a consultar.
     * @return cadena JSON con los datos del RainMap, o {@code null} si no existe.
     */
    @Transactional(readOnly = true)
    public String getRainMap(String rainMapId) {
        Optional<RainMapEntity> opt = rainMapRepository.findByRainMapId(rainMapId);
        if (opt.isEmpty()) {
            return null;
        }
        RainMapEntity entity = opt.get();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("rainMapId", entity.getRainMapId());
            body.put("encryptedPayloadHex", entity.getEncryptedPayloadHex());
            body.put("n", entity.getN());
            body.put("k", entity.getK());
            body.put("directMode", entity.getCiphertextHex() == null || entity.getCiphertextHex().isBlank());
            body.put("ciphertextHex", entity.getCiphertextHex());
            body.put("nodeId", entity.getNodeId());
            return mapper.writeValueAsString(body);
        } catch (Exception e) {
            log.error("Error serializing RainMap {}", rainMapId, e);
            return null;
        }
    }

    /**
     * Construye un RainMap a partir de drops serializados, URLs de nodos y una clave maestra.
     *
     * <p>Método estático que no requiere instancia. Crea el RainMap con los commitments
     * de Feldman VSS y retorna un resultado con los datos necesarios para su almacenamiento.</p>
     *
     * @param dropJsons lista de representaciones JSON de los drops.
     * @param nodeUrls  URLs de los nodos Storage (mismo orden que los drops).
     * @param masterKey clave maestra para cifrar el RainMap (32 bytes).
     * @param k         umbral de reconstrucción.
     * @return resultado con el ID, payload cifrado y parámetros n/k del RainMap construido.
     */
    public static BuildRainMapResult buildRainMap(List<String> dropJsons, List<String> nodeUrls, byte[] masterKey, int k) {
        HexFormat hex = HexFormat.of();
        List<Drop> drops = dropJsons.stream().map(DropSerializer::fromJson).toList();
        RainMap rainMap = RainMap.create(drops, nodeUrls, masterKey, k);
        String rainMapId = hex.formatHex(drops.get(0).getId());
        return new BuildRainMapResult(rainMapId, rainMap.getCombinedPayload(), rainMap.getN(), rainMap.getK());
    }

    /**
     * Resultado de la construcción de un RainMap.
     *
     * @param rainMapId       identificador del RainMap (hex del primer drop).
     * @param encryptedPayload payload cifrado del RainMap (nonce + ciphertext).
     * @param n               número total de shares.
     * @param k               umbral de reconstrucción.
     */
    public record BuildRainMapResult(String rainMapId, byte[] encryptedPayload, int n, int k) {}
}
