package com.eventmanagement.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import java.util.*;

@ApplicationScoped
public class GatewayRuleStore {
    @Inject DataSource dataSource;
    @Inject ObjectMapper mapper;
    public record Entry(long revision, JsonNode rule) {}

    public List<Entry> list() throws Exception {
        try (var c = dataSource.getConnection(); var s = c.prepareStatement("SELECT revision,definition FROM event_management.gateway_rule ORDER BY id")) {
            try (var rs = s.executeQuery()) {
                var result = new ArrayList<Entry>();
                while (rs.next()) result.add(new Entry(rs.getLong(1), mapper.readTree(rs.getString(2))));
                return List.copyOf(result);
            }
        }
    }
    public List<Entry> history(String id) throws Exception {
        try (var c = dataSource.getConnection(); var s = c.prepareStatement("SELECT revision,definition FROM event_management.gateway_rule_history WHERE id=? ORDER BY revision DESC LIMIT 100")) {
            s.setString(1,id);
            try (var rs = s.executeQuery()) {
                var result = new ArrayList<Entry>();
                while (rs.next()) result.add(new Entry(rs.getLong(1),mapper.readTree(rs.getString(2))));
                return result;
            }
        }
    }
    public Entry save(JsonNode rule, long expected) throws Exception {
        String id = rule.path("id").asText();
        try (var c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Serialize bounded catalog mutations, including concurrent creates.
                try (var s = c.createStatement()) { s.execute("SELECT pg_advisory_xact_lock(782341026)"); }
                long current = 0;
                try (var s = c.prepareStatement("SELECT revision FROM event_management.gateway_rule WHERE id=?")) {
                    s.setString(1,id);
                    try (var rs = s.executeQuery()) { if (rs.next()) current = rs.getLong(1); }
                }
                if (current != expected) throw new Conflict();
                if (current == 0) {
                    try (var s = c.createStatement(); var rs = s.executeQuery("SELECT count(*) FROM event_management.gateway_rule")) {
                        rs.next(); if (rs.getInt(1) >= 256) throw new IllegalArgumentException("RULE_LIMIT_REACHED");
                    }
                }
                long revision = current + 1;
                try (var s = c.prepareStatement("INSERT INTO event_management.gateway_rule(id,revision,definition) VALUES (?,?,?::jsonb) ON CONFLICT(id) DO UPDATE SET revision=EXCLUDED.revision,definition=EXCLUDED.definition")) {
                    s.setString(1,id); s.setLong(2,revision); s.setString(3,rule.toString()); s.executeUpdate();
                }
                try (var s = c.prepareStatement("INSERT INTO event_management.gateway_rule_history(id,revision,definition) VALUES (?,?,?::jsonb)")) {
                    s.setString(1,id); s.setLong(2,revision); s.setString(3,rule.toString()); s.executeUpdate();
                }
                c.commit(); return new Entry(revision,rule);
            } catch (Exception e) { c.rollback(); throw e; }
        }
    }
    public static class Conflict extends RuntimeException {}
}
