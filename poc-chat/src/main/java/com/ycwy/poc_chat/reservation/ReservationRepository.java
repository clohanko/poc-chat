package com.ycwy.poc_chat.reservation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, String> {
    List<Reservation> findByUserIdOrderByStartAtDesc(String userId);
}
