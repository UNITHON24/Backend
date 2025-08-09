package com.example.unithon.domain.menu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

import com.example.unithon.domain.menu.entity.MenuSynonym;
import com.example.unithon.domain.menu.entity.Menu;

public interface MenuSynonymRepository extends JpaRepository<MenuSynonym, Long> {

    @Query("SELECT ms.menu FROM MenuSynonym ms WHERE ms.synonym = :synonym AND ms.menu.isActive = true ORDER BY ms.priority")
    Optional<Menu> findMenuBySynonym(@Param("synonym") String synonym);
} 