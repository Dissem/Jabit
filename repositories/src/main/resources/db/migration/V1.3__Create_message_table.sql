CREATE TABLE Message (
  id                      BIGINT        AUTO_INCREMENT PRIMARY KEY,
  iv                      BINARY(32)    UNIQUE,
  type                    VARCHAR(20)   NOT NULL,
  sender                  VARCHAR(40)   NOT NULL,
  recipient               VARCHAR(40),
  data                    BLOB          NOT NULL,
  sent                    BIGINT,
  received                BIGINT,
  status                  VARCHAR(20)   NOT NULL,

  FOREIGN KEY (sender)    REFERENCES Address (address),
  FOREIGN KEY (recipient) REFERENCES Address (address)
);

CREATE TABLE Label (
  id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
  label                   VARCHAR(255)  NOT NULL,
  type                    VARCHAR(20),
  color                   INT NOT NULL DEFAULT X'FF000000',
  ord                     BIGINT,

  CONSTRAINT UC_label UNIQUE (label),
  CONSTRAINT UC_order UNIQUE (ord)
);

CREATE TABLE Message_Label (
  message_id BIGINT NOT NULL,
  label_id   BIGINT NOT NULL,

  PRIMARY KEY (message_id, label_id),
  FOREIGN KEY (message_id) REFERENCES Message (id),
  FOREIGN KEY (label_id)   REFERENCES Label (id)
);

INSERT INTO Label(label, type, color, ord) VALUES ('Inbox', 'INBOX', X'FF0000FF', 0);
INSERT INTO Label(label, type, color, ord) VALUES ('Drafts', 'DRAFTS', X'FFFF9900', 10);
INSERT INTO Label(label, type, color, ord) VALUES ('Sent', 'SENT', X'FFFFFF00', 20);
INSERT INTO Label(label, type, ord) VALUES ('Unread', 'UNREAD', 90);
INSERT INTO Label(label, type, ord) VALUES ('Trash', 'TRASH', 100);
