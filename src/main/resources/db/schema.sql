-- =============================================================================
-- Gym Management System - Practica 1, Analisis y Diseno de Sistemas 1
-- DDL for PostgreSQL 17. Normalized up to Third Normal Form (3NF).
--
-- Deployment: this file is mounted read-only into the Postgres container at
-- /docker-entrypoint-initdb.d/01-schema.sql, so the engine runs it once when
-- the data volume is created. To rebuild the schema during development:
--     docker compose down -v && docker compose up -d
--
-- Conventions:
--   - Table names are singular, snake_case, English.
--   - Primary key is always <table>_id BIGSERIAL.
--   - Foreign key is always <referenced_table>_id, suffixed when the same
--     table is referenced in more than one role (created_by_user_id).
--   - Closed value sets are VARCHAR + CHECK, never a two-column catalog table.
--   - No derived column is ever stored: totals, counters, positions and
--     balances are computed by query.
--   - Logical deletion (active / status) on every entity that carries history.
-- =============================================================================


-- =============================================================================
-- MODULE: directory - people and their files
-- Declared first because iam.app_user depends on person.
-- =============================================================================

-- A person is anyone the gym holds identity data about: members, employees and
-- walk-in guests using a trial pass. Splitting it from member/employee is what
-- lets a guest exist without credentials and a trainer also be a member,
-- without duplicating name, document or phone.
CREATE TABLE person
(
    person_id       BIGSERIAL    PRIMARY KEY,
    document_type   VARCHAR(15)  NOT NULL,
    document_number VARCHAR(25)  NOT NULL,
    first_name      VARCHAR(50)  NOT NULL,
    last_name       VARCHAR(50)  NOT NULL,
    -- Free text and not a CHECK enum: it is not a closed set the code branches on,
    -- and forcing one would make the form reject values it has no reason to reject.
    gender          VARCHAR(20),
    birth_date      DATE,
    email           VARCHAR(120),
    phone           VARCHAR(20),
    address         VARCHAR(200),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_person_document    UNIQUE (document_type, document_number),
    CONSTRAINT ck_person_document_ty CHECK (document_type IN ('DPI', 'PASSPORT', 'NIT')),
    -- A static bound on purpose: CURRENT_DATE inside a CHECK is not immutable and
    -- would turn valid rows invalid on a dump/restore. "Not in the future" is
    -- validated by the service.
    CONSTRAINT ck_person_birth_date  CHECK (birth_date IS NULL OR birth_date > DATE '1900-01-01')
);

-- Two people must not share an email: it is the destination of the two-factor
-- code and of every expiry notice.
CREATE UNIQUE INDEX uq_person_email ON person (LOWER(email)) WHERE email IS NOT NULL;


-- =============================================================================
-- MODULE: iam - identity, access and security
-- =============================================================================

-- Credentials and role of a person who can sign in. The four roles are fixed by
-- the statement, so they are an enum: a role/permission/role_permission/user_role
-- RBAC would be four tables serving four constant values.
CREATE TABLE app_user
(
    app_user_id         BIGSERIAL    PRIMARY KEY,
    person_id           BIGINT       NOT NULL,
    username            VARCHAR(50)  NOT NULL,
    password_hash       VARCHAR(100) NOT NULL,
    role                VARCHAR(20)  NOT NULL,
    status              VARCHAR(25)  NOT NULL DEFAULT 'PENDING_ACTIVATION',
    two_factor_enabled  BOOLEAN      NOT NULL DEFAULT TRUE,
    two_factor_channel  VARCHAR(10)  NOT NULL DEFAULT 'EMAIL',
    failed_attempts     SMALLINT     NOT NULL DEFAULT 0,
    locked_until        TIMESTAMPTZ,
    last_login_at       TIMESTAMPTZ,
    password_changed_at TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_app_user_person   FOREIGN KEY (person_id) REFERENCES person (person_id) ON DELETE RESTRICT,
    CONSTRAINT uq_app_user_person   UNIQUE (person_id),
    CONSTRAINT uq_app_user_username UNIQUE (username),
    CONSTRAINT ck_app_user_role     CHECK (role IN ('ADMIN', 'RECEPTIONIST', 'TRAINER', 'MEMBER')),
    CONSTRAINT ck_app_user_status   CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLOCKED', 'PENDING_ACTIVATION')),
    CONSTRAINT ck_app_user_channel  CHECK (two_factor_channel IN ('EMAIL', 'SMS')),
    CONSTRAINT ck_app_user_attempts CHECK (failed_attempts >= 0)
);

-- Temporary codes for two-factor login and password recovery. The code itself
-- is stored hashed; masked_destination is a deliberate snapshot of where it was
-- sent, so a later change of email does not rewrite the audit trail.
--
-- SMS is kept because the statement asks for "correo electronico o numero de
-- telefono", but there is no SMS provider: an SMS code is written to the
-- application log instead of being delivered. Only EMAIL actually leaves the
-- system, through spring-boot-starter-mail.
CREATE TABLE verification_code
(
    verification_code_id BIGSERIAL    PRIMARY KEY,
    app_user_id          BIGINT       NOT NULL,
    code_type            VARCHAR(20)  NOT NULL,
    channel              VARCHAR(10)  NOT NULL,
    code_hash            VARCHAR(100) NOT NULL,
    masked_destination   VARCHAR(120) NOT NULL,
    status               VARCHAR(10)  NOT NULL DEFAULT 'ISSUED',
    attempt_count        SMALLINT     NOT NULL DEFAULT 0,
    issued_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at           TIMESTAMPTZ  NOT NULL,
    used_at              TIMESTAMPTZ,

    CONSTRAINT fk_verif_code_user    FOREIGN KEY (app_user_id) REFERENCES app_user (app_user_id) ON DELETE CASCADE,
    CONSTRAINT ck_verif_code_type    CHECK (code_type IN ('TWO_FACTOR', 'PASSWORD_RESET')),
    CONSTRAINT ck_verif_code_channel CHECK (channel IN ('EMAIL', 'SMS')),
    CONSTRAINT ck_verif_code_status  CHECK (status IN ('ISSUED', 'USED', 'EXPIRED')),
    CONSTRAINT ck_verif_code_expiry  CHECK (expires_at > issued_at),
    CONSTRAINT ck_verif_code_attempt CHECK (attempt_count >= 0)
);

CREATE INDEX ix_verif_code_user_status ON verification_code (app_user_id, code_type, status);


