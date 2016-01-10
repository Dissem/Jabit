CREATE TABLE POW (
  initial_hash          BINARY(64)    PRIMARY KEY,
  data                  BLOB          NOT NULL,
  version               BIGINT        NOT NULL,
  nonce_trials_per_byte BIGINT        NOT NULL,
  extra_bytes           BIGINT        NOT NULL
);
