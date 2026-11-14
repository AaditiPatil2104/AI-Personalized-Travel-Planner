package com.aaditi.travelplanner.planner;

import java.time.LocalDate;
import java.util.*;

/** Parsed + validated planner input. */
public class PlanRequest {
    public static final List<String> TRANSPORTS = List.of("Train/Bus", "Car", "Flight");
    public static final List<String> STYLES = List.of("Relaxed", "Moderate", "Packed");
    public static final List<String> FOODS = List.of("Any", "Vegetarian", "Non-Vegetarian", "Jain", "Vegan", "Local Food", "Premium Dining");
    public static final List<String> HOTEL_STANDARDS = List.of("Auto", "Budget", "Comfort", "Premium");

    public String startLocation = "", destination = "";
    public int days = 3, adults = 2, children = 0, occupancy = 2;
    public double budget = 0;
    public List<Interests.Interest> interests = new ArrayList<>();
    public String travelStyle = "Moderate", transport = "Train/Bus", food = "Any", hotelStandard = "Auto", hotelPlan = "change";
    public LocalDate startDate;
    public final List<String> errors = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();

    public int travellers() { return adults + children; }

    private static String s(Map<String, Object> m, String k) { Object v = m.get(k); return v == null ? "" : v.toString().trim(); }
    private static int i(Map<String, Object> m, String k, int d) {
        String v = s(m, k); if (v.isEmpty()) return d;
        try { return (int) Math.round(Double.parseDouble(v)); } catch (Exception e) { return Integer.MIN_VALUE; }
    }
    private static String pick(List<String> allowed, String v, String def) {
        for (String a : allowed) if (a.equalsIgnoreCase(v)) return a;
        return v.isEmpty() ? def : null;
    }

    public static PlanRequest from(Map<String, Object> m, boolean needBudget) {
        PlanRequest r = new PlanRequest();
        r.startLocation = s(m, "startLocation");
        r.destination = s(m, "destination");
        r.days = i(m, "days", 0);
        if (m.get("adults") != null) {
            r.adults = i(m, "adults", 1);
            r.children = i(m, "children", 0);
        } else { // older clients sent a single "travelers" number
            r.adults = i(m, "travelers", 1);
            r.children = 0;
        }
        r.occupancy = i(m, "occupancy", 2);
        String b = s(m, "budget");
        try { r.budget = b.isEmpty() ? 0 : Double.parseDouble(b); } catch (Exception e) { r.budget = -1; }

        String iv = s(m, "interests");
        if (m.get("interests") instanceof Collection<?> c) iv = String.join(",", c.stream().map(Object::toString).toList());
        Set<String> seen = new HashSet<>();
        for (String part : iv.split(",")) {
            if (part.isBlank()) continue;
            Interests.Interest it = Interests.find(part);
            if (it != null && seen.add(it.key())) r.interests.add(it);
        }

        String v;
        v = pick(STYLES, s(m, "travelStyle"), "Moderate"); if (v == null) { r.errors.add("Choose a valid travel style."); v = "Moderate"; } r.travelStyle = v;
        v = pick(TRANSPORTS, s(m, "transport"), "Train/Bus"); if (v == null) { r.errors.add("Choose a valid travel mode."); v = "Train/Bus"; } r.transport = v;
        String foodRaw = s(m, "food"); if (foodRaw.isEmpty()) foodRaw = s(m, "foodPreference");
        v = pick(FOODS, foodRaw, "Any"); if (v == null) { r.warnings.add("Unknown food preference – using 'Any'."); v = "Any"; } r.food = v;
        v = pick(HOTEL_STANDARDS, s(m, "hotelStandard"), "Auto"); if (v == null) { r.errors.add("Choose a valid hotel standard."); v = "Auto"; } r.hotelStandard = v;
        r.hotelPlan = "same".equalsIgnoreCase(s(m, "hotelPlan")) ? "same" : "change";

        String sd = s(m, "startDate");
        if (!sd.isEmpty()) {
            try { r.startDate = LocalDate.parse(sd); } catch (Exception e) { r.errors.add("Start date is not valid."); }
        }

        // ---- validation ----
        if (r.startLocation.isEmpty()) r.errors.add("Starting location is required.");
        if (r.destination.isEmpty()) r.errors.add("Destination is required.");
        if (r.days < 1) r.errors.add("Number of days must be at least 1.");
        if (r.adults < 1) r.errors.add("At least 1 adult is required.");
        if (r.children < 0) r.errors.add("Children cannot be negative.");
        if (r.adults + Math.max(0, r.children) > 30) r.errors.add("This planner supports groups of up to 30 travellers. For larger groups please contact a tour operator.");
        if (r.occupancy < 1 || r.occupancy > 4) r.errors.add("Room sharing must be between 1 and 4 people per room.");
        if (r.interests.isEmpty()) r.errors.add("Select at least one interest so the plan can be personalised.");
        if (r.startDate == null) r.errors.add("Please choose the trip start date.");
        else if (r.startDate.isBefore(LocalDate.now())) r.errors.add("Start date cannot be in the past.");
        if (needBudget) {
            if (r.budget <= 0) r.errors.add("Enter your total trip budget in ₹.");
        } else if (r.budget < 0) r.errors.add("Budget is not a valid number.");
        return r;
    }
}
