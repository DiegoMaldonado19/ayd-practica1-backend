# Módulo 8 — nutrition (alimentación): plan de implementación

Documento de trabajo del backend (`ayd-practica1-backend`) para construir el módulo de
**nutrition** del sistema de gestión de gimnasio. Contraste:
`instructiones_oc/03-API-REST.md` (§3.8), `instructiones_oc/02-Modulos.md` (módulo 8) y
`04-Base-de-Datos.md` (§6).

Convenciones del proyecto que este módulo respeta:

- Paquete vertical `com.fitness.app.nutrition` con subpaquetes `controller`, `dto`,
  `model`, `repository`, `service` (igual que el módulo `training`).
- `@JsonProperty` explícito en **cada** campo de los DTO, aunque el `ObjectMapper`
  global ya usa `SNAKE_CASE` (estilo de `TrainerAssignmentResponse`, `ProgressMeasurementResponse`).
- JSON `snake_case`, base URL `/api/v1`, autenticación JWT Bearer (`AuthenticatedUser`).
- Aislamiento entre módulos (02-Modulos §1 y §3): nutrition **solo inyecta Services**
  de otros módulos (`MemberService`, `TrainerService`, `MembershipService`,
  `TrainerAssignmentService`) y nunca sus repositorios.
- Reglas de fila de §3.2 #3 concentradas en un solo punto por módulo: aquí en
  `NutritionGuard`, que es una fachada sobre `MemberService.findById`.
- Fechas de rango abiertas con sentinelas `0001-01-01` / `9999-12-31` y patrón JPQL
  `(:param IS NULL OR ...)` (igual que `RoutineService` / `ExerciseRepository`).
- Paginación devuelta como `PagedModel<>` (igual que `ExerciseController`, `RoutineController`).

---

## 0. Integración requerida (lo único que toca código existente)

### 0.1 `ErrorCode` — NO se toca

Todos los códigos de error del módulo ya existen en `common/exception/ErrorCode.java`:

| Código | HTTP | suggestedAction | Uso |
|---|---|---|---|
| `FOOD_NOT_FOUND` | 404 | – | alimento inexistente |
| `FOOD_IN_USE` | 409 | `DEACTIVATE_INSTEAD` | intento de borrar un alimento usado (no se dispara: el DELETE es lógico) |
| `MEAL_NOT_FOUND` | 404 | – | comida inexistente |
| `MEAL_EDIT_WINDOW_CLOSED` | 409 | – | editar/borrar una comida de otro día |
| `NUTRITION_GOAL_NOT_FOUND` | 404 | – | el socio no tiene meta vigente |
| `TRAINER_SCOPE_VIOLATION` | 403 | – | entrenador sobre socio no asignado (lo lanza `MemberService.findById`) |
| `FORBIDDEN_RESOURCE` | 403 | – | socio sobre el archivo de otro socio |
| `VALIDATION_ERROR` | 400 | – | reglas de negocio de entrada (alimento desactivado, duplicados, rangos) |

### 0.2 `GymProperties` — agregar el bloque `nutrition`

`application.yml` ya declara `gym.nutrition.default-tolerance-percent: 10`, pero
`GymProperties` (en `config`) aún no lo enlaza. Agregar el componente y la firma:

```java
@ConfigurationProperties(prefix = "gym")
public record GymProperties(Freeze freeze, GuestPass guestPass, Classes classes, Membership membership,
                            Nutrition nutrition)
{
    // ...records existentes (Freeze, GuestPass, Classes, Membership, Billing)...

    /**
     * "El rango aceptable de la meta se define con un porcentaje de tolerancia
     * (por ejemplo, ±10%)" (§3.8): value left to the team, so it is configuration.
     */
    public record Nutrition(int defaultTolerancePercent)
    {
    }
}
```

> Nota: la tolerancia se toma de aquí (no de una constante) porque es una regla que el
> enunciado deja a criterio del equipo, igual que `freeze.*`, `classes.*`, etc.

### 0.3 `SecurityConfig` — los matchers que faltan

El `SecurityConfig` **ya cubre** las rutas bajo `/api/v1/members/*` (líneas ~73-81):

```java
.requestMatchers(HttpMethod.PUT,
        "/api/v1/members/*/routines",
        "/api/v1/members/*/nutrition-goal")
        .hasAnyRole("ADMIN", "TRAINER", "MEMBER")
.requestMatchers(HttpMethod.GET,
        "/api/v1/members/*/routines",
        "/api/v1/members/*/nutrition-summary",
        "/api/v1/members/*/nutrition-goal")
        .hasAnyRole("ADMIN", "TRAINER", "MEMBER")
```

**Ajuste sugerido:** según la matriz de §3.8, `nutrition-summary` y `nutrition-goal`
son T/S (sin ADMIN). Quitar `"ADMIN"` de ambos bloques para cumplir la matriz (el
guard de servicio ya bloquea a ADMIN igualmente, pero es mejor que la matriz de
seguridad lo refleje).

**Agregar** los bloques de `/foods` y `/meals` (no existen), respetando la regla de
especificidad del archivo — antes de los matchers genéricos:

```java
// --- nutrition (§3.8): catálogo de alimentos (solo ADMIN edita)
.requestMatchers(HttpMethod.GET, "/api/v1/foods", "/api/v1/foods/*")
        .hasAnyRole("ADMIN", "TRAINER", "MEMBER")
.requestMatchers(HttpMethod.POST, "/api/v1/foods", "/api/v1/foods/*").hasRole("ADMIN")
.requestMatchers(HttpMethod.PUT, "/api/v1/foods", "/api/v1/foods/*").hasRole("ADMIN")
.requestMatchers(HttpMethod.DELETE, "/api/v1/foods", "/api/v1/foods/*").hasRole("ADMIN")

// --- nutrition (§3.8): comidas diarias (S registra/edita/elimina; T y S consultan)
.requestMatchers(HttpMethod.GET, "/api/v1/meals", "/api/v1/meals/*").hasAnyRole("TRAINER", "MEMBER")
.requestMatchers(HttpMethod.POST, "/api/v1/meals").hasRole("MEMBER")
.requestMatchers(HttpMethod.PUT, "/api/v1/meals/*").hasRole("MEMBER")
.requestMatchers(HttpMethod.DELETE, "/api/v1/meals/*").hasRole("MEMBER")
```

> Con esto el único trabajo pendiente sobre código existente es: `GymProperties`
> (sección 0.2) y `SecurityConfig` (sección 0.3). **ErrorCode y schema.sql no se
> tocan.**

---

## 1. Arquitectura y lógica de cálculo proporcional

### 1.1 Mapa de endpoints (13, según §3.8)

| # | Método y ruta | Roles | Descripción |
|---|---|---|---|
| 1 | `GET /api/v1/foods` | A T S | Catálogo. Filtros: `category`, `search`, `active`. Paginado. |
| 2 | `POST /api/v1/foods` | A | Alta de alimento con macros por porción. |
| 3 | `GET /api/v1/foods/{id}` | A T S | Detalle de un alimento. |
| 4 | `PUT /api/v1/foods/{id}` | A | Edición. |
| 5 | `DELETE /api/v1/foods/{id}` | A | **Baja lógica** (`active=false`). 204. |
| 6 | `GET /api/v1/meals` | T S | Historial. Filtros: `member_id`, `date`, `from`, `to`. Paginado. |
| 7 | `POST /api/v1/meals` | S | Registra la comida del día con sus líneas. 201. |
| 8 | `GET /api/v1/meals/{id}` | T S | Detalle con aporte calculado por línea. |
| 9 | `PUT /api/v1/meals/{id}` | S | Edición (solo el mismo día). |
| 10 | `DELETE /api/v1/meals/{id}` | S | Eliminación (solo el mismo día). 204. |
| 11 | `GET /api/v1/members/{id}/nutrition-summary` | T S | Resumen del día (o rango `from`/`to`): totales, desglose por tiempo de comida y comparación contra la meta. |
| 12 | `GET /api/v1/members/{id}/nutrition-goal` | T S | Meta vigente con tolerancia. |
| 13 | `PUT /api/v1/members/{id}/nutrition-goal` | T S | Fija/ajusta la meta: cierra la vigente y abre una nueva. |

### 1.2 Cálculo proporcional (el corazón del módulo)

`food` almacena las macros de **UNA porción** (`serving_size` + `serving_unit`). El
socio come una `quantity` cualquiera (gramos, ml o unidades), así que el aporte de
cada línea se deriva siempre, nunca se persiste (04-Base-de-Datos §6):

```
aporte_macro = macro_por_porcion * quantity / serving_size
```

Reglas de redondeo: **2 decimales, HALF_UP** (consistente con el `NUMERIC(x,2)` de la BD).
Como todas las cantidades son `BigDecimal`, `quantity` se normaliza con `setScale(2)`
para que la operación nunca pierda precisión.

Esta única fórmula se usa en tres lugares distintos:
- detalle de comida → por línea (campo `quantity` + macros derivadas),
- totales de una comida → suma de sus líneas,
- resumen diario → suma por tiempo de comida (BREAKFAST/LUNCH/DINNER/SNACK).

### 1.3 Clasificación calórica del día

Con la meta vigente (`daily_calories` ± `tolerance_percent` de `gym.nutrition.*`):

- consumido `< daily_calories * (1 - tol/100)` → `UNDER`
- consumido `> daily_calories * (1 + tol/100)` → `OVER`
- en medio → `ACCEPTABLE`

Se devuelve además `calorie_difference` (consumido − meta) y `percent_of_goal`
(consumido/meta × 100). Sin meta vigente, los tres campos van en `null` (no hay
contra qué comparar).

### 1.4 DDL de soporte (ya existe en `schema.sql`, no se modifica)

