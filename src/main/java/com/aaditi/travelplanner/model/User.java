package com.aaditi.travelplanner.model;

import jakarta.persistence.*;

@Entity
@Table(name="app_users")
public class User {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(nullable=false) private String name;
  @Column(nullable=false, unique=true) private String email;
  @Column(nullable=false) private String passwordHash;
  @Column(nullable=false) private String role = "USER";
  private String phone;
  private String city;
  private String bio;
  public User() {}
  public Long getId(){return id;} public void setId(Long v){id=v;}
  public String getName(){return name;} public void setName(String v){name=v;}
  public String getEmail(){return email;} public void setEmail(String v){email=v;}
  public String getPasswordHash(){return passwordHash;} public void setPasswordHash(String v){passwordHash=v;}
  public String getRole(){return role;} public void setRole(String v){role=v;}
  public String getPhone(){return phone;} public void setPhone(String v){phone=v;}
  public String getCity(){return city;} public void setCity(String v){city=v;}
  public String getBio(){return bio;} public void setBio(String v){bio=v;}
}
