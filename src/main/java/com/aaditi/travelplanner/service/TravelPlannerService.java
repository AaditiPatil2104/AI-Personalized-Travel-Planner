package com.aaditi.travelplanner.service;

import com.aaditi.travelplanner.model.Trip;
import com.aaditi.travelplanner.model.User;
import com.aaditi.travelplanner.repo.TripRepository;
import com.aaditi.travelplanner.repo.UserRepository;
import com.aaditi.travelplanner.repo.FeedbackRepository;
import com.aaditi.travelplanner.model.Feedback;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aaditi.travelplanner.planner.TripPlanner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class TravelPlannerService {
    private final UserRepository users;
    private final TripRepository trips;
    private final PasswordEncoder encoder;
    private final FeedbackRepository feedbacks;
    private final ObjectMapper mapper = new ObjectMapper();
    private final TripPlanner planner;

    public TravelPlannerService(UserRepository users, TripRepository trips, PasswordEncoder encoder, FeedbackRepository feedbacks, TripPlanner planner) {
        this.users = users; this.trips = trips; this.encoder = encoder; this.feedbacks = feedbacks; this.planner = planner;
    }

    public User register(String name, String email, String password, String phone, String city) {
        if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new IllegalArgumentException("Enter a valid email address.");
        if (users.findByEmailIgnoreCase(email).isPresent()) throw new IllegalArgumentException("Email is already registered.");
        if (password == null || password.length() < 6) throw new IllegalArgumentException("Password must contain at least 6 characters.");
        User u = new User(); u.setName(name.trim()); u.setEmail(email.trim().toLowerCase());
        u.setPasswordHash(encoder.encode(password)); u.setPhone(phone); u.setCity(city); return users.save(u);
    }
    public User login(String email, String password) {
        User u = users.findByEmailIgnoreCase(email).orElseThrow(() -> new IllegalArgumentException("Invalid email or password."));
        if (!encoder.matches(password, u.getPasswordHash())) throw new IllegalArgumentException("Invalid email or password."); return u;
    }
    public User getUser(Long id) { return users.findById(id).orElseThrow(); }
    public User updateProfile(Long id, Map<String,Object> body) {
        User u=getUser(id); if(body.get("name")!=null) u.setName(body.get("name").toString().trim());
        if(body.get("phone")!=null) u.setPhone(body.get("phone").toString().trim());
        if(body.get("city")!=null) u.setCity(body.get("city").toString().trim());
        if(body.get("bio")!=null) u.setBio(body.get("bio").toString().trim()); return users.save(u);
    }
    public List<Trip> myTrips(Long id){return trips.findByUserIdOrderByCreatedAtDesc(id);}
    public Trip getTrip(Long uid, Long tid){Trip t=trips.findById(tid).orElseThrow(); if(!t.getUser().getId().equals(uid)) throw new IllegalArgumentException("Trip access denied."); return t;}
    public void deleteTrip(Long uid,Long tid){trips.delete(getTrip(uid,tid));}
    public Map<String,Object> stats(){Map<String,Object>m=new LinkedHashMap<>();m.put("users",users.count());m.put("trips",trips.count());m.put("admins",users.findAll().stream().filter(u->"ADMIN".equals(u.getRole())).count());m.put("feedback",feedbacks.count());return m;}
    public List<User> allUsers(){return users.findAll();}
    public List<Trip> allTrips(){return trips.findAll().stream().sorted(Comparator.comparing(Trip::getCreatedAt).reversed()).toList();}
    public Feedback addFeedback(Long uid,int rating,String message){
        if(rating<1||rating>5) throw new IllegalArgumentException("Rating must be between 1 and 5.");
        if(message==null||message.trim().length()<3) throw new IllegalArgumentException("Please enter a short feedback message.");
        Feedback f=new Feedback();f.setUser(getUser(uid));f.setRating(rating);f.setMessage(message.trim());return feedbacks.save(f);
    }
    public List<Feedback> allFeedback(){return feedbacks.findAllByOrderByCreatedAtDesc();}

    public Map<String,Object> meta(){ return planner.meta(); }
    public Map<String,Object> estimate(Map<String,Object> req){ return planner.estimate(req); }

    @SuppressWarnings("unchecked")
    public Map<String,Object> plan(Long uid, Map<String,Object> req){
        User user=getUser(uid);
        Map<String,Object> out=planner.plan(req);
        try {
            Trip t=new Trip();t.setUser(user);
            t.setStartLocation(String.valueOf(out.get("startLocation")));t.setDestination(String.valueOf(out.get("destination")));
            t.setDays((Integer)out.get("days"));t.setTravelers((Integer)out.get("travelers"));
            t.setAdults((Integer)out.get("adults"));t.setChildren((Integer)out.get("children"));
            @SuppressWarnings("unchecked") Map<String,Object> g=(Map<String,Object>)out.get("group");
            t.setRooms(((Number)g.get("rooms")).intValue());
            if(out.get("startDate")!=null) t.setStartDate(java.time.LocalDate.parse(out.get("startDate").toString()));
            t.setBudget(((Number)out.get("budget")).doubleValue());t.setEstimatedCost(((Number)out.get("estimatedCost")).doubleValue());
            t.setInterests(String.join(",",(java.util.List<String>)out.get("interestKeys")));t.setTravelStyle(String.valueOf(out.get("travelStyle")));
            t.setFoodPreference(String.valueOf(out.get("foodPreference")));t.setTransport(String.valueOf(out.get("transport")));
            t.setItineraryJson(mapper.writeValueAsString(out));t.setCreatedAt(LocalDateTime.now());
            trips.save(t);out.put("tripId",t.getId());out.put("saved",true);
        } catch(Exception ex) {
            // Never block itinerary generation because persistence is temporarily unavailable.
            out.put("saved",false);
            out.put("saveMessage","The itinerary was generated, but it could not be saved to Recent Trips right now.");
        }
        return out;
    }
}
