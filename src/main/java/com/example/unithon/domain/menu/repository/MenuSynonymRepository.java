package com.example.unithon.domain.menu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

import com.example.unithon.domain.menu.entity.MenuSynonym;
import com.example.unithon.domain.menu.entity.Menu;

public interface MenuSynonymRepository extends JpaRepository<MenuSynonym, Long> {

    @Query("SELECT ms.menu FROM MenuSynonym ms WHERE ms.synonym = :synonym ORDER BY ms.priority")
    List<Menu> findMenuBySynonym(@Param("synonym") String synonym); //아메리카노
} 