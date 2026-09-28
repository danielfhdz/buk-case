CREATE TABLE resources (
    id        BIGSERIAL PRIMARY KEY,
    code      VARCHAR(100) NOT NULL UNIQUE,
    name      VARCHAR(150) NOT NULL,
    parent_id BIGINT REFERENCES resources (id)
);

CREATE INDEX idx_resources_parent ON resources (parent_id);

CREATE TABLE resource_closure (
    ancestor_id   BIGINT  NOT NULL REFERENCES resources (id),
    descendant_id BIGINT  NOT NULL REFERENCES resources (id),
    depth         INTEGER NOT NULL,
    PRIMARY KEY (ancestor_id, descendant_id)
);

CREATE INDEX idx_resource_closure_descendant ON resource_closure (descendant_id, ancestor_id);

CREATE FUNCTION resource_closure_on_insert() RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO resource_closure (ancestor_id, descendant_id, depth)
    VALUES (NEW.id, NEW.id, 0);

    INSERT INTO resource_closure (ancestor_id, descendant_id, depth)
    SELECT ancestor_id, NEW.id, depth + 1
    FROM resource_closure
    WHERE descendant_id = NEW.parent_id;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_resource_closure_on_insert
    AFTER INSERT ON resources
    FOR EACH ROW EXECUTE FUNCTION resource_closure_on_insert();

CREATE TABLE profiles (
    id        BIGSERIAL PRIMARY KEY,
    name      VARCHAR(150) NOT NULL UNIQUE,
    is_admin  BOOLEAN      NOT NULL DEFAULT FALSE,
    is_system BOOLEAN      NOT NULL DEFAULT FALSE
);

-- access_level: 1 = READ, 2 = WRITE. Numeric so that "at least READ" is a single >= comparison.
CREATE TABLE profile_grants (
    id           BIGSERIAL PRIMARY KEY,
    profile_id   BIGINT   NOT NULL REFERENCES profiles (id) ON DELETE CASCADE,
    area_id      BIGINT   NOT NULL REFERENCES areas (id),
    resource_id  BIGINT   NOT NULL REFERENCES resources (id),
    access_level SMALLINT NOT NULL CHECK (access_level IN (1, 2)),
    UNIQUE (profile_id, area_id, resource_id)
);

CREATE TABLE user_profiles (
    user_id    BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    profile_id BIGINT NOT NULL REFERENCES profiles (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, profile_id)
);

CREATE INDEX idx_user_profiles_profile ON user_profiles (profile_id);

INSERT INTO profiles (name, is_admin, is_system) VALUES ('Administrator', TRUE, TRUE);