```sql
CREATE TABLE food (
    food_id         BIGSERIAL    PRIMARY KEY,
    code            VARCHAR(30)  NOT NULL,
    name            VARCHAR(80)  NOT NULL,
    category        VARCHAR(20)  NOT NULL,
    serving_size    NUMERIC(7,2) NOT NULL,
    serving_unit    VARCHAR(10)  NOT NULL,
    calories        NUMERIC(7,2) NOT NULL,
    protein_g       NUMERIC(6,2) NOT NULL,
    carbohydrates_g NUMERIC(6,2) NOT NULL,
    fat_g           NUMERIC(6,2) NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_food_code UNIQUE (code),
    CONSTRAINT ck_food_serving_size CHECK (serving_size > 0),
    CONSTRAINT ck_food_macros       CHECK (calories >= 0 AND protein_g >= 0 AND carbohydrates_g >= 0 AND fat_g >= 0),
    CONSTRAINT ck_food_category     CHECK (category IN ('PROTEIN','CARBOHYDRATE','FAT','VEGETABLE','FRUIT','DAIRY','BEVERAGE','PREPARED','OTHER')),
    CONSTRAINT ck_food_serving_unit CHECK (serving_unit IN ('GRAM','MILLILITER','UNIT'))
);

CREATE TABLE meal (
    meal_id    BIGSERIAL    PRIMARY KEY,
    member_id  BIGINT       NOT NULL,   -- plain Long: directory, no JPA hacia member
    log_date   DATE         NOT NULL,
    meal_type  VARCHAR(20)  NOT NULL,
    notes      VARCHAR(200),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT fk_meal_member FOREIGN KEY (member_id) REFERENCES member (member_id),
    CONSTRAINT ck_meal_type   CHECK (meal_type IN ('BREAKFAST','LUNCH','DINNER','SNACK'))
);

CREATE TABLE meal_item (
    meal_item_id BIGSERIAL    PRIMARY KEY,
    meal_id      BIGINT       NOT NULL,
    food_id      BIGINT       NOT NULL,
    quantity     NUMERIC(7,2) NOT NULL,
    CONSTRAINT fk_meal_item_meal FOREIGN KEY (meal_id) REFERENCES meal (meal_id) ON DELETE CASCADE,
    CONSTRAINT fk_meal_item_food FOREIGN KEY (food_id) REFERENCES food (food_id) ON DELETE RESTRICT,
    CONSTRAINT uq_meal_item_food UNIQUE (meal_id, food_id),
    CONSTRAINT ck_meal_item_qty  CHECK (quantity > 0)
);

CREATE TABLE nutrition_goal (
    nutrition_goal_id BIGSERIAL    PRIMARY KEY,
    member_id         BIGINT       NOT NULL,
    goal_type         VARCHAR(20)  NOT NULL,
    daily_calories    NUMERIC(6,2) NOT NULL,
    tolerance_percent NUMERIC(4,2) NOT NULL DEFAULT 10,
    target_weight_kg  NUMERIC(5,2),
    defined_by        VARCHAR(20)  NOT NULL,
    defined_by_user_id BIGINT      NOT NULL,
    start_date        DATE         NOT NULL,
    end_date          DATE,
    CONSTRAINT fk_goal_member        FOREIGN KEY (member_id)        REFERENCES member (member_id),
    CONSTRAINT fk_goal_defined_by    FOREIGN KEY (defined_by_user_id) REFERENCES app_user (user_id),
    CONSTRAINT uq_goal_current       UNIQUE (member_id, end_date),  -- end_date NULL = vigente; una sola por socio
    CONSTRAINT ck_goal_type          CHECK (goal_type IN ('WEIGHT_LOSS','MUSCLE_GAIN','MAINTENANCE')),
    CONSTRAINT ck_goal_calories      CHECK (daily_calories BETWEEN 800 AND 8000),
    CONSTRAINT ck_goal_tolerance     CHECK (tolerance_percent BETWEEN 0 AND 50)
);
```

Los `CHECK` de `food` se reflejan en las anotaciones de validación de `FoodRequest`,
de modo que el `GlobalExceptionHandler` devuelva `VALIDATION_ERROR` (400) antes de
llegar a PostgreSQL.

---

## 2. Entidades JPA (`com.fitness.app.nutrition.model`)

Diez archivos: seis enums y cuatro entidades. Las entidades usan **`Long` planos**
para FKs de otros módulos (`memberId`), igual que `ProgressMeasurement` y `Routine`.
Los campos numéricos son `BigDecimal`; los enums se mapean con `@Enumerated(STRING)`.

### 2.1 `FoodCategory.java`

```java
package com.fitness.app.nutrition.model;

/**
 * The closed set of food categories, mirroring ck_food_category from schema.sql.
 */
public enum FoodCategory
{
    PROTEIN, CARBOHYDRATE, FAT, VEGETABLE, FRUIT, DAIRY, BEVERAGE, PREPARED, OTHER
}
```

### 2.2 `ServingUnit.java`

```java
package com.fitness.app.nutrition.model;

/** How a serving is measured, mirroring ck_food_serving_unit from schema.sql. */
public enum ServingUnit
{
    GRAM, MILLILITER, UNIT
}
```

### 2.3 `MealType.java`

```java
package com.fitness.app.nutrition.model;

/** The four meal times of the day, mirroring ck_meal_type from schema.sql. */
public enum MealType
{
    BREAKFAST, LUNCH, DINNER, SNACK
}
```

### 2.4 `GoalType.java`

```java
package com.fitness.app.nutrition.model;

/** The kind of caloric goal, mirroring ck_goal_type from schema.sql. */
public enum GoalType
{
    WEIGHT_LOSS, MUSCLE_GAIN, MAINTENANCE
}
```

### 2.5 `GoalDefinedBy.java`

```java
package com.fitness.app.nutrition.model;

/** Who defined the goal stretch (the statement: the member, or the assigned trainer). */
public enum GoalDefinedBy
{
    MEMBER, TRAINER
}
```

### 2.6 `CalorieStatus.java`

```java
package com.fitness.app.nutrition.model;

/**
 * How the consumed calories of a day compare to the goal: below, inside the
 * tolerance band (±tolerance_percent) or above it.
 */
public enum CalorieStatus
{
    UNDER, ACCEPTABLE, OVER
}
```

### 2.7 `Food.java`

```java
package com.fitness.app.nutrition.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Catalog row the member picks from when logging a meal. The macros are the
 * reference values for ONE serving (serving_size + serving_unit); the totals of a
 * day are computed on the fly against the quantity actually eaten and are never
 * stored (04-Base-de-Datos §6).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Food
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long foodId;

    private String      code;
    private String      name;

    @Enumerated(EnumType.STRING)
    private FoodCategory category;

    private BigDecimal  servingSize;

    @Enumerated(EnumType.STRING)
    private ServingUnit servingUnit;

    private BigDecimal  calories;
    private BigDecimal  proteinG;
    private BigDecimal  carbohydratesG;
    private BigDecimal  fatG;

    private boolean     active;
    private Instant     createdAt;
}
```

### 2.8 `Meal.java`

```java
package com.fitness.app.nutrition.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One of the four daily meal times, with an optional note. member_id is a plain
 * Long: Member belongs to directory and the isolation rule of 02-Modulos §1
 * forbids navigating there through JPA.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Meal
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long      mealId;

    private Long      memberId;
    private LocalDate logDate;

    @Enumerated(EnumType.STRING)
    private MealType  mealType;

    private String    notes;
    private Instant   createdAt;
    private Instant   updatedAt;
}
```

### 2.9 `MealItem.java`

```java
package com.fitness.app.nutrition.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One line of a meal: the food and the quantity actually eaten. The macros are
 * derived from food.serving_size and the quantity - never stored - which is why
 * the row carries only the quantity (schema.sql).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class MealItem
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long       mealItemId;

    private Long       mealId;
    private Long       foodId;
    private BigDecimal quantity;
}
```

### 2.10 `NutritionGoal.java`

```java
package com.fitness.app.nutrition.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One stretch of the member's caloric target. The active row is the one with
 * end_date IS NULL (uq_goal_current enforces one open goal per member). A goal is
 * never edited: a new stretch opens and the previous one closes, which is also the
 * change history.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class NutritionGoal
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long        nutritionGoalId;

    private Long        memberId;

    @Enumerated(EnumType.STRING)
    private GoalType    goalType;

    private BigDecimal  dailyCalories;
    private BigDecimal  tolerancePercent;
    private BigDecimal  targetWeightKg;

    @Enumerated(EnumType.STRING)
    private GoalDefinedBy definedBy;

    private Long        definedByUserId;
    private LocalDate   startDate;
    private LocalDate   endDate;
}
```

---

## 3. DTOs (`com.fitness.app.nutrition.dto`)

Doce archivos: ocho records de request/response y tres de agregación (más el que
agrupa el resumen). Todos los componentes llevan `@JsonProperty` explícito.

### 3.1 `FoodRequest.java`

```java
package com.fitness.app.nutrition.dto;

import com.fitness.app.nutrition.model.FoodCategory;
import com.fitness.app.nutrition.model.ServingUnit;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Create or update a catalog row. The bounds mirror ck_food_* from schema.sql. */
public record FoodRequest(
    @NotBlank @Size(max = 30)
    String code,

    @NotBlank @Size(max = 80)
    String name,

    @NotNull
    FoodCategory category,

    @NotNull @DecimalMin("0.01") @Digits(integer = 5, fraction = 2)
    @JsonProperty("serving_size")
    BigDecimal servingSize,

    @NotNull
    @JsonProperty("serving_unit")
    ServingUnit servingUnit,

    @NotNull @DecimalMin("0.00") @Digits(integer = 5, fraction = 2)
    BigDecimal calories,

    @NotNull @DecimalMin("0.00") @Digits(integer = 4, fraction = 2)
    @JsonProperty("protein_g")
    BigDecimal proteinG,

    @NotNull @DecimalMin("0.00") @Digits(integer = 4, fraction = 2)
    @JsonProperty("carbohydrates_g")
    BigDecimal carbohydratesG,

    @NotNull @DecimalMin("0.00") @Digits(integer = 4, fraction = 2)
    @JsonProperty("fat_g")
    BigDecimal fatG
)
{
}
```

### 3.2 `FoodResponse.java`

