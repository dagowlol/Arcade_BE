package com.exe.arcade_be.repository;

import com.exe.arcade_be.entity.Food;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FoodRepository extends JpaRepository<Food, Long> {
    List<Food> findByActiveTrue();
    Optional<Food> findByNameIgnoreCase(String name);
}
