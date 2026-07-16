package io.raindrops.witness.controller;

import io.raindrops.witness.service.WitnessService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.Map;

/**
 * Controlador REST del nodo Witness.
 *
 * <p>Expone los endpoints HTTP para verificación, almacenamiento y reconstrucción
 * de drops a través del servicio {@link WitnessService}.</p>
 *
 * <p>Endpoints disponibles:</p>
 * <ul>
 *   <li>{@code POST /witness/verify} — verifica la validez de un drop.</li>
 *   <li>{@code POST /witness/store} — almacena datos distribuidos entre nodos Storage.</li>
 *   <li>{@code POST /witness/reconstruct} — reconstruye el dato original desde un RainMap.</li>
 *   <li>{@code GET /health} — endpoint de salud del servicio.</li>
 * </ul>
 */
@RestController
public class WitnessController {

    private final WitnessService witnessService;

    /**
     * Constructor del WitnessController.
     *
     * @param witnessService servicio de lógica de negocio del witness.
     */
    public WitnessController(WitnessService witnessService) {
        this.witnessService = witnessService;
    }

    /**
     * Verifica la validez de un drop proporcionado como JSON.
     *
     * @param body mapa con los campos {@code dropJson} y {@code masterKeyHex}.
     * @return respuesta HTTP con el resultado de la verificación o error 400 si faltan campos.
     */
    @PostMapping("/witness/verify")
    public ResponseEntity<Map<String, Object>> verify(@RequestBody Map<String, String> body) {
        String dropJson = body.get("dropJson");
        String masterKeyHex = body.get("masterKeyHex");
        if (dropJson == null || masterKeyHex == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "dropJson and masterKeyHex required"));
        }
        WitnessService.VerifyResult result = witnessService.verifyDrop(dropJson, masterKeyHex);
        return ResponseEntity.ok(Map.of(
            "valid", result.valid(),
            "message", result.message()
        ));
    }

    /**
     * Almacena datos distribuyéndolos entre nodos Storage.
     *
     * @param body mapa con los campos {@code data} (Base64), {@code n}, {@code k} y {@code ttlDays}.
     * @return respuesta HTTP con el ID del RainMap y la clave maestra, o error 400 si falla.
     */
    @PostMapping("/witness/store")
    public ResponseEntity<Map<String, Object>> store(@RequestBody Map<String, Object> body) {
        String dataBase64 = (String) body.get("data");
        int n = (int) body.get("n");
        int k = (int) body.get("k");
        int ttlDays = (int) body.get("ttlDays");

        if (dataBase64 == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "data (base64) required"));
        }

        byte[] data = Base64.getDecoder().decode(dataBase64);
        WitnessService.StoreResult result = witnessService.storeData(data, n, k, ttlDays);

        if (!result.success()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Store failed"));
        }

        return ResponseEntity.ok(Map.of(
            "rainMapId", result.rainMapId(),
            "masterKeyHex", result.masterKeyHex()
        ));
    }

    /**
     * Reconstruye el dato original a partir de un RainMap y su clave maestra.
     *
     * @param body mapa con los campos {@code rainMapId} y {@code masterKeyHex}.
     * @return respuesta HTTP con los datos reconstruidos en Base64 o error 400 si falla.
     */
    @PostMapping("/witness/reconstruct")
    public ResponseEntity<Map<String, Object>> reconstruct(@RequestBody Map<String, String> body) {
        String rainMapId = body.get("rainMapId");
        String masterKeyHex = body.get("masterKeyHex");
        if (rainMapId == null || masterKeyHex == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "rainMapId and masterKeyHex required"));
        }

        WitnessService.ReconstructResult result = witnessService.reconstruct(rainMapId, masterKeyHex);

        if (!result.success()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", result.message(),
                "badDrops", result.badDrops(),
                "n", result.n(),
                "k", result.k()
            ));
        }

        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", Base64.getEncoder().encodeToString(result.data()),
            "badDrops", result.badDrops(),
            "n", result.n(),
            "k", result.k()
        ));
    }

    /**
     * Endpoint de salud del nodo Witness.
     *
     * @return respuesta HTTP con el estado {@code UP} y el nombre del servicio.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "witness"));
    }
}
