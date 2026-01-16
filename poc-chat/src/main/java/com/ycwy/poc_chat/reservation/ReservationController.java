package com.ycwy.poc_chat.reservation;

import com.ycwy.poc_chat.reservation.dto.ReservationDto;
import com.ycwy.poc_chat.security.SecurityUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.FORBIDDEN;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationRepository reservationRepository;

    public ReservationController(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    @GetMapping
    public List<ReservationDto> listReservations() {
        String role = SecurityUtils.currentRole();
        if (!"CLIENT".equals(role)) {
            throw new ResponseStatusException(FORBIDDEN, "Not allowed");
        }
        String userId = SecurityUtils.currentUserId();
        return reservationRepository.findByUserIdOrderByStartAtDesc(userId).stream()
                .map(reservation -> new ReservationDto(
                        reservation.getId(),
                        reservation.getStartAt(),
                        reservation.getEndAt(),
                        reservation.getStatus(),
                        reservation.getTotalPriceCents(),
                        reservation.getCurrency(),
                        reservation.getCarCategoryCode()
                ))
                .toList();
    }
}
