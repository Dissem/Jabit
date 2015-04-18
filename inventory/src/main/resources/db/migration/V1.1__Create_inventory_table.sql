CREATE TABLE Inventory (
  hash    BINARY(32) NOT NULL PRIMARY KEY,
  stream  BIGINT     NOT NULL,
  expires BIGINT     NOT NULL,
  data    BLOB       NOT NULL,
  type    BIGINT     NOT NULL,
  version INT        NOT NULL
);