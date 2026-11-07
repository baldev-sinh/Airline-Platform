package com.baldev.airline.pricing.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@EntityListeners(AuditingEntityListener.class)
public class BaggagePolicy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JsonIgnore
    private Fare fare;

    @Column(nullable = false)
    private String name;

    private String description;

    private Double cabinBaggageMaxWeight;
    @Builder.Default
    private Integer cabinBaggagePieces=1;
    private Double cabinBaggageWeightPerPiece;
    private Integer cabinBaggageMaxDimension;

    private Double checkInBaggageMaxWeight;
    @Builder.Default
    private Integer checkInBaggagePieces=1;

    private Double checkInBaggageWeightPerPiece;

    @Builder.Default
    private Integer freeCheckedBagsAllowance = 0;

    @Builder.Default
    private Boolean priorityBaggage = false;

    @Builder.Default
    private Boolean extraBaggageAllowance = false;

    private Long airlineId;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

}
