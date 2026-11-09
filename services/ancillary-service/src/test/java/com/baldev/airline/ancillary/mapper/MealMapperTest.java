package com.baldev.airline.ancillary.mapper;

import com.baldev.airline.ancillary.model.Meal;
import com.baldev.airline.ancillary.payload.response.MealResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MealMapperTest {

    @Test
    void responseIncludesTheMealCode() {
        // The code is the airline's own identifier for a meal (unique per airline). It used to be left out of the
        // response, so clients could not tell meals apart by code or check whether one already existed.
        Meal meal = Meal.builder()
                .id(7L).code("VGML").name("Paneer tikka wrap").mealType("VEG")
                .ingredients("Grilled paneer, mint chutney").airlineId(3L)
                .build();

        MealResponse response = MealMapper.toResponse(meal);

        assertThat(response.getCode()).isEqualTo("VGML");
        assertThat(response.getName()).isEqualTo("Paneer tikka wrap");
        assertThat(response.getIngredients()).isEqualTo("Grilled paneer, mint chutney");
        assertThat(response.getAvailable()).isTrue(); // @Builder.Default
    }

    @Test
    void nullMealGivesNullResponse() {
        assertThat(MealMapper.toResponse(null)).isNull();
    }
}
