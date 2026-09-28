CREATE TABLE asset_categories (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    resource_id BIGINT       NOT NULL REFERENCES resources (id)
);

CREATE TABLE assets (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    category_id BIGINT       NOT NULL REFERENCES asset_categories (id),
    employee_id BIGINT REFERENCES employees (id)
);

CREATE INDEX idx_assets_category ON assets (category_id);
CREATE INDEX idx_assets_employee ON assets (employee_id);

CREATE TABLE complaint_types (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    resource_id BIGINT       NOT NULL REFERENCES resources (id)
);

CREATE TABLE complaints (
    id          BIGSERIAL PRIMARY KEY,
    description TEXT   NOT NULL,
    type_id     BIGINT NOT NULL REFERENCES complaint_types (id),
    area_id     BIGINT NOT NULL REFERENCES areas (id)
);

CREATE INDEX idx_complaints_type ON complaints (type_id);
CREATE INDEX idx_complaints_area ON complaints (area_id);

CREATE TABLE vacation_requests (
    id          BIGSERIAL PRIMARY KEY,
    employee_id BIGINT      NOT NULL REFERENCES employees (id),
    start_date  DATE        NOT NULL,
    end_date    DATE        NOT NULL,
    status      VARCHAR(20) NOT NULL,
    CHECK (end_date >= start_date)
);

CREATE INDEX idx_vacation_requests_employee ON vacation_requests (employee_id);
