package com.example.campusfind.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "claims")
@Data
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "item_id")
    private Item item;

    @ManyToOne
    @JoinColumn(name = "claimed_by")
    private User claimedBy;

    private String message;

    @Enumerated(EnumType.STRING)
    private ClaimStatus status;

    private LocalDateTime createdAt;
}