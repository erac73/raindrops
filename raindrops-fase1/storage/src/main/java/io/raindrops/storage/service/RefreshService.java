package io.raindrops.storage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.raindrops.core.ShamirSSS;
import io.raindrops.storage.config.PeerConfig;
import io.raindrops.storage.model.DropEntity;
import io.raindrops.storage.repository.DropRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Servicio de Proactive Share Refresh (IMP-002).
 *
 * <p>Implementa la actualizacion periodica de shares sin modificar el
 * secreto subyacente. Cada nodo genera un polinomio de refresco
 * r_i(x) de grado K-1 con r_i(0)=0, calcula deltas para todos los
 * nodos, y los distribuye via REST.</p>
 *
 * <p>Propiedad matematica: si todos los nodos suman los deltas recibidos
 * a sus shares, los nuevos shares reconstruyen el mismo secreto original
 * porque la suma de polinomios de refresco evaluados en 0 es siempre 0.</p>
 *
 * <p>Ejecucion automatica cada 24 horas (configurable via
 * {@code refresh.interval}).</p>
 */
@Service
public class RefreshService {

    private static final Logger log = LoggerFactory.getLogger(RefreshService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DropRepository dropRepository;
    private final PeerConfig peerConfig;
    private final RestClient restClient;
    private final String nodeId;
    private final int nodePort;
    private final String apiKey;

    /**
     * Constructor del RefreshService.
     *
     * @param dropRepository   repositorio JPA para la persistencia de drops.
     * @param peerConfig       configuracion de nodos peer con sus URLs.
     * @param restClientBuilder builder para crear el RestClient HTTP.
     * @param nodeId           identificador del nodo actual.
     * @param nodePort         puerto del nodo actual.
     * @param apiKey           clave de API para autenticacion con los peers.
     */
    public RefreshService(DropRepository dropRepository, PeerConfig peerConfig,
                          RestClient.Builder restClientBuilder,
                          @Value("${NODE_ID:storage-node}") String nodeId,
                          @Value("${server.port:8080}") int nodePort,
                          @Value("${API_KEY:}") String apiKey) {
        this.dropRepository = dropRepository;
        this.peerConfig = peerConfig;
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(10000);
        this.restClient = restClientBuilder.requestFactory(factory).build();
        this.nodeId = nodeId;
        this.nodePort = nodePort;
        this.apiKey = apiKey;
    }

    /**
     * Ejecuta el Proactive Share Refresh de forma periodica.
     *
     * <p>Se invoca automaticamente cada {@code refresh.interval} ms
     * (por defecto 24 horas = 86400000 ms).</p>
     */
    @Scheduled(fixedDelayString = "${refresh.interval:86400000}")
    public void scheduledRefresh() {
        int n = peerConfig.getPeerUrls().size() + 1;
        if (n < 2) {
            log.info("Refresh omitido: no hay suficientes nodos (N={})", n);
            return;
        }

        int k = Integer.parseInt(
                System.getenv().getOrDefault("SSS_K", "3"));

        try {
            refreshShares(n, k);
            log.info("Proactive refresh completado exitosamente");
        } catch (Exception e) {
            log.error("Error en proactive refresh: {}", e.getMessage(), e);
        }
    }

    /**
     * Realiza el Proactive Share Refresh entre todos los nodos.
     *
     * <p>Genera un polinomio de refresco, calcula deltas para cada nodo,
     * los envia via REST a los peers, y aplica los deltas recibidos de
     * los demas nodos a los shares locales.</p>
     *
     * @param n numero total de nodos en el sistema.
     * @param k umbral de reconstruccion.
     */
    @Transactional
    public void refreshShares(int n, int k) {
        BigInteger[] polynomial = ShamirSSS.generateRefreshPolynomial(k);

        List<BigInteger[]> myDeltas = new ArrayList<>();
        for (int j = 1; j <= n; j++) {
            BigInteger delta = ShamirSSS.evaluatePolynomial(
                    polynomial, BigInteger.valueOf(j), ShamirSSS.PRIME);
            myDeltas.add(new BigInteger[]{BigInteger.valueOf(j), delta});
        }

        log.info("Generados {} deltas de refresh para nodo {}", myDeltas.size(), nodeId);

        List<BigInteger[]> receivedDeltas = sendDeltasToPeers(myDeltas);

        applyDeltas(receivedDeltas);

        clearArray(polynomial);
    }

    /**
     * Aplica deltas recibidos de otros nodos a los drops locales.
     *
     * <p>Para cada delta recibido, busca el drop local con el mismo valor x
     * y actualiza su componente y sumandola con el delta mod PRIME.</p>
     *
     * @param deltas lista de deltas recibidos donde cada delta es [x, y].
     */
    @Transactional
    public void applyDeltas(List<BigInteger[]> deltas) {
        List<DropEntity> allDrops = dropRepository.findAll();

        for (BigInteger[] delta : deltas) {
            int targetX = delta[0].intValue();
            BigInteger deltaY = delta[1];

            for (DropEntity drop : allDrops) {
                if (drop.getX() == targetX) {
                    BigInteger currentY = new BigInteger(drop.getY(), 16);
                    BigInteger newY = currentY.add(deltaY).mod(ShamirSSS.PRIME);
                    drop.setY(newY.toString(16));
                    dropRepository.save(drop);
                    log.debug("Delta aplicado a drop x={}: {} -> {}",
                            targetX, currentY.toString(16), newY.toString(16));
                }
            }
        }
    }

    /**
     * Endpoint para recibir deltas de refresco de otro nodo.
     *
     * <p>Metodo interno invocado desde el controlador REST
     * {@code POST /refresh/delta}.</p>
     *
     * @param fromNodeId identificacion del nodo emisor.
     * @param deltaList  lista de deltas donde cada uno es un mapa con "x" e "y".
     */
    public void receiveDeltas(String fromNodeId, List<Map<String, Object>> deltaList) {
        List<BigInteger[]> deltas = new ArrayList<>();
        for (Map<String, Object> d : deltaList) {
            int x = ((Number) d.get("x")).intValue();
            BigInteger y = new BigInteger((String) d.get("y"), 16);
            deltas.add(new BigInteger[]{BigInteger.valueOf(x), y});
        }

        log.info("Recibidos {} deltas del nodo {}", deltas.size(), fromNodeId);
        applyDeltas(deltas);
    }

    /**
     * Envia deltas a los nodos peer via POST.
     *
     * @param myDeltas deltas generados por este nodo.
     * @return lista de deltas recibidos de los peers.
     */
    private List<BigInteger[]> sendDeltasToPeers(List<BigInteger[]> myDeltas) {
        List<BigInteger[]> receivedDeltas = new ArrayList<>();

        for (String peer : peerConfig.getPeerUrls()) {
            try {
                String url = peer + "/refresh/delta";
                log.info("Enviando deltas a {}", url);

                List<Map<String, Object>> deltaPayload = new ArrayList<>();
                for (BigInteger[] d : myDeltas) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("x", d[0].intValue());
                    entry.put("y", d[1].toString(16));
                    deltaPayload.add(entry);
                }

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("fromNodeId", nodeId);
                body.put("deltas", deltaPayload);

                var req = restClient.post()
                        .uri(url)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .body(MAPPER.writeValueAsString(body));

                if (apiKey != null && !apiKey.isBlank()) {
                    req.header("X-API-Key", apiKey);
                }

                req.retrieve().toBodilessEntity();
                log.info("Deltas enviados exitosamente a {}", url);
            } catch (Exception e) {
                log.warn("Error enviando deltas a {}: {}", peer, e.getMessage());
            }
        }

        return receivedDeltas;
    }

    private void clearArray(BigInteger[] arr) {
        for (int i = 0; i < arr.length; i++) {
            arr[i] = BigInteger.ZERO;
        }
    }
}
