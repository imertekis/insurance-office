-- The vehicle brands (Task 23a, decision 4): the only values the brand field
-- takes from now on, and what the Excel import maps its spellings onto.
-- vehicle.brand stays text, not a foreign key: search_normalized reads it as
-- it is, and an old value from before the list, or one the import could not
-- map, is kept as it came.
--
-- A new brand is a new migration, until a screen for it exists (Task 27).
--
-- synonyms: spellings the import maps onto the brand, beyond case and
-- accents, which are never significant ("volkswagen", "CITROEN" need no
-- synonym). Only spellings with a single meaning. The search does not look
-- in them (decision 9).
CREATE TABLE vehicle_brand (
    id       BIGSERIAL   PRIMARY KEY,
    name     VARCHAR(50) NOT NULL,
    synonyms TEXT[]      NOT NULL DEFAULT '{}'
);

-- One brand per spelling, case and accents aside, compared as
-- search_normalized compares (V2). That no synonym names two brands is
-- checked when the application reads the table (VehicleValues.Brands).
CREATE UNIQUE INDEX idx_vehicle_brand_name ON vehicle_brand
    (upper(public.immutable_unaccent(name) COLLATE pg_unicode_fast));

-- Cars, light commercial vehicles, quadricycles and motorcycles of the
-- Greek market; BMW, Honda, Peugeot and Suzuki make both cars and
-- motorcycles. Written as each maker writes its own name.
INSERT INTO vehicle_brand (name, synonyms) VALUES
    ('Abarth', '{}'),
    ('Aixam', '{}'),
    ('Alfa Romeo', '{"ALFA-ROMEO"}'),
    ('Alpine', '{}'),
    ('Aprilia', '{}'),
    ('Aston Martin', '{}'),
    ('Audi', '{}'),
    ('Benelli', '{}'),
    ('Bentley', '{}'),
    ('Beta', '{}'),
    ('BMW', '{}'),
    ('BYD', '{}'),
    ('Cadillac', '{}'),
    ('CFMOTO', '{}'),
    ('Chevrolet', '{}'),
    ('Chrysler', '{}'),
    ('Citroën', '{}'),
    ('Cupra', '{}'),
    ('Dacia', '{}'),
    ('Daelim', '{}'),
    ('Daewoo', '{}'),
    ('Daihatsu', '{}'),
    ('Daytona', '{}'),
    ('Derbi', '{}'),
    ('Dodge', '{}'),
    ('DS', '{}'),
    ('Ducati', '{}'),
    ('Ferrari', '{}'),
    ('Fiat', '{}'),
    ('Ford', '{}'),
    ('Genesis', '{}'),
    ('Gilera', '{}'),
    ('Harley-Davidson', '{"HARLEY DAVIDSON"}'),
    ('Honda', '{}'),
    ('Husqvarna', '{}'),
    ('Hyundai', '{}'),
    ('Infiniti', '{}'),
    ('Isuzu', '{}'),
    ('Iveco', '{}'),
    ('Jaecoo', '{}'),
    ('Jaguar', '{}'),
    ('Jeep', '{}'),
    ('Kawasaki', '{}'),
    ('Keeway', '{}'),
    ('KGM', '{}'),
    ('Kia', '{}'),
    ('KTM', '{}'),
    ('KYMCO', '{}'),
    ('Lada', '{}'),
    ('Lamborghini', '{}'),
    ('Lancia', '{}'),
    ('Land Rover', '{"LAND-ROVER", "LANDROVER"}'),
    ('Leapmotor', '{}'),
    ('Lexus', '{}'),
    ('Ligier', '{}'),
    ('Lotus', '{}'),
    ('Lynk & Co', '{}'),
    ('Mahindra', '{}'),
    ('Malaguti', '{}'),
    ('MAN', '{}'),
    ('Maserati', '{}'),
    ('Mazda', '{}'),
    ('McLaren', '{}'),
    ('Mercedes-Benz', '{"MERCEDES", "MERCEDES BENZ"}'),
    ('MG', '{}'),
    ('Microcar', '{}'),
    ('MINI', '{}'),
    ('Mitsubishi', '{}'),
    ('Modenas', '{}'),
    ('Moto Guzzi', '{"MOTO-GUZZI"}'),
    ('MV Agusta', '{"MV-AGUSTA"}'),
    ('Nissan', '{}'),
    ('Omoda', '{}'),
    ('Opel', '{}'),
    ('Peugeot', '{}'),
    ('Piaggio', '{}'),
    ('Polestar', '{}'),
    ('Porsche', '{}'),
    ('Renault', '{}'),
    ('Rolls-Royce', '{"ROLLS ROYCE"}'),
    ('Rover', '{}'),
    ('Royal Enfield', '{}'),
    ('Saab', '{}'),
    ('SEAT', '{}'),
    ('Škoda', '{}'),
    ('Smart', '{}'),
    ('SsangYong', '{"SSANG YONG"}'),
    ('Subaru', '{}'),
    ('Suzuki', '{}'),
    ('SYM', '{}'),
    ('Tesla', '{}'),
    ('Toyota', '{}'),
    ('Triumph', '{}'),
    ('Vespa', '{}'),
    ('Voge', '{}'),
    ('Volkswagen', '{"VW", "V.W."}'),
    ('Volvo', '{}'),
    ('Xpeng', '{}'),
    ('Yamaha', '{}'),
    ('Zontes', '{}');
