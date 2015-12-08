CREATE TABLE POW (
  initial_hash       BINARY(64)    PRIMARY KEY,
  data               BLOB          NOT NULL,
  version            BIGINT        NOT NULL
);
