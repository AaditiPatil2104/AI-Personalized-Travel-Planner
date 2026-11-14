# YatraAI – AI Personalized India and worldwide Travel Planner (Java)

A Java 17 + Spring Boot travel planner with a nature-green responsive UI, user/admin authentication, budget intelligence, unique day-by-day experiences, area-aware hotel suggestions, place images, Maps/Wikipedia links, recent trips, feedback and admin reports.



## Main user features
- First gateway: **User Login** or **Admin Login**; sticky navigation; travel-style recommendation pages
- Plan Trip -> live budget check -> day-wise itinerary with images, Maps/Wikipedia links, distances and per-day costs
- Recent Trips with View / Edit (pre-fills the planner) / Delete; About Us; Feedback

## Admin features
- Dashboard statistics, traveller list, recent feedback
- Reports (All / Date-wise From-To / Traveller / Place / City) with PDF and CSV download

## Important notes on accuracy
- Hotel names, addresses and places are real, curated entries (data/destinations.json). **Prices are planning estimates, not live quotes**; hotels can rename/close. Use the "Check rates & book" link before paying.
- Distances inside a city are approximate; use the "Open day route in Maps" link for exact routing.
- To add a destination, add an entry to `src/main/resources/data/destinations.json` (areas, places, zones, hotels) - no Java change needed.
- The H2 web console is disabled by default (`spring.h2.console.enabled=false`) for safety.

## Image behavior
The backend exposes `/api/place-image`. It tries to load a matching Wikipedia image server-side so the browser does not need to call Wikipedia directly. If a matching image is unavailable or the network is unavailable, the endpoint returns a built-in YatraAI visual place card, so every itinerary/recommendation card still has an image.

## Technology
- Java 17
- Spring Boot 3.5.5
- Spring Web
- Spring Data JPA
- Spring Security + BCrypt
- H2 embedded database
- HTML/CSS/JavaScript frontend
- Maven

## Default admin
Email: admin@example.com
Password: Use the default password configured locally.


## Run on Windows
1. Extract the ZIP.
2. Open PowerShell in the folder that contains `pom.xml`.
3. Run:

```powershell
mvn clean install
mvn spring-boot:run
```

4. Open `http://localhost:8080`.

You can also run `start.bat` from the project root. It starts Spring Boot and waits for port 8080 before opening the browser.

## Notes
- Attraction prices, hotel prices, fares and distances are **planning estimates**, not live booking/navigation quotes.
- Real hotel and attraction names are used in the curated destination catalogs.
- For destinations outside the curated catalog, the planner uses a generic catalog; for best real-place results use the supported destinations such as Delhi, Agra, Goa, Mumbai, Jaipur, Kerala, Pune and Bengaluru.


FINAL V8 FIXES
- Generate button has a visible in-page progress/error status.
- Persistence failures no longer prevent an itinerary from being generated.
- Added curated world destinations: Paris, London, Dubai, Singapore, Bangkok, Bali, Tokyo, New York, Rome and Sydney.
- Added real hotel names and planning-rate estimates for international destinations. Prices are estimates, not live booking quotes.
