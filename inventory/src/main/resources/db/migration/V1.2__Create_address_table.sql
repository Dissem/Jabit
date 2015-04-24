CREATE TABLE Address (
  address                VARCHAR(40)   NOT NULL PRIMARY KEY,
  alias                  VARCHAR(255),
  public_key             BLOB,
  private_key            BLOB
);