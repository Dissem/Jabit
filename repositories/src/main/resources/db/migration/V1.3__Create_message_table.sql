CREATE TABLE Message (
  "id"                     BIGINT        AUTO_INCREMENT PRIMARY KEY,
  "from"                   VARCHAR(40)   NOT NULL,
  "to"                     VARCHAR(40)   NOT NULL,
  "data"                   BLOB          NOT NULL,
  "sent"                   BIGINT,
  "received"               BIGINT,
  "status"                 VARCHAR(20)   NOT NULL
);

CREATE TABLE Label (
  "id"    BIGINT AUTO_INCREMENT PRIMARY KEY,
  "label" VARCHAR(255) NOT NULL,
  "color" INT,
  "order" BIGINT,
  CONSTRAINT UC_label UNIQUE ("label"),
  CONSTRAINT UC_order UNIQUE ("order")
);

CREATE TABLE Message_Label (
  "message_id" BIGINT NOT NULL,
  "label_id"   BIGINT NOT NULL,

  PRIMARY KEY ("message_id", "label_id"),
  FOREIGN KEY ("message_id") REFERENCES Message ("id"),
  FOREIGN KEY ("label_id") REFERENCES Label ("id")
);

INSERT INTO Label("label", "order") VALUES ('Inbox', 0);
INSERT INTO Label("label", "order") VALUES ('Sent', 10);
INSERT INTO Label("label", "order") VALUES ('Drafts', 20);
INSERT INTO Label("label", "order") VALUES ('Trash', 100);
