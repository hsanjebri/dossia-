-- Coordinates for existing seeded offices
UPDATE office_locations
SET latitude = 36.8065, longitude = 10.1815
WHERE id = 'd1000000-0000-4000-8000-000000000001';

-- Canonical service points (police, finance, etc.) for nearest-office search
CREATE TABLE service_points (
    id           UUID PRIMARY KEY,
    name         VARCHAR(300) NOT NULL,
    office_type  VARCHAR(50)  NOT NULL,
    category     VARCHAR(50),
    ministry     VARCHAR(300),
    address      VARCHAR(500) NOT NULL,
    city         VARCHAR(100),
    governorate  VARCHAR(100),
    hours_fr     TEXT,
    latitude     DOUBLE PRECISION NOT NULL,
    longitude    DOUBLE PRECISION NOT NULL
);

CREATE INDEX idx_service_points_office_type ON service_points (office_type);
CREATE INDEX idx_service_points_category ON service_points (category);

INSERT INTO service_points (id, name, office_type, category, ministry, address, city, governorate, hours_fr, latitude, longitude) VALUES
-- Police / Interior (passport, CIN)
('e1000000-0000-4000-8000-000000000001', 'Commissariat central — Tunis', 'POLICE', 'CIVIL_STATUS', 'Ministry of Interior', 'Rue de la Kasbah, Tunis', 'Tunis', 'Tunis', 'Lun-Ven: 08:00 – 16:00', 36.7989, 10.1654),
('e1000000-0000-4000-8000-000000000002', 'Commissariat — La Marsa', 'POLICE', 'CIVIL_STATUS', 'Ministry of Interior', 'Avenue Habib Bourguiba, La Marsa', 'La Marsa', 'Tunis', 'Lun-Ven: 08:00 – 16:00', 36.8780, 10.3242),
('e1000000-0000-4000-8000-000000000003', 'Commissariat — Ariana', 'POLICE', 'CIVIL_STATUS', 'Ministry of Interior', 'Centre-ville, Ariana', 'Ariana', 'Ariana', 'Lun-Ven: 08:00 – 16:00', 36.8625, 10.1956),
('e1000000-0000-4000-8000-000000000004', 'Commissariat — Sfax', 'POLICE', 'CIVIL_STATUS', 'Ministry of Interior', 'Avenue Habib Bourguiba, Sfax', 'Sfax', 'Sfax', 'Lun-Ven: 08:00 – 16:00', 34.7406, 10.7603),
('e1000000-0000-4000-8000-000000000005', 'Commissariat — Sousse', 'POLICE', 'CIVIL_STATUS', 'Ministry of Interior', 'Avenue Mohamed V, Sousse', 'Sousse', 'Sousse', 'Lun-Ven: 08:00 – 16:00', 35.8256, 10.6360),
-- Finance / timbre fiscal
('e1000000-0000-4000-8000-000000000010', 'Recette des finances — Tunis', 'FINANCE', 'TAX', 'Ministry of Finance', 'Avenue de France, Tunis', 'Tunis', 'Tunis', 'Lun-Ven: 08:00 – 16:30', 36.7989, 10.1631),
('e1000000-0000-4000-8000-000000000011', 'Recette des finances — La Marsa', 'FINANCE', 'TAX', 'Ministry of Finance', 'La Marsa', 'La Marsa', 'Tunis', 'Lun-Ven: 08:00 – 16:30', 36.8850, 10.3300),
('e1000000-0000-4000-8000-000000000012', 'Recette des finances — Sfax', 'FINANCE', 'TAX', 'Ministry of Finance', 'Centre-ville, Sfax', 'Sfax', 'Sfax', 'Lun-Ven: 08:00 – 16:30', 34.7333, 10.7667),
('e1000000-0000-4000-8000-000000000013', 'Bureau de poste — Tunis (timbres)', 'POST', 'TAX', 'La Poste Tunisienne', 'Avenue de la Liberté, Tunis', 'Tunis', 'Tunis', 'Lun-Ven: 08:00 – 17:00', 36.8065, 10.1815),
-- Municipal / civil status
('e1000000-0000-4000-8000-000000000020', 'Municipalité — Tunis', 'MUNICIPALITY', 'CIVIL_STATUS', 'Municipality of Tunis', 'Place de la Kasbah, Tunis', 'Tunis', 'Tunis', 'Lun-Ven: 08:00 – 16:00', 36.7980, 10.1650),
('e1000000-0000-4000-8000-000000000021', 'Municipalité — Sfax', 'MUNICIPALITY', 'CIVIL_STATUS', 'Municipality of Sfax', 'Place de la République, Sfax', 'Sfax', 'Sfax', 'Lun-Ven: 08:00 – 16:00', 34.7350, 10.7620),
-- ATTT / vehicles
('e1000000-0000-4000-8000-000000000030', 'ATTT — Tunis', 'ATTT', 'VEHICLES', 'ATTT', 'Route de la Marsa, Le Bardo', 'Le Bardo', 'Tunis', 'Lun-Ven: 08:00 – 16:00', 36.8092, 10.1420),
('e1000000-0000-4000-8000-000000000031', 'ATTT — Sousse', 'ATTT', 'VEHICLES', 'ATTT', 'Sousse', 'Sousse', 'Sousse', 'Lun-Ven: 08:00 – 16:00', 35.8300, 10.6400),
-- Business
('e1000000-0000-4000-8000-000000000040', 'APII — Tunis', 'BUSINESS', 'BUSINESS', 'APII', 'Centre Urbain Nord, Tunis', 'Tunis', 'Tunis', 'Lun-Ven: 08:00 – 16:30', 36.8340, 10.1550);
