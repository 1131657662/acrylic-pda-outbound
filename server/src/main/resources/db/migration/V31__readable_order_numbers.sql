SET SESSION lock_wait_timeout = 10;
CREATE TABLE order_number_counter (
    business_date DATE PRIMARY KEY,
    last_sequence INT NOT NULL,
    CHECK(last_sequence >= 0)
) ENGINE=InnoDB;
