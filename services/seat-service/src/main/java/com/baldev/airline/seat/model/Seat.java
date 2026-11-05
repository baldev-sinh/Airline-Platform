package com.baldev.airline.seat.model;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.baldev.airline.common.util.Money;
import java.math.BigDecimal;

import com.baldev.airline.common.enums.SeatType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@EntityListeners(AuditingEntityListener.class)
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String seatNumber;

    @Column(nullable = false)
    private Integer seatRow;

    private Character columnLetter;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private SeatType seatType;

    private BigDecimal basePrice;

    private BigDecimal premiumSuperCharge;

    private Boolean isAvailable=true;

    private Boolean isBlocked=false;

    private Boolean isEmergencyExist=false;

    private Boolean isActive=true;

    private Boolean hasExtraLegroom=false;

    private Boolean hasPowerOutlet=false;

    private Boolean hasTvScreen=false;

    private Boolean hasExtraWidth = false;

    private Integer seatPitch;
    private Integer seatWidth;


    @ManyToOne
    private SeatMap seatMap;

    @ManyToOne
    private CabinClass cabinClass;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;

    @Version
    private Long version;

    public BigDecimal getTotalPrice() {
        return Money.sum(basePrice, premiumSuperCharge);
    }

    public boolean isBookable(){
        return isActive && isAvailable && !isBlocked;
    }
    public String getFullPosition(){
        return seatRow + ""+columnLetter;
    }



}
