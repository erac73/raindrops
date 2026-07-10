package io.raindrops.storage.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "rainmaps")
public class RainMapEntity {

    @Id
    @Column(nullable = false, unique = true)
    private String rainMapId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String encryptedPayloadHex;

    @Column(nullable = false)
    private int n;

    @Column(nullable = false)
    private int k;

    @Column(columnDefinition = "TEXT")
    private String ciphertextHex;

    @Column(nullable = false)
    private String nodeId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public RainMapEntity() {}

    public String getRainMapId() { return rainMapId; }
    public void setRainMapId(String rainMapId) { this.rainMapId = rainMapId; }
    public String getEncryptedPayloadHex() { return encryptedPayloadHex; }
    public void setEncryptedPayloadHex(String encryptedPayloadHex) { this.encryptedPayloadHex = encryptedPayloadHex; }
    public int getN() { return n; }
    public void setN(int n) { this.n = n; }
    public int getK() { return k; }
    public void setK(int k) { this.k = k; }
    public String getCiphertextHex() { return ciphertextHex; }
    public void setCiphertextHex(String ciphertextHex) { this.ciphertextHex = ciphertextHex; }
    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}