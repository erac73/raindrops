package io.raindrops.storage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.raindrops.storage.config.PeerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;

/**
 * Servicio de réplica de datos entre nodos Storage peer.
 *
 * <p>Encargado de replicar drops y RainMaps de forma asíncrona a los nodos peer
 * configurados. Utiliza un pool de hilos fijo de 4 threads para ejecutar las
 * réplicas en paralelo, sin bloquear la operación principal.</p>
 *
 * <p>Si la réplica a un peer falla, se registra un warning pero no se propaga
 * la excepción (fire-and-forget). Las llamadas a los peers incluyen autenticación
 * mediante header {@code X-API-Key} si está configurado.</p>
 *
 * <p>No es thread-safe en cuanto a la configuración de peers (se espera que
 * {@link PeerConfig} sea inmutable), pero las operaciones de réplica en sí
 * son seguras para concurrencia.</p>
 */
@Service
public class ReplicationService {

    private static final Logger log = LoggerFactory.getLogger(ReplicationService.class);

    private final PeerConfig peerConfig;
    private final RestClient restClient;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final ObjectMapper mapper;
    private final String apiKey;

    /**
     * Constructor del ReplicationService.
     *
     * @param peerConfig       configuración de nodos peer con sus URLs.
     * @param restClientBuilder builder para crear el RestClient HTTP.
     * @param mapper           ObjectMapper compartido para serialización JSON.
     * @param apiKey           clave de API para autenticación con los peers (puede estar vacía).
     */
    public ReplicationService(PeerConfig peerConfig, RestClient.Builder restClientBuilder,
                              ObjectMapper mapper,
                              @Value("${API_KEY:}") String apiKey) {
        this.peerConfig = peerConfig;
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        this.restClient = restClientBuilder.requestFactory(factory).build();
        this.mapper = mapper;
        this.apiKey = apiKey;
    }

    private void addAuth(RestClient.RequestHeadersSpec<?> spec) {
        if (apiKey != null && !apiKey.isBlank()) {
            spec.header("X-API-Key", apiKey);
        }
    }

    /**
     * Replicá un drop de forma asíncrona a todos los nodos peer configurados.
     *
     * <p>Cada réplica se ejecuta en el pool de hilos de forma independiente.
     * Si la réplica a un peer falla, se registra un warning pero no se
     * interrumpe la réplica a los demás peers.</p>
     *
     * @param dropId identificador del drop a replicar.
     * @param body   representación JSON del drop a enviar.
     */
    public void replicateDrop(String dropId, String body) {
        for (String peer : peerConfig.getPeerUrls()) {
            CompletableFuture.runAsync(() -> {
                try {
                    String url = peer + "/drops";
                    log.info("Replicating drop {} to {}", dropId, url);
                    var req = restClient.post().uri(url).body(body);
                    addAuth(req);
                    req.retrieve().toBodilessEntity();
                    log.info("Replicated drop {} to {}", dropId, url);
                } catch (Exception e) {
                    log.warn("Failed to replicate drop {} to peer {}: {}", dropId, peer, e.getMessage());
                }
            }, executor);
        }
    }

    /**
     * Replicá un RainMap de forma asíncrona a todos los nodos peer configurados.
     *
     * <p>Envía el payload cifrado del RainMap junto con sus parámetros n, k
     * y el cifrado adicional (si existe) a cada peer vía el endpoint
     * {@code /rainmaps/external}.</p>
     *
     * @param rainMapId        identificador del RainMap a replicar.
     * @param encryptedPayload payload cifrado del RainMap.
     * @param n                número total de shares.
     * @param k                umbral de reconstrucción.
     * @param ciphertextHex    cifrado adicional en hexadecimal (puede ser null).
     */
    public void replicateRainMap(String rainMapId, byte[] encryptedPayload, int n, int k, String ciphertextHex) {
        HexFormat hex = HexFormat.of();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rainMapId", rainMapId);
        body.put("encryptedPayloadHex", hex.formatHex(encryptedPayload));
        body.put("n", n);
        body.put("k", k);
        if (ciphertextHex != null) {
            body.put("ciphertextHex", ciphertextHex);
        }

        for (String peer : peerConfig.getPeerUrls()) {
            CompletableFuture.runAsync(() -> {
                try {
                    String url = peer + "/rainmaps/external";
                    log.info("Replicating RainMap {} to {}", rainMapId, url);
                    var req = restClient.post()
                        .uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(mapper.writeValueAsString(body));
                    addAuth(req);
                    req.retrieve().toBodilessEntity();
                    log.info("Replicated RainMap {} to {}", rainMapId, url);
                } catch (Exception e) {
                    log.warn("Failed to replicate RainMap {} to peer {}: {}", rainMapId, peer, e.getMessage());
                }
            }, executor);
        }
    }

    /**
     * Intenta obtener un drop de los nodos peer configurados.
     *
     * <p>Recorre los peers en orden y retorna el primer drop encontrado.
     * Utilizado cuando un drop no está disponible en el nodo local.</p>
     *
     * @param dropId identificador del drop a buscar.
     * @return representación JSON del drop encontrado, o {@code null} si ningún peer lo tiene.
     */
    public String tryFetchFromPeers(String dropId) {
        for (String peer : peerConfig.getPeerUrls()) {
            try {
                String url = peer + "/drops/" + dropId;
                log.info("Fetching drop {} from peer {}", dropId, url);
                var req = restClient.get().uri(url);
                addAuth(req);
                String body = req.retrieve().body(String.class);
                if (body != null && !body.isBlank()) {
                    log.info("Found drop {} on peer {}", dropId, url);
                    return body;
                }
            } catch (Exception e) {
                log.debug("Drop {} not on peer {}: {}", dropId, peer, e.getMessage());
            }
        }
        return null;
    }
}