-- =============================================================================
-- MODULE: directory (continued) - member, employee and trainer files
-- =============================================================================

-- A member is the gym's customer file. It holds no plan, no expiry date and no
-- trainer: those belong to membership and trainer_assignment, which are the
-- entities that actually change over time.
CREATE TABLE member
(
    member_id               BIGSERIAL   PRIMARY KEY,
    person_id               BIGINT      NOT NULL,
    member_code             VARCHAR(20) NOT NULL,
    joined_on               DATE        NOT NULL DEFAULT CURRENT_DATE,
    status                  VARCHAR(15) NOT NULL DEFAULT 'ACTIVE',
    emergency_contact_name  VARCHAR(100),
    emergency_contact_phone VARCHAR(20),
    terminated_on           DATE,
    notes                   VARCHAR(300),

    CONSTRAINT fk_member_person  FOREIGN KEY (person_id) REFERENCES person (person_id) ON DELETE RESTRICT,
    CONSTRAINT uq_member_person  UNIQUE (person_id),
    CONSTRAINT uq_member_code    UNIQUE (member_code),
    CONSTRAINT ck_member_status  CHECK (status IN ('ACTIVE', 'INACTIVE', 'WITHDRAWN')),
    CONSTRAINT ck_member_term_on CHECK (terminated_on IS NULL OR terminated_on >= joined_on)
);

-- Staff file. position drives what the person does at the gym; the sign-in role
-- lives in app_user.role, because an employee may exist before having an account.
CREATE TABLE employee
(
    employee_id   BIGSERIAL   PRIMARY KEY,
    person_id     BIGINT      NOT NULL,
    employee_code VARCHAR(20) NOT NULL,
    position      VARCHAR(15) NOT NULL,
    hired_on      DATE        NOT NULL,
    terminated_on DATE,
    status        VARCHAR(15) NOT NULL DEFAULT 'ACTIVE',

    CONSTRAINT fk_employee_person  FOREIGN KEY (person_id) REFERENCES person (person_id) ON DELETE RESTRICT,
    CONSTRAINT uq_employee_person  UNIQUE (person_id),
    CONSTRAINT uq_employee_code    UNIQUE (employee_code),
    CONSTRAINT ck_employee_pos     CHECK (position IN ('ADMIN', 'RECEPTIONIST', 'TRAINER')),
    CONSTRAINT ck_employee_status  CHECK (status IN ('ACTIVE', 'SUSPENDED', 'TERMINATED')),
    CONSTRAINT ck_employee_term_on CHECK (terminated_on IS NULL OR terminated_on >= hired_on)
);

-- Specialization of employee. It exists as its own table (and not as a nullable
-- column on employee) because only trainers carry a member load cap, and every
-- training table points here rather than at employee.
CREATE TABLE trainer
(
    trainer_id      BIGSERIAL    PRIMARY KEY,
    employee_id     BIGINT       NOT NULL,
    max_member_load SMALLINT     NOT NULL DEFAULT 20,
    bio             VARCHAR(500),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_trainer_employee FOREIGN KEY (employee_id) REFERENCES employee (employee_id) ON DELETE RESTRICT,
    CONSTRAINT uq_trainer_employee UNIQUE (employee_id),
    CONSTRAINT ck_trainer_load     CHECK (max_member_load > 0)
);

-- A trainer may hold several specialties, so 1NF forces a child table. The
-- specialty itself is an enum: the statement fixes the list.
CREATE TABLE trainer_specialty
(
    trainer_id   BIGINT      NOT NULL,
    specialty    VARCHAR(20) NOT NULL,
    certified_on DATE,

    CONSTRAINT pk_trainer_specialty PRIMARY KEY (trainer_id, specialty),
    CONSTRAINT fk_trn_spec_trainer  FOREIGN KEY (trainer_id) REFERENCES trainer (trainer_id) ON DELETE CASCADE,
    CONSTRAINT ck_trn_spec_value    CHECK (specialty IN ('WEIGHT_LOSS', 'MUSCLE_GAIN', 'REHABILITATION',
                                                         'FUNCTIONAL', 'CARDIO'))
);


-- =============================================================================
-- MODULE: membership - plans, contracts and lifecycle
-- =============================================================================

-- Plan catalog maintained by the administrator. Benefits are columns and not a
-- benefit/plan_benefit pair because the statement fixes which benefits exist
-- (classes, personal trainer) and only lets the team add new *levels* - and a
-- new level is a row, which this design already supports.
--   tier                  : 1 = Basic, 2 = Premium, 3 = Elite. Drives upgrade vs
--                           downgrade AND waitlist priority, so it is stored once.
--   weekly_class_limit    : NULL means unlimited when classes are included.
CREATE TABLE membership_plan
(
    membership_plan_id        BIGSERIAL     PRIMARY KEY,
    code                      VARCHAR(20)   NOT NULL,
    name                      VARCHAR(60)   NOT NULL,
    description               VARCHAR(300),
    billing_period            VARCHAR(15)   NOT NULL,
    price                     NUMERIC(10, 2) NOT NULL,
    tier                      SMALLINT      NOT NULL,
    includes_group_classes    BOOLEAN       NOT NULL DEFAULT FALSE,
    weekly_class_limit        SMALLINT,
    includes_personal_trainer BOOLEAN       NOT NULL DEFAULT FALSE,
    active                    BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_plan_code     UNIQUE (code),
    CONSTRAINT uq_plan_tier     UNIQUE (tier),
    CONSTRAINT ck_plan_period   CHECK (billing_period IN ('MONTHLY', 'QUARTERLY', 'SEMIANNUAL', 'ANNUAL')),
    CONSTRAINT ck_plan_price    CHECK (price >= 0),
    CONSTRAINT ck_plan_tier     CHECK (tier > 0),
    CONSTRAINT ck_plan_limit    CHECK (weekly_class_limit IS NULL OR weekly_class_limit > 0),
    -- A plan without group classes cannot carry a weekly class limit.
    CONSTRAINT ck_plan_limit_ok CHECK (includes_group_classes OR weekly_class_limit IS NULL)
);

