-- =============================================================================
-- Gym Management System - Practica 1, Analisis y Diseno de Sistemas 1
-- DML: seed data for the catalog tables and the bootstrap administrator.
--
-- Deployment: mounted read-only at /docker-entrypoint-initdb.d/02-data.sql, so
-- Postgres runs it right after 01-schema.sql when the volume is created.
--
-- Every statement is idempotent (ON CONFLICT DO NOTHING) so the file can also be
-- replayed by hand against an existing database without failing.
--
-- Only five tables carry seed data, because only five catalogs are maintained
-- by the administrator at run time. Every other closed value set in this system
-- is a VARCHAR + CHECK enum and therefore lives in the Java code, not here.
-- =============================================================================


-- =============================================================================
-- 1. Bootstrap administrator
-- The system needs one account able to create every other account.
-- =============================================================================

INSERT INTO person (document_type, document_number, first_name, last_name, email, phone)
VALUES ('DPI', '1000000000101', 'System', 'Administrator', 'admin@fitnessapp.local', '00000000')
ON CONFLICT (document_type, document_number) DO NOTHING;

-- !! REPLACE THE HASH BEFORE THE FIRST LOGIN !!
-- The value below is a placeholder, not a valid BCrypt digest: the account
-- cannot be used until the team generates a real one. Produce it with:
--     mvn -q compile exec:java -Dexec.mainClass=... , or simply run once
--     new BCryptPasswordEncoder().encode("your-password")
-- and then:
--     UPDATE app_user SET password_hash = '<hash>', status = 'ACTIVE'
--      WHERE username = 'admin';
INSERT INTO app_user (person_id, username, password_hash, role, status, two_factor_enabled)
VALUES ((SELECT person_id FROM person WHERE document_type = 'DPI' AND document_number = '1000000000101'),
        'admin', 'REPLACE_WITH_BCRYPT_HASH', 'ADMIN', 'PENDING_ACTIVATION', TRUE)
ON CONFLICT (username) DO NOTHING;

INSERT INTO employee (person_id, employee_code, position, hired_on, status)
VALUES ((SELECT person_id FROM person WHERE document_type = 'DPI' AND document_number = '1000000000101'),
        'EMP-0001', 'ADMIN', CURRENT_DATE, 'ACTIVE')
ON CONFLICT (employee_code) DO NOTHING;


-- =============================================================================
-- 2. membership_plan - the three levels described in the statement
--
-- tier drives two things at once: which change of plan is an upgrade and which
-- is a downgrade, and who gets promoted first from a waitlist (Elite over
-- Premium). Prices are in GTQ.
-- =============================================================================

INSERT INTO membership_plan (code, name, description, billing_period, price, tier,
                             includes_group_classes, weekly_class_limit, includes_personal_trainer)
VALUES
    ('BASIC',   'Plan Basico',  'Acceso a instalaciones y equipo en horario general, registro de asistencia y modulo de nutricion.',
                                'MONTHLY', 250.00, 1, FALSE, NULL, FALSE),

    ('PREMIUM', 'Plan Premium', 'Todo lo del Plan Basico, mas inscripcion hasta 3 clases grupales por semana sujeto a cupo, con historial y calificacion de clases.',
                                'MONTHLY', 400.00, 2, TRUE,     3, FALSE),

    ('ELITE',   'Plan Elite',   'Todo lo del Plan Premium con clases ilimitadas sujetas a cupo, entrenador personal asignado y prioridad en lista de espera.',
                                'MONTHLY', 650.00, 3, TRUE,  NULL, TRUE)
ON CONFLICT (code) DO NOTHING;


-- =============================================================================
-- 3. exercise - starter catalog the trainer builds routines from
-- =============================================================================

