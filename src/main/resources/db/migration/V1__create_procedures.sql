CREATE TABLE procedures (
    id              UUID PRIMARY KEY,
    slug            VARCHAR(200) NOT NULL UNIQUE,
    title_fr        VARCHAR(500) NOT NULL,
    title_ar        VARCHAR(500) NOT NULL,
    title_tn        VARCHAR(500),
    description_fr  TEXT,
    description_ar  TEXT,
    ministry        VARCHAR(300) NOT NULL,
    category        VARCHAR(50)  NOT NULL,
    difficulty      VARCHAR(20)  NOT NULL,
    delivery_mode   VARCHAR(100),
    processing_time VARCHAR(100),
    fees            VARCHAR(100),
    source_url      VARCHAR(1000),
    source_reference VARCHAR(500),
    last_verified_at TIMESTAMPTZ,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_procedures_category ON procedures (category);
CREATE INDEX idx_procedures_status ON procedures (status);
CREATE INDEX idx_procedures_ministry ON procedures (ministry);

CREATE TABLE procedure_documents (
    id              UUID PRIMARY KEY,
    procedure_id    UUID NOT NULL REFERENCES procedures (id) ON DELETE CASCADE,
    sort_order      INT  NOT NULL,
    title_fr        VARCHAR(500) NOT NULL,
    title_ar        VARCHAR(500) NOT NULL,
    description_fr  TEXT,
    description_ar  TEXT
);

CREATE INDEX idx_procedure_documents_procedure ON procedure_documents (procedure_id);

CREATE TABLE procedure_steps (
    id              UUID PRIMARY KEY,
    procedure_id    UUID NOT NULL REFERENCES procedures (id) ON DELETE CASCADE,
    step_number     INT  NOT NULL,
    title_fr        VARCHAR(500) NOT NULL,
    title_ar        VARCHAR(500) NOT NULL,
    description_fr  TEXT,
    description_ar  TEXT
);

CREATE INDEX idx_procedure_steps_procedure ON procedure_steps (procedure_id);

CREATE TABLE office_locations (
    id              UUID PRIMARY KEY,
    procedure_id    UUID NOT NULL REFERENCES procedures (id) ON DELETE CASCADE,
    name            VARCHAR(300) NOT NULL,
    address         VARCHAR(500) NOT NULL,
    city            VARCHAR(100),
    governorate     VARCHAR(100),
    hours_fr        TEXT,
    hours_ar        TEXT,
    latitude        DOUBLE PRECISION,
    longitude       DOUBLE PRECISION
);

CREATE INDEX idx_office_locations_procedure ON office_locations (procedure_id);

CREATE TABLE procedure_relations (
    procedure_id         UUID NOT NULL REFERENCES procedures (id) ON DELETE CASCADE,
    related_procedure_id UUID NOT NULL REFERENCES procedures (id) ON DELETE CASCADE,
    PRIMARY KEY (procedure_id, related_procedure_id),
    CHECK (procedure_id <> related_procedure_id)
);
