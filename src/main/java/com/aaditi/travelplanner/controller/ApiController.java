package com.aaditi.travelplanner.controller;

import com.aaditi.travelplanner.model.*;
import com.aaditi.travelplanner.service.TravelPlannerService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class ApiController {
 private final TravelPlannerService service;
 private static final HttpClient IMAGE_CLIENT=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
 private static final Map<String,byte[]> IMAGE_CACHE=new ConcurrentHashMap<>();
 public ApiController(TravelPlannerService s){service=s;}
 private Map<String,Object> ok(Object data){return Map.of("success",true,"data",data);}
 private Long uid(HttpSession session){Object id=session.getAttribute("userId");if(id==null)throw new IllegalStateException("Please login first.");return ((Number)id).longValue();}
 @PostMapping("/auth/register") public ResponseEntity<?> register(@RequestBody Map<String,Object>b,HttpSession s){try{User u=service.register(req(b,"name"),req(b,"email"),req(b,"password"),opt(b,"phone"),opt(b,"city"));s.setAttribute("userId",u.getId());return ResponseEntity.ok(ok(safeUser(u)));}catch(Exception e){return bad(e.getMessage());}}
 @PostMapping("/auth/login") public ResponseEntity<?> login(@RequestBody Map<String,Object>b,HttpSession s){try{User u=service.login(req(b,"email"),req(b,"password"));String role=opt(b,"role");if(!role.isBlank()&&!role.equalsIgnoreCase(u.getRole()))throw new IllegalArgumentException("These credentials are not for the selected login type.");s.setAttribute("userId",u.getId());return ResponseEntity.ok(ok(safeUser(u)));}catch(Exception e){return bad(e.getMessage());}}
 @PostMapping("/auth/logout") public Map<String,Object> logout(HttpSession s){s.invalidate();return Map.of("success",true);}
 @GetMapping("/auth/me") public ResponseEntity<?> me(HttpSession s){try{return ResponseEntity.ok(ok(safeUser(service.getUser(uid(s)))));}catch(Exception e){return bad(e.getMessage());}}
 @PutMapping("/profile") public ResponseEntity<?> profile(@RequestBody Map<String,Object>b,HttpSession s){try{return ResponseEntity.ok(ok(safeUser(service.updateProfile(uid(s),b))));}catch(Exception e){return bad(e.getMessage());}}
 @PostMapping("/plan") public ResponseEntity<?> plan(@RequestBody Map<String,Object>b,HttpSession s){try{return ResponseEntity.ok(ok(service.plan(uid(s),b)));}catch(Exception e){return bad(e.getMessage());}}
 @GetMapping("/meta") public ResponseEntity<?> meta(){return ResponseEntity.ok(ok(service.meta()));}
 @PostMapping("/plan/estimate") public ResponseEntity<?> estimate(@RequestBody Map<String,Object>b,HttpSession s){try{uid(s);return ResponseEntity.ok(ok(service.estimate(b)));}catch(Exception e){return bad(e.getMessage());}}

 @GetMapping(value="/place-image")
 public ResponseEntity<byte[]> placeImage(@RequestParam String name,@RequestParam(defaultValue="") String city){
   String key=name.trim().toLowerCase(Locale.ROOT)+"|"+city.trim().toLowerCase(Locale.ROOT);
   byte[] cached=IMAGE_CACHE.get(key);
   if(cached!=null) return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(cached);
   try{
     String[] candidates={name.trim(),name.trim().replace(" View","").replace(" Area","").replace(" Walk","").replace(" Market","").replace(" Viewpoint","").replace(" Garden Walk","")};
     com.fasterxml.jackson.databind.ObjectMapper om=new com.fasterxml.jackson.databind.ObjectMapper();
     // First try exact Wikipedia pages, then use Wikipedia search so that places with
     // slightly different page titles (for example "Marine Drive Mumbai") still get a real image.
     for(String candidate:candidates){
       if(candidate.isBlank()) continue;
       String title=URLEncoder.encode(candidate.replace(" ","_"),StandardCharsets.UTF_8).replace("+","%20");
       String api="https://en.wikipedia.org/api/rest_v1/page/summary/"+title;
       HttpRequest req=HttpRequest.newBuilder(URI.create(api)).header("User-Agent","YatraAI-Travel-Planner/1.0").timeout(java.time.Duration.ofSeconds(8)).GET().build();
       HttpResponse<String> r=IMAGE_CLIENT.send(req,HttpResponse.BodyHandlers.ofString());
       if(r.statusCode()==200){
         com.fasterxml.jackson.databind.JsonNode n=om.readTree(r.body());
         String src=n.path("thumbnail").path("source").asText("");
         if(src.isBlank()) src=n.path("originalimage").path("source").asText("");
         byte[] image=downloadImage(src);
         if(image!=null){IMAGE_CACHE.put(key,image);return imageResponse(image);}
       }
     }
     String searchQuery=name.trim()+" "+city.trim();
     String searchUrl="https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch="+URLEncoder.encode(searchQuery,StandardCharsets.UTF_8).replace("+","%20")+"&gsrnamespace=0&gsrlimit=8&prop=pageimages&piprop=thumbnail&pithumbsize=1200&format=json&origin=*";
     HttpRequest sr=HttpRequest.newBuilder(URI.create(searchUrl)).header("User-Agent","YatraAI-Travel-Planner/1.0").timeout(java.time.Duration.ofSeconds(10)).GET().build();
     HttpResponse<String> sres=IMAGE_CLIENT.send(sr,HttpResponse.BodyHandlers.ofString());
     if(sres.statusCode()==200){
       com.fasterxml.jackson.databind.JsonNode pages=om.readTree(sres.body()).path("query").path("pages");
       if(pages.isObject()) for(com.fasterxml.jackson.databind.JsonNode page:pages){
         String src=page.path("thumbnail").path("source").asText("");
         byte[] image=downloadImage(src);
         if(image!=null){IMAGE_CACHE.put(key,image);return imageResponse(image);}
       }
     }
     // Wikimedia Commons search gives real photographs even when the exact
     // Wikipedia article has no thumbnail.
     String commons="https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch="+URLEncoder.encode(searchQuery+" filetype:bitmap",StandardCharsets.UTF_8).replace("+","%20")+"&gsrnamespace=6&gsrlimit=8&prop=imageinfo&iiprop=url&iiurlwidth=1200&format=json&origin=*";
     HttpRequest cr=HttpRequest.newBuilder(URI.create(commons)).header("User-Agent","YatraAI-Travel-Planner/1.0").timeout(java.time.Duration.ofSeconds(10)).GET().build();
     HttpResponse<String> cres=IMAGE_CLIENT.send(cr,HttpResponse.BodyHandlers.ofString());
     if(cres.statusCode()==200){
       com.fasterxml.jackson.databind.JsonNode pages=om.readTree(cres.body()).path("query").path("pages");
       if(pages.isObject()) for(com.fasterxml.jackson.databind.JsonNode page:pages){
         com.fasterxml.jackson.databind.JsonNode ii=page.path("imageinfo").isArray()&&page.path("imageinfo").size()>0?page.path("imageinfo").get(0):null;
         String src=ii==null?"":ii.path("thumburl").asText(ii.path("url").asText(""));
         byte[] image=downloadImage(src);
         if(image!=null){IMAGE_CACHE.put(key,image);return imageResponse(image);}
       }
     }
   }catch(Exception ignored){}
   return ResponseEntity.ok().contentType(MediaType.valueOf("image/svg+xml")).header("Cache-Control","no-store").body(placeSvg(name,city));
 }
 private byte[] downloadImage(String src){
   try{
     if(src==null||src.isBlank()) return null;
     HttpRequest ir=HttpRequest.newBuilder(URI.create(src)).header("User-Agent","YatraAI-Travel-Planner/1.0").timeout(java.time.Duration.ofSeconds(12)).GET().build();
     HttpResponse<byte[]> im=IMAGE_CLIENT.send(ir,HttpResponse.BodyHandlers.ofByteArray());
     if(im.statusCode()==200 && im.body().length>500) return im.body();
   }catch(Exception ignored){}
   return null;
 }
 private ResponseEntity<byte[]> imageResponse(byte[] image){
   return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(image);
 }
 private byte[] placeSvg(String name,String city){
   String safe=name.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
   String safeCity=city.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
   String svg="<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"900\" height=\"560\" viewBox=\"0 0 900 560\"><defs><linearGradient id=\"g\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"1\"><stop stop-color=\"#dff4e7\"/><stop offset=\"1\" stop-color=\"#6da98a\"/></linearGradient></defs><rect width=\"900\" height=\"560\" fill=\"url(#g)\"/><circle cx=\"740\" cy=\"110\" r=\"62\" fill=\"#c8efd5\" opacity=\".9\"/><path d=\"M0 470 Q180 360 350 455 T900 420 V560 H0Z\" fill=\"#4d8d6d\" opacity=\".7\"/><path d=\"M110 470 C160 360 190 300 235 230 C280 300 305 365 350 470\" fill=\"#2f7055\" opacity=\".9\"/><text x=\"60\" y=\"105\" font-family=\"Arial,sans-serif\" font-size=\"24\" font-weight=\"700\" fill=\"#17664b\">YatraAI PLACE PREVIEW</text><text x=\"60\" y=\"170\" font-family=\"Arial,sans-serif\" font-size=\"42\" font-weight=\"800\" fill=\"#17372b\">"+safe+"</text><text x=\"60\" y=\"215\" font-family=\"Arial,sans-serif\" font-size=\"24\" fill=\"#355c4c\">"+safeCity+"</text></svg>";
   return svg.getBytes(StandardCharsets.UTF_8);
 }

 @GetMapping("/trips") public ResponseEntity<?> trips(HttpSession s){try{return ResponseEntity.ok(ok(service.myTrips(uid(s)).stream().map(this::tripSummary).toList()));}catch(Exception e){return bad(e.getMessage());}}
 @GetMapping("/trips/{id}") public ResponseEntity<?> trip(@PathVariable Long id,HttpSession s){try{Trip t=service.getTrip(uid(s),id);return ResponseEntity.ok(ok(tripFull(t)));}catch(Exception e){return bad(e.getMessage());}}
 @DeleteMapping("/trips/{id}") public ResponseEntity<?> delete(@PathVariable Long id,HttpSession s){try{service.deleteTrip(uid(s),id);return ResponseEntity.ok(Map.of("success",true));}catch(Exception e){return bad(e.getMessage());}}
 @PostMapping("/feedback") public ResponseEntity<?> feedback(@RequestBody Map<String,Object>b,HttpSession s){try{int r=Integer.parseInt(opt(b,"rating"));return ResponseEntity.ok(ok(service.addFeedback(uid(s),r,opt(b,"message"))));}catch(Exception e){return bad(e.getMessage());}}
 @GetMapping("/admin/stats") public ResponseEntity<?> stats(HttpSession s){try{checkAdmin(s);return ResponseEntity.ok(ok(service.stats()));}catch(Exception e){return bad(e.getMessage());}}
 @GetMapping("/admin/users") public ResponseEntity<?> users(HttpSession s){try{checkAdmin(s);return ResponseEntity.ok(ok(service.allUsers().stream().map(this::safeUser).toList()));}catch(Exception e){return bad(e.getMessage());}}
 @GetMapping("/admin/trips") public ResponseEntity<?> allTrips(HttpSession s){try{checkAdmin(s);return ResponseEntity.ok(ok(service.allTrips().stream().map(this::tripSummary).toList()));}catch(Exception e){return bad(e.getMessage());}}
 @GetMapping("/admin/feedback") public ResponseEntity<?> feedbackList(HttpSession s){try{checkAdmin(s);List<Map<String,Object>> out=new ArrayList<>();for(Feedback f:service.allFeedback()){Map<String,Object>m=new LinkedHashMap<>();m.put("userName",f.getUser().getName());m.put("email",f.getUser().getEmail());m.put("rating",f.getRating());m.put("message",f.getMessage());m.put("createdAt",f.getCreatedAt());out.add(m);}return ResponseEntity.ok(ok(out));}catch(Exception e){return bad(e.getMessage());}}
 @GetMapping("/admin/report") public ResponseEntity<?> report(@RequestParam(defaultValue="all") String type,@RequestParam(defaultValue="") String value,@RequestParam(defaultValue="") String from,@RequestParam(defaultValue="") String to,HttpSession s){
   try{
     checkAdmin(s);
     LocalDate f=from.isBlank()?null:LocalDate.parse(from), tt=to.isBlank()?null:LocalDate.parse(to);
     if(f!=null&&tt!=null&&tt.isBefore(f)) throw new IllegalArgumentException("The 'To' date cannot be before the 'From' date.");
     String v=value.toLowerCase(Locale.ROOT).trim();
     if(type.equalsIgnoreCase("date")&&f==null&&tt==null) throw new IllegalArgumentException("Choose a From date and/or a To date for the date-wise report.");
     if((type.equalsIgnoreCase("user")||type.equalsIgnoreCase("place")||type.equalsIgnoreCase("city"))&&v.isBlank()) throw new IllegalArgumentException("Enter a value to filter by.");
     List<Map<String,Object>> rows=new ArrayList<>(); double sumBudget=0,sumEst=0; int sumTrav=0;
     for(Trip t:service.allTrips()){
       LocalDate created=t.getCreatedAt().toLocalDate();
       if(f!=null&&created.isBefore(f)) continue; if(tt!=null&&created.isAfter(tt)) continue;
       boolean match=true;
       if(type.equalsIgnoreCase("user")) match=t.getUser().getName().toLowerCase(Locale.ROOT).contains(v)||t.getUser().getEmail().toLowerCase(Locale.ROOT).contains(v);
       else if(type.equalsIgnoreCase("place")) match=t.getDestination().toLowerCase(Locale.ROOT).contains(v)||(t.getItineraryJson()!=null&&t.getItineraryJson().toLowerCase(Locale.ROOT).contains(v));
       else if(type.equalsIgnoreCase("city")) match=t.getDestination().toLowerCase(Locale.ROOT).contains(v)||t.getStartLocation().toLowerCase(Locale.ROOT).contains(v);
       if(!match) continue;
       rows.add(tripSummary(t)); sumBudget+=t.getBudget(); sumEst+=t.getEstimatedCost(); sumTrav+=t.getTravelers();
     }
     Map<String,Object> out=new LinkedHashMap<>();
     out.put("type",type);out.put("value",value);out.put("from",from);out.put("to",to);out.put("count",rows.size());
     out.put("totalBudget",Math.round(sumBudget));out.put("totalEstimate",Math.round(sumEst));out.put("totalTravellers",sumTrav);
     out.put("averageEstimate",rows.isEmpty()?0:Math.round(sumEst/rows.size()));
     out.put("generatedAt",LocalDateTime.now().toString());out.put("rows",rows);
     return ResponseEntity.ok(ok(out));
   }catch(java.time.format.DateTimeParseException e){return bad("Please enter valid dates.");}
   catch(Exception e){return bad(e.getMessage());}
 }
 private void checkAdmin(HttpSession s){User u=service.getUser(uid(s));if(!"ADMIN".equals(u.getRole()))throw new IllegalArgumentException("Administrator access required.");}
 private String req(Map<String,Object>b,String k){String v=opt(b,k);if(v.isBlank())throw new IllegalArgumentException(k+" is required.");return v;}
 private String opt(Map<String,Object>b,String k){return b.get(k)==null?"":b.get(k).toString().trim();}
 private ResponseEntity<Map<String,Object>> bad(String msg){return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("success",false,"message",msg==null?"Something went wrong.":msg));}
 private Map<String,Object> safeUser(User u){Map<String,Object>m=new LinkedHashMap<>();m.put("id",u.getId());m.put("name",u.getName());m.put("email",u.getEmail());m.put("role",u.getRole());m.put("phone",u.getPhone()==null?"":u.getPhone());m.put("city",u.getCity()==null?"":u.getCity());m.put("bio",u.getBio()==null?"":u.getBio());return m;}
 private Map<String,Object> tripSummary(Trip t){Map<String,Object>m=new LinkedHashMap<>();m.put("id",t.getId());m.put("destination",t.getDestination());m.put("startLocation",t.getStartLocation());m.put("days",t.getDays());m.put("travelers",t.getTravelers());m.put("budget",t.getBudget());m.put("estimatedCost",Math.round(t.getEstimatedCost()));m.put("interests",t.getInterests());m.put("createdAt",t.getCreatedAt());m.put("userName",t.getUser().getName());m.put("userEmail",t.getUser().getEmail());m.put("startDate",t.getStartDate()==null?"":t.getStartDate().toString());m.put("adults",t.getAdults()==null?t.getTravelers():t.getAdults());m.put("children",t.getChildren()==null?0:t.getChildren());m.put("rooms",t.getRooms()==null?0:t.getRooms());m.put("travelStyle",t.getTravelStyle());m.put("transport",t.getTransport());m.put("food",t.getFoodPreference());return m;}
 private Map<String,Object> tripFull(Trip t){try{Map<String,Object>m=tripSummary(t);m.put("itinerary",new com.fasterxml.jackson.databind.ObjectMapper().readValue(t.getItineraryJson(),Map.class));return m;}catch(Exception e){throw new RuntimeException(e);}}
}