INSERT INTO exercise (code, name, muscle_group, description)
VALUES
    -- Chest
    ('BENCH_PRESS',      'Barbell bench press',   'CHEST',     'Press the barbell from chest to full arm extension.'),
    ('PUSH_UP',          'Push-up',               'CHEST',     'Lower the chest to the floor keeping the body in a straight line.'),
    ('DUMBBELL_FLY',     'Dumbbell fly',          'CHEST',     'Open the arms in an arc with a slight elbow bend.'),

    -- Back
    ('PULL_UP',          'Pull-up',               'BACK',      'Pull the body up until the chin clears the bar.'),
    ('BARBELL_ROW',      'Bent-over barbell row', 'BACK',      'Row the bar to the lower chest with a flat back.'),
    ('LAT_PULLDOWN',     'Lat pulldown',          'BACK',      'Pull the bar down to the upper chest.'),
    ('DEADLIFT',         'Conventional deadlift', 'BACK',      'Lift the bar from the floor to the hips keeping a neutral spine.'),

    -- Legs
    ('BACK_SQUAT',       'Back squat',            'LEGS',      'Squat until the thighs are parallel to the floor.'),
    ('LEG_PRESS',        'Leg press',             'LEGS',      'Push the platform without locking the knees.'),
    ('WALKING_LUNGE',    'Walking lunge',         'LEGS',      'Step forward and lower the back knee towards the floor.'),
    ('ROMANIAN_DL',      'Romanian deadlift',     'LEGS',      'Hinge at the hips with soft knees to load the hamstrings.'),
    ('CALF_RAISE',       'Standing calf raise',   'LEGS',      'Rise onto the toes through the full range of motion.'),

    -- Shoulders
    ('OVERHEAD_PRESS',   'Overhead press',        'SHOULDERS', 'Press the bar overhead from shoulder height.'),
    ('LATERAL_RAISE',    'Lateral raise',         'SHOULDERS', 'Raise the arms to the sides up to shoulder height.'),

    -- Arms
    ('BICEPS_CURL',      'Biceps curl',           'ARMS',      'Curl the weight keeping the elbows fixed at the sides.'),
    ('TRICEPS_PUSHDOWN', 'Triceps pushdown',      'ARMS',      'Extend the elbows pushing the bar down.'),

    -- Core
    ('PLANK',            'Front plank',           'CORE',      'Hold the body aligned on forearms and toes.'),
    ('CRUNCH',           'Abdominal crunch',      'CORE',      'Lift the shoulder blades off the floor contracting the abs.'),
    ('RUSSIAN_TWIST',    'Russian twist',         'CORE',      'Rotate the torso side to side while seated.'),

    -- Cardio and full body
    ('TREADMILL_RUN',    'Treadmill run',         'CARDIO',    'Continuous run at the pace set in the routine.'),
    ('STATIONARY_BIKE',  'Stationary bike',       'CARDIO',    'Steady pedalling at the prescribed resistance.'),
    ('JUMP_ROPE',        'Jump rope',             'CARDIO',    'Continuous skipping at a moderate pace.'),
    ('BURPEE',           'Burpee',                'FULL_BODY', 'Squat, kick back to a plank, push up and jump.')
ON CONFLICT (code) DO NOTHING;


-- =============================================================================
-- 4. food - master food catalog for the nutrition module
--
-- Every row is expressed per its own reference serving (100 g, 100 ml or 1
-- unit). A member who logs 150 g of a food defined per 100 g is charged 1.5x
-- these figures; that multiplication happens in the query, never in a stored
-- column.
-- =============================================================================

