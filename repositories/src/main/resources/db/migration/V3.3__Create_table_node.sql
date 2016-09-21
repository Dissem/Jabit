CREATE TABLE Node (
  stream   BIGINT     NOT NULL,
  address  BINARY(32) NOT NULL,
  port     INT        NOT NULL,
  services BIGINT     NOT NULL,
  time     BIGINT     NOT NULL,
  PRIMARY KEY (stream, address, port)
);
CREATE INDEX idx_time on Node(time);
