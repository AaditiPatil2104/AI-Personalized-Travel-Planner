package com.aaditi.travelplanner.planner;

import java.util.*;

/** Interest options shown in the planner. Each one matches catalogue categories and place-name keywords. */
public final class Interests {
    public record Interest(String key, String label, String icon, Set<String> categories, List<String> keywords) {}

    private static Interest i(String key, String label, String icon, String cats, String kws) {
        Set<String> c = new HashSet<>();
        if (!cats.isBlank()) c.addAll(Arrays.asList(cats.split(",")));
        return new Interest(key, label, icon, c, kws.isBlank() ? List.of() : Arrays.asList(kws.split(",")));
    }

    public static final List<Interest> ALL = List.of(
        i("nature", "Nature & Scenery", "🌿", "Nature", "garden,lake,hill,park,falls,waterfall,view,sunset,trail,forest,ridge,dam,river,terrace"),
        i("beach", "Beaches", "🏖️", "Beach", "beach,coast,bay,island,cliff"),
        i("adventure", "Adventure", "🧗", "Adventure", "trek,rafting,safari,cruise,kayak,cycling,paraglid,waterfall,zip,dune,climb,sunrise,aquaventure"),
        i("history", "History & Heritage", "🏰", "History", "fort,palace,tomb,ruins,heritage,museum,memorial,wada,gate"),
        i("culture", "Culture & Traditions", "🎭", "Culture", "culture,heritage,street,market,theatre,show,village"),
        i("wildlife", "Wildlife & Safari", "🐘", "Wildlife", "zoo,sanctuary,national park,bird,aquarium,wildlife,safari,bear,snake"),
        i("food", "Food & Street Food", "🍜", "Food", "food,cafe,café,bakery,thali,kachori,jalebi,ramen,gelato,coffee,misal,cooking,breakfast,sushi,bistro,warung,restaurant"),
        i("shopping", "Shopping & Markets", "🛍️", "Shopping", "market,bazaar,bazaar,mall,shopping,street,souk,flea,avenue,road,souvenir"),
        i("spiritual", "Spiritual & Religious", "🛕", "", "temple,mandir,church,basilica,masjid,mosque,gurudwara,cathedral,shrine,synagogue,samadhi,mission,wat ,jingu,senso,abbey,pura,iskcon,sri ,buddha,cloister"),
        i("architecture", "Architecture", "🏛️", "", "fort,palace,tower,bridge,tomb,cathedral,gate,opera,church,castle,dome,basilica,bridge,minar,mahal,station,building"),
        i("art", "Art & Museums", "🎨", "", "museum,gallery,art,planetarium,theatre,biennale,studio,ncpa,exhibition,tate,louvre,moma,guggenheim,whitney,met "),
        i("photography", "Photography & Viewpoints", "📸", "", "view,sunset,bridge,tower,fort,gate,falls,cliff,promenade,terrace,sunrise,palace,lake,point,observatory,sky,fountain"),
        i("nightlife", "Nightlife & Evenings", "🌃", "", "night,show,bar,promenade,evening,bandstand,fountain,kabukicho,brew,izakaya,cruise,observatory,sky,opera,dinner,moulin,yakitori,fire dance"),
        i("romantic", "Romantic & Couples", "💞", "", "sunset,garden,lake,beach,promenade,cruise,palace,tower,bridge,cliff,bagh,terrace,canoe,houseboat"),
        i("family", "Family & Kids", "👨‍👩‍👧", "", "zoo,aquarium,park,planetarium,museum,garden,island,waterfront,science,universal,safari,boat,sea life,butterfly,ferry,tram"),
        i("wellness", "Wellness & Relaxation", "🧘", "", "garden,beach,lake,spa,retreat,plantation,backwater,promenade,park,canoe,tea,ridge,osho,cliff"),
        i("trekking", "Trekking & Hiking", "🥾", "", "trek,trail,hill,ridge,walk,fort,ghat,falls,canyon,mountain,peak,walk,sinhagad,nature walk"),
        i("water", "Water Activities", "🚤", "", "cruise,boat,canoe,rafting,kayak,river,beach,backwater,houseboat,dam,lake,waterpark,aquaventure,ferry,harbour"),
        i("crafts", "Local Crafts & Workshops", "🧵", "", "workshop,craft,weaving,pottery,block,inlay,handloom,handicraft,studio,cooking class,zardozi,leather,gemstone,carpet")
    );

    public static Interest find(String s) {
        if (s == null) return null;
        String t = s.trim().toLowerCase(Locale.ROOT);
        for (Interest x : ALL) if (x.key().equals(t) || x.label().toLowerCase(Locale.ROOT).equals(t)) return x;
        // legacy labels from older saved trips ("Nature", "Beach", "History", ...)
        for (Interest x : ALL) if (x.label().toLowerCase(Locale.ROOT).startsWith(t) && t.length() >= 4) return x;
        return null;
    }

    public static double score(List<Interest> selected, String place, String area, String category) {
        String s = (place + " " + area).toLowerCase(Locale.ROOT);
        double sc = 0;
        for (Interest it : selected) {
            if (it.categories().contains(category)) sc += 3;
            for (String k : it.keywords()) if (s.contains(k)) { sc += 2; break; }
        }
        return sc;
    }

    private Interests() {}
}