INSERT INTO food (code, name, category, serving_size, serving_unit, calories, protein_g, carbohydrates_g, fat_g)
VALUES
    -- Protein
    ('CHICKEN_BREAST', 'Chicken breast, cooked',    'PROTEIN',      100, 'GRAM',       165.00, 31.00,  0.00,  3.60),
    ('GROUND_BEEF',    'Lean ground beef, cooked',  'PROTEIN',      100, 'GRAM',       250.00, 26.00,  0.00, 15.00),
    ('SALMON',         'Salmon, cooked',            'PROTEIN',      100, 'GRAM',       208.00, 20.00,  0.00, 13.00),
    ('TUNA_WATER',     'Canned tuna in water',      'PROTEIN',      100, 'GRAM',       116.00, 26.00,  0.00,  0.80),
    ('EGG',            'Whole egg',                 'PROTEIN',        1, 'UNIT',        78.00,  6.30,  0.60,  5.30),
    ('WHEY_SCOOP',     'Whey protein, one scoop',   'PROTEIN',        1, 'UNIT',       120.00, 24.00,  3.00,  1.50),
    ('BLACK_BEANS',    'Black beans, cooked',       'PROTEIN',      100, 'GRAM',       132.00,  8.90, 23.70,  0.50),
    ('LENTILS',        'Lentils, cooked',           'PROTEIN',      100, 'GRAM',       116.00,  9.00, 20.00,  0.40),

    -- Carbohydrate
    ('WHITE_RICE',     'White rice, cooked',        'CARBOHYDRATE', 100, 'GRAM',       130.00,  2.70, 28.00,  0.30),
    ('PASTA',          'Pasta, cooked',             'CARBOHYDRATE', 100, 'GRAM',       131.00,  5.00, 25.00,  1.10),
    ('OATS',           'Rolled oats, dry',          'CARBOHYDRATE', 100, 'GRAM',       389.00, 16.90, 66.30,  6.90),
    ('WHEAT_BREAD',    'Whole wheat bread',         'CARBOHYDRATE', 100, 'GRAM',       247.00, 13.00, 41.00,  3.40),
    ('CORN_TORTILLA',  'Corn tortilla',             'CARBOHYDRATE',   1, 'UNIT',        63.00,  1.60, 13.00,  0.80),
    ('POTATO',         'Potato, boiled',            'CARBOHYDRATE', 100, 'GRAM',        87.00,  1.90, 20.10,  0.10),
    ('SWEET_POTATO',   'Sweet potato, baked',       'CARBOHYDRATE', 100, 'GRAM',        86.00,  1.60, 20.10,  0.10),

    -- Vegetables
    ('BROCCOLI',       'Broccoli, raw',             'VEGETABLE',    100, 'GRAM',        34.00,  2.80,  6.60,  0.40),
    ('SPINACH',        'Spinach, raw',              'VEGETABLE',    100, 'GRAM',        23.00,  2.90,  3.60,  0.40),
    ('TOMATO',         'Tomato, raw',               'VEGETABLE',    100, 'GRAM',        18.00,  0.90,  3.90,  0.20),
    ('CARROT',         'Carrot, raw',               'VEGETABLE',    100, 'GRAM',        41.00,  0.90,  9.60,  0.20),
    ('LETTUCE',        'Lettuce, raw',              'VEGETABLE',    100, 'GRAM',        15.00,  1.40,  2.90,  0.20),

    -- Fruit
    ('BANANA',         'Banana',                    'FRUIT',        100, 'GRAM',        89.00,  1.10, 22.80,  0.30),
    ('APPLE',          'Apple',                     'FRUIT',        100, 'GRAM',        52.00,  0.30, 13.80,  0.20),
    ('PAPAYA',         'Papaya',                    'FRUIT',        100, 'GRAM',        43.00,  0.50, 10.80,  0.30),
    ('ORANGE',         'Orange',                    'FRUIT',        100, 'GRAM',        47.00,  0.90, 11.80,  0.10),

    -- Dairy
    ('WHOLE_MILK',     'Whole milk',                'DAIRY',        100, 'MILLILITER',  61.00,  3.20,  4.80,  3.30),
    ('GREEK_YOGURT',   'Plain Greek yogurt',        'DAIRY',        100, 'GRAM',        59.00, 10.00,  3.60,  0.40),
    ('FRESH_CHEESE',   'Fresh cheese',              'DAIRY',        100, 'GRAM',       310.00, 18.00,  4.00, 24.00),

    -- Fat
    ('AVOCADO',        'Avocado',                   'FAT',          100, 'GRAM',       160.00,  2.00,  8.50, 14.70),
    ('ALMONDS',        'Almonds',                   'FAT',          100, 'GRAM',       579.00, 21.20, 21.60, 49.90),
    ('PEANUT_BUTTER',  'Peanut butter',             'FAT',          100, 'GRAM',       588.00, 25.10, 19.60, 50.40),
    ('OLIVE_OIL',      'Olive oil',                 'FAT',          100, 'MILLILITER', 884.00,  0.00,  0.00,100.00),

    -- Beverages
    ('WATER',          'Water',                     'BEVERAGE',     100, 'MILLILITER',   0.00,  0.00,  0.00,  0.00),
    ('BLACK_COFFEE',   'Black coffee, unsweetened', 'BEVERAGE',     100, 'MILLILITER',   2.00,  0.10,  0.00,  0.00),
    ('ORANGE_JUICE',   'Orange juice',              'BEVERAGE',     100, 'MILLILITER',  45.00,  0.70, 10.40,  0.20)
ON CONFLICT (code) DO NOTHING;


-- =============================================================================
-- 5. promotion - two examples authorized by the administrator
--
-- Seeded so the discount flow is demonstrable end to end. Both are tied to the
-- bootstrap administrator, the only role the statement allows to authorize them.
-- =============================================================================

INSERT INTO promotion (code, name, description, discount_type, discount_value,
                       valid_from, valid_to, max_uses, max_uses_per_member, authorized_by_user_id)
VALUES
    ('WELCOME10', 'Descuento de bienvenida',
                  'Diez por ciento sobre la primera membresia contratada.',
                  'PERCENTAGE',  10.00, CURRENT_DATE, CURRENT_DATE + 365, NULL, 1,
                  (SELECT app_user_id FROM app_user WHERE username = 'admin')),

    ('ANNUAL50',  'Promocion de renovacion anual',
                  'Cincuenta quetzales de descuento al renovar antes del vencimiento.',
                  'FIXED_AMOUNT', 50.00, CURRENT_DATE, CURRENT_DATE + 180, 200, 2,
                  (SELECT app_user_id FROM app_user WHERE username = 'admin'))
ON CONFLICT (code) DO NOTHING;
