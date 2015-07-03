CREATE TABLE Address (
  address                VARCHAR(40)   NOT NULL PRIMARY KEY,
  version                BIGINT        NOT NULL,
  alias                  VARCHAR(255),
  public_key             BLOB,
  private_key            BLOB,
  subscribed             BIT DEFAULT '0'
);