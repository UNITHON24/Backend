package com.example.unithon.domain.menu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

import com.example.unithon.domain.menu.entity.MenuOption;

public interface MenuOptionRepository extends JpaRepository<MenuOption, Long> {

    @Query("SELECT mo FROM MenuOption mo WHERE mo.menu.id = :menuId AND mo.optionType = :optionType AND mo.isActive = true ORDER BY mo.displayOrder")
    List<MenuOption> findActiveOptionsByMenuIdAndType(@Param("menuId") Long menuId, @Param("optionType") MenuOption.OptionType optionType);
} 