```java
package com.fitness.app.nutrition.dto;

import com.fitness.app.nutrition.model.Food;
import com.fitness.app.nutrition.model.FoodCategory;
import com.fitness.app.nutrition.model.ServingUnit;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

/** One catalog row as the interface sees it. */
public record FoodResponse(
    @JsonProperty("food_id")           Long         foodId,
    @JsonProperty("code")              String       code,
    @JsonProperty("name")              String       name,
    @JsonProperty("category")          FoodCategory category,
    @JsonProperty("serving_size")      BigDecimal   servingSize,
    @JsonProperty("serving_unit")      ServingUnit  servingUnit,
    @JsonProperty("calories")          BigDecimal   calories,
    @JsonProperty("protein_g")         BigDecimal   proteinG,
    @JsonProperty("carbohydrates_g")   BigDecimal   carbohydratesG,
    @JsonProperty("fat_g")             BigDecimal   fatG,
    @JsonProperty("active")            boolean      active,
    @JsonProperty("created_at")        Instant      createdAt
)
{
    public static FoodResponse from(Food food)
    {
        return new FoodResponse(
            food.getFoodId(),
            food.getCode(),
            food.getName(),
            food.getCategory(),
            food.getServingSize(),
            food.getServingUnit(),
            food.getCalories(),
            food.getProteinG(),
            food.getCarbohydratesG(),
            food.getFatG(),
            food.isActive(),
            food.getCreatedAt()
        );
    }
}
```

### 3.3 `MealItemRequest.java`

```java
package com.fitness.app.nutrition.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** One line of a meal being registered: the food and the quantity eaten. */
public record MealItemRequest(
    @NotNull @JsonProperty("food_id")
    Long foodId,

    @NotNull @DecimalMin("0.01") @Digits(integer = 5, fraction = 2)
    BigDecimal quantity
)
{
}
```

### 3.4 `MealRequest.java`

```java
package com.fitness.app.nutrition.dto;

import com.fitness.app.nutrition.model.MealType;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * POST /meals: the logged meal with its lines. log_date defaults to today and is
 * the anchor of the same-day edit rule, so it cannot be a future date.
 */
public record MealRequest(
    @JsonProperty("member_id")
    Long memberId,

    @PastOrPresent
    @JsonProperty("log_date")
    LocalDate logDate,

    @NotNull
    @JsonProperty("meal_type")
    MealType mealType,

    @Size(max = 200)
    String notes,

    @NotEmpty @Valid
    List<MealItemRequest> items
)
{
}
```

> `memberId` no lleva `@NotNull`: un socio que no lo envía se resuelve a su propio
> archivo en `NutritionGuard.scopedMemberId`; el entrenador **sí** debe enviarlo.

### 3.5 `MealUpdateRequest.java`

```java
package com.fitness.app.nutrition.dto;

import com.fitness.app.nutrition.model.MealType;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** PUT /meals/{id}: same day only, so the date is not editable. */
public record MealUpdateRequest(
    @NotNull @JsonProperty("meal_type")
    MealType mealType,

    @Size(max = 200)
    String notes,

    @NotEmpty @Valid
    List<MealItemRequest> items
)
{
}
```

### 3.6 `MealItemResponse.java`

```java
package com.fitness.app.nutrition.dto;

import com.fitness.app.nutrition.model.ServingUnit;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * One line of a meal with the contribution already computed against the quantity
 * eaten: calories and macros are quantity/serving_size * base and are never stored.
 */
public record MealItemResponse(
    @JsonProperty("meal_item_id")      Long         mealItemId,
    @JsonProperty("meal_id")           Long         mealId,
    @JsonProperty("food_id")           Long         foodId,
    @JsonProperty("food_name")         String       foodName,
    @JsonProperty("quantity")          BigDecimal   quantity,
    @JsonProperty("serving_size")      BigDecimal   servingSize,
    @JsonProperty("serving_unit")      ServingUnit  servingUnit,
    @JsonProperty("calories")          BigDecimal   calories,
    @JsonProperty("protein_g")         BigDecimal   proteinG,
    @JsonProperty("carbohydrates_g")   BigDecimal   carbohydratesG,
    @JsonProperty("fat_g")             BigDecimal   fatG
)
{
}
```

### 3.7 `MealResponse.java`

```java
package com.fitness.app.nutrition.dto;

import com.fitness.app.nutrition.model.MealType;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** A meal as the interface sees it, with the totals of its lines already derived. */
public record MealResponse(
    @JsonProperty("meal_id")             Long               mealId,
    @JsonProperty("member_id")           Long               memberId,
    @JsonProperty("log_date")            LocalDate          logDate,
    @JsonProperty("meal_type")           MealType           mealType,
    @JsonProperty("notes")               String             notes,
    @JsonProperty("created_at")          Instant            createdAt,
    @JsonProperty("updated_at")          Instant            updatedAt,
    @JsonProperty("items")               List<MealItemResponse> items,
    @JsonProperty("total_calories")      BigDecimal         totalCalories,
    @JsonProperty("total_protein_g")     BigDecimal         totalProteinG,
    @JsonProperty("total_carbohydrates_g") BigDecimal       totalCarbohydratesG,
    @JsonProperty("total_fat_g")         BigDecimal         totalFatG
)
{
}
```

### 3.8 `NutritionGoalRequest.java`

```java
package com.fitness.app.nutrition.dto;

import com.fitness.app.nutrition.model.GoalType;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * The target to put in force. tolerance_percent is optional: it falls back to
 * gym.nutrition.default-tolerance-percent. The bounds mirror ck_goal_*.
 */
public record NutritionGoalRequest(
    @NotNull @JsonProperty("goal_type")
    GoalType goalType,

    @NotNull @DecimalMin("800") @DecimalMax("8000") @Digits(integer = 4, fraction = 2)
    @JsonProperty("daily_calories")
    BigDecimal dailyCalories,

    @DecimalMin("0") @DecimalMax("50") @Digits(integer = 2, fraction = 2)
    @JsonProperty("tolerance_percent")
    BigDecimal tolerancePercent,

    @DecimalMin("0.01") @Digits(integer = 3, fraction = 2)
    @JsonProperty("target_weight_kg")
    BigDecimal targetWeightKg
)
{
}
```

### 3.9 `NutritionGoalResponse.java`

```java
package com.fitness.app.nutrition.dto;

import com.fitness.app.nutrition.model.GoalDefinedBy;
import com.fitness.app.nutrition.model.GoalType;
import com.fitness.app.nutrition.model.NutritionGoal;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

/** The goal in force (end_date IS NULL). The response has no end_date on purpose. */
public record NutritionGoalResponse(
    @JsonProperty("nutrition_goal_id")   Long           nutritionGoalId,
    @JsonProperty("member_id")           Long           memberId,
    @JsonProperty("goal_type")           GoalType       goalType,
    @JsonProperty("daily_calories")      BigDecimal     dailyCalories,
    @JsonProperty("tolerance_percent")   BigDecimal     tolerancePercent,
    @JsonProperty("target_weight_kg")    BigDecimal     targetWeightKg,
    @JsonProperty("defined_by")          GoalDefinedBy  definedBy,
    @JsonProperty("start_date")          LocalDate      startDate
)
{
    public static NutritionGoalResponse from(NutritionGoal goal)
    {
        return new NutritionGoalResponse(
            goal.getNutritionGoalId(),
            goal.getMemberId(),
            goal.getGoalType(),
            goal.getDailyCalories(),
            goal.getTolerancePercent(),
            goal.getTargetWeightKg(),
            goal.getDefinedBy(),
            goal.getStartDate()
        );
    }
}
```

### 3.10 `DailyTotals.java`

```java
package com.fitness.app.nutrition.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/** The four derived totals of a day (or of one meal time). */
public record DailyTotals(
    @JsonProperty("calories")          BigDecimal calories,
    @JsonProperty("protein_g")         BigDecimal proteinG,
    @JsonProperty("carbohydrates_g")   BigDecimal carbohydratesG,
    @JsonProperty("fat_g")             BigDecimal fatG
)
{
}
```

### 3.11 `MealTimeSummary.java`

```java
package com.fitness.app.nutrition.dto;

import com.fitness.app.nutrition.model.MealType;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/** Aggregates of one meal time for the daily summary (BREAKFAST, LUNCH, ...). */
public record MealTimeSummary(
    @JsonProperty("meal_type")   MealType     mealType,
    @JsonProperty("meal_count")  long         mealCount,
    @JsonProperty("calories")    BigDecimal   calories,
    @JsonProperty("protein_g")   BigDecimal   proteinG,
    @JsonProperty("carbohydrates_g") BigDecimal carbohydratesG,
    @JsonProperty("fat_g")       BigDecimal   fatG
)
{
}
```

### 3.12 `NutritionSummaryResponse.java`

```java
package com.fitness.app.nutrition.dto;

import com.fitness.app.nutrition.model.CalorieStatus;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The daily summary: totals, breakdown by meal time and the comparison against the
 * goal in force. Without a goal, goal/calorie_status/calorie_difference/percent_of_goal
 * are null - there is nothing to compare against.
 */
public record NutritionSummaryResponse(
    @JsonProperty("member_id")          Long                    memberId,
    @JsonProperty("date")               LocalDate               date,
    @JsonProperty("totals")             DailyTotals             totals,
    @JsonProperty("by_meal_time")       List<MealTimeSummary>   byMealTime,
    @JsonProperty("goal")               NutritionGoalResponse   goal,
    @JsonProperty("calorie_status")     CalorieStatus           calorieStatus,
    @JsonProperty("calorie_difference") BigDecimal              calorieDifference,
    @JsonProperty("percent_of_goal")    BigDecimal              percentOfGoal
)
{
}
```

---

## 4. Repositorios JPA (`com.fitness.app.nutrition.repository`)

Cuatro interfaces. Mismo estilo que el resto del proyecto: `@Query` JPQL con el
patrón `(:param IS NULL OR ...)` para filtros opcionales y sentinelas de fecha para
rangos abiertos.

### 4.1 `FoodRepository.java`

