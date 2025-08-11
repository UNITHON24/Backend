package com.example.unithon.domain.menu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

import com.example.unithon.domain.menu.entity.Menu;

public interface MenuRepository extends JpaRepository<Menu, Long> {

    @Query("SELECT m FROM Menu m WHERE m.displayName LIKE %:keyword%")
    List<Menu> findByDisplayNameContaining(@Param("keyword") String keyword);

    List<Menu> findByDisplayNameEqualsIgnoreCase(String keyword);
}