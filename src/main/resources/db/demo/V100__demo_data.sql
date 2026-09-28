INSERT INTO areas (id, name, parent_id) VALUES
    (1, 'General Management', NULL),
    (2, 'Commercial Management', 1),
    (3, 'North Sales', 2),
    (4, 'South Sales', 2),
    (5, 'Marketing', 2),
    (6, 'Operations Management', 1),
    (7, 'IT', 6),
    (8, 'Payroll', 6);

INSERT INTO resources (id, code, name, parent_id) VALUES
    (10, 'ASSETS', 'Asset Management', NULL),
    (11, 'ASSETS.COMPUTERS', 'Computers', 10),
    (12, 'ASSETS.PHONES', 'Phones', 10),
    (13, 'ASSETS.VEHICLES', 'Vehicles', 10),
    (20, 'COMPLAINTS', 'Whistleblowing Channel', NULL),
    (21, 'COMPLAINTS.HARASSMENT', 'Harassment', 20),
    (22, 'COMPLAINTS.FRAUD', 'Fraud', 20),
    (23, 'COMPLAINTS.DISCRIMINATION', 'Discrimination', 20),
    (30, 'VACATIONS', 'Vacations', NULL),
    (40, 'DOCUMENTS', 'Documents', NULL),
    (50, 'PAYROLL', 'Payroll', NULL);

INSERT INTO positions (id, name, area_id) VALUES
    (1, 'General Manager', 1),
    (2, 'Commercial Manager', 2),
    (3, 'North Sales Executive', 3),
    (4, 'South Sales Executive', 4),
    (5, 'Marketing Analyst', 5),
    (6, 'Operations Manager', 6),
    (7, 'IT Manager', 7),
    (8, 'Payroll Analyst', 8),
    (9, 'Asset Coordinator', 6),
    (10, 'Compliance Officer', 1);

INSERT INTO employees (id, full_name, position_id) VALUES
    (1, 'Laura General', 1),
    (2, 'Andres Commercial', 2),
    (3, 'Carolina North', 3),
    (4, 'Diego North', 3),
    (5, 'Sofia South', 4),
    (6, 'Mateo Marketing', 5),
    (7, 'Valentina Operations', 6),
    (8, 'Jorge IT', 7),
    (9, 'Paula Payroll', 8),
    (10, 'Pedro Assets', 9),
    (11, 'Elena Compliance', 10),
    (12, 'Tomas Compliance', 10),
    (13, 'Nicolas Unassigned', 4);

INSERT INTO users (id, username, employee_id) VALUES
    (1, 'laura.general', 1),
    (2, 'andres.commercial', 2),
    (3, 'carolina.north', 3),
    (4, 'pedro.assets', 10),
    (5, 'jorge.it', 8),
    (6, 'paula.payroll', 9),
    (7, 'elena.compliance', 11),
    (8, 'tomas.compliance', 12),
    (9, 'nicolas.noaccess', 13);

INSERT INTO profiles (id, name) VALUES
    (2, 'Asset Manager - Company'),
    (3, 'Vacations - North Sales'),
    (4, 'Commercial Management Viewer'),
    (5, 'IT Assets - Computers and Phones'),
    (6, 'Payroll Analyst - Read Only'),
    (7, 'Complaints - Harassment and Discrimination'),
    (8, 'Complaints - Fraud');

INSERT INTO profile_grants (profile_id, area_id, resource_id, access_level) VALUES
    (2, 1, 10, 2),
    (2, 1, 40, 1),
    (3, 3, 30, 2),
    (4, 2, 30, 1),
    (4, 2, 40, 1),
    (4, 2, 10, 1),
    (5, 1, 11, 2),
    (5, 1, 12, 2),
    (6, 1, 50, 1),
    (7, 1, 21, 2),
    (7, 1, 23, 2),
    (8, 1, 22, 2);

INSERT INTO user_profiles (user_id, profile_id) VALUES
    (1, 1),
    (4, 2),
    (3, 3),
    (2, 4),
    (5, 5),
    (6, 6),
    (7, 7),
    (8, 8);

INSERT INTO asset_categories (id, name, resource_id) VALUES
    (1, 'Computers', 11),
    (2, 'Phones', 12),
    (3, 'Vehicles', 13);

INSERT INTO assets (name, category_id, employee_id) VALUES
    ('MacBook Pro 14', 1, 3),
    ('ThinkPad T14', 1, 5),
    ('Lenovo IdeaPad', 1, 8),
    ('iPhone 15', 2, 2),
    ('Samsung Galaxy S24', 2, 4),
    ('Toyota Hilux', 3, 7),
    ('Spare ThinkPad', 1, NULL);

INSERT INTO complaint_types (id, name, resource_id) VALUES
    (1, 'Harassment', 21),
    (2, 'Fraud', 22),
    (3, 'Discrimination', 23);

INSERT INTO complaints (description, type_id, area_id) VALUES
    ('Inappropriate comments in team meeting', 1, 3),
    ('Expense report manipulation', 2, 4),
    ('Unequal treatment in promotions', 3, 5),
    ('Supplier kickback suspicion', 2, 7);

INSERT INTO vacation_requests (employee_id, start_date, end_date, status) VALUES
    (3, '2026-10-05', '2026-10-09', 'PENDING'),
    (4, '2026-11-02', '2026-11-13', 'APPROVED'),
    (5, '2026-12-21', '2026-12-31', 'PENDING'),
    (6, '2026-10-19', '2026-10-23', 'PENDING'),
    (8, '2026-11-16', '2026-11-20', 'APPROVED');

SELECT setval('areas_id_seq', (SELECT MAX(id) FROM areas));
SELECT setval('resources_id_seq', (SELECT MAX(id) FROM resources));
SELECT setval('positions_id_seq', (SELECT MAX(id) FROM positions));
SELECT setval('employees_id_seq', (SELECT MAX(id) FROM employees));
SELECT setval('users_id_seq', (SELECT MAX(id) FROM users));
SELECT setval('profiles_id_seq', (SELECT MAX(id) FROM profiles));
SELECT setval('asset_categories_id_seq', (SELECT MAX(id) FROM asset_categories));
SELECT setval('complaint_types_id_seq', (SELECT MAX(id) FROM complaint_types));