```java
package com.fitness.app.nutrition.repository;

import com.fitness.app.nutrition.model.Food;
import com.fitness.app.nutrition.model.FoodCategory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FoodRepository extends JpaRepository<Food, Long>
{
    boolean existsByCode(String code);

    /**
     * "Catálogo. Filtros: category, search, active" (§3.8). search must never be
     * null, only empty: PostgreSQL types an untyped null as bytea. FoodService
     * normalizes null to "" before calling.
     */
    @Query("""
           SELECT f
             FROM Food f
            WHERE (:category IS NULL OR f.category = :category)
              AND (:active IS NULL OR f.active = :active)
              AND (:search = '' OR LOWER(f.name) LIKE LOWER(CONCAT('%', :search, '%'))
                                 OR LOWER(f.code) LIKE LOWER(CONCAT('%', :search, '%')))
           """)
    Page<Food> search(FoodCategory category, Boolean active, String search, Pageable pageable);
}
```

### 4.2 `MealRepository.java`

```java
package com.fitness.app.nutrition.repository;

import com.fitness.app.nutrition.model.Meal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface MealRepository extends JpaRepository<Meal, Long>
{
    /**
     * "Historial. Filtros: member_id, date, from, to" (§3.8). A date makes from = to
     * = date; the service normalizes the sentinels before calling. The explicit
     * ORDER BY is the default when the client sends no sort.
     */
    @Query("""
           SELECT m
             FROM Meal m
            WHERE (:memberId IS NULL OR m.memberId = :memberId)
              AND m.logDate BETWEEN :from AND :to
            ORDER BY m.logDate DESC, m.mealId DESC
           """)
    Page<Meal> search(Long memberId, LocalDate from, LocalDate to, Pageable pageable);

    List<Meal> findByMemberIdAndLogDateOrderByMealId(Long memberId, LocalDate logDate);

    List<Meal> findByMemberIdAndLogDateBetweenOrderByLogDateAscMealIdAsc(Long memberId,
                                                                         LocalDate from,
                                                                         LocalDate to);
}
```

### 4.3 `MealItemRepository.java`

```java
package com.fitness.app.nutrition.repository;

import com.fitness.app.nutrition.model.MealItem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface MealItemRepository extends JpaRepository<MealItem, Long>
{
    List<MealItem> findByMealIdIn(Collection<Long> mealIds);

    List<MealItem> findByMealIdOrderByMealItemId(Long mealId);

    /**
     * Bulk delete runs immediately (bypasses the persistence context): on PUT
     * /meals/{id} the old lines are gone before the new ones are inserted, so
     * uq_meal_item_food never sees a duplicate (meal_id, food_id) row.
     */
    @Modifying
    @Query("DELETE FROM MealItem mi WHERE mi.mealId = :mealId")
    void deleteByMealId(Long mealId);
}
```

### 4.4 `NutritionGoalRepository.java`

```java
package com.fitness.app.nutrition.repository;

import com.fitness.app.nutrition.model.NutritionGoal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NutritionGoalRepository extends JpaRepository<NutritionGoal, Long>
{
    /** uq_goal_current in Java: the row with end_date IS NULL is the goal in force. */
    Optional<NutritionGoal> findByMemberIdAndEndDateIsNull(Long memberId);
}
```

---

## 5. Servicios (`com.fitness.app.nutrition.service`)

Cinco clases: `NutritionMath`, `NutritionGuard`, `FoodService`, `MealService`,
`NutritionGoalService` y `NutritionSummaryService`. **Importante:** solo inyectan
Services de otros módulos (`MemberService`, `MembershipService`), nunca repositorios
de otros módulos.

### 5.1 `NutritionMath.java`

```java
package com.fitness.app.nutrition.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The one formula of the module: what a quantity eaten contributes against the
 * reference macros of one serving. Derived values are never persisted.
 */
final class NutritionMath
{
    static final BigDecimal HUNDRED = new BigDecimal("100");

    private NutritionMath()
    {
    }

    static BigDecimal contribution(BigDecimal quantity, BigDecimal servingSize, BigDecimal baseValue)
    {
        return baseValue.multiply(quantity)
                .divide(servingSize, 2, RoundingMode.HALF_UP);
    }
}
```

### 5.2 `NutritionGuard.java`

```java
package com.fitness.app.nutrition.service;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.directory.MemberService;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.iam.model.UserRole;
import com.fitness.app.membership.MembershipService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The two gates every member-scoped nutrition route shares: the row-level rule of
 * §3.2 #3 (a member only reaches its own file, a trainer only its assigned members)
 * and "registrar comidas o consultar resúmenes requiere membresía activa" (§3.8).
 *
 * The scoping rules live in one place (MemberService.findById), so this guard is a
 * thin, nutrition-shaped façade over them.
 */
@Component
@RequiredArgsConstructor
public class NutritionGuard
{
    private final MemberService     memberService;
    private final MembershipService membershipService;

    /**
     * The memberId the caller may act on:
     * MEMBER  -> the caller's own file (a member passing another id gets
     *            FORBIDDEN_RESOURCE; omitting member_id resolves to its own file),
     * TRAINER -> the requested member, only when a trainer_assignment is open
     *            (TRAINER_SCOPE_VIOLATION otherwise),
     * any other role -> the requested member.
     */
    public Long scopedMemberId(Long memberId, AuthenticatedUser principal)
    {
        if (principal.role() == UserRole.MEMBER && memberId == null)
        {
            return memberService.findOwnMemberId(principal);
        }

        if (memberId == null)
        {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Debe indicar el socio (member_id).");
        }

        memberService.findById(memberId, principal);

        return memberId;
    }

    /** "La membresía debe estar activa" (§3.8 #3) - MEMBERSHIP_NOT_ACTIVE otherwise. */
    public void requireActiveMembership(Long memberId)
    {
        membershipService.findActiveMembership(memberId);
    }
}
```

> `MemberService.findById` ya discrimina por rol y lanza `TRAINER_SCOPE_VIOLATION`
> para un entrenador sobre un socio no asignado y `FORBIDDEN_RESOURCE` para un socio
> sobre el archivo de otro (ver `MemberService.assertOwnFile`). No hace falta replicar
> esa lógica aquí.

### 5.3 `FoodService.java`

```java
package com.fitness.app.nutrition.service;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.nutrition.dto.FoodRequest;
import com.fitness.app.nutrition.dto.FoodResponse;
import com.fitness.app.nutrition.model.Food;
import com.fitness.app.nutrition.model.FoodCategory;
import com.fitness.app.nutrition.repository.FoodRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class FoodService
{
    private final FoodRepository foodRepository;

    @Transactional(readOnly = true)
    public Page<FoodResponse> search(FoodCategory category, String search, Boolean active, Pageable pageable)
    {
        return foodRepository.search(category, active, search == null ? "" : search, pageable)
                .map(FoodResponse::from);
    }

    public FoodResponse create(FoodRequest request)
    {
        if (foodRepository.existsByCode(request.code()))
        {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Ya existe un alimento con ese código.");
        }

        var food = new Food();
        food.setCode(request.code());
        food.setName(request.name());
        food.setCategory(request.category());
        food.setServingSize(request.servingSize());
        food.setServingUnit(request.servingUnit());
        food.setCalories(request.calories());
        food.setProteinG(request.proteinG());
        food.setCarbohydratesG(request.carbohydratesG());
        food.setFatG(request.fatG());
        food.setActive(true);
        food.setCreatedAt(Instant.now());

        return FoodResponse.from(foodRepository.save(food));
    }

    @Transactional(readOnly = true)
    public FoodResponse findById(Long foodId)
    {
        return FoodResponse.from(findOrFail(foodId));
    }

    public FoodResponse update(Long foodId, FoodRequest request)
    {
        var food = findOrFail(foodId);

        if (!request.code().equals(food.getCode()) && foodRepository.existsByCode(request.code()))
        {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Ya existe un alimento con ese código.");
        }

        food.setCode(request.code());
        food.setName(request.name());
        food.setCategory(request.category());
        food.setServingSize(request.servingSize());
        food.setServingUnit(request.servingUnit());
        food.setCalories(request.calories());
        food.setProteinG(request.proteinG());
        food.setCarbohydratesG(request.carbohydratesG());
        food.setFatG(request.fatG());

        return FoodResponse.from(food);
    }

    /**
     * "DELETE /foods/{id}: desactiva el alimento" (§3.8). Logical deletion: meal_item
     * is ON DELETE RESTRICT and the history of already logged meals must survive.
     */
    public void deactivate(Long foodId)
    {
        findOrFail(foodId).setActive(false);
    }

    /**
     * Package-private for the module's services: a meal line references the catalog.
     * The active check is done by the caller, which carries the exact context.
     */
    Food findOrFail(Long foodId)
    {
        return foodRepository.findById(foodId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FOOD_NOT_FOUND));
    }

    /** Package-private bulk lookup for the module's summary builders. */
    Map<Long, Food> findAllById(Collection<Long> foodIds)
    {
        if (foodIds.isEmpty())
        {
            return Map.of();
        }

        return foodRepository.findAllById(foodIds).stream()
                .collect(Collectors.toMap(Food::getFoodId, food -> food));
    }
}
```

### 5.4 `MealService.java`

