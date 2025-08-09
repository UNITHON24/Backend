package com.example.unithon.domain.menu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

import com.example.unithon.domain.menu.entity.Menu;

public interface MenuRepository extends JpaRepository<Menu, Long> {

    @Query("SELECT m FROM Menu m WHERE m.isActive = true ORDER BY m.displayOrder")
    List<Menu> findActiveMenus();

    @Query("SELECT m FROM Menu m JOIN FETCH m.options WHERE m.isActive = true AND m.id = :menuId")
    Optional<Menu> findActiveMenuWithOptions(@Param("menuId") Long menuId);
} 