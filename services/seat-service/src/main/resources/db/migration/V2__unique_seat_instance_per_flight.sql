-- One SeatInstance per physical seat per flight instance.
-- createInventory() skips work when inventory already exists, but that check alone cannot stop two concurrent
-- runs (a Kafka redelivery during a consumer rebalance, or the admin endpoint racing the event). With this key the
-- second insert fails, the Kafka error handler retries, and the retry sees the inventory and skips.
-- A database that already holds duplicates must be cleaned before this migration can apply.
alter table seat_instance
    add constraint uk_seat_instance_flight_seat unique (flight_instance_id, seat_id);
