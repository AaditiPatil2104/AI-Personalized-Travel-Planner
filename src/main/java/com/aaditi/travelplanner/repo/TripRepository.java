package com.aaditi.travelplanner.repo;
import com.aaditi.travelplanner.model.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface TripRepository extends JpaRepository<Trip,Long>{ List<Trip> findByUserIdOrderByCreatedAtDesc(Long userId); long countByUserId(Long userId); }
