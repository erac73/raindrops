package io.raindrops.storage.controller;

import io.raindrops.storage.config.PeerConfig;
import io.raindrops.storage.repository.DropRepository;
import io.raindrops.storage.service.DropService;
import io.raindrops.storage.service.RainMapService;
import io.raindrops.storage.service.RefreshService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Controlador REST del nodo Storage.
 *
 * <p>Expone los endpoints HTTP para la gestion de drops, RainMaps, informacion
 * de nodos peer y refresh proactivo de shares. Este controlador actua como la
 * API publica del nodo Storage dentro del sistema distribuido RainDrops.</p>
 *
 * <p>Endpoints disponibles:</p>
 * <ul>
 *   <li>{@code POST /drops} — almacena un drop.</li>
 *   <li>{@code GET /drops/{dropId}} — obtiene un drop por su ID.</li>
 *   <li>{@code POST /rainmaps} — construye y almacena un RainMap a partir de drops.</li>
 *   <li>{@code POST /rainmaps/external} — almacena un RainMap enviado externamente.</li>
 *   <li>{@code GET /rainmaps/{rainMapId}} — obtiene un RainMap por su ID.</li>
 *   <li>{@code PUT /rainmaps/{rainMapId}/ciphertext} — almacena el cifrado adicional de un RainMap.</li>
 *   <li>{@code POST /refresh/delta} — recibe deltas de Proactive Share Refresh.</li>
 *   <li>{@code GET /peers} — lista los nodos peer configurados.</li>
 *   <li>{@code GET /health} — endpoint de salud del servicio.</li>
 * </ul>
 */
@RestController
public class DropController {

    private final DropService dropService;
    private final DropRepository dropRepository;
    private final RainMapService rainMapService;
    private final PeerConfig peerConfig;
    private final RefreshService refreshService;
    private final String nodeId;

    /**
     * Constructor del DropController.
     *
     * @param dropService    servicio de logica de negocio para drops.
     * @param dropRepository repositorio JPA para la persistencia de drops.
     * @param rainMapService servicio de gestion de RainMaps.
     * @param peerConfig     configuracion de nodos peer para la replica.
     * @param refreshService servicio de Proactive Share Refresh.
     */
    public DropController(DropService dropService, DropRepository dropRepository,
                          RainMapService rainMapService, PeerConfig peerConfig,
                          RefreshService refreshService) {
        this.dropService = dropService;
        this.dropRepository = dropRepository;
        this.rainMapService = rainMapService;
        this.peerConfig = peerConfig;
        this.nodeId = peerConfig.getNodeId();
        this.refreshService = refreshService;
    }

    /**
     * Almacena un drop en este nodo Storage.
     *
     * @param body representacion JSON del drop a almacenar.
     * @return respuesta HTTP con el ID del drop almacenado y el ID del nodo.
     */
    @PostMapping("/drops")
    public ResponseEntity<Map<String, String>> storeDrop(@RequestBody String body) {
        String dropId = dropService.storeDrop(body);
        return ResponseEntity.ok(Map.of("dropId", dropId, "nodeId", nodeId));
    }

