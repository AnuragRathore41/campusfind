package com.example.campusfind.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Entity
@Table(name = "items")
@Data
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Title is required")
    @Size(max = 100, message = "Title must be at most 100 characters")
    private String title;

    @Size(max = 255, message = "Description must be at most 255 characters")
    private String description;

    @NotNull(message = "Type is required (LOST or FOUND)")
    @Enumerated(EnumType.STRING)
    private ItemType type;

    @NotBlank(message = "Category is required")
    private String category;

    @NotBlank(message = "Location is required")
    private String location;

    @Enumerated(EnumType.STRING)
    private ItemStatus status;

    private String imageUrl;

    @ManyToOne
    @JoinColumn(name = "reported_by")
    private User reportedBy;
}