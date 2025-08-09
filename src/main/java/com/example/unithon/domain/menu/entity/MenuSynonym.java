package com.example.unithon.domain.menu.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "menu_synonym", 
       uniqueConstraints = @UniqueConstraint(name = "unique_synonym", columnNames = "synonym"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuSynonym {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id", nullable = false)
    private Menu menu;

    @Column(nullable = false, length = 200)
    private String synonym;

    @Column(columnDefinition = "INT DEFAULT 1")
    private Integer priority = 1;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    public MenuSynonym(Menu menu, String synonym, Integer priority) {
        this.menu = menu;
        this.synonym = synonym;
        this.priority = priority;
    }
} 