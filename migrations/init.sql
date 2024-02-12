create DATABASE counterdb;
create TABLE if NOT EXISTS increment_result(
  -- mostly ignored.
  id BIGSERIAL PRIMARY KEY,
  key varchar(64) unique,
  value bigint,
  created_at timestamp default(now()),
  last_updated_at timestamp default(now())
);