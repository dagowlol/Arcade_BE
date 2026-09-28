package com.exe.arcade_be.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodDto {
    private Long id;
    private String name;
    private String displayName;
    private String image;
    private String category;
    private String pronunciationText;
    private Integer price;
    private Boolean active;
}
