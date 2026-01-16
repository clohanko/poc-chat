package com.ycwy.poc_chat.reservation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(nullable = false)
    private String status;

    @Column(name = "total_price_cents", nullable = false)
    private int totalPriceCents;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "pickup_agency_id", nullable = false, length = 36)
    private String pickupAgencyId;

    @Column(name = "dropoff_agency_id", nullable = false, length = 36)
    private String dropoffAgencyId;

    @Column(name = "car_category_code", nullable = false, length = 4)
    private String carCategoryCode;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public void setStartAt(LocalDateTime startAt) {
        this.startAt = startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public void setEndAt(LocalDateTime endAt) {
        this.endAt = endAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getTotalPriceCents() {
        return totalPriceCents;
    }

    public void setTotalPriceCents(int totalPriceCents) {
        this.totalPriceCents = totalPriceCents;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPickupAgencyId() {
        return pickupAgencyId;
    }

    public void setPickupAgencyId(String pickupAgencyId) {
        this.pickupAgencyId = pickupAgencyId;
    }

    public String getDropoffAgencyId() {
        return dropoffAgencyId;
    }

    public void setDropoffAgencyId(String dropoffAgencyId) {
        this.dropoffAgencyId = dropoffAgencyId;
    }

    public String getCarCategoryCode() {
        return carCategoryCode;
    }

    public void setCarCategoryCode(String carCategoryCode) {
        this.carCategoryCode = carCategoryCode;
    }
}