```java
package com.fitness.app.nutrition.service;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.nutrition.dto.MealItemRequest;
import com.fitness.app.nutrition.dto.MealItemResponse;
import com.fitness.app.nutrition.dto.MealRequest;
import com.fitness.app.nutrition.dto.MealResponse;
import com.fitness.app.nutrition.dto.MealUpdateRequest;
import com.fitness.app.nutrition.model.Meal;
import com.fitness.app.nutrition.model.MealItem;
import com.fitness.app.nutrition.repository.MealItemRepository;
import com.fitness.app.nutrition.repository.MealRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The daily meals of §3.8. Registration and edit are S-only; the trainer reaches
 * the meals of its assigned members. Every derived macro comes from
 * NutritionMath.contribution against the quantity eaten.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MealService
{
    private static final LocalDate NO_LOWER_BOUND = LocalDate.of(1, 1, 1);
    private static final LocalDate NO_UPPER_BOUND = LocalDate.of(9999, 12, 31);

    private final MealRepository     mealRepository;
    private final MealItemRepository mealItemRepository;
    private final FoodService        foodService;
    private final NutritionGuard     guard;

    @Transactional(readOnly = true)
    public Page<MealResponse> search(Long memberId, LocalDate date, LocalDate from, LocalDate to,
                                     AuthenticatedUser principal, Pageable pageable)
    {
        var scopedMemberId = guard.scopedMemberId(memberId, principal);
        guard.requireActiveMembership(scopedMemberId);

        var effectiveFrom = date != null ? date : (from != null ? from : NO_LOWER_BOUND);
        var effectiveTo   = date != null ? date : (to != null ? to : NO_UPPER_BOUND);

        if (effectiveFrom.isAfter(effectiveTo))
        {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "La fecha inicial no puede ser posterior a la final.");
        }

        var page = mealRepository.search(scopedMemberId, effectiveFrom, effectiveTo, pageable);

        return new PageImpl<>(buildResponses(page.getContent()), page.getPageable(), page.getTotalElements());
    }

    public MealResponse create(MealRequest request, AuthenticatedUser principal)
    {
        var memberId = guard.scopedMemberId(request.memberId(), principal);
        guard.requireActiveMembership(memberId);
        assertDistinctFoods(request.items());

        var meal = new Meal();
        meal.setMemberId(memberId);
        meal.setLogDate(request.logDate() == null ? LocalDate.now() : request.logDate());
        meal.setMealType(request.mealType());
        meal.setNotes(request.notes());
        meal.setCreatedAt(Instant.now());
        mealRepository.save(meal);

        var items = new ArrayList<MealItem>();

        for (var itemRequest : request.items())
        {
            items.add(toItem(meal.getMealId(), itemRequest));
        }

        mealItemRepository.saveAll(items);

        return buildResponse(meal, items);
    }

    @Transactional(readOnly = true)
    public MealResponse findById(Long mealId, AuthenticatedUser principal)
    {
        var meal = findOrFail(mealId);

        guard.scopedMemberId(meal.getMemberId(), principal);
        guard.requireActiveMembership(meal.getMemberId());

        var items = mealItemRepository.findByMealIdOrderByMealItemId(mealId);

        return buildResponse(meal, items);
    }

    /** "Editar... solo en el mismo día" (§3.8): MEAL_EDIT_WINDOW_CLOSED otherwise. */
    public MealResponse update(Long mealId, MealUpdateRequest request, AuthenticatedUser principal)
    {
        var meal = findOrFail(mealId);

        assertSameDay(meal.getLogDate());
        guard.scopedMemberId(meal.getMemberId(), principal);
        guard.requireActiveMembership(meal.getMemberId());
        assertDistinctFoods(request.items());

        meal.setMealType(request.mealType());
        meal.setNotes(request.notes());
        meal.setUpdatedAt(Instant.now());

        mealItemRepository.deleteByMealId(mealId);

        var items = new ArrayList<MealItem>();

        for (var itemRequest : request.items())
        {
            items.add(toItem(mealId, itemRequest));
        }

        mealItemRepository.saveAll(items);

        return buildResponse(meal, items);
    }

    /** "Eliminar... solo en el mismo día" (§3.8): MEAL_EDIT_WINDOW_CLOSED otherwise. */
    public void delete(Long mealId, AuthenticatedUser principal)
    {
        var meal = findOrFail(mealId);

        assertSameDay(meal.getLogDate());
        guard.scopedMemberId(meal.getMemberId(), principal);
        guard.requireActiveMembership(meal.getMemberId());

        mealItemRepository.deleteByMealId(mealId);
        mealRepository.delete(meal);
    }

    private static void assertSameDay(LocalDate logDate)
    {
        if (!logDate.equals(LocalDate.now()))
        {
            throw new BusinessException(ErrorCode.MEAL_EDIT_WINDOW_CLOSED);
        }
    }

    private static void assertDistinctFoods(List<MealItemRequest> items)
    {
        var distinctFoodIds = items.stream().map(MealItemRequest::foodId).distinct().count();

        if (distinctFoodIds != items.size())
        {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "El mismo alimento no puede repetirse en una comida.");
        }
    }

    private MealItem toItem(Long mealId, MealItemRequest itemRequest)
    {
        var food = foodService.findOrFail(itemRequest.foodId());

        if (!food.isActive())
        {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "El alimento '" + food.getName() + "' está desactivado y no puede usarse.");
        }

        var item = new MealItem();
        item.setMealId(mealId);
        item.setFoodId(food.getFoodId());
        item.setQuantity(itemRequest.quantity());

        return item;
    }

    private Meal findOrFail(Long mealId)
    {
        return mealRepository.findById(mealId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEAL_NOT_FOUND));
    }

    private List<MealResponse> buildResponses(List<Meal> meals)
    {
        if (meals.isEmpty())
        {
            return List.of();
        }

        var mealIds = meals.stream().map(Meal::getMealId).toList();
        var itemsByMeal = mealItemRepository.findByMealIdIn(mealIds).stream()
                .collect(Collectors.groupingBy(MealItem::getMealId));
        var foods = foodService.findAllById(
                itemsByMeal.values().stream().flatMap(List::stream).map(MealItem::getFoodId).toList());

        return meals.stream()
                .map(meal -> buildResponse(meal, itemsByMeal.getOrDefault(meal.getMealId(), List.of()), foods))
                .toList();
    }

    private MealResponse buildResponse(Meal meal, List<MealItem> items)
    {
        return buildResponse(meal, items, foodService.findAllById(
                items.stream().map(MealItem::getFoodId).toList()));
    }

    private MealResponse buildResponse(Meal meal, List<MealItem> items, Map<Long, Food> foods)
    {
        var itemResponses = items.stream()
                .map(item -> toItemResponse(item, foods.get(item.getFoodId())))
                .toList();

        return new MealResponse(
            meal.getMealId(),
            meal.getMemberId(),
            meal.getLogDate(),
            meal.getMealType(),
            meal.getNotes(),
            meal.getCreatedAt(),
            meal.getUpdatedAt(),
            itemResponses,
            sum(itemResponses, MealItemResponse::calories),
            sum(itemResponses, MealItemResponse::proteinG),
            sum(itemResponses, MealItemResponse::carbohydratesG),
            sum(itemResponses, MealItemResponse::fatG)
        );
    }

    private static MealItemResponse toItemResponse(MealItem item, Food food)
    {
        return new MealItemResponse(
            item.getMealItemId(),
            item.getMealId(),
            item.getFoodId(),
            food.getName(),
            item.getQuantity(),
            food.getServingSize(),
            food.getServingUnit(),
            NutritionMath.contribution(item.getQuantity(), food.getServingSize(), food.getCalories()),
            NutritionMath.contribution(item.getQuantity(), food.getServingSize(), food.getProteinG()),
            NutritionMath.contribution(item.getQuantity(), food.getServingSize(), food.getCarbohydratesG()),
            NutritionMath.contribution(item.getQuantity(), food.getServingSize(), food.getFatG())
        );
    }

    private static BigDecimal sum(List<MealItemResponse> items,
                                  Function<MealItemResponse, BigDecimal> getter)
    {
        return items.stream().map(getter).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
```

> Detalle de `update`: el borrado masivo de líneas con `deleteByMealId` es un
> `@Modifying` inmediato, así que al insertar las líneas nuevas `uq_meal_item_food`
> (UNIQUE `meal_id, food_id`) nunca ve una fila duplicada pendiente en el contexto de
> persistencia. La repetición del mismo alimento en un `PUT` se bloquea antes con
> `assertDistinctFoods`.

### 5.5 `NutritionGoalService.java`

```java
package com.fitness.app.nutrition.service;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.config.GymProperties;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.iam.model.UserRole;
import com.fitness.app.nutrition.dto.NutritionGoalRequest;
import com.fitness.app.nutrition.dto.NutritionGoalResponse;
import com.fitness.app.nutrition.model.GoalDefinedBy;
import com.fitness.app.nutrition.model.NutritionGoal;
import com.fitness.app.nutrition.repository.NutritionGoalRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional
public class NutritionGoalService
{
    private final NutritionGoalRepository nutritionGoalRepository;
    private final NutritionGuard          guard;
    private final GymProperties           gymProperties;

    @Transactional(readOnly = true)
    public NutritionGoalResponse findCurrent(Long memberId, AuthenticatedUser principal)
    {
        var scopedMemberId = guard.scopedMemberId(memberId, principal);
        guard.requireActiveMembership(scopedMemberId);

        return nutritionGoalRepository.findByMemberIdAndEndDateIsNull(scopedMemberId)
                .map(NutritionGoalResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.NUTRITION_GOAL_NOT_FOUND));
    }

    /**
     * "El socio fija su meta calórica; el entrenador la ajusta para sus socios
     * asignados" (§3.8). A goal is never edited: the active stretch closes (end_date
     * = today) and a new one opens, which is also the change history (schema.sql
     * uq_goal_current). The tolerance falls back to
     * gym.nutrition.default-tolerance-percent when the caller leaves it blank.
     */
    public NutritionGoalResponse upsert(Long memberId, NutritionGoalRequest request, AuthenticatedUser principal)
    {
        var scopedMemberId = guard.scopedMemberId(memberId, principal);
        guard.requireActiveMembership(scopedMemberId);

        nutritionGoalRepository.findByMemberIdAndEndDateIsNull(scopedMemberId)
                .ifPresent(goal -> goal.setEndDate(LocalDate.now()));

        var definedBy = principal.role() == UserRole.TRAINER ? GoalDefinedBy.TRAINER : GoalDefinedBy.MEMBER;

        var goal = new NutritionGoal();
        goal.setMemberId(scopedMemberId);
        goal.setGoalType(request.goalType());
        goal.setDailyCalories(request.dailyCalories());
        goal.setTolerancePercent(request.tolerancePercent() == null
                ? new BigDecimal(gymProperties.nutrition().defaultTolerancePercent())
                : request.tolerancePercent());
        goal.setTargetWeightKg(request.targetWeightKg());
        goal.setDefinedBy(definedBy);
        goal.setDefinedByUserId(principal.appUserId());
        goal.setStartDate(LocalDate.now());

        return NutritionGoalResponse.from(nutritionGoalRepository.save(goal));
    }
}
```

