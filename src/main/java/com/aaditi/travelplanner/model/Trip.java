package com.aaditi.travelplanner.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name="trips")
public class Trip {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @ManyToOne(optional=false) private User user;
  private String startLocation, destination, travelStyle, foodPreference, transport;
  @Column(length=1200) private String interests;
  private LocalDate startDate;
  private Integer adults, children, rooms;
  private int days, travelers;
  private double budget, estimatedCost;
  @Lob private String itineraryJson;
  private LocalDateTime createdAt = LocalDateTime.now();
  public Trip() {}
  public LocalDate getStartDate(){return startDate;} public void setStartDate(LocalDate v){startDate=v;}
  public Integer getAdults(){return adults;} public void setAdults(Integer v){adults=v;}
  public Integer getChildren(){return children;} public void setChildren(Integer v){children=v;}
  public Integer getRooms(){return rooms;} public void setRooms(Integer v){rooms=v;}
  public Long getId(){return id;} public User getUser(){return user;} public void setUser(User v){user=v;}
  public void setId(Long v){id=v;} public String getStartLocation(){return startLocation;} public void setStartLocation(String v){startLocation=v;}
  public String getDestination(){return destination;} public void setDestination(String v){destination=v;}
  public String getInterests(){return interests;} public void setInterests(String v){interests=v;}
  public String getTravelStyle(){return travelStyle;} public void setTravelStyle(String v){travelStyle=v;}
  public String getFoodPreference(){return foodPreference;} public void setFoodPreference(String v){foodPreference=v;}
  public String getTransport(){return transport;} public void setTransport(String v){transport=v;}
  public int getDays(){return days;} public void setDays(int v){days=v;} public int getTravelers(){return travelers;} public void setTravelers(int v){travelers=v;}
  public double getBudget(){return budget;} public void setBudget(double v){budget=v;} public double getEstimatedCost(){return estimatedCost;} public void setEstimatedCost(double v){estimatedCost=v;}
  public String getItineraryJson(){return itineraryJson;} public void setItineraryJson(String v){itineraryJson=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
}
