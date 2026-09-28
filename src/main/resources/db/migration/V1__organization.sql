CREATE TABLE areas (
    id        BIGSERIAL PRIMARY KEY,
    name      VARCHAR(150) NOT NULL,
    parent_id BIGINT REFERENCES areas (id)
);

CREATE INDEX idx_areas_parent ON areas (parent_id);

-- Closure table: one row per (ancestor, descendant) pair, including each node with itself (depth 0).
-- Resolves "an area and all its sub-areas" with a single indexed join instead of a recursive query.
CREATE TABLE area_closure (
    ancestor_id   BIGINT  NOT NULL REFERENCES areas (id),
    descendant_id BIGINT  NOT NULL REFERENCES areas (id),
    depth         INTEGER NOT NULL,
    PRIMARY KEY (ancestor_id, descendant_id)
);

CREATE INDEX idx_area_closure_descendant ON area_closure (descendant_id, ancestor_id);

CREATE FUNCTION area_closure_on_insert() RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO area_closure (ancestor_id, descendant_id, depth)
    VALUES (NEW.id, NEW.id, 0);

    INSERT INTO area_closure (ancestor_id, descendant_id, depth)
    SELECT ancestor_id, NEW.id, depth + 1
    FROM area_closure
    WHERE descendant_id = NEW.parent_id;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_area_closure_on_insert
    AFTER INSERT ON areas
    FOR EACH ROW EXECUTE FUNCTION area_closure_on_insert();

CREATE TABLE positions (
    id      BIGSERIAL PRIMARY KEY,
    name    VARCHAR(150) NOT NULL,
    area_id BIGINT       NOT NULL REFERENCES areas (id)
);

CREATE INDEX idx_positions_area ON positions (area_id);

CREATE TABLE employees (
    id          BIGSERIAL PRIMARY KEY,
    full_name   VARCHAR(200) NOT NULL,
    position_id BIGINT       NOT NULL REFERENCES positions (id)
);

CREATE INDEX idx_employees_position ON employees (position_id);

CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(100) NOT NULL UNIQUE,
    employee_id BIGINT UNIQUE REFERENCES employees (id)
);