-- One row per contract. The renewal history is the set of rows of one member
-- ordered by start_date, so no self-referencing "previous contract" link is kept.
-- paid_price freezes what the member actually agreed to,
-- so the administrator can change membership_plan.price without rewriting the
-- value of contracts already signed - which is why no plan_price history table
-- is needed.
CREATE TABLE membership
(
    membership_id          BIGSERIAL      PRIMARY KEY,
    member_id              BIGINT         NOT NULL,
    membership_plan_id     BIGINT         NOT NULL,
    paid_price             NUMERIC(10, 2) NOT NULL,
    start_date             DATE           NOT NULL,
    end_date               DATE           NOT NULL,
    status                 VARCHAR(15)    NOT NULL DEFAULT 'ACTIVE',
    cancelled_on           DATE,
    cancellation_reason    VARCHAR(20),
    notes                  VARCHAR(300),
    created_by_user_id     BIGINT         NOT NULL,
    created_at             TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT fk_membership_member   FOREIGN KEY (member_id) REFERENCES member (member_id) ON DELETE RESTRICT,
    CONSTRAINT fk_membership_plan     FOREIGN KEY (membership_plan_id) REFERENCES membership_plan (membership_plan_id) ON DELETE RESTRICT,
    CONSTRAINT fk_membership_user     FOREIGN KEY (created_by_user_id) REFERENCES app_user (app_user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_membership_status   CHECK (status IN ('ACTIVE', 'FROZEN', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_membership_reason   CHECK (cancellation_reason IS NULL OR cancellation_reason IN
                                             ('COST', 'RELOCATION', 'DISSATISFACTION', 'INJURY', 'OTHER')),
    CONSTRAINT ck_membership_dates    CHECK (end_date > start_date),
    CONSTRAINT ck_membership_price    CHECK (paid_price >= 0),
    -- CANCELLED and cancelled_on must agree in both directions.
    CONSTRAINT ck_membership_cancel   CHECK ((status = 'CANCELLED') = (cancelled_on IS NOT NULL))
);

-- A member may accumulate a history of contracts, but only one of them can be
-- in force at any moment.
CREATE UNIQUE INDEX uq_membership_in_force ON membership (member_id) WHERE status IN ('ACTIVE', 'FROZEN');
CREATE INDEX ix_membership_end_date ON membership (end_date) WHERE status = 'ACTIVE';

-- Freeze periods. Real start and reactivation dates are what allow the expiry
-- date to be recomputed on reactivation; the number of frozen days is derived
-- from the two dates and therefore never stored.
-- The per-cycle limit (gym.freeze.* in application.yml) is enforced by the
-- service, because it is a tunable policy and not a structural invariant.
CREATE TABLE membership_freeze
(
    membership_freeze_id   BIGSERIAL   PRIMARY KEY,
    membership_id          BIGINT      NOT NULL,
    reason                 VARCHAR(15) NOT NULL,
    reason_detail          VARCHAR(300),
    requested_on           DATE        NOT NULL DEFAULT CURRENT_DATE,
    start_date             DATE        NOT NULL,
    expected_end_date      DATE,
    reactivated_on         DATE,
    authorized_by_user_id  BIGINT      NOT NULL,
    reactivated_by_user_id BIGINT,

    CONSTRAINT fk_freeze_membership FOREIGN KEY (membership_id) REFERENCES membership (membership_id) ON DELETE CASCADE,
    CONSTRAINT fk_freeze_auth_user  FOREIGN KEY (authorized_by_user_id) REFERENCES app_user (app_user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_freeze_react_user FOREIGN KEY (reactivated_by_user_id) REFERENCES app_user (app_user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_freeze_reason     CHECK (reason IN ('TRAVEL', 'INJURY', 'ILLNESS', 'WORK', 'OTHER')),
    CONSTRAINT ck_freeze_react_date CHECK (reactivated_on IS NULL OR reactivated_on >= start_date)
);

-- The freeze is in progress while reactivated_on is NULL, so there is no status
-- column: it would be derivable from that single date.
CREATE UNIQUE INDEX uq_freeze_in_progress ON membership_freeze (membership_id) WHERE reactivated_on IS NULL;


-- =============================================================================
-- MODULE: access - attendance and guest passes
-- =============================================================================

-- Check-in / check-out. membership_id records under which contract the member
-- walked in, which keeps the attendance report correct after a renewal.
CREATE TABLE facility_visit
(
    facility_visit_id      BIGSERIAL   PRIMARY KEY,
    member_id              BIGINT      NOT NULL,
    membership_id          BIGINT      NOT NULL,
    checked_in_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    checked_out_at         TIMESTAMPTZ,
    channel                VARCHAR(15) NOT NULL DEFAULT 'FRONT_DESK',
    checked_in_by_user_id  BIGINT      NOT NULL,
    checked_out_by_user_id BIGINT,

    CONSTRAINT fk_visit_member    FOREIGN KEY (member_id) REFERENCES member (member_id) ON DELETE RESTRICT,
    CONSTRAINT fk_visit_membershp FOREIGN KEY (membership_id) REFERENCES membership (membership_id) ON DELETE RESTRICT,
    CONSTRAINT fk_visit_in_user   FOREIGN KEY (checked_in_by_user_id) REFERENCES app_user (app_user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_visit_out_user  FOREIGN KEY (checked_out_by_user_id) REFERENCES app_user (app_user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_visit_channel   CHECK (channel IN ('FRONT_DESK', 'SELF_SERVICE')),
    CONSTRAINT ck_visit_times     CHECK (checked_out_at IS NULL OR checked_out_at >= checked_in_at)
);

-- A member cannot check in twice without checking out first.
CREATE UNIQUE INDEX uq_visit_open ON facility_visit (member_id) WHERE checked_out_at IS NULL;
CREATE INDEX ix_visit_checked_in_at ON facility_visit (checked_in_at);

-- Trial passes and guests. The visitor is registered as a person, which is what
-- makes the "one free pass per person" rule enforceable by document number.
-- The statement only asks to capture name, document and date of the visit, so
-- guests have no check-out, and no visit_date column: the date of the visit is
-- the date part of checked_in_at.
CREATE TABLE guest_pass
(
    guest_pass_id         BIGSERIAL   PRIMARY KEY,
    person_id             BIGINT      NOT NULL,
    pass_type             VARCHAR(20) NOT NULL,
    host_member_id        BIGINT,
    checked_in_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    notes                 VARCHAR(200),
    registered_by_user_id BIGINT      NOT NULL,

    CONSTRAINT fk_guest_pass_person FOREIGN KEY (person_id) REFERENCES person (person_id) ON DELETE RESTRICT,
    CONSTRAINT fk_guest_pass_host   FOREIGN KEY (host_member_id) REFERENCES member (member_id) ON DELETE RESTRICT,
    CONSTRAINT fk_guest_pass_user   FOREIGN KEY (registered_by_user_id) REFERENCES app_user (app_user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_guest_pass_type   CHECK (pass_type IN ('FREE_TRIAL', 'PAID_DAY_PASS', 'MEMBER_GUEST')),
    CONSTRAINT ck_guest_pass_host   CHECK (pass_type <> 'MEMBER_GUEST' OR host_member_id IS NOT NULL)
);

-- Documented rule: one free trial pass per person, ever. This index hard-codes
-- the value 1 declared in gym.guest_pass.max_free_per_person; if the team ever
-- raises that limit, drop the index and leave the check to the service.
CREATE UNIQUE INDEX uq_guest_pass_free_trial ON guest_pass (person_id) WHERE pass_type = 'FREE_TRIAL';
CREATE INDEX ix_guest_pass_checked_in_at ON guest_pass (checked_in_at);


-- =============================================================================
-- MODULE: billing - payments, discounts and receipts
-- =============================================================================

-- Discounts and promotions, which only the administrator may authorize.
CREATE TABLE promotion
(
    promotion_id          BIGSERIAL      PRIMARY KEY,
    code                  VARCHAR(25)    NOT NULL,
    name                  VARCHAR(80)    NOT NULL,
    description           VARCHAR(300),
    discount_type         VARCHAR(15)    NOT NULL,
    discount_value        NUMERIC(10, 2) NOT NULL,
    valid_from            DATE           NOT NULL,
    valid_to              DATE           NOT NULL,
    max_uses              INTEGER,
    max_uses_per_member   SMALLINT,
    authorized_by_user_id BIGINT         NOT NULL,
    authorized_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    active                BOOLEAN        NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_promotion_user   FOREIGN KEY (authorized_by_user_id) REFERENCES app_user (app_user_id) ON DELETE RESTRICT,
    CONSTRAINT uq_promotion_code   UNIQUE (code),
    CONSTRAINT ck_promotion_type   CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT')),
    CONSTRAINT ck_promotion_value  CHECK (discount_value > 0),
    CONSTRAINT ck_promotion_pct    CHECK (discount_type <> 'PERCENTAGE' OR discount_value <= 100),
    CONSTRAINT ck_promotion_dates  CHECK (valid_to >= valid_from),
    CONSTRAINT ck_promotion_uses   CHECK (max_uses IS NULL OR max_uses > 0)
);

-- One row per payment taken at the counter. The receipt is not a separate table
-- because it is 1:1 with the payment and adds only three attributes, all of them
-- functionally dependent on payment_id.
-- Not stored: net total, which is gross_amount - discount_amount.
-- Stored on purpose: discount_amount, because the promotion may later be edited
-- or deactivated and the amount actually charged must stay immutable.
CREATE TABLE payment
(
    payment_id            BIGSERIAL      PRIMARY KEY,
    -- Nullable on purpose: a visitor who pays their own day pass is a person, not a
    -- member. ck_payment_target below is what keeps every payment attributable.
    member_id             BIGINT,
    membership_id         BIGINT,
    guest_pass_id         BIGINT,
    concept               VARCHAR(20)    NOT NULL,
    payment_method        VARCHAR(20)    NOT NULL,
    gross_amount          NUMERIC(10, 2) NOT NULL,
    promotion_id          BIGINT,
    discount_amount       NUMERIC(10, 2) NOT NULL DEFAULT 0,
    status                VARCHAR(15)    NOT NULL DEFAULT 'REGISTERED',
    paid_at               TIMESTAMPTZ    NOT NULL DEFAULT now(),
    receipt_series        VARCHAR(5),
    receipt_number        INTEGER,
    receipt_issued_at     TIMESTAMPTZ,
    voided_at             TIMESTAMPTZ,
    void_reason           VARCHAR(200),
    registered_by_user_id BIGINT         NOT NULL,

    CONSTRAINT fk_payment_member     FOREIGN KEY (member_id) REFERENCES member (member_id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_membership FOREIGN KEY (membership_id) REFERENCES membership (membership_id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_guest_pass FOREIGN KEY (guest_pass_id) REFERENCES guest_pass (guest_pass_id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_promotion  FOREIGN KEY (promotion_id) REFERENCES promotion (promotion_id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_user       FOREIGN KEY (registered_by_user_id) REFERENCES app_user (app_user_id) ON DELETE RESTRICT,
    CONSTRAINT uq_payment_receipt    UNIQUE (receipt_series, receipt_number),
    CONSTRAINT ck_payment_concept    CHECK (concept IN ('MEMBERSHIP', 'GUEST_PASS', 'OTHER')),
    CONSTRAINT ck_payment_method     CHECK (payment_method IN ('CASH', 'DEBIT_CARD', 'CREDIT_CARD', 'TRANSFER')),
    CONSTRAINT ck_payment_status     CHECK (status IN ('REGISTERED', 'CONFIRMED', 'VOIDED')),
    CONSTRAINT ck_payment_gross      CHECK (gross_amount > 0),
    CONSTRAINT ck_payment_discount   CHECK (discount_amount >= 0 AND discount_amount <= gross_amount),
    -- A membership payment must say which contract it settles, and so must a pass.
    -- It also keeps every payment attributable now that member_id is nullable: only a
    -- GUEST_PASS may leave it empty, and there the payer is guest_pass.person_id.
    CONSTRAINT ck_payment_target     CHECK ((concept <> 'MEMBERSHIP' OR (membership_id IS NOT NULL
                                                                    AND member_id IS NOT NULL))
                                        AND (concept <> 'GUEST_PASS' OR guest_pass_id IS NOT NULL)
                                        AND (concept <> 'OTHER'      OR member_id IS NOT NULL)),
    CONSTRAINT ck_payment_receipt    CHECK ((receipt_series IS NULL) = (receipt_number IS NULL)),
    CONSTRAINT ck_payment_void       CHECK ((status = 'VOIDED') = (voided_at IS NOT NULL))
);

CREATE INDEX ix_payment_member  ON payment (member_id, paid_at);
CREATE INDEX ix_payment_paid_at ON payment (paid_at) WHERE status = 'CONFIRMED';


-- =============================================================================
-- MODULE: classes - group classes, capacity and waitlist
-- =============================================================================

-- Recurring definition: "spinning, Mondays at 18:00, capacity 20, trainer X".
-- A class that runs on more than one weekday is more than one row, which keeps
-- the model free of an extra schedule table.
CREATE TABLE group_class
(
    group_class_id   BIGSERIAL   PRIMARY KEY,
    code             VARCHAR(20) NOT NULL,
    name             VARCHAR(60) NOT NULL,
    discipline       VARCHAR(20) NOT NULL,
    difficulty_level VARCHAR(15) NOT NULL DEFAULT 'BEGINNER',
    trainer_id       BIGINT      NOT NULL,
    weekday          VARCHAR(10) NOT NULL,
    start_time       TIME        NOT NULL,
    duration_minutes SMALLINT    NOT NULL,
    max_capacity     SMALLINT    NOT NULL,
    active           BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_group_class_trainer FOREIGN KEY (trainer_id) REFERENCES trainer (trainer_id) ON DELETE RESTRICT,
    CONSTRAINT uq_group_class_code    UNIQUE (code),
    -- A trainer cannot be booked twice in the same weekly slot.
    CONSTRAINT uq_group_class_slot    UNIQUE (trainer_id, weekday, start_time),
    CONSTRAINT ck_group_class_disc    CHECK (discipline IN ('YOGA', 'SPINNING', 'CROSSFIT', 'ZUMBA',
                                                            'FUNCTIONAL', 'PILATES', 'BOXING', 'OTHER')),
    CONSTRAINT ck_group_class_level   CHECK (difficulty_level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    CONSTRAINT ck_group_class_weekday CHECK (weekday IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY',
                                                         'FRIDAY', 'SATURDAY', 'SUNDAY')),
    CONSTRAINT ck_group_class_dur     CHECK (duration_minutes BETWEEN 15 AND 240),
    CONSTRAINT ck_group_class_cap     CHECK (max_capacity > 0)
);

-- Dated occurrence of a class. Enrolment, capacity, attendance, cancellation and
-- the waitlist all hang off the session, never off the recurring definition.
-- trainer_id, start_time and max_capacity are copied from the class as defaults
-- but belong to the session: the statement requires the administrator to
-- reschedule a single date and to reassign its trainer. The duration is NOT
-- copied, because rescheduling never changes it: it is read from group_class.
CREATE TABLE class_session
(
    class_session_id    BIGSERIAL   PRIMARY KEY,
    group_class_id      BIGINT      NOT NULL,
    trainer_id          BIGINT      NOT NULL,
    session_date        DATE        NOT NULL,
    start_time          TIME        NOT NULL,
    max_capacity        SMALLINT    NOT NULL,
    status              VARCHAR(15) NOT NULL DEFAULT 'SCHEDULED',
    cancellation_reason VARCHAR(200),
    opened_at           TIMESTAMPTZ,
    closed_at           TIMESTAMPTZ,

    CONSTRAINT fk_session_class    FOREIGN KEY (group_class_id) REFERENCES group_class (group_class_id) ON DELETE RESTRICT,
    CONSTRAINT fk_session_trainer  FOREIGN KEY (trainer_id) REFERENCES trainer (trainer_id) ON DELETE RESTRICT,
    CONSTRAINT uq_session_date     UNIQUE (group_class_id, session_date),
    CONSTRAINT ck_session_status   CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_session_cap      CHECK (max_capacity > 0),
    CONSTRAINT ck_session_cancel   CHECK ((status = 'CANCELLED') = (cancellation_reason IS NOT NULL)),
    CONSTRAINT ck_session_closed   CHECK (closed_at IS NULL OR opened_at IS NOT NULL)
);

CREATE INDEX ix_session_date    ON class_session (session_date, start_time);
CREATE INDEX ix_session_trainer ON class_session (trainer_id, session_date);

-- One row per member and session. Attendance is the status of the enrolment,
-- not a separate table: "did the enrolled member show up" is an attribute of
-- the enrolment itself.
CREATE TABLE class_enrollment
(
    class_enrollment_id  BIGSERIAL   PRIMARY KEY,
    class_session_id     BIGINT      NOT NULL,
    member_id            BIGINT      NOT NULL,
    status               VARCHAR(15) NOT NULL DEFAULT 'ENROLLED',
    channel              VARCHAR(15) NOT NULL DEFAULT 'SELF_SERVICE',
    enrolled_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    cancelled_at         TIMESTAMPTZ,
    attendance_marked_at TIMESTAMPTZ,
    enrolled_by_user_id  BIGINT      NOT NULL,

    CONSTRAINT fk_enroll_session FOREIGN KEY (class_session_id) REFERENCES class_session (class_session_id) ON DELETE CASCADE,
    CONSTRAINT fk_enroll_member  FOREIGN KEY (member_id) REFERENCES member (member_id) ON DELETE RESTRICT,
    CONSTRAINT fk_enroll_user    FOREIGN KEY (enrolled_by_user_id) REFERENCES app_user (app_user_id) ON DELETE RESTRICT,
    CONSTRAINT uq_enroll_member  UNIQUE (class_session_id, member_id),
    CONSTRAINT ck_enroll_status  CHECK (status IN ('ENROLLED', 'CANCELLED', 'ATTENDED', 'ABSENT')),
    CONSTRAINT ck_enroll_channel CHECK (channel IN ('SELF_SERVICE', 'FRONT_DESK')),
    CONSTRAINT ck_enroll_cancel  CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL))
);

CREATE INDEX ix_enroll_member ON class_enrollment (member_id, enrolled_at);
-- Seats taken on a session = COUNT(*) WHERE status <> 'CANCELLED'. Never stored.
CREATE INDEX ix_enroll_session_active ON class_enrollment (class_session_id) WHERE status <> 'CANCELLED';

-- Queue for a full session. There is no position column: the order is derived
-- from the member's plan tier (Elite before Premium) and then requested_at, so
-- it cannot drift when somebody leaves the queue or changes plan.
CREATE TABLE waitlist_entry
(
    waitlist_entry_id     BIGSERIAL   PRIMARY KEY,
    class_session_id      BIGINT      NOT NULL,
    member_id             BIGINT      NOT NULL,
    status                VARCHAR(15) NOT NULL DEFAULT 'WAITING',
    requested_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    notified_at           TIMESTAMPTZ,
    confirmation_deadline TIMESTAMPTZ,
    resolved_at           TIMESTAMPTZ,

    CONSTRAINT fk_waitlist_session FOREIGN KEY (class_session_id) REFERENCES class_session (class_session_id) ON DELETE CASCADE,
    CONSTRAINT fk_waitlist_member  FOREIGN KEY (member_id) REFERENCES member (member_id) ON DELETE RESTRICT,
    CONSTRAINT uq_waitlist_member  UNIQUE (class_session_id, member_id),
    CONSTRAINT ck_waitlist_status  CHECK (status IN ('WAITING', 'NOTIFIED', 'PROMOTED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_waitlist_notify  CHECK (status <> 'NOTIFIED' OR notified_at IS NOT NULL)
);

CREATE INDEX ix_waitlist_pending ON waitlist_entry (class_session_id, requested_at) WHERE status IN ('WAITING', 'NOTIFIED');

-- The member rates the class and, optionally, the trainer who taught it. One
-- table instead of two because both scores are given in the same act and share
-- the same key: which member rated which session.
CREATE TABLE class_rating
(
    class_rating_id  BIGSERIAL   PRIMARY KEY,
    class_session_id BIGINT      NOT NULL,
    member_id        BIGINT      NOT NULL,
    class_score      SMALLINT    NOT NULL,
    trainer_score    SMALLINT,
    comment          VARCHAR(500),
    rated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_rating_session FOREIGN KEY (class_session_id) REFERENCES class_session (class_session_id) ON DELETE CASCADE,
    CONSTRAINT fk_rating_member  FOREIGN KEY (member_id) REFERENCES member (member_id) ON DELETE RESTRICT,
    CONSTRAINT uq_rating_member  UNIQUE (class_session_id, member_id),
    CONSTRAINT ck_rating_class   CHECK (class_score BETWEEN 1 AND 5),
    CONSTRAINT ck_rating_trainer CHECK (trainer_score IS NULL OR trainer_score BETWEEN 1 AND 5)
);


-- =============================================================================
-- MODULE: training - assignment, routines and physical progress
-- =============================================================================

-- Historical member-trainer relationship. This is a table and not a member.
-- trainer_id column precisely because the statement demands transferring a whole
-- caseload "without losing the history of routines and follow-up": reassigning
-- closes one row and opens another, and nothing is overwritten.
CREATE TABLE trainer_assignment
(
    trainer_assignment_id BIGSERIAL   PRIMARY KEY,
    member_id             BIGINT      NOT NULL,
    trainer_id            BIGINT      NOT NULL,
    start_date            DATE        NOT NULL DEFAULT CURRENT_DATE,
    end_date              DATE,
    end_reason            VARCHAR(20),
    assigned_by_user_id   BIGINT      NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_assign_member  FOREIGN KEY (member_id) REFERENCES member (member_id) ON DELETE RESTRICT,
    CONSTRAINT fk_assign_trainer FOREIGN KEY (trainer_id) REFERENCES trainer (trainer_id) ON DELETE RESTRICT,
    CONSTRAINT fk_assign_user    FOREIGN KEY (assigned_by_user_id) REFERENCES app_user (app_user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_assign_reason  CHECK (end_reason IS NULL OR end_reason IN
                                        ('REASSIGNMENT', 'TRAINER_LEFT', 'PLAN_DOWNGRADE', 'MEMBER_REQUEST')),
    CONSTRAINT ck_assign_dates   CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_assign_closed  CHECK ((end_date IS NULL) = (end_reason IS NULL))
);

-- A member has at most one trainer at a time; the closed rows are the history.
CREATE UNIQUE INDEX uq_assign_current ON trainer_assignment (member_id) WHERE end_date IS NULL;
-- Current caseload of a trainer = COUNT(*) WHERE end_date IS NULL. Never stored.
CREATE INDEX ix_assign_trainer_current ON trainer_assignment (trainer_id) WHERE end_date IS NULL;

-- Exercise catalog the trainer picks from when building a routine. It stays a
-- table (and not free text) so that the same exercise is named identically
-- across routines and can be reported on.
CREATE TABLE exercise
(
    exercise_id  BIGSERIAL   PRIMARY KEY,
    code         VARCHAR(30) NOT NULL,
    name         VARCHAR(80) NOT NULL,
    muscle_group VARCHAR(20) NOT NULL,
    description  VARCHAR(300),
    video_url    VARCHAR(255),
    active       BOOLEAN     NOT NULL DEFAULT TRUE,

    CONSTRAINT uq_exercise_code  UNIQUE (code),
    CONSTRAINT ck_exercise_group CHECK (muscle_group IN ('CHEST', 'BACK', 'LEGS', 'SHOULDERS',
                                                         'ARMS', 'CORE', 'CARDIO', 'FULL_BODY'))
);

CREATE TABLE routine
(
    routine_id   BIGSERIAL   PRIMARY KEY,
    member_id    BIGINT      NOT NULL,
    trainer_id   BIGINT      NOT NULL,
    name         VARCHAR(80) NOT NULL,
    goal_summary VARCHAR(300),
    status       VARCHAR(15) NOT NULL DEFAULT 'DRAFT',
    start_date   DATE        NOT NULL DEFAULT CURRENT_DATE,
    end_date     DATE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ,

    CONSTRAINT fk_routine_member  FOREIGN KEY (member_id) REFERENCES member (member_id) ON DELETE RESTRICT,
    CONSTRAINT fk_routine_trainer FOREIGN KEY (trainer_id) REFERENCES trainer (trainer_id) ON DELETE RESTRICT,
    CONSTRAINT ck_routine_status  CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_routine_dates   CHECK (end_date IS NULL OR end_date >= start_date)
);

-- Only one routine is in force per member; the archived ones are the history.
CREATE UNIQUE INDEX uq_routine_published ON routine (member_id) WHERE status = 'PUBLISHED';

-- Exercises of a routine. weekday lives here instead of in a routine_day table:
-- that table would have held nothing but the day of the week.
CREATE TABLE routine_exercise
(
    routine_exercise_id BIGSERIAL   PRIMARY KEY,
    routine_id          BIGINT      NOT NULL,
    exercise_id         BIGINT      NOT NULL,
    weekday             VARCHAR(10) NOT NULL,
    display_order       SMALLINT    NOT NULL,
    sets                SMALLINT    NOT NULL,
    repetitions         SMALLINT    NOT NULL,
    rest_seconds        SMALLINT,
    notes               VARCHAR(200),

    CONSTRAINT fk_rtn_ex_routine  FOREIGN KEY (routine_id) REFERENCES routine (routine_id) ON DELETE CASCADE,
    CONSTRAINT fk_rtn_ex_exercise FOREIGN KEY (exercise_id) REFERENCES exercise (exercise_id) ON DELETE RESTRICT,
    CONSTRAINT uq_rtn_ex_order    UNIQUE (routine_id, weekday, display_order),
    CONSTRAINT ck_rtn_ex_weekday  CHECK (weekday IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY',
                                                     'FRIDAY', 'SATURDAY', 'SUNDAY')),
    CONSTRAINT ck_rtn_ex_sets     CHECK (sets > 0),
    CONSTRAINT ck_rtn_ex_reps     CHECK (repetitions > 0),
    CONSTRAINT ck_rtn_ex_rest     CHECK (rest_seconds IS NULL OR rest_seconds >= 0)
);

-- Periodic body measurements taken by the trainer. The statement fixes the list
-- of measures, so they are columns; an attribute-value pair table would be no
-- more normalized, only harder to query and to chart.
CREATE TABLE progress_measurement
(
    progress_measurement_id BIGSERIAL     PRIMARY KEY,
    member_id               BIGINT        NOT NULL,
    trainer_id              BIGINT        NOT NULL,
    measured_on             DATE          NOT NULL DEFAULT CURRENT_DATE,
    weight_kg               NUMERIC(5, 2) NOT NULL,
    waist_cm                NUMERIC(5, 2),
    arm_cm                  NUMERIC(5, 2),
    leg_cm                  NUMERIC(5, 2),
    body_fat_percent        NUMERIC(4, 2),
    notes                   VARCHAR(300),
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT fk_measure_member  FOREIGN KEY (member_id) REFERENCES member (member_id) ON DELETE RESTRICT,
    CONSTRAINT fk_measure_trainer FOREIGN KEY (trainer_id) REFERENCES trainer (trainer_id) ON DELETE RESTRICT,
    CONSTRAINT uq_measure_date    UNIQUE (member_id, measured_on),
    CONSTRAINT ck_measure_weight  CHECK (weight_kg > 0 AND weight_kg < 500),
    CONSTRAINT ck_measure_waist   CHECK (waist_cm IS NULL OR waist_cm > 0),
    CONSTRAINT ck_measure_arm     CHECK (arm_cm IS NULL OR arm_cm > 0),
    CONSTRAINT ck_measure_leg     CHECK (leg_cm IS NULL OR leg_cm > 0),
    CONSTRAINT ck_measure_fat     CHECK (body_fat_percent IS NULL OR body_fat_percent BETWEEN 0 AND 100)
);

-- Free-text follow-up the trainer leaves for the member, including the
-- nutritional recommendations the statement asks for.
CREATE TABLE trainer_note
(
    trainer_note_id BIGSERIAL     PRIMARY KEY,
    member_id       BIGINT        NOT NULL,
    trainer_id      BIGINT        NOT NULL,
    note_type       VARCHAR(15)   NOT NULL,
    content         VARCHAR(1000) NOT NULL,
    reference_date  DATE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT fk_note_member  FOREIGN KEY (member_id) REFERENCES member (member_id) ON DELETE RESTRICT,
    CONSTRAINT fk_note_trainer FOREIGN KEY (trainer_id) REFERENCES trainer (trainer_id) ON DELETE RESTRICT,
    CONSTRAINT ck_note_type    CHECK (note_type IN ('NUTRITION', 'TRAINING', 'GENERAL'))
);

CREATE INDEX ix_note_member ON trainer_note (member_id, created_at);

-- The trainer escalates to the administrator: either the member should be
-- reassigned to a colleague, or the member needs special attention.
CREATE TABLE trainer_alert
(
    trainer_alert_id    BIGSERIAL   PRIMARY KEY,
    member_id           BIGINT      NOT NULL,
    trainer_id          BIGINT      NOT NULL,
    alert_type          VARCHAR(20) NOT NULL,
    description         VARCHAR(500) NOT NULL,
    status              VARCHAR(15) NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at         TIMESTAMPTZ,
    resolved_by_user_id BIGINT,
    resolution_notes    VARCHAR(500),

    CONSTRAINT fk_alert_member  FOREIGN KEY (member_id) REFERENCES member (member_id) ON DELETE RESTRICT,
    CONSTRAINT fk_alert_trainer FOREIGN KEY (trainer_id) REFERENCES trainer (trainer_id) ON DELETE RESTRICT,
    CONSTRAINT fk_alert_user    FOREIGN KEY (resolved_by_user_id) REFERENCES app_user (app_user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_alert_type    CHECK (alert_type IN ('REASSIGNMENT', 'SPECIAL_ATTENTION')),
    CONSTRAINT ck_alert_status  CHECK (status IN ('PENDING', 'RESOLVED', 'DISMISSED')),
    CONSTRAINT ck_alert_closed  CHECK ((status = 'PENDING') = (resolved_at IS NULL))
);

CREATE INDEX ix_alert_pending ON trainer_alert (created_at) WHERE status = 'PENDING';


-- =============================================================================
-- MODULE: nutrition - food catalog, daily log and calorie goal
-- =============================================================================

-- Master food catalog maintained by the administrator. The statement fixes the
-- four nutritional figures, and all four depend on the food itself, so they are
-- columns of this table rather than a nutrient / food_nutrient pair.
-- serving_size + serving_unit are the reference portion (for example 100 GRAM);
-- a member who ate 150 g is charged 1.5x these values, computed at query time.
CREATE TABLE food
(
    food_id         BIGSERIAL      PRIMARY KEY,
    code            VARCHAR(30)    NOT NULL,
    name            VARCHAR(80)    NOT NULL,
    category        VARCHAR(20)    NOT NULL,
    serving_size    NUMERIC(7, 2)  NOT NULL,
    serving_unit    VARCHAR(15)    NOT NULL,
    calories        NUMERIC(7, 2)  NOT NULL,
    protein_g       NUMERIC(6, 2)  NOT NULL,
    carbohydrates_g NUMERIC(6, 2)  NOT NULL,
    fat_g           NUMERIC(6, 2)  NOT NULL,
    active          BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT uq_food_code     UNIQUE (code),
    CONSTRAINT ck_food_category CHECK (category IN ('PROTEIN', 'CARBOHYDRATE', 'FAT', 'VEGETABLE',
                                                    'FRUIT', 'DAIRY', 'BEVERAGE', 'PREPARED', 'OTHER')),
    CONSTRAINT ck_food_unit     CHECK (serving_unit IN ('GRAM', 'MILLILITER', 'UNIT')),
    CONSTRAINT ck_food_serving  CHECK (serving_size > 0),
    CONSTRAINT ck_food_macros   CHECK (calories >= 0 AND protein_g >= 0
                                   AND carbohydrates_g >= 0 AND fat_g >= 0)
);

-- One meal the member logged on a given day. There is no daily-diary header
-- table: it would have held nothing but the member and the date, both of which
-- are already here.
-- SNACK is deliberately not unique per day: the statement says "snacks", plural.
CREATE TABLE meal
(
    meal_id    BIGSERIAL   PRIMARY KEY,
    member_id  BIGINT      NOT NULL,
    log_date   DATE        NOT NULL DEFAULT CURRENT_DATE,
    meal_type  VARCHAR(15) NOT NULL,
    notes      VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,

    CONSTRAINT fk_meal_member FOREIGN KEY (member_id) REFERENCES member (member_id) ON DELETE RESTRICT,
    CONSTRAINT ck_meal_type   CHECK (meal_type IN ('BREAKFAST', 'LUNCH', 'DINNER', 'SNACK'))
);

CREATE INDEX ix_meal_member_date ON meal (member_id, log_date);

-- Foods that make up a meal, with the amount eaten expressed in the food's own
-- serving unit. No calorie or macro total is stored here: every figure the
-- statement asks for (daily total, per-macro total, breakdown by meal type) is
-- SUM(quantity / food.serving_size * food.<column>).
CREATE TABLE meal_item
(
    meal_item_id BIGSERIAL     PRIMARY KEY,
    meal_id      BIGINT        NOT NULL,
    food_id      BIGINT        NOT NULL,
    quantity     NUMERIC(7, 2) NOT NULL,

    CONSTRAINT fk_meal_item_meal FOREIGN KEY (meal_id) REFERENCES meal (meal_id) ON DELETE CASCADE,
    CONSTRAINT fk_meal_item_food FOREIGN KEY (food_id) REFERENCES food (food_id) ON DELETE RESTRICT,
    CONSTRAINT uq_meal_item_food UNIQUE (meal_id, food_id),
    CONSTRAINT ck_meal_item_qty  CHECK (quantity > 0)
);

-- The member's current goal: the calorie target the nutrition module compares
-- the day against, plus the physical target the trainer follows up on. Both are
-- attributes of the same decision ("I want to lose weight"), so they share a row.
-- Superseded goals keep their end_date and become the history.
CREATE TABLE nutrition_goal
(
    nutrition_goal_id  BIGSERIAL     PRIMARY KEY,
    member_id          BIGINT        NOT NULL,
    goal_type          VARCHAR(20)   NOT NULL,
    daily_calories     NUMERIC(6, 2) NOT NULL,
    tolerance_percent  NUMERIC(4, 2) NOT NULL DEFAULT 10,
    target_weight_kg   NUMERIC(5, 2),
    defined_by         VARCHAR(10)   NOT NULL,
    defined_by_user_id BIGINT        NOT NULL,
    start_date         DATE          NOT NULL DEFAULT CURRENT_DATE,
    end_date           DATE,

    CONSTRAINT fk_goal_member    FOREIGN KEY (member_id) REFERENCES member (member_id) ON DELETE RESTRICT,
    CONSTRAINT fk_goal_user      FOREIGN KEY (defined_by_user_id) REFERENCES app_user (app_user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_goal_type      CHECK (goal_type IN ('WEIGHT_LOSS', 'MUSCLE_GAIN', 'MAINTENANCE')),
    CONSTRAINT ck_goal_source    CHECK (defined_by IN ('MEMBER', 'TRAINER')),
    CONSTRAINT ck_goal_calories  CHECK (daily_calories BETWEEN 800 AND 8000),
    CONSTRAINT ck_goal_tolerance CHECK (tolerance_percent BETWEEN 0 AND 50),
    CONSTRAINT ck_goal_weight    CHECK (target_weight_kg IS NULL OR target_weight_kg > 0),
    CONSTRAINT ck_goal_dates     CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE UNIQUE INDEX uq_goal_current ON nutrition_goal (member_id) WHERE end_date IS NULL;


-- =============================================================================
-- MODULE: notification - outgoing notices and in-app inbox
-- =============================================================================

-- The message body is rendered in Java from the notification_type, so no
-- template table exists.
--
-- The SESSION_ prefix is deliberate and matches ErrorCode (SESSION_CANCELLED,
-- SESSION_NOT_OPEN) and the /class-sessions resource: what the administrator
-- cancels or reschedules is one dated class_session, never the recurring
-- group_class - which is why only class_session carries cancellation_reason.
CREATE TABLE notification
(
    notification_id   BIGSERIAL     PRIMARY KEY,
    app_user_id       BIGINT        NOT NULL,
    notification_type VARCHAR(30)   NOT NULL,
    channel           VARCHAR(10)   NOT NULL DEFAULT 'IN_APP',
    title             VARCHAR(120)  NOT NULL,
    message           VARCHAR(1000) NOT NULL,
    status            VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    sent_at           TIMESTAMPTZ,
    read_at           TIMESTAMPTZ,
    failure_reason    VARCHAR(300),

    CONSTRAINT fk_notification_user FOREIGN KEY (app_user_id) REFERENCES app_user (app_user_id) ON DELETE CASCADE,
    CONSTRAINT ck_notification_type CHECK (notification_type IN (
        'MEMBERSHIP_EXPIRING', 'MEMBERSHIP_EXPIRED', 'MEMBERSHIP_FROZEN', 'MEMBERSHIP_REACTIVATED',
        'WAITLIST_SEAT_AVAILABLE', 'SESSION_CANCELLED', 'SESSION_RESCHEDULED',
        'TRAINER_ASSIGNED', 'TRAINER_ALERT', 'PAYMENT_CONFIRMED')),
    CONSTRAINT ck_notification_chan CHECK (channel IN ('EMAIL', 'SMS', 'IN_APP')),
    CONSTRAINT ck_notification_stat CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'READ'))
);

CREATE INDEX ix_notification_inbox   ON notification (app_user_id, created_at DESC);
CREATE INDEX ix_notification_pending ON notification (created_at) WHERE status = 'PENDING';