> No hace falta `flush()` entre cerrar la meta vieja y abrir la nueva: `uq_goal_current`
> es UNIQUE sobre `(member_id, end_date)` y en PostgreSQL los NULL son distintos entre
> sí, así que el cierre (end_date = hoy) y la nueva fila (end_date = NULL) jamás
> chocan, sin importar el orden del flush.

### 5.6 `NutritionSummaryService.java`

```java
package com.fitness.app.nutrition.service;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.nutrition.dto.DailyTotals;
import com.fitness.app.nutrition.dto.MealTimeSummary;
import com.fitness.app.nutrition.dto.NutritionGoalResponse;
import com.fitness.app.nutrition.dto.NutritionSummaryResponse;
import com.fitness.app.nutrition.model.CalorieStatus;
import com.fitness.app.nutrition.model.Meal;
import com.fitness.app.nutrition.model.MealItem;
import com.fitness.app.nutrition.model.MealType;
import com.fitness.app.nutrition.model.NutritionGoal;
import com.fitness.app.nutrition.repository.MealItemRepository;
import com.fitness.app.nutrition.repository.MealRepository;
import com.fitness.app.nutrition.repository.NutritionGoalRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NutritionSummaryService
{
    private final MealRepository          mealRepository;
    private final MealItemRepository      mealItemRepository;
    private final FoodService             foodService;
    private final NutritionGoalRepository nutritionGoalRepository;
    private final NutritionGuard          guard;

    public NutritionSummaryResponse daily(Long memberId, LocalDate date, AuthenticatedUser principal)
    {
        var scopedMemberId = guard.scopedMemberId(memberId, principal);
        guard.requireActiveMembership(scopedMemberId);

        var meals = mealRepository.findByMemberIdAndLogDateOrderByMealId(scopedMemberId, date);

        return build(scopedMemberId, date, meals);
    }

    /** "Tendencia de 7 o 30 días": one summary per day, oldest first. */
    public List<NutritionSummaryResponse> trend(Long memberId, LocalDate from, LocalDate to,
                                                AuthenticatedUser principal)
    {
        var scopedMemberId = guard.scopedMemberId(memberId, principal);
        guard.requireActiveMembership(scopedMemberId);

        if (from.isAfter(to))
        {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "La fecha inicial no puede ser posterior a la final.");
        }

        return mealRepository.findByMemberIdAndLogDateBetweenOrderByLogDateAscMealIdAsc(
                        scopedMemberId, from, to)
                .stream()
                .collect(Collectors.groupingBy(Meal::getLogDate, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> build(scopedMemberId, entry.getKey(), entry.getValue()))
                .toList();
    }

    private NutritionSummaryResponse build(Long memberId, LocalDate date, List<Meal> meals)
    {
        var byMeal = mealContributions(meals);
        var totals = totals(byMeal);
        var byMealTime = byMealTime(meals, byMeal);

        var goal = nutritionGoalRepository.findByMemberIdAndEndDateIsNull(memberId).orElse(null);
        var goalResponse = goal == null ? null : NutritionGoalResponse.from(goal);
        var evaluation = evaluate(totals.calories(), goal);

        return new NutritionSummaryResponse(
            memberId,
            date,
            totals,
            byMealTime,
            goalResponse,
            evaluation.status(),
            evaluation.difference(),
            evaluation.percentOfGoal()
        );
    }

    /** One Contribution per meal: the sum of its lines, computed on the fly. */
    private Map<Long, Contribution> mealContributions(List<Meal> meals)
    {
        if (meals.isEmpty())
        {
            return Map.of();
        }

        var mealIds = meals.stream().map(Meal::getMealId).toList();
        var items = mealItemRepository.findByMealIdIn(mealIds);

        if (items.isEmpty())
        {
            return Map.of();
        }

        var foods = foodService.findAllById(
                items.stream().map(MealItem::getFoodId).distinct().toList());

        return items.stream()
                .collect(Collectors.groupingBy(MealItem::getMealId))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .map(item -> contribution(item, foods.get(item.getFoodId())))
                                .reduce(Contribution.ZERO, Contribution::add)));
    }

    private static DailyTotals totals(Map<Long, Contribution> byMeal)
    {
        var total = byMeal.values().stream()
                .reduce(Contribution.ZERO, Contribution::add);

        return new DailyTotals(total.calories(), total.proteinG(), total.carbohydratesG(), total.fatG());
    }

    private static List<MealTimeSummary> byMealTime(List<Meal> meals, Map<Long, Contribution> byMeal)
    {
        var mealsByType = meals.stream().collect(Collectors.groupingBy(Meal::getMealType));

        return Arrays.stream(MealType.values())
                .map(type ->
                {
                    var typeMeals = mealsByType.getOrDefault(type, List.of());

                    if (typeMeals.isEmpty())
                    {
                        return null;
                    }

                    var total = typeMeals.stream()
                            .map(meal -> byMeal.getOrDefault(meal.getMealId(), Contribution.ZERO))
                            .reduce(Contribution.ZERO, Contribution::add);

                    return new MealTimeSummary(type, typeMeals.size(),
                            total.calories(), total.proteinG(), total.carbohydratesG(), total.fatG());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * "Clasifica el total del día en UNDER, ACCEPTABLE (dentro de ±tolerance) u OVER"
     * (§3.8 #5). Without a goal there is nothing to compare against.
     */
    private static Evaluation evaluate(BigDecimal consumed, NutritionGoal goal)
    {
        if (goal == null)
        {
            return Evaluation.NONE;
        }

        var target = goal.getDailyCalories();
        var tolerance = goal.getTolerancePercent().multiply(target)
                .divide(NutritionMath.HUNDRED, 2, RoundingMode.HALF_UP);
        var lower = target.subtract(tolerance);
        var upper = target.add(tolerance);

        var status = consumed.compareTo(lower) < 0 ? CalorieStatus.UNDER
                   : consumed.compareTo(upper) > 0 ? CalorieStatus.OVER
                   : CalorieStatus.ACCEPTABLE;

        var percent = consumed.multiply(NutritionMath.HUNDRED)
                .divide(target, 2, RoundingMode.HALF_UP);

        return new Evaluation(status, consumed.subtract(target), percent);
    }

    private static Contribution contribution(MealItem item, Food food)
    {
        return new Contribution(
                NutritionMath.contribution(item.getQuantity(), food.getServingSize(), food.getCalories()),
                NutritionMath.contribution(item.getQuantity(), food.getServingSize(), food.getProteinG()),
                NutritionMath.contribution(item.getQuantity(), food.getServingSize(), food.getCarbohydratesG()),
                NutritionMath.contribution(item.getQuantity(), food.getServingSize(), food.getFatG()));
    }

    private record Contribution(BigDecimal calories,
                                BigDecimal proteinG,
                                BigDecimal carbohydratesG,
                                BigDecimal fatG)
    {
        private static final Contribution ZERO =
                new Contribution(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        private Contribution add(Contribution other)
        {
            return new Contribution(calories.add(other.calories),
                                    proteinG.add(other.proteinG),
                                    carbohydratesG.add(other.carbohydratesG),
                                    fatG.add(other.fatG));
        }
    }

    private record Evaluation(CalorieStatus status, BigDecimal difference, BigDecimal percentOfGoal)
    {
        private static final Evaluation NONE =
                new Evaluation(null, null, null);
    }
}
```

---

## 6. Controladores REST (`com.fitness.app.nutrition.controller`)

Tres controladores. Patrón idéntico al resto del proyecto: `@RestController`,
`@RequestMapping("/api/v1/...")`, `@RequiredArgsConstructor`, y el `principal` se
inyecta con `@AuthenticationPrincipal AuthenticatedUser`.

### 6.1 `FoodController.java`

```java
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
 * Catalog of §3.8 (the food the member picks when logging a meal). ADMIN edits;
 * ADMIN, TRAINER and MEMBER read. DELETE is a logical deletion: the row stays for
 * the history of meals that already reference it (FOOD_IN_USE would fire on a hard
 * delete, which is exactly why the module deactivates instead).
 */
@RestController
@RequestMapping("/api/v1/foods")
@RequiredArgsConstructor
public class FoodController
{
    private final FoodService foodService;

    @GetMapping
    public PagedModel<FoodResponse> search(@RequestParam(required = false) FoodCategory category,
                                           @RequestParam(required = false) String search,
                                           @RequestParam(required = false) Boolean active,
                                           Pageable pageable)
    {
        return new PagedModel<>(foodService.search(category, search, active, pageable));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FoodResponse create(@Valid @RequestBody FoodRequest request)
    {
        return foodService.create(request);
    }

    @GetMapping("/{foodId}")
    public FoodResponse findById(@PathVariable Long foodId)
    {
        return foodService.findById(foodId);
    }

    @PutMapping("/{foodId}")
    public FoodResponse update(@PathVariable Long foodId, @Valid @RequestBody FoodRequest request)
    {
        return foodService.update(foodId, request);
    }

    @DeleteMapping("/{foodId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long foodId)
    {
        foodService.deactivate(foodId);
    }
}
```

### 6.2 `MealController.java`

```java
package com.fitness.app.nutrition.controller;

import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.nutrition.dto.MealRequest;
import com.fitness.app.nutrition.dto.MealResponse;
import com.fitness.app.nutrition.dto.MealUpdateRequest;
import com.fitness.app.nutrition.service.MealService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

import java.time.LocalDate;

/**
 * The daily meals of §3.8. The member registers, edits and deletes only its own
 * meals and only on the same day (MEAL_EDIT_WINDOW_CLOSED); the trainer reaches the
 * meals of its assigned members.
 */
@RestController
@RequestMapping("/api/v1/meals")
@RequiredArgsConstructor
public class MealController
{
    private final MealService mealService;

    @GetMapping
    public PagedModel<MealResponse> search(
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal AuthenticatedUser principal,
            Pageable pageable)
    {
        return new PagedModel<>(mealService.search(memberId, date, from, to, principal, pageable));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MealResponse create(@Valid @RequestBody MealRequest request,
                               @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return mealService.create(request, principal);
    }

    @GetMapping("/{mealId}")
    public MealResponse findById(@PathVariable Long mealId,
                                 @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return mealService.findById(mealId, principal);
    }

    @PutMapping("/{mealId}")
    public MealResponse update(@PathVariable Long mealId,
                               @Valid @RequestBody MealUpdateRequest request,
                               @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return mealService.update(mealId, request, principal);
    }

    @DeleteMapping("/{mealId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long mealId,
                       @AuthenticationPrincipal AuthenticatedUser principal)
    {
        mealService.delete(mealId, principal);
    }
}
```

