-- init.sql
-- Schéma DB MariaDB pour YourCarYourWay (V1) basé sur ton diagramme UML
-- Engine: InnoDB | Charset: utf8mb4

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- ====== USERS ======
CREATE TABLE IF NOT EXISTS users (
  id            CHAR(36) NOT NULL,
  email         VARCHAR(255) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  first_name    VARCHAR(100) NOT NULL,
  last_name     VARCHAR(100) NOT NULL,
  role          VARCHAR(50) NOT NULL,
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DELIMITER //
CREATE TRIGGER trg_users_uuid
BEFORE INSERT ON users
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id = '' THEN
    SET NEW.id = UUID();
  END IF;
END//
DELIMITER ;

-- ====== AGENCIES ======
CREATE TABLE IF NOT EXISTS agencies (
  id           CHAR(36) NOT NULL,
  name         VARCHAR(255) NOT NULL,
  city         VARCHAR(255) NOT NULL,
  country_code CHAR(2) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DELIMITER //
CREATE TRIGGER trg_agencies_uuid
BEFORE INSERT ON agencies
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id = '' THEN
    SET NEW.id = UUID();
  END IF;
END//
DELIMITER ;

-- ====== CAR CATEGORIES (ACRISS) ======
CREATE TABLE IF NOT EXISTS car_categories (
  code        VARCHAR(4) NOT NULL,
  label       VARCHAR(255) NOT NULL,
  description TEXT NULL,
  PRIMARY KEY (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ====== AGENCY <-> CAR CATEGORY (many-to-many) ======
CREATE TABLE IF NOT EXISTS agency_car_categories (
  agency_id     CHAR(36) NOT NULL,
  category_code VARCHAR(4) NOT NULL,
  PRIMARY KEY (agency_id, category_code),
  CONSTRAINT fk_acc_agency
    FOREIGN KEY (agency_id) REFERENCES agencies(id)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_acc_category
    FOREIGN KEY (category_code) REFERENCES car_categories(code)
    ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ====== RESERVATIONS ======
CREATE TABLE IF NOT EXISTS reservations (
  id                CHAR(36) NOT NULL,
  created_at        TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  start_at          DATETIME(3) NOT NULL,
  end_at            DATETIME(3) NOT NULL,
  status            VARCHAR(50) NOT NULL,
  total_price_cents INT NOT NULL,
  currency          CHAR(3) NOT NULL,

  user_id           CHAR(36) NOT NULL,
  pickup_agency_id  CHAR(36) NOT NULL,
  dropoff_agency_id CHAR(36) NOT NULL,
  car_category_code VARCHAR(4) NOT NULL,

  PRIMARY KEY (id),
  KEY idx_res_user (user_id),
  KEY idx_res_pickup (pickup_agency_id),
  KEY idx_res_dropoff (dropoff_agency_id),
  KEY idx_res_category (car_category_code),
  KEY idx_res_start_at (start_at),

  CONSTRAINT fk_res_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE RESTRICT ON UPDATE CASCADE,

  CONSTRAINT fk_res_pickup_agency
    FOREIGN KEY (pickup_agency_id) REFERENCES agencies(id)
    ON DELETE RESTRICT ON UPDATE CASCADE,

  CONSTRAINT fk_res_dropoff_agency
    FOREIGN KEY (dropoff_agency_id) REFERENCES agencies(id)
    ON DELETE RESTRICT ON UPDATE CASCADE,

  CONSTRAINT fk_res_category
    FOREIGN KEY (car_category_code) REFERENCES car_categories(code)
    ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DELIMITER //
CREATE TRIGGER trg_reservations_uuid
BEFORE INSERT ON reservations
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id = '' THEN
    SET NEW.id = UUID();
  END IF;
END//
DELIMITER ;

-- (Optionnel) empêcher end_at <= start_at au niveau DB
DELIMITER //
CREATE TRIGGER trg_reservations_dates
BEFORE INSERT ON reservations
FOR EACH ROW
BEGIN
  IF NEW.end_at <= NEW.start_at THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'end_at must be after start_at';
  END IF;
END//
DELIMITER ;

DELIMITER //
CREATE TRIGGER trg_reservations_dates_upd
BEFORE UPDATE ON reservations
FOR EACH ROW
BEGIN
  IF NEW.end_at <= NEW.start_at THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'end_at must be after start_at';
  END IF;
END//
DELIMITER ;

-- ====== PAYMENTS ======
CREATE TABLE IF NOT EXISTS payments (
  id                      CHAR(36) NOT NULL,
  created_at              TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  mode                    VARCHAR(50) NOT NULL,
  kind                    VARCHAR(50) NOT NULL,
  status                  VARCHAR(50) NOT NULL,
  amount_cents            INT NOT NULL,
  currency                CHAR(3) NOT NULL,
  paid_at                 DATETIME(3) NULL,
  stripe_payment_intent_id VARCHAR(255) NULL,

  reservation_id          CHAR(36) NOT NULL,

  PRIMARY KEY (id),
  KEY idx_pay_reservation (reservation_id),
  KEY idx_pay_stripe_pi (stripe_payment_intent_id),

  CONSTRAINT fk_pay_reservation
    FOREIGN KEY (reservation_id) REFERENCES reservations(id)
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DELIMITER //
CREATE TRIGGER trg_payments_uuid
BEFORE INSERT ON payments
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id = '' THEN
    SET NEW.id = UUID();
  END IF;
END//
DELIMITER ;

-- Unicité PaymentIntent si présent (MariaDB: unique + NULL autorisés en multiple)
CREATE UNIQUE INDEX uq_payments_stripe_pi ON payments (stripe_payment_intent_id);

-- ====== SUPPORT THREADS ======
CREATE TABLE IF NOT EXISTS support_threads (
  id                 CHAR(36) NOT NULL,
  created_at         TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  subject            VARCHAR(255) NOT NULL,
  status             VARCHAR(50) NOT NULL,

  created_by_user_id CHAR(36) NOT NULL,
  reservation_id     CHAR(36) NULL,
  assigned_support_user_id CHAR(36) NULL,

  PRIMARY KEY (id),
  KEY idx_threads_created_by (created_by_user_id),
  KEY idx_threads_reservation (reservation_id),
  KEY idx_threads_assigned (assigned_support_user_id),

  CONSTRAINT fk_threads_user
    FOREIGN KEY (created_by_user_id) REFERENCES users(id)
    ON DELETE RESTRICT ON UPDATE CASCADE,

  CONSTRAINT fk_threads_reservation
    FOREIGN KEY (reservation_id) REFERENCES reservations(id)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT fk_threads_assigned_support
    FOREIGN KEY (assigned_support_user_id) REFERENCES users(id)
    ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DELIMITER //
CREATE TRIGGER trg_threads_uuid
BEFORE INSERT ON support_threads
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id = '' THEN
    SET NEW.id = UUID();
  END IF;
END//
DELIMITER ;

-- ====== SUPPORT MESSAGES ======
CREATE TABLE IF NOT EXISTS support_messages (
  id             CHAR(36) NOT NULL,
  sent_at        TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  content        TEXT NOT NULL,

  thread_id      CHAR(36) NOT NULL,
  sender_user_id CHAR(36) NOT NULL,

  PRIMARY KEY (id),
  KEY idx_msg_thread_sent (thread_id, sent_at),
  KEY idx_msg_sender (sender_user_id),

  CONSTRAINT fk_msg_thread
    FOREIGN KEY (thread_id) REFERENCES support_threads(id)
    ON DELETE CASCADE ON UPDATE CASCADE,

  CONSTRAINT fk_msg_sender
    FOREIGN KEY (sender_user_id) REFERENCES users(id)
    ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DELIMITER //
CREATE TRIGGER trg_messages_uuid
BEFORE INSERT ON support_messages
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id = '' THEN
    SET NEW.id = UUID();
  END IF;
END//
DELIMITER ;

-- ====== SEED (POC CHAT) ======
-- Two demo users + one support thread to test WebSocket persistence.
INSERT IGNORE INTO users (id, email, password_hash, first_name, last_name, role)
VALUES
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'client@test.com', '{noop}123soleil', 'Client', 'Demo', 'CLIENT'),
  ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'client2@test.com', '{noop}123soleil', 'Client', 'Demo2', 'CLIENT'),
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'support@test.com', '{noop}123soleil', 'Support', 'Agent', 'SUPPORT');

INSERT IGNORE INTO agencies (id, name, city, country_code)
VALUES
  ('aaaa0000-0000-0000-0000-000000000001', 'Paris Central', 'Paris', 'FR'),
  ('aaaa0000-0000-0000-0000-000000000002', 'Lyon Station', 'Lyon', 'FR');

INSERT IGNORE INTO car_categories (code, label, description)
VALUES
  ('VAN6', 'Voiture 6 places', '6 places, grand coffre'),
  ('SED4', 'Voiture 4 places', '4 places, compacte');

INSERT IGNORE INTO agency_car_categories (agency_id, category_code)
VALUES
  ('aaaa0000-0000-0000-0000-000000000001', 'VAN6'),
  ('aaaa0000-0000-0000-0000-000000000001', 'SED4'),
  ('aaaa0000-0000-0000-0000-000000000002', 'VAN6'),
  ('aaaa0000-0000-0000-0000-000000000002', 'SED4');

INSERT IGNORE INTO reservations (
  id, start_at, end_at, status, total_price_cents, currency,
  user_id, pickup_agency_id, dropoff_agency_id, car_category_code
)
VALUES
  ('dddddddd-0000-0000-0000-000000000001', '2025-01-10 10:00:00.000', '2025-01-12 10:00:00.000', 'CONFIRMED', 12999, 'EUR',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'aaaa0000-0000-0000-0000-000000000001', 'aaaa0000-0000-0000-0000-000000000002', 'VAN6'),
  ('dddddddd-0000-0000-0000-000000000002', '2025-02-05 09:00:00.000', '2025-02-08 09:00:00.000', 'CONFIRMED', 18999, 'EUR',
    'cccccccc-cccc-cccc-cccc-cccccccccccc', 'aaaa0000-0000-0000-0000-000000000002', 'aaaa0000-0000-0000-0000-000000000002', 'SED4');

INSERT IGNORE INTO support_threads (id, subject, status, created_by_user_id, reservation_id)
VALUES
  ('11111111-1111-1111-1111-111111111111', 'Delayed pickup', 'OPEN', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'dddddddd-0000-0000-0000-000000000001'),
  ('22222222-2222-2222-2222-222222222222', 'Billing question', 'OPEN', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'dddddddd-0000-0000-0000-000000000001'),
  ('33333333-3333-3333-3333-333333333333', 'Vehicle change request', 'OPEN', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'dddddddd-0000-0000-0000-000000000001'),
  ('44444444-4444-4444-4444-444444444444', 'Return time update', 'OPEN', 'cccccccc-cccc-cccc-cccc-cccccccccccc', 'dddddddd-0000-0000-0000-000000000002');

INSERT IGNORE INTO support_messages (id, sent_at, content, thread_id, sender_user_id)
VALUES
  ('44444444-4444-4444-4444-444444444444', DATE_SUB(NOW(3), INTERVAL 2 HOUR), 'Bonjour, ma prise en charge a ete retardee.', '11111111-1111-1111-1111-111111111111', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'),
  ('55555555-5555-5555-5555-555555555555', DATE_SUB(NOW(3), INTERVAL 90 MINUTE), 'Merci pour le signalement, nous verifions avec l agence.', '11111111-1111-1111-1111-111111111111', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'),
  ('66666666-6666-6666-6666-666666666666', DATE_SUB(NOW(3), INTERVAL 45 MINUTE), 'Une mise a jour ?', '11111111-1111-1111-1111-111111111111', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'),
  ('77777777-7777-7777-7777-777777777777', DATE_SUB(NOW(3), INTERVAL 3 HOUR), 'J ai une question sur ma facture.', '22222222-2222-2222-2222-222222222222', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'),
  ('88888888-8888-8888-8888-888888888888', DATE_SUB(NOW(3), INTERVAL 2 HOUR), 'Nous vous enverrons la facture detaillee par email.', '22222222-2222-2222-2222-222222222222', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'),
  ('99999999-9999-9999-9999-999999999999', DATE_SUB(NOW(3), INTERVAL 30 MINUTE), 'Puis-je changer la categorie du vehicule ?', '33333333-3333-3333-3333-333333333333', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
