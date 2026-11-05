package com.baldev.airline.airlinecore.model;

import com.baldev.airline.common.embeddable.Support;
import com.baldev.airline.common.enums.AirlineStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Airline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String iataCode;

    @Column(unique = true, nullable = false)
    private String icaoCode;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private String name;

    private String alias;

    private String logoUrl;
    private String website;

    @Enumerated(EnumType.STRING)
    private AirlineStatus status=AirlineStatus.ACTIVE;

    private String alliance;

    private Long headquartersCityId;

    @Embedded
    private Support support;

    private Long updatedById;

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
