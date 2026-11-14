package com.aaditi.travelplanner.planner;

import com.aaditi.travelplanner.catalog.CatalogService;
import com.aaditi.travelplanner.catalog.CatalogService.*;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

/** Builds day-wise itineraries, hotel/room plans and budget estimates. All prices are planning estimates in INR. */
@Component
public class TripPlanner {
    private final CatalogService catalog;
    public TripPlanner(CatalogService catalog) { this.catalog = catalog; }

    static final String[] TIER = {"", "Budget", "Comfort", "Premium"};

    private record Built(Map<String, Object> plan, double total) {}

    // ---------------------------------------------------------------- public API
    public Map<String, Object> meta() {
        Map<String, Object> m = new LinkedHashMap<>();
        List<Map<String, Object>> ds = new ArrayList<>();
        for (Destination d : catalog.destinations()) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("name", d.name()); x.put("country", d.country()); x.put("intl", d.intl()); x.put("maxDays", d.maxDays());
            ds.add(x);
        }
        m.put("destinations", ds);
        m.put("origins", catalog.origins().stream().map(CatalogService.Origin::name).toList());
        m.put("interests", Interests.ALL.stream().map(i -> {
            Map<String, Object> x = new LinkedHashMap<>(); x.put("key", i.key()); x.put("label", i.label()); x.put("icon", i.icon()); return x;
        }).toList());
        m.put("transports", PlanRequest.TRANSPORTS); m.put("styles", PlanRequest.STYLES);
        m.put("foods", PlanRequest.FOODS); m.put("hotelStandards", PlanRequest.HOTEL_STANDARDS);
        m.put("today", LocalDate.now().toString());
        return m;
    }

    /** Live budget check. Never throws; problems are returned in "errors". */
    public Map<String, Object> estimate(Map<String, Object> raw) {
        PlanRequest r = PlanRequest.from(raw, false);
        Map<String, Object> out = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>(r.errors);
        Destination d = catalog.findDestination(r.destination);
        if (d == null && !r.destination.isEmpty()) errors.add(unsupported(r.destination));
        if (d != null) checkDestinationRules(d, r, errors);
        out.put("errors", errors);
        out.put("warnings", r.warnings);
        if (d == null || hasBlocking(errors, r)) { out.put("ok", false); return out; }
        int tier = r.hotelStandard.equals("Auto") ? 1 : tierOf(r.hotelStandard);
        Built b = build(d, r, tier);
        @SuppressWarnings("unchecked") Map<String, Object> p = b.plan();
        out.put("ok", true);
        out.put("destination", d.name()); out.put("maxDays", d.maxDays());
        out.put("minimumFeasible", Math.ceil(b.total()));
        out.put("breakdown", p.get("breakdown"));
        out.put("group", p.get("group"));
        out.put("nights", Math.max(0, r.days - 1));
        out.put("minimumBasis", r.hotelStandard.equals("Auto") ? "Budget-standard hotels" : r.hotelStandard + " hotels");
        out.put("comfortBudget", Math.ceil(b.total() * 1.10));
        out.put("warnings", p.get("warnings"));
        if (r.budget > 0) {
            out.put("budgetOk", r.budget >= b.total());
            out.put("shortfall", Math.max(0, Math.ceil(b.total() - r.budget)));
        }
        return out;
    }

    /** Full plan; throws IllegalArgumentException with a readable message on any validation problem. */
    public Map<String, Object> plan(Map<String, Object> raw) {
        PlanRequest r = PlanRequest.from(raw, true);
        List<String> errors = new ArrayList<>(r.errors);
        Destination d = catalog.findDestination(r.destination);
        if (d == null && !r.destination.isEmpty()) errors.add(unsupported(r.destination));
        if (d != null) checkDestinationRules(d, r, errors);
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join(" ", errors));

        Built chosen = null;
        if (r.hotelStandard.equals("Auto")) {
            for (int t = 3; t >= 1 && chosen == null; t--) { Built b = build(d, r, t); if (b.total() * 1.10 <= r.budget) chosen = b; }
            for (int t = 3; t >= 1 && chosen == null; t--) { Built b = build(d, r, t); if (b.total() <= r.budget) chosen = b; }
            if (chosen == null) {
                Built min = build(d, r, 1);
                throw new IllegalArgumentException(shortMsg(r, min.total(), "Budget-standard hotels"));
            }
        } else {
            Built b = build(d, r, tierOf(r.hotelStandard));
            if (b.total() > r.budget) throw new IllegalArgumentException(shortMsg(r, b.total(), r.hotelStandard + " hotels"));
            chosen = b;
        }
        Map<String, Object> plan = chosen.plan();
        plan.put("budget", r.budget);
        double est = chosen.total();
        plan.put("remainingBudget", Math.round(r.budget - est));
        plan.put("budgetStatus", est <= r.budget ? "Within budget" : "Over budget");
        return plan;
    }

    // ---------------------------------------------------------------- validation helpers
    private String unsupported(String dest) {
        String names = String.join(", ", catalog.destinations().stream().map(Destination::name).toList());
        return "We don't have verified real places and hotels for \"" + dest + "\" yet, so we won't invent fake ones. Supported destinations: " + names + ".";
    }
    private void checkDestinationRules(Destination d, PlanRequest r, List<String> errors) {
        if (r.days > d.maxDays()) errors.add("For " + d.name() + " we have verified experiences for up to " + d.maxDays() + " days. Reduce the days or choose another destination.");
        if (d.intl() && !r.transport.equals("Flight")) errors.add(d.name() + " is an international destination – please choose Flight as the travel mode.");
    }
    private boolean hasBlocking(List<String> errors, PlanRequest r) {
        // every error blocks building except the budget one (not produced here)
        return !errors.isEmpty();
    }
    private int tierOf(String s) { return s.equals("Premium") ? 3 : s.equals("Comfort") ? 2 : 1; }
    private String shortMsg(PlanRequest r, double min, String basis) {
        return "Trip not possible within ₹" + money(r.budget) + ". Minimum required for " + r.days + " day(s), " + r.adults + " adult(s)"
            + (r.children > 0 ? " + " + r.children + " child(ren)" : "") + " by " + r.transport + " with " + basis + " and " + r.food
            + " food is about ₹" + money(Math.ceil(min)) + ". Increase the budget, reduce days/travellers, or change the travel mode.";
    }

    // ---------------------------------------------------------------- core builder
    private Built build(Destination d, PlanRequest r, int tier) {
        int n = r.travellers();
        int days = r.days, nights = Math.max(0, days - 1);
        List<String> warnings = new ArrayList<>();

        if (!d.intl() && catalog.findOrigin(r.startLocation) == null)
            warnings.add("Starting location \"" + r.startLocation + "\" is not in our city list, so the travel distance is assumed to be ~450 km. Pick a nearby major city for a more accurate fare.");

        // ---- group & rooms
        // adults fill rooms by the chosen sharing; children (2-12) share with adults, up to 2 per room
        int rooms = Math.max(1, Math.max((int) Math.ceil(r.adults / (double) r.occupancy), (int) Math.ceil(r.children / 2.0)));
        rooms = Math.min(rooms, Math.max(1, r.adults));      // every room needs at least one adult
        List<Map<String, Object>> roomPlan = new ArrayList<>();
        int aLeft = r.adults, cLeft = r.children, ai = 1, ci = 1;
        for (int k = 0; k < rooms; k++) {
            int a = aLeft / (rooms - k) + ((aLeft % (rooms - k)) > 0 ? 1 : 0);
            int c = cLeft / (rooms - k) + ((cLeft % (rooms - k)) > 0 ? 1 : 0);
            aLeft -= a; cLeft -= c;
            String who = "Adult" + (a > 1 ? "s " + ai + "–" + (ai + a - 1) : " " + ai);
            if (c > 0) who += " + Child" + (c > 1 ? "ren " + ci + "–" + (ci + c - 1) : " " + ci);
            ai += a; ci += c;
            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("room", k + 1); rm.put("adults", a); rm.put("children", c);
            int heads = a + c;
            rm.put("type", heads <= 1 ? "Single" : heads == 2 ? "Double" : heads == 3 ? "Triple" : "Quad / Family");
            rm.put("label", "Room " + (k + 1) + ": " + who);
            roomPlan.add(rm);
        }
        Map<String, Integer> typeCount = new LinkedHashMap<>();
        for (Map<String, Object> rm : roomPlan) typeCount.merge((String) rm.get("type"), 1, Integer::sum);
        List<String> parts = new ArrayList<>();
        typeCount.forEach((t, c) -> parts.add(c + " × " + t + " room" + (c > 1 ? "s" : "")));
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("adults", r.adults); group.put("children", r.children); group.put("total", n);
        group.put("occupancy", r.occupancy); group.put("rooms", rooms);
        group.put("summary", n + (n == 1 ? " traveller" : " travellers") + " → " + rooms + " room" + (rooms > 1 ? "s" : "") + " (" + String.join(", ", parts) + ")");
        group.put("roomPlan", roomPlan);
        List<String> gtips = new ArrayList<>();
        if (r.children > 0) gtips.add("Children are assumed to be 2–12 years and share a room with adults (extra bed/rollaway may be charged by the hotel). Infants under 2 usually stay free.");
        if (rooms >= 3) gtips.add("Ask for rooms on the same floor or connecting rooms so the group stays together.");
        if (rooms >= 5) gtips.add("For 5+ rooms, email the hotel's reservations desk for a group rate – hotels often discount block bookings.");
        if (n >= 8) gtips.add("For a group of " + n + ", pre-book local transport (tempo traveller / minibus) and restaurant tables for dinners.");
        group.put("tips", gtips);

        // ---- places per day
        List<Area> scored = new ArrayList<>(d.areas());
        Map<Area, Double> areaScore = new HashMap<>();
        for (Area a : d.areas()) {
            double s = 0;
            for (String p : a.places()) s += Interests.score(r.interests, p, a.name(), a.category());
            areaScore.put(a, s);
        }
        List<Area> order = new ArrayList<>(scored);
        order.sort((x, y) -> Double.compare(areaScore.get(y), areaScore.get(x)));
        List<Area> dayAreas = new ArrayList<>(order.subList(0, days));

        // ---- hotels per night
        Map<String, Integer> usage = new HashMap<>();
        List<Hotel> nightHotel = new ArrayList<>();
        List<List<Hotel>> nightOptions = new ArrayList<>();
        List<Boolean> repeated = new ArrayList<>();
        Hotel sameHotel = null;
        if (r.hotelPlan.equals("same") && nights > 0) {
            Map<String, Integer> freq = new HashMap<>();
            for (int k = 0; k < nights; k++) freq.merge(dayAreas.get(k).zone(), 1, Integer::sum);
            String zone = Collections.max(freq.entrySet(), Map.Entry.comparingByValue()).getKey();
            sameHotel = chooseHotel(d, zone, tier, new HashMap<>());
        }
        for (int k = 0; k < nights; k++) {
            String zone = dayAreas.get(k).zone();
            Hotel h = sameHotel != null ? sameHotel : chooseHotel(d, zone, tier, usage);
            repeated.add(usage.getOrDefault(h.name(), 0) > 0 && sameHotel == null);
            usage.merge(h.name(), 1, Integer::sum);
            nightHotel.add(h);
            nightOptions.add(optionsFor(d, zone, h, tier));
        }
        boolean anyRepeat = repeated.stream().anyMatch(b -> b);
        if (anyRepeat) warnings.add("We have a limited number of verified hotels in some parts of " + d.name() + ", so one or more hotels repeat.");
        if (r.hotelPlan.equals("change") && nights >= 2) {
            Set<String> uniq = new HashSet<>(); nightHotel.forEach(h -> uniq.add(h.name()));
            if (!anyRepeat && uniq.size() == nights) { /* all distinct */ }
        }
        if (nightHotel.stream().anyMatch(h -> h.tier() != tier) && nights > 0 && sameHotel == null)
            warnings.add("To avoid repeating a hotel, some nights use a neighbouring hotel category. Choose \"Stay in one hotel\" to keep the same hotel and lower the cost.");

        // ---- dates
        LocalDate start = r.startDate;

        // ---- day loop
        double actTotal = 0, localTotal = 0, hotelTotal = 0, foodTotal = 0;
        double foodPerDay = foodPerPersonDay(r.food) * d.costIndex() * (r.adults + 0.6 * r.children);
        List<Map<String, Object>> dayPlans = new ArrayList<>();
        List<Map<String, Object>> bookingEntries = new ArrayList<>();
        int placesPerDay = r.travelStyle.equals("Relaxed") ? 3 : 4;
        Set<String> uniquePlaces = new LinkedHashSet<>();
        for (int day = 1; day <= days; day++) {
            Area area = dayAreas.get(day - 1);
            Zone zone = d.zone(area.zone());
            List<String> names = new ArrayList<>(area.places());
            if (placesPerDay < names.size()) {
                List<String> byScore = new ArrayList<>(names);
                byScore.sort((x, y) -> Double.compare(Interests.score(r.interests, y, area.name(), area.category()), Interests.score(r.interests, x, area.name(), area.category())));
                Set<String> keep = new HashSet<>(byScore.subList(0, placesPerDay));
                names.removeIf(x -> !keep.contains(x));
            }
            Hotel prev = day >= 2 ? nightHotel.get(day - 2) : null;
            Hotel tonight = day <= nights ? nightHotel.get(day - 1) : null;
            double toFirst = prev == null ? 6.0 : zoneDist(d.zone(prev.zone()), zone) + area.exc();
            double back = tonight == null ? (area.exc() > 0 ? area.exc() + 3 : 4.0) : zoneDist(zone, d.zone(tonight.zone())) + area.exc();
            String[] times = timesForStyle(r.travelStyle, day);

            List<Map<String, Object>> entries = new ArrayList<>();
            List<Double> legKms = new ArrayList<>();
            legKms.add(toFirst);
            double dayAct = 0;
            for (int i = 0; i < names.size(); i++) {
                String pn = names.get(i);
                String pcat = placeCategory(pn, area.category());
                uniquePlaces.add(pn);
                double km = i == 0 ? toFirst : 1.2 + (Math.abs((names.get(i - 1) + pn).hashCode()) % 38) / 10.0;
                if (i > 0) legKms.add(km);
                double perAdult = Math.round(entryCost(pn, pcat) * d.activityIndex() / 10.0) * 10.0;
                double groupCost = perAdult * (r.adults + 0.5 * r.children);
                dayAct += groupCost;
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("time", times[Math.min(i, times.length - 1)]);
                e.put("name", pn); e.put("category", pcat); e.put("area", area.name());
                e.put("cost", Math.round(groupCost)); e.put("perPerson", Math.round(perAdult));
                e.put("description", describe(pn, d.name(), pcat, area.name()));
                e.put("duration", durationFor(pcat, i));
                e.put("imageUrl", "/api/place-image?name=" + enc(pn) + "&city=" + enc(d.name()));
                e.put("mapUrl", "https://www.google.com/maps/search/?api=1&query=" + enc(pn + " " + d.name()));
                e.put("wikiUrl", "https://en.wikipedia.org/w/index.php?search=" + enc(pn + " " + d.name()));
                e.put("distanceKm", Math.round(km * 10.0) / 10.0);
                e.put("travelMinutes", minutes(km));
                e.put("distanceFromPrevious", (i == 0 ? (prev == null ? "From arrival point (approx.) • " : "From last night's hotel (approx.) • ") : "") + String.format(Locale.US, "~%.1f km • ~%d min", km, minutes(km)));
                entries.add(e);
            }
            legKms.add(back);
            // local transport
            double dayLocal;
            if (d.intl()) {
                dayLocal = 260 * d.costIndex() * (r.adults + 0.5 * r.children);
            } else {
                int cabs = (int) Math.ceil(n / 4.0);
                double perLegCost = 0;
                for (double km : legKms) perLegCost += Math.max(90 * d.costIndex(), km * 17 * d.costIndex());
                dayLocal = n >= 7 ? Math.min(perLegCost * cabs, perLegCost * 1.7 * Math.ceil(n / 12.0)) : perLegCost * cabs;
            }
            double dayHotel = tonight == null ? 0 : tonight.rate() * rooms;
            actTotal += dayAct; localTotal += dayLocal; hotelTotal += dayHotel; foodTotal += foodPerDay;
            double share = legKms.size() > 0 ? dayLocal / legKms.size() : 0;
            for (Map<String, Object> e : entries) e.put("localTravelCost", Math.round(share));

            Map<String, Object> dp = new LinkedHashMap<>();
            dp.put("day", day);
            dp.put("date", start == null ? null : start.plusDays(day - 1).toString());
            dp.put("title", day == 1 ? "Arrival & first impressions" : day == days ? "Last views & relaxed finish" : "Explore • eat • experience");
            dp.put("areaName", area.name()); dp.put("hotelArea", area.name());
            dp.put("zoneName", zone.name());
            dp.put("excursionKm", area.exc());
            if (area.exc() >= 15) dp.put("excursionNote", "This area is about " + area.exc() + " km from the city base – start early and allow extra travel time.");
            dp.put("places", entries);
            dp.put("firstStopFrom", prev == null ? "arrival point" : prev.name());
            dp.put("hotelToFirstKm", Math.round(toFirst * 10.0) / 10.0);
            dp.put("hotelToFirstMinutes", minutes(toFirst));
            dp.put("activitiesCost", Math.round(dayAct));
            dp.put("localTransportCost", Math.round(dayLocal));
            dp.put("foodCost", Math.round(foodPerDay));
            dp.put("routeUrl", "https://www.google.com/maps/dir/" + String.join("/", names.stream().map(x -> enc(x + " " + d.name())).toList()));
            if (tonight != null) {
                List<Map<String, Object>> opts = new ArrayList<>();
                for (Hotel h : nightOptions.get(day - 1)) opts.add(hotelMap(d, h, rooms, h.name().equals(tonight.name())));
                dp.put("hotel", hotelMap(d, tonight, rooms, true));
                dp.put("hotelOptions", opts);
                dp.put("hotelRepeated", repeated.get(day - 1));
                dp.put("nightNumber", day);
                dp.put("checkIn", start == null ? null : start.plusDays(day - 1).toString());
                dp.put("checkOut", start == null ? null : start.plusDays(day).toString());
                dp.put("hotelNightCost", Math.round(dayHotel));
                dp.put("hotelName", tonight.name());
                Map<String, Object> be = new LinkedHashMap<>(hotelMap(d, tonight, rooms, true));
                be.put("night", day); be.put("checkIn", dp.get("checkIn")); be.put("checkOut", dp.get("checkOut")); be.put("area", area.name());
                bookingEntries.add(be);
            } else {
                dp.put("hotel", null);
                dp.put("checkoutFrom", prev == null ? null : prev.name());
                dp.put("hotelNightCost", 0);
            }
            dayPlans.add(dp);
        }

        // ---- trip-level costs
        double intercity = intercity(r, d, n);
        double visa = d.intl() ? d.visaInsurance() * n : 0;
        double total = intercity + hotelTotal + foodTotal + localTotal + actTotal + visa;

        Map<String, Object> br = new LinkedHashMap<>();
        br.put("intercity", Math.round(intercity)); br.put("stay", Math.round(hotelTotal)); br.put("food", Math.round(foodTotal));
        br.put("local", Math.round(localTotal)); br.put("activities", Math.round(actTotal)); br.put("visa", Math.round(visa));

        Map<String, Object> booking = new LinkedHashMap<>();
        booking.put("rooms", rooms); booking.put("nights", nights); booking.put("roomSummary", String.join(", ", parts));
        booking.put("entries", bookingEntries);
        booking.put("note", "Rates are planning estimates per room per night. Click \"Check rates & book\" to see live prices on the hotel's own site/Google before you pay.");

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("version", 2);
        p.put("destination", d.name()); p.put("country", d.country()); p.put("intl", d.intl());
        p.put("startLocation", r.startLocation); p.put("startDate", start == null ? null : start.toString());
        p.put("endDate", start == null ? null : start.plusDays(days - 1).toString());
        p.put("days", days); p.put("nights", nights);
        p.put("travelers", n); p.put("adults", r.adults); p.put("children", r.children);
        p.put("estimatedCost", Math.round(total));
        p.put("minimumFeasible", Math.round(total));
        p.put("intercityTravel", Math.round(intercity)); p.put("activitiesCost", Math.round(actTotal));
        p.put("localTravelCost", Math.round(localTotal)); p.put("hotelCost", Math.round(hotelTotal)); p.put("foodCost", Math.round(foodTotal));
        p.put("visaCost", Math.round(visa));
        p.put("emergencyBuffer", Math.round(total * 0.10)); p.put("recommendedBudget", Math.round(total * 1.10));
        p.put("breakdown", br);
        p.put("interests", String.join(",", r.interests.stream().map(Interests.Interest::label).toList()));
        p.put("interestKeys", r.interests.stream().map(Interests.Interest::key).toList());
        p.put("travelStyle", r.travelStyle); p.put("transport", r.transport); p.put("foodPreference", r.food);
        p.put("hotelStandard", r.hotelStandard); p.put("hotelTier", TIER[tier]); p.put("hotelPlan", r.hotelPlan);
        p.put("group", group); p.put("booking", booking);
        p.put("daysPlan", dayPlans); p.put("uniquePlaces", uniquePlaces.size());
        p.put("warnings", warnings);
        p.put("tips", List.of(
            "Keep a 10% emergency buffer above the estimated cost.",
            "Hotel rates, fares and entry fees are planning estimates, not live quotes – always confirm before paying.",
            "Check attraction opening days/timings, weather and local rules (and visa rules for international trips) before you travel.",
            "Hotels were chosen to be close to each day's activity area to reduce local travel."));
        return new Built(p, total);
    }

    // ---------------------------------------------------------------- hotels
    private Hotel chooseHotel(Destination d, String zoneId, int tier, Map<String, Integer> usage) {
        Zone z = d.zone(zoneId);
        Hotel best = null; double[] bestKey = null;
        for (Hotel h : d.hotels()) {
            double km = zoneDist(z, d.zone(h.zone()));
            int bucket = km <= 15 ? 0 : km <= 60 ? 1 : 2;
            int used = usage.getOrDefault(h.name(), 0);
            int tp = h.tier() == tier ? 0 : h.tier() < tier ? 1 : 2;
            double[] key = {bucket, used, tp, km, h.rate()};
            if (bestKey == null || less(key, bestKey)) { best = h; bestKey = key; }
        }
        return best;
    }
    private static boolean less(double[] a, double[] b) {
        for (int i = 0; i < a.length; i++) { if (a[i] < b[i]) return true; if (a[i] > b[i]) return false; }
        return false;
    }
    private List<Hotel> optionsFor(Destination d, String zoneId, Hotel selected, int tier) {
        List<Hotel> out = new ArrayList<>(); out.add(selected);
        Set<String> names = new HashSet<>(); names.add(selected.name());
        for (int t = 1; t <= 3; t++) {
            if (t == selected.tier()) continue;
            Zone z = d.zone(zoneId); Hotel best = null; double bk = 1e18;
            for (Hotel h : d.hotels()) {
                if (h.tier() != t || names.contains(h.name())) continue;
                double km = zoneDist(z, d.zone(h.zone()));
                if (km < bk) { bk = km; best = h; }
            }
            if (best != null) { out.add(best); names.add(best.name()); }
        }
        out.sort(Comparator.comparingInt(Hotel::tier));
        return out;
    }
    private Map<String, Object> hotelMap(Destination d, Hotel h, int rooms, boolean selected) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", h.name()); m.put("tier", h.tier()); m.put("tierLabel", TIER[h.tier()]);
        m.put("zone", h.zone()); m.put("zoneName", d.zone(h.zone()).name());
        m.put("rate", Math.round(h.rate())); m.put("rooms", rooms); m.put("nightCost", Math.round(h.rate() * rooms));
        m.put("address", h.address()); m.put("website", h.site() == null ? "" : h.site());
        m.put("mapUrl", "https://www.google.com/maps/search/?api=1&query=" + enc(h.name() + " " + h.address()));
        m.put("searchUrl", "https://www.google.com/search?q=" + enc(h.name() + " " + d.name() + " book rooms"));
        m.put("selected", selected);
        return m;
    }

    // ---------------------------------------------------------------- costs
    private double intercity(PlanRequest r, Destination d, int n) {
        if (d.intl()) return d.flightReturn() * (r.adults + 0.85 * r.children);
        CatalogService.Origin o = catalog.findOrigin(r.startLocation);
        double air = o == null ? 450 : GeoUtil.km(o.lat(), o.lon(), d.lat(), d.lon());
        if (o == null) { /* origin not in list – distance assumed */ }
        if (air < 40) return 0;
        double road = air * 1.4;
        switch (r.transport) {
            case "Flight": return Math.max(5200, 2 * (2300 + 3.6 * air)) * n;
            case "Car": {
                double cabs = Math.ceil(n / 4.0) * (road * 2 * 13 + r.days * 600);
                double tempo = Math.ceil(n / 12.0) * (road * 2 * 22 + r.days * 900);
                return n >= 7 ? Math.min(cabs, tempo) : cabs;
            }
            default: return Math.max(300, road * 1.7) * 2 * (r.adults + 0.6 * r.children);
        }
    }
    private static double foodPerPersonDay(String pref) {
        return switch (pref) {
            case "Vegetarian", "Jain" -> 650; case "Vegan" -> 700; case "Non-Vegetarian" -> 800;
            case "Local Food" -> 600; case "Premium Dining" -> 1800; default -> 700;
        };
    }
    private static final String[][] PREMIUM = {{"universal", "3500"}, {"aquaventure", "3500"}, {"teamlab", "2800"}, {"desert safari", "2200"}, {"houseboat", "3000"},
        {"safari", "1500"}, {"observatory", "1100"}, {"cruise", "900"}, {"rafting", "900"}, {"show", "800"}, {"aquarium", "800"}, {"sea life", "800"}, {"tower", "600"},
        {"sky", "700"}, {"national park", "400"}, {"sanctuary", "450"}, {"zoo", "450"}, {"falls", "500"}, {"cooking", "1200"}, {"workshop", "400"}, {"studio", "400"},
        {"palace", "450"}, {"museum", "350"}, {"gallery", "300"}, {"fort", "250"}, {"tomb", "300"}, {"cave", "250"}};
    private static final String[] FREE = {"beach", "walk", "trail", "promenade", "drive", "bazaar", "market", "street", "square", "piazza", "view", "point", "ghat", "gate", "bridge",
        "fountain", "riverside", "banks", "circle", "road", "mall", "temple", "church", "basilica", "masjid", "mosque", "gurudwara", "shrine", "cathedral", "lake", "park", "hill", "causeway",
        "village", "bagh", "garden", "ridge", "island", "terrace", "seafront", "sea face", "bandstand", "dock", "avenue", "alley", "souk"};
    private static boolean any(String s, String... ks) { for (String k : ks) if (s.contains(k)) return true; return false; }
    /** Each place gets its own category (the catalogue only stores one per area). */
    private static String placeCategory(String name, String areaCat) {
        String s = name.toLowerCase(Locale.ROOT);
        if (any(s, "beach")) return "Beach";
        if (any(s, "food", "cafe", "café", "bakery", "thali", "restaurant", "gelato", "ramen", "cooking class", "breakfast", "misal", "kachori", "dining", "lassi", "coffee", "izakaya", "yakitori", "sushi", "warung", "hawker", "pasta", "monja")) return "Food";
        if (any(s, "zoo", "sanctuary", "national park", "aquarium", "bear rescue", "bird", "wildlife", "safari", "snake", "biological park", "butterfly")) return "Wildlife";
        if (any(s, "rafting", "trek", "paraglid", "kayak", "cruise", "falls", "waterfall", "aquaventure", "universal", "dune", "balloon", "tram")) return "Adventure";
        if (any(s, "fort", "palace", "tomb", "ruins", "wada", "memorial", "castle", "baoli", "stepwell", "citadel", "forum", "colosseum", "acropolis")) return "History";
        if (any(s, "museum", "gallery", "theatre", "temple", "church", "basilica", "masjid", "mosque", "cathedral", "gurudwara", "synagogue", "shrine", "samadhi", "mandir", "wat ", "jingu", "abbey", "planetarium", "opera", "show")) return "Culture";
        if (any(s, "market", "bazaar", "mall", "souk", "shopping", "street", "avenue", "workshop", "craft", "souvenir", "fashion")) return "Shopping";
        if (any(s, "garden", "lake", "park", "hill", "view", "ridge", "terrace", "promenade", "island", "backwater", "plantation", "tea", "dam", "forest", "cliff", "point")) return "Nature";
        return areaCat;
    }
    private double entryCost(String name, String category) {
        String s = name.toLowerCase(Locale.ROOT);
        for (String[] p : PREMIUM) if (s.contains(p[0])) return Double.parseDouble(p[1]);
        for (String f : FREE) if (s.contains(f)) return 0;
        return switch (category) {
            case "Adventure" -> 800; case "Wildlife" -> 500; case "History" -> 300; case "Culture" -> 250; case "Food" -> 350; case "Nature" -> 150; default -> 0;
        };
    }

    // ---------------------------------------------------------------- misc helpers
    private double zoneDist(Zone a, Zone b) {
        if (a.id().equals(b.id())) return 2.5;
        return Math.max(3.0, GeoUtil.km(a.lat(), a.lon(), b.lat(), b.lon()) * 1.35);
    }
    private static int minutes(double km) { return km <= 15 ? (int) Math.max(8, Math.round(km * 4.5)) : (int) Math.round(km * 1.4 + 25); }
    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"); }
    static String money(double x) { return String.format(Locale.US, "%,.0f", Math.max(0, x)); }

    private String[] timesForStyle(String style, int day) {
        String[][] packed = {{"08:00", "11:00", "14:30", "18:00"}, {"08:30", "11:30", "15:00", "18:30"}, {"07:45", "10:45", "14:15", "17:45"}, {"08:15", "11:15", "14:45", "18:15"}};
        String[][] relaxed = {{"10:00", "14:00", "17:30"}, {"09:30", "13:30", "17:00"}, {"10:15", "14:15", "17:45"}, {"09:45", "13:45", "17:15"}};
        String[][] moderate = {{"09:00", "12:15", "15:45", "18:45"}, {"08:45", "12:00", "16:15", "19:00"}, {"09:15", "12:45", "15:30", "18:30"}, {"08:30", "11:45", "15:15", "18:15"}};
        String[][] src = "Packed".equalsIgnoreCase(style) ? packed : "Relaxed".equalsIgnoreCase(style) ? relaxed : moderate;
        return src[(day - 1) % src.length];
    }
    private String durationFor(String c, int i) {
        if (c.equals("Adventure")) return i % 2 == 0 ? "2–4 hrs" : "1.5–3 hrs";
        if (c.equals("Food")) return "1–2 hrs";
        return i % 2 == 0 ? "1–2 hrs" : "1.5–3 hrs";
    }
    private String describe(String name, String city, String category, String area) {
        String known = catalog.description(name);
        if (known != null) return known;
        return switch (category) {
            case "Nature" -> "An open-air stop in " + area + " for a slower walk, photographs and a break from busy streets.";
            case "History" -> "A heritage stop in " + area + " where you can see local architecture and traces of " + city + "'s past.";
            case "Culture" -> "A culture stop in " + area + " with architecture, art or neighbourhood character that works well on foot.";
            case "Food" -> "A food-focused stop in " + area + " – best treated as a tasting or short meal rather than a long sightseeing visit.";
            case "Shopping" -> "A market-style stop in " + area + " for local products, crafts and souvenirs; the surrounding streets are part of the experience.";
            case "Adventure" -> "An active outing around " + area + " with outdoor scenery or a little extra excitement. Keep some flexibility for weather and conditions.";
            case "Wildlife" -> "A nature-and-wildlife experience around " + area + ", a good change of pace from monuments.";
            case "Beach" -> "A coastal stop in " + area + " for sea views, a walk along the shore and local snacks.";
            default -> "A worthwhile stop in " + area + " that adds variety to the day.";
        };
    }
}
