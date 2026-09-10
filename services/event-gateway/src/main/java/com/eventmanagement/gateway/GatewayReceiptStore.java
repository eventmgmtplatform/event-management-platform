package com.eventmanagement.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import javax.sql.DataSource;
import java.sql.Connection;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/** Durable original body, separate from mutable validation/publication observations. */
@ApplicationScoped
public class GatewayReceiptStore {
    private final DataSource dataSource;
    private final ObjectMapper mapper;

    @Inject
    public GatewayReceiptStore(DataSource dataSource, ObjectMapper mapper) {
        this.dataSource = dataSource;
        this.mapper = mapper;
    }

    public void capture(Exchange exchange) throws Exception {
        byte[] bytes = exchange.getMessage().getBody(byte[].class);
        if (bytes == null) bytes = new byte[0];
        UUID id = UUID.randomUUID();
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        String contentType = exchange.getMessage().getHeader(Exchange.CONTENT_TYPE, String.class);
        if (contentType != null && contentType.length() > 256) contentType = contentType.substring(0,256);
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (var config = conn.createStatement()) { config.execute("SET LOCAL synchronous_commit=on"); }
                try (var stmt = conn.prepareStatement("INSERT INTO event_management.gateway_receipt(receipt_id,content_type,original_body,body_sha256) VALUES (?,?,?,?)")) {
                    stmt.setObject(1,id); stmt.setString(2,contentType); stmt.setBytes(3,bytes); stmt.setString(4,hash); stmt.executeUpdate();
                }
                try (var stmt = conn.prepareStatement("INSERT INTO event_management.gateway_receipt_status(receipt_id,status) VALUES (?,'RECEIVED')")) {
                    stmt.setObject(1,id); stmt.executeUpdate();
                }
                conn.commit();
            } catch (Exception ex) { conn.rollback(); throw ex; }
        }
        // A receipt ID is exposed only after its transaction has committed.
        exchange.setProperty("gatewayReceiptId", id.toString());
    }

    public void validated(Exchange exchange) throws Exception {
        JsonNode n = mapper.readTree(exchange.getMessage().getBody(String.class));
        JsonNode original = n.path("originalEvent");
        String tenant = n.path("tenant").path("code").asText(null);
        if (tenant == null) tenant = original.path("CustomerCode").asText(null);
        String source = n.path("source").isObject() ? n.path("source").path("system").asText(null) : n.path("source").asText(null);
        JsonNode severity = n.has("effectiveSeverity") ? n.path("effectiveSeverity") : n.path("alert").path("severity");
        try (Connection conn = dataSource.getConnection(); var stmt = conn.prepareStatement("UPDATE event_management.gateway_receipt_status SET status='VALIDATED',event_id=?,event_key=?,customer_code=?,source_system=?,severity=?,updated_at=clock_timestamp() WHERE receipt_id=?")) {
            stmt.setString(1,n.path("eventId").asText(null));
            stmt.setString(2,exchange.getMessage().getHeader("eventKey",String.class));
            stmt.setString(3,tenant); stmt.setString(4,source);
            stmt.setObject(5,severity.isIntegralNumber() ? severity.intValue() : null);
            stmt.setObject(6,UUID.fromString(exchange.getProperty("gatewayReceiptId",String.class)));
            if (stmt.executeUpdate() != 1) throw new IllegalStateException("Missing durable receipt");
        }
    }

    public void mark(Exchange exchange, String status, String error) throws Exception {
        String id = exchange.getProperty("gatewayReceiptId",String.class);
        if (id == null) return;
        try (Connection conn = dataSource.getConnection(); var stmt = conn.prepareStatement("UPDATE event_management.gateway_receipt_status SET status=?,error_code=?,updated_at=clock_timestamp() WHERE receipt_id=?")) {
            stmt.setString(1,status); stmt.setString(2,error); stmt.setObject(3,UUID.fromString(id));
            if (stmt.executeUpdate() != 1) throw new IllegalStateException("Missing durable receipt");
        }
    }

    public void ready(Exchange exchange) throws Exception {
        try (Connection conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.executeQuery("SELECT receipt_id FROM event_management.gateway_receipt_status LIMIT 0").close();
        }
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE,"application/json");
        exchange.getMessage().setBody("{\"status\":\"UP\",\"originalStorage\":\"postgresql\"}");
    }
}