### 6.3 `MemberNutritionController.java`

```java
package com.fitness.app.nutrition.controller;

import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.nutrition.dto.NutritionGoalRequest;
import com.fitness.app.nutrition.dto.NutritionGoalResponse;
import com.fitness.app.nutrition.dto.NutritionSummaryResponse;
import com.fitness.app.nutrition.service.NutritionGoalService;
import com.fitness.app.nutrition.service.NutritionSummaryService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * The three member-scoped routes of §3.8. Roles: TRAINER and MEMBER; the row-level
 * scoping (a trainer only its assigned members) is enforced in the services via
 * NutritionGuard.
 */
@RestController
@RequestMapping("/api/v1/members/{memberId}")
@RequiredArgsConstructor
public class MemberNutritionController
{
    private static final LocalDate NO_LOWER_BOUND = LocalDate.of(1, 1, 1);
    private static final LocalDate NO_UPPER_BOUND = LocalDate.of(9999, 12, 31);

    private final NutritionSummaryService nutritionSummaryService;
    private final NutritionGoalService    nutritionGoalService;

    /**
     * Filters: date (one day) or from/to (the 7/30-day trend). The two shapes share
     * the route, so the return type is Object: NutritionSummaryResponse for a day,
     * a List of them for a range.
     */
    @GetMapping("/nutrition-summary")
    public Object summary(@PathVariable Long memberId,
                          @RequestParam(required = false) LocalDate date,
                          @RequestParam(required = false) LocalDate from,
                          @RequestParam(required = false) LocalDate to,
                          @AuthenticationPrincipal AuthenticatedUser principal)
    {
        if (from != null || to != null)
        {
            return nutritionSummaryService.trend(memberId,
                    from == null ? NO_LOWER_BOUND : from,
                    to == null ? NO_UPPER_BOUND : to,
                    principal);
        }

        return nutritionSummaryService.daily(memberId, date == null ? LocalDate.now() : date, principal);
    }

    @GetMapping("/nutrition-goal")
    public NutritionGoalResponse currentGoal(@PathVariable Long memberId,
                                             @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return nutritionGoalService.findCurrent(memberId, principal);
    }

    /** "El socio fija su meta; el entrenador la ajusta para sus asignados" (§3.8). */
    @PutMapping("/nutrition-goal")
    public NutritionGoalResponse upsertGoal(@PathVariable Long memberId,
                                            @Valid @RequestBody NutritionGoalRequest request,
                                            @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return nutritionGoalService.upsert(memberId, request, principal);
    }
}
```

---

## 7. Pruebas HTTP (`nutrition.http`)

Un bloque completo de REST Client para probar el módulo de punta a punta, con el
mismo flujo de 2FA que `rutines.http` (login → `/auth/verify` → `/auth/token`). La
sección C crea tres alimentos nuevos (los códigos del catálogo sembrado en
`data.sql` son `CHICKEN_BREAST`, `WHITE_RICE`, `AVOCADO`, ...; estos usan códigos
propios para no chocar).

