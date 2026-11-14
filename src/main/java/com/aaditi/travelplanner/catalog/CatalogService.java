package com.aaditi.travelplanner.catalog;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

/** Loads the curated destination / hotel catalogue from src/main/resources/data/*.json */
@Component
public class CatalogService {
    public record Zone(String id, String name, double lat, double lon) {}
    public record Area(String name, String zone, String category, int exc, List<String> places) {}
    public record Hotel(String name, String zone, int tier, double rate, String address, String site) {}
    public record Destination(String name, List<String> aliases, String country, boolean intl, double lat, double lon,
                              double costIndex, double activityIndex, double flightReturn, double visaInsurance,
                              List<Zone> zones, List<Area> areas, List<Hotel> hotels) {
        public Zone zone(String id) {
            for (Zone z : zones) if (z.id().equals(id)) return z;
            return zones.get(0);
        }
        public int maxDays() { return areas.size(); }
    }
    public record Origin(String name, double lat, double lon, List<String> aliases) {}
    public record Meta(List<Origin> origins, Map<String, String> descriptions) {}

    private List<Destination> destinations = List.of();
    private Meta meta = new Meta(List.of(), Map.of());

    @PostConstruct
    void load() throws Exception {
        ObjectMapper om = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try (InputStream in = new ClassPathResource("data/destinations.json").getInputStream()) {
            destinations = om.readValue(in, new TypeReference<List<Destination>>() {});
        }
        try (InputStream in = new ClassPathResource("data/meta.json").getInputStream()) {
            meta = om.readValue(in, Meta.class);
        }
    }

    public List<Destination> destinations() { return destinations; }
    public List<Origin> origins() { return meta.origins(); }
    public String description(String place) { return meta.descriptions().get(place); }

    private static boolean has(String text, String alias) {
        return Pattern.compile("(^|[^a-z])" + Pattern.quote(alias) + "([^a-z]|$)").matcher(text).find();
    }

    public Destination findDestination(String text) {
        if (text == null) return null;
        String t = text.toLowerCase(Locale.ROOT).trim();
        if (t.isEmpty()) return null;
        for (Destination d : destinations) if (d.name().equalsIgnoreCase(t)) return d;
        for (Destination d : destinations) for (String a : d.aliases()) if (has(t, a)) return d;
        return null;
    }

    public Origin findOrigin(String text) {
        if (text == null) return null;
        String t = text.toLowerCase(Locale.ROOT).trim();
        if (t.isEmpty()) return null;
        for (Origin o : meta.origins()) if (o.name().equalsIgnoreCase(t)) return o;
        for (Origin o : meta.origins()) for (String a : o.aliases()) if (has(t, a)) return o;
        return null;
    }
}
