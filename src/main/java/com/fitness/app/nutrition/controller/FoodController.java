package com.fitness.app.nutrition.controller;

import com.fitness.app.nutrition.dto.FoodRequest;
import com.fitness.app.nutrition.dto.FoodResponse;
import com.fitness.app.nutrition.model.FoodCategory;
import com.fitness.app.nutrition.service.FoodService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Expone el catálogo de alimentos para registrar comidas.
 * El administrador gestiona los datos y el resto de roles solo los consulta.
 */
@RestController
@RequestMapping("/api/v1/foods")
@RequiredArgsConstructor
public class FoodController
{
    private final FoodService foodService;

    /** Busca alimentos por categoria, texto y estado activo. */
    @GetMapping
    public PagedModel<FoodResponse> search(@RequestParam(required = false) FoodCategory category,
                                           @RequestParam(required = false) String search,
                                           @RequestParam(required = false) Boolean active,
                                           Pageable pageable)
    {
        return new PagedModel<>(foodService.search(category, search, active, pageable));
    }

    /** Crea un alimento nuevo en el catálogo. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FoodResponse create(@Valid @RequestBody FoodRequest request)
    {
        return foodService.create(request);
    }

    /** Recupera un alimento por su identificador. */
    @GetMapping("/{foodId}")
    public FoodResponse findById(@PathVariable Long foodId)
    {
        return foodService.findById(foodId);
    }

    /** Actualiza los datos de un alimento existente. */
    @PutMapping("/{foodId}")
    public FoodResponse update(@PathVariable Long foodId, @Valid @RequestBody FoodRequest request)
    {
        return foodService.update(foodId, request);
    }

    /** Desactiva un alimento sin borrar su historial. */
    @DeleteMapping("/{foodId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long foodId)
    {
        foodService.deactivate(foodId);
    }
}