```http
# =============================================================================
# nutrition.http — Módulo 8 · alimentación (REST Client)
#
# Contraste: instructiones_oc/03-API-REST.md (§3.8 nutrition),
#            instructiones_oc/02-Modulos.md (módulo 8).
#
# Precondiciones:
#   1. docker compose up -d --wait
#   2. Login de admin (data.sql: admin / Admin123*, 2FA desactivado).
#   3. El socio y el entrenador se crean en la sección B (2FA por correo:
#      docker logs fitness_backend | grep -i verification).
# =============================================================================

@baseUrl = http://localhost:8080/api/v1

@admin_username = admin
@admin_password = Admin123*

@member_username = mariana.castillo
@member_password = Socio123*
@member_code = 000000

@trainer_username = luis.ramos.nutri
@trainer_password = Trainer123*
@trainer_code = 000000

# =============================================================================
# A. AUTENTICACIÓN
# =============================================================================

### A.1 LOGIN ADMIN (sin 2FA)
# @name loginAdmin
POST {{baseUrl}}/auth/login
Content-Type: application/json

{
  "username": "{{admin_username}}",
  "password": "{{admin_password}}"
}

### A.2 LOGIN SOCIO (2FA: pegar el código del log en @member_code)
# @name loginMemberStep1
POST {{baseUrl}}/auth/login
Content-Type: application/json

{
  "username": "{{member_username}}",
  "password": "{{member_password}}"
}

### A.3 VERIFICAR 2FA SOCIO
# @name verifyMember
POST {{baseUrl}}/auth/verify
Content-Type: application/json

{
  "challenge_id": "{{loginMemberStep1.response.body.challenge_id}}",
  "code": "{{member_code}}"
}

### A.4 TOKEN SOCIO
# @name loginMember
POST {{baseUrl}}/auth/token
Content-Type: application/json

{
  "challenge_id": "{{loginMemberStep1.response.body.challenge_id}}",
  "code": "{{member_code}}"
}

### A.5 LOGIN ENTRENADOR (2FA: pegar el código en @trainer_code)
# @name loginTrainerStep1
POST {{baseUrl}}/auth/login
Content-Type: application/json

{
  "username": "{{trainer_username}}",
  "password": "{{trainer_password}}"
}

### A.6 VERIFICAR 2FA ENTRENADOR
# @name verifyTrainer
POST {{baseUrl}}/auth/verify
Content-Type: application/json

{
  "challenge_id": "{{loginTrainerStep1.response.body.challenge_id}}",
  "code": "{{trainer_code}}"
}

### A.7 TOKEN ENTRENADOR
# @name loginTrainer
POST {{baseUrl}}/auth/token
Content-Type: application/json

{
  "challenge_id": "{{loginTrainerStep1.response.body.challenge_id}}",
  "code": "{{trainer_code}}"
}

# =============================================================================
# B. PREPARACIÓN: socio, entrenador, membresía ÉLITE y asignación
# =============================================================================

### B.1 CREAR SOCIO
# @name createMember
POST {{baseUrl}}/members
Authorization: Bearer {{loginAdmin.response.body.access_token}}
Content-Type: application/json

{
  "document_type": "DPI",
  "document_number": "3123456789",
  "first_name": "Mariana",
  "last_name": "Castillo",
  "email": "mariana.castillo@fitnessapp.local",
  "phone": "55550303",
  "username": "mariana.castillo",
  "password": "Socio123*",
  "joined_on": "2026-08-01"
}

### B.2 CREAR ENTRENADOR (empleado con perfil de trainer)
# @name createTrainerEmployee
POST {{baseUrl}}/employees/trainers
Authorization: Bearer {{loginAdmin.response.body.access_token}}
Content-Type: application/json

{
  "document_type": "DPI",
  "document_number": "3234567890",
  "first_name": "Luis",
  "last_name": "Ramos",
  "email": "luis.ramos.nutri@fitnessapp.local",
  "phone": "55550404",
  "username": "luis.ramos.nutri",
  "password": "Trainer123*",
  "role": "TRAINER",
  "trainer_profile": {
    "max_member_load": 10,
    "bio": "Nutrición deportiva",
    "specialties": ["STRENGTH"]
  }
}

### B.3 CONTRATAR PLAN ÉLITE AL SOCIO (plan 1 = ÉLITE)
# @name createMembership
POST {{baseUrl}}/memberships
Authorization: Bearer {{loginAdmin.response.body.access_token}}
Content-Type: application/json

{
  "member_id": {{createMember.response.body.member_id}},
  "membership_plan_id": 1,
  "start_date": "2026-01-01",
  "paid_price": 499.99
}

### B.4 ASIGNAR ENTRENADOR AL SOCIO
# @name assignTrainer
POST {{baseUrl}}/trainer-assignments
Authorization: Bearer {{loginAdmin.response.body.access_token}}
Content-Type: application/json

{
  "member_id": {{createMember.response.body.member_id}},
  "trainer_id": {{createTrainerEmployee.response.body.trainer_id}}
}

# =============================================================================
# C. CATÁLOGO DE ALIMENTOS (CRUD de admin, §3.8)
# =============================================================================

### C.1 CREAR ALIMENTO — pechuga (100 g = 1 porción)
# @name createFoodPechuga
POST {{baseUrl}}/foods
Authorization: Bearer {{loginAdmin.response.body.access_token}}
Content-Type: application/json

{
  "code": "PECHUGA_100",
  "name": "Pechuga de pollo a la plancha",
  "category": "PROTEIN",
  "serving_size": 100.00,
  "serving_unit": "GRAM",
  "calories": 165.00,
  "protein_g": 31.00,
  "carbohydrates_g": 0.00,
  "fat_g": 3.60
}

### C.2 CREAR ALIMENTO — arroz
# @name createFoodArroz
POST {{baseUrl}}/foods
Authorization: Bearer {{loginAdmin.response.body.access_token}}
Content-Type: application/json

{
  "code": "ARROZ_100",
  "name": "Arroz blanco cocido",
  "category": "CARBOHYDRATE",
  "serving_size": 100.00,
  "serving_unit": "GRAM",
  "calories": 130.00,
  "protein_g": 2.70,
  "carbohydrates_g": 28.20,
  "fat_g": 0.30
}

### C.3 CREAR ALIMENTO — aguacate
# @name createFoodAguacate
POST {{baseUrl}}/foods
Authorization: Bearer {{loginAdmin.response.body.access_token}}
Content-Type: application/json

{
  "code": "AGUACATE_100",
  "name": "Aguacate",
  "category": "FAT",
  "serving_size": 100.00,
  "serving_unit": "GRAM",
  "calories": 160.00,
  "protein_g": 2.00,
  "carbohydrates_g": 8.50,
  "fat_g": 14.70
}

### C.4 LISTAR CATÁLOGO con filtros (search + active)
GET {{baseUrl}}/foods?search=pollo&active=true
Authorization: Bearer {{loginMember.response.body.access_token}}

### C.5 DETALLE
GET {{baseUrl}}/foods/{{createFoodPechuga.response.body.food_id}}
Authorization: Bearer {{loginTrainer.response.body.access_token}}

### C.6 EDITAR (solo admin)
PUT {{baseUrl}}/foods/{{createFoodPechuga.response.body.food_id}}
Authorization: Bearer {{loginAdmin.response.body.access_token}}
Content-Type: application/json

{
  "code": "PECHUGA_100",
  "name": "Pechuga de pollo a la plancha (actualizada)",
  "category": "PROTEIN",
  "serving_size": 100.00,
  "serving_unit": "GRAM",
  "calories": 165.00,
  "protein_g": 31.00,
  "carbohydrates_g": 0.00,
  "fat_g": 3.60
}

### C.7 DESACTIVAR (baja lógica; 204)
DELETE {{baseUrl}}/foods/{{createFoodAguacate.response.body.food_id}}
Authorization: Bearer {{loginAdmin.response.body.access_token}}

# =============================================================================
# D. COMIDAS DIARIAS (§3.8: registro, historial, edición y baja solo el mismo día)
# =============================================================================

### D.1 REGISTRAR DESAYUNO (socio; member_id opcional para S)
# @name createBreakfast
POST {{baseUrl}}/meals
Authorization: Bearer {{loginMember.response.body.access_token}}
Content-Type: application/json

{
  "member_id": {{createMember.response.body.member_id}},
  "log_date": "2026-08-11",
  "meal_type": "BREAKFAST",
  "notes": "Desayuno pre-entreno",
  "items": [
    { "food_id": {{createFoodArroz.response.body.food_id}}, "quantity": 150.00 },
    { "food_id": {{createFoodPechuga.response.body.food_id}}, "quantity": 100.00 }
  ]
}

### D.2 REGISTRAR ALMUERZO
# @name createLunch
POST {{baseUrl}}/meals
Authorization: Bearer {{loginMember.response.body.access_token}}
Content-Type: application/json

{
  "member_id": {{createMember.response.body.member_id}},
  "log_date": "2026-08-11",
  "meal_type": "LUNCH",
  "notes": "Comida post-entreno",
  "items": [
    { "food_id": {{createFoodPechuga.response.body.food_id}}, "quantity": 150.00 },
    { "food_id": {{createFoodArroz.response.body.food_id}}, "quantity": 200.00 }
  ]
}

### D.3 HISTORIAL del socio (filtro date)
GET {{baseUrl}}/meals?member_id={{createMember.response.body.member_id}}&date=2026-08-11
Authorization: Bearer {{loginMember.response.body.access_token}}

### D.4 DETALLE de una comida (entrenador asignado)
GET {{baseUrl}}/meals/{{createBreakfast.response.body.meal_id}}
Authorization: Bearer {{loginTrainer.response.body.access_token}}

### D.5 EDITAR la comida del día (¡log_date = HOY para que el PUT aplique!)
PUT {{baseUrl}}/meals/{{createBreakfast.response.body.meal_id}}
Authorization: Bearer {{loginMember.response.body.access_token}}
Content-Type: application/json

{
  "meal_type": "BREAKFAST",
  "notes": "Desayuno pre-entreno (ajustado)",
  "items": [
    { "food_id": {{createFoodArroz.response.body.food_id}}, "quantity": 180.00 },
    { "food_id": {{createFoodPechuga.response.body.food_id}}, "quantity": 120.00 }
  ]
}

### D.6 ELIMINAR la comida del día (¡log_date = HOY para que el DELETE aplique!)
# @name deleteLunch
DELETE {{baseUrl}}/meals/{{createLunch.response.body.meal_id}}
Authorization: Bearer {{loginMember.response.body.access_token}}

# =============================================================================
# E. META CALÓRICA (§3.8: el socio la fija; el entrenador la ajusta)
# =============================================================================

### E.1 SOCIO FIJA SU META (WEIGHT_LOSS, 2000 kcal ±10%)
# @name upsertGoalByMember
PUT {{baseUrl}}/members/{{createMember.response.body.member_id}}/nutrition-goal
Authorization: Bearer {{loginMember.response.body.access_token}}
Content-Type: application/json

{
  "goal_type": "WEIGHT_LOSS",
  "daily_calories": 2000.00,
  "target_weight_kg": 60.00
}

### E.2 CONSULTAR META VIGENTE (entrenador asignado)
GET {{baseUrl}}/members/{{createMember.response.body.member_id}}/nutrition-goal
Authorization: Bearer {{loginTrainer.response.body.access_token}}

### E.3 ENTRENADOR AJUSTA LA META (MUSCLE_GAIN, cierra la anterior)
# @name upsertGoalByTrainer
PUT {{baseUrl}}/members/{{createMember.response.body.member_id}}/nutrition-goal
Authorization: Bearer {{loginTrainer.response.body.access_token}}
Content-Type: application/json

{
  "goal_type": "MUSCLE_GAIN",
  "daily_calories": 2500.00,
  "tolerance_percent": 10,
  "target_weight_kg": 65.00
}

### E.4 CONSULTAR META VIGENTE (debe devolver la MUSCLE_GAIN de E.3)
GET {{baseUrl}}/members/{{createMember.response.body.member_id}}/nutrition-goal
Authorization: Bearer {{loginMember.response.body.access_token}}

# =============================================================================
# F. RESUMEN DIARIO Y TENDENCIA (§3.8)
# =============================================================================

### F.1 RESUMEN DIARIO (socio, su propio archivo)
GET {{baseUrl}}/members/{{createMember.response.body.member_id}}/nutrition-summary?date=2026-08-11
Authorization: Bearer {{loginMember.response.body.access_token}}

### F.2 RESUMEN DIARIO (entrenador asignado)
GET {{baseUrl}}/members/{{createMember.response.body.member_id}}/nutrition-summary?date=2026-08-11
Authorization: Bearer {{loginTrainer.response.body.access_token}}

### F.3 TENDENCIA 7 DÍAS (rango from/to → lista de resúmenes)
GET {{baseUrl}}/members/{{createMember.response.body.member_id}}/nutrition-summary?from=2026-08-05&to=2026-08-11
Authorization: Bearer {{loginTrainer.response.body.access_token}}

# =============================================================================
# G. CASOS NEGATIVOS (las reglas de negocio y de alcance en acción)
# =============================================================================

### G.1 SOCIO registra comida para OTRO socio → 403 FORBIDDEN_RESOURCE
POST {{baseUrl}}/meals
Authorization: Bearer {{loginMember.response.body.access_token}}
Content-Type: application/json

{
  "member_id": 1,
  "log_date": "2026-08-11",
  "meal_type": "SNACK",
  "items": [
    { "food_id": {{createFoodPechuga.response.body.food_id}}, "quantity": 50.00 }
  ]
}

### G.2 ENTRENADOR consulta la comida de un socio NO asignado → 403
GET {{baseUrl}}/meals/1
Authorization: Bearer {{loginTrainer.response.body.access_token}}

### G.3 COMIDA con alimento desactivado → 400 VALIDATION_ERROR (aguacate desactivado en C.7)
POST {{baseUrl}}/meals
Authorization: Bearer {{loginMember.response.body.access_token}}
Content-Type: application/json

{
  "member_id": {{createMember.response.body.member_id}},
  "log_date": "2026-08-11",
  "meal_type": "SNACK",
  "items": [
    { "food_id": {{createFoodAguacate.response.body.food_id}}, "quantity": 50.00 }
  ]
}

### G.4 COMIDA con el mismo alimento repetido → 400 VALIDATION_ERROR
POST {{baseUrl}}/meals
Authorization: Bearer {{loginMember.response.body.access_token}}
Content-Type: application/json

{
  "member_id": {{createMember.response.body.member_id}},
  "log_date": "2026-08-11",
  "meal_type": "SNACK",
  "items": [
    { "food_id": {{createFoodArroz.response.body.food_id}}, "quantity": 100.00 },
    { "food_id": {{createFoodArroz.response.body.food_id}}, "quantity": 100.00 }
  ]
}

### G.5 EDICIÓN fuera del mismo día → 409 MEAL_EDIT_WINDOW_CLOSED
# (corre contra una comida con log_date anterior al día de hoy)
PUT {{baseUrl}}/meals/{{createBreakfast.response.body.meal_id}}
Authorization: Bearer {{loginMember.response.body.access_token}}
Content-Type: application/json

{
  "meal_type": "DINNER",
  "notes": "Intento de cambiar una comida de otro día",
  "items": [
    { "food_id": {{createFoodPechuga.response.body.food_id}}, "quantity": 100.00 }
  ]
}

### G.6 SOCIO SIN META VIGENTE → 404 NUTRITION_GOAL_NOT_FOUND
GET {{baseUrl}}/members/1/nutrition-goal
Authorization: Bearer {{loginMember.response.body.access_token}}
```

> Nota de ejecución: D.5, D.6 y G.5 dependen de la regla "solo el mismo día", por lo
> que deben correrse con `log_date` = fecha de hoy. Las secciones F (resumen) son
> independientes de eso y se pueden probar siempre.

---

## 8. Orden sugerido de implementación

1. `GymProperties` (sección 0.2) — desbloquea la tolerancia configurable.
2. Enums y entidades (sección 2) — sin dependencias externas.
3. DTOs (sección 3) — sin dependencias.
4. Repositorios (sección 4).
5. `NutritionMath` y `NutritionGuard` (5.1-5.2) — la base de los servicios.
6. `FoodService` (5.3) — catálogo, no depende de los demás.
7. `MealService` (5.4) y `NutritionGoalService` (5.5).
8. `NutritionSummaryService` (5.6) — último, porque agrega todo lo anterior.
9. Controladores (sección 6) y matchers de `SecurityConfig` (sección 0.3).
10. Compilar (`./mvnw compile`) y probar con `nutrition.http` (sección 7).
