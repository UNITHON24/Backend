package com.example.unithon.domain.menu.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "menu")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Menu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private MenuCategory category;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 200)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    @Column(columnDefinition = "BOOLEAN DEFAULT true")
    private Boolean isActive = true;

    @Column(columnDefinition = "BOOLEAN DEFAULT false")
    private Boolean hasTemperature = false;

    @Column(columnDefinition = "BOOLEAN DEFAULT false")
    private Boolean hasSize = false;

    @Column(columnDefinition = "BOOLEAN DEFAULT false")
    private Boolean hasSetOption = false;

    @Column(columnDefinition = "INT DEFAULT 0")
    private Integer displayOrder = 0;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "menu", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<MenuSynonym> synonyms = new ArrayList<>();

    @OneToMany(mappedBy = "menu", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<MenuOption> options = new ArrayList<>();

    public Menu(MenuCategory category, String name, String displayName, String description, 
                BigDecimal basePrice, Boolean hasTemperature, Boolean hasSize, Boolean hasSetOption) {
        this.category = category;
        this.name = name;
        this.displayName = displayName;
        this.description = description;
        this.basePrice = basePrice;
        this.hasTemperature = hasTemperature;
        this.hasSize = hasSize;
        this.hasSetOption = hasSetOption;
    }
} 