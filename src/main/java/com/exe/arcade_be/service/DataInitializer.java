package com.exe.arcade_be.service;

import com.exe.arcade_be.entity.Food;
import com.exe.arcade_be.repository.FoodRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final FoodRepository foodRepository;

    @Override
    public void run(String... args) {
        if (foodRepository.count() == 0) {
            log.info("Seeding initial 9 foods for Food Ordering Challenge...");
            List<Food> defaultFoods = Arrays.asList(
                    Food.builder()
                            .name("Burger")
                            .displayName("Burger")
                            .image("🍔")
                            .category("Fast Food")
                            .pronunciationText("Burger")
                            .price(35000)
                            .active(true)
                            .build(),
                    Food.builder()
                            .name("Pizza")
                            .displayName("Pizza")
                            .image("🍕")
                            .category("Fast Food")
                            .pronunciationText("Pizza")
                            .price(55000)
                            .active(true)
                            .build(),
                    Food.builder()
                            .name("Fried Chicken")
                            .displayName("Fried Chicken")
                            .image("🍗")
                            .category("Fast Food")
                            .pronunciationText("Fried Chicken")
                            .price(45000)
                            .active(true)
                            .build(),
                    Food.builder()
                            .name("French Fries")
                            .displayName("French Fries")
                            .image("🍟")
                            .category("Snack")
                            .pronunciationText("French Fries")
                            .price(25000)
                            .active(true)
                            .build(),
                    Food.builder()
                            .name("Ice Cream")
                            .displayName("Ice Cream")
                            .image("🍦")
                            .category("Dessert")
                            .pronunciationText("Ice Cream")
                            .price(20000)
                            .active(true)
                            .build(),
                    Food.builder()
                            .name("Hot Dog")
                            .displayName("Hot Dog")
                            .image("🌭")
                            .category("Fast Food")
                            .pronunciationText("Hot Dog")
                            .price(30000)
                            .active(true)
                            .build(),
                    Food.builder()
                            .name("Sandwich")
                            .displayName("Sandwich")
                            .image("🥪")
                            .category("Fast Food")
                            .pronunciationText("Sandwich")
                            .price(28000)
                            .active(true)
                            .build(),
                    Food.builder()
                            .name("Chicken Rice")
                            .displayName("Chicken Rice")
                            .image("🍚")
                            .category("Main Dish")
                            .pronunciationText("Chicken Rice")
                            .price(40000)
                            .active(true)
                            .build(),
                    Food.builder()
                            .name("Pasta")
                            .displayName("Pasta")
                            .image("🍝")
                            .category("Main Dish")
                            .pronunciationText("Pasta")
                            .price(45000)
                            .active(true)
                            .build()
            );

            foodRepository.saveAll(defaultFoods);
            log.info("Successfully seeded {} foods.", defaultFoods.size());
        }
    }
}
