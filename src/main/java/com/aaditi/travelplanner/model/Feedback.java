package com.aaditi.travelplanner.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="feedback")
public class Feedback {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @ManyToOne(optional=false) private User user;
  private int rating;
  @Column(length=1000) private String message;
  private LocalDateTime createdAt = LocalDateTime.now();
  public Feedback() {}
  public Long getId(){return id;} public User getUser(){return user;} public void setUser(User v){user=v;}
  public int getRating(){return rating;} public void setRating(int v){rating=v;}
  public String getMessage(){return message;} public void setMessage(String v){message=v;}
  public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
}
