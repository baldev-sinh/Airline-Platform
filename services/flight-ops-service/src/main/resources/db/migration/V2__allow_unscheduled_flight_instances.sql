-- A flight instance can be created on its own (POST /api/flight-instances without scheduleId), e.g. for an
-- extra section or a charter. The baseline wrongly required a schedule.
alter table flight_instance modify schedule_id bigint null;