    /**
     * Obtiene un drop por su identificador.
     *
     * @param dropId identificador del drop a consultar.
     * @return respuesta HTTP con el JSON del drop, o 404 si no existe.
     */
    @GetMapping("/drops/{dropId}")
    public ResponseEntity<String> getDrop(@PathVariable String dropId) {
        String json = dropService.getDrop(dropId);
        if (json == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(json);
    }

    /**
     * Construye un RainMap a partir de drops y lo almacena.
     *
     * @param body mapa con los campos {@code drops} (lista de JSON), {@code nodeUrls},
     *             {@code masterKeyHex} y {@code k}.
     * @return respuesta HTTP con el ID del RainMap, payload cifrado y parametros n/k.
     */
    @PostMapping("/rainmaps")
    public ResponseEntity<Map<String, Object>> buildAndStoreRainMap(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> dropJsons = (List<String>) body.get("drops");
        @SuppressWarnings("unchecked")
        List<String> nodeUrls = (List<String>) body.get("nodeUrls");
        String masterKeyHex = (String) body.get("masterKeyHex");
        int k = ((Number) body.get("k")).intValue();

        java.util.HexFormat hex = java.util.HexFormat.of();
        byte[] masterKey = hex.parseHex(masterKeyHex);

        RainMapService.BuildRainMapResult result = RainMapService.buildRainMap(dropJsons, nodeUrls, masterKey, k);
        rainMapService.storeRainMap(result.rainMapId(), result.encryptedPayload(), result.n(), result.k(), null);

        return ResponseEntity.ok(Map.of(
            "rainMapId", result.rainMapId(),
            "encryptedPayloadHex", hex.formatHex(result.encryptedPayload()),
            "n", result.n(),
            "k", result.k()
        ));
    }

    /**
     * Almacena un RainMap enviado externamente (por el Witness u otro nodo).
     *
     * @param body mapa con los campos {@code rainMapId}, {@code encryptedPayloadHex}, {@code n} y {@code k}.
     * @return respuesta HTTP con el ID del RainMap almacenado.
     */
    @PostMapping("/rainmaps/external")
    public ResponseEntity<Map<String, String>> storeExternalRainMap(@RequestBody Map<String, Object> body) {
        String rainMapId = (String) body.get("rainMapId");
        String encryptedPayloadHex = (String) body.get("encryptedPayloadHex");
        int n = (int) body.get("n");
        int k = (int) body.get("k");

        java.util.HexFormat hex = java.util.HexFormat.of();
        rainMapService.storeRainMap(rainMapId, hex.parseHex(encryptedPayloadHex), n, k, null);

        return ResponseEntity.ok(Map.of("rainMapId", rainMapId));
    }

    /**
     * Obtiene un RainMap por su identificador.
     *
     * @param rainMapId identificador del RainMap a consultar.
     * @return respuesta HTTP con el JSON del RainMap, o 404 si no existe.
     */
    @GetMapping("/rainmaps/{rainMapId}")
    public ResponseEntity<String> getRainMap(@PathVariable String rainMapId) {
        String json = rainMapService.getRainMap(rainMapId);
        if (json == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(json);
    }

    /**
     * Almacena o actualiza el cifrado adicional de un RainMap.
     *
     * @param rainMapId identificador del RainMap a actualizar.
     * @param body      mapa con el campo {@code ciphertextHex}.
     * @return respuesta HTTP con el ID del RainMap actualizado.
     */
    @PutMapping("/rainmaps/{rainMapId}/ciphertext")
    public ResponseEntity<Map<String, String>> storeCiphertext(
            @PathVariable String rainMapId, @RequestBody Map<String, Object> body) {
        String ciphertextHex = (String) body.get("ciphertextHex");
        rainMapService.storeCiphertext(rainMapId, ciphertextHex);
        return ResponseEntity.ok(Map.of("rainMapId", rainMapId));
    }

    /**
     * Recibe deltas de Proactive Share Refresh desde otro nodo.
     *
     * <p>Endpoint invocado por los nodos peer durante el refresh periodico.
     * Cada delta contiene la posicion x del share y el valor a sumar (mod p)
     * para actualizar el share local.</p>
     *
     * @param body mapa JSON con {@code fromNodeId} y {@code dadas} (lista de deltas).
     * @return respuesta HTTP 200 con confirmacion.
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/refresh/delta")
    public ResponseEntity<Map<String, String>> receiveRefreshDelta(@RequestBody Map<String, Object> body) {
        String fromNodeId = (String) body.get("fromNodeId");
        List<Map<String, Object>> deltas = (List<Map<String, Object>>) body.get("deltas");
        refreshService.receiveDeltas(fromNodeId, deltas);
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "nodeId", nodeId,
                "deltasApplied", String.valueOf(deltas.size())
        ));
    }

    /**
     * Lista los nodos peer configurados para este nodo Storage.
     *
     * @return respuesta HTTP con el ID del nodo y la lista de URLs de sus peers.
     */
    @GetMapping("/peers")
    public ResponseEntity<Map<String, Object>> getPeers() {
        return ResponseEntity.ok(Map.of(
            "nodeId", nodeId,
            "peers", peerConfig.getPeerUrls()
        ));
    }

    /**
     * Endpoint de salud del nodo Storage.
     *
     * @return respuesta HTTP con el estado, ID del nodo, cantidad de drops almacenados y lista de peers.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        long count = dropRepository.count();
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "nodeId", nodeId,
                "dropsStored", count,
                "peers", peerConfig.getPeerUrls()
        ));
    }
}
