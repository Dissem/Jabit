CREATE TABLE Node (
  "ip"       BINARY(16) NOT NULL,
  "port"     INT        NOT NULL,
  "stream"   BIGINT     NOT NULL,
  "services" BIGINT     NOT NULL,
  "time"     BIGINT     NOT NULL,

  PRIMARY KEY ("ip", "port", "stream")
);