package com.example.unithon.domain.menu.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "menu_option")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id", nullable = false)
    private Menu menu;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OptionType optionType;

    @Column(nullable = false, length = 100)
    private String optionName;

    @Column(nullable = false, length = 100)
    private String optionValue;

    @Column(precision = 10, scale = 2, columnDefinition = "DECIMAL(10,2) DEFAULT 0.00")
    private BigDecimal additionalPrice = BigDecimal.ZERO;

    @Column(columnDefinition = "BOOLEAN DEFAULT false")
    private Boolean isDefault = false;

    @Column(columnDefinition = "BOOLEAN DEFAULT true")
    private Boolean isActive = true;

    @Column(columnDefinition = "INT DEFAULT 0")
    private Integer displayOrder = 0;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    public MenuOption(Menu menu, OptionType optionType, String optionName, String optionValue, 
                     BigDecimal additionalPrice, Boolean isDefault) {
        this.menu = menu;
        this.optionType = optionType;
        this.optionName = optionName;
        this.optionValue = optionValue;
        this.additionalPrice = additionalPrice;
        this.isDefault = isDefault;
    }

    public enum OptionType {
        SIZE, TEMPERATURE, SET_SIDE, SET_DRINK, ADD_ON
    }
} 