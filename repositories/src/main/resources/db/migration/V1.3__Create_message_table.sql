CREATE TABLE Message (
  id                     BIGINT        AUTO_INCREMENT PRIMARY KEY,
  sender                 VARCHAR(40)   NOT NULL,
  recipient              VARCHAR(40)   NOT NULL,
  data                   BLOB          NOT NULL,
  sent                   BIGINT,
  received               BIGINT,
  status                 VARCHAR(20)   NOT NULL,

  FOREIGN KEY (sender)    REFERENCES Address (address),
  FOREIGN KEY (recipient) REFERENCES Address (address)
);

CREATE TABLE Label (
  id    BIGINT AUTO_INCREMENT PRIMARY KEY,
  label VARCHAR(255) NOT NULL,
  color INT,
  ord   BIGINT,
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

INSERT INTO Label(label, ord) VALUES ('Inbox', 0);
INSERT INTO Label(label, ord) VALUES ('Sent', 10);
INSERT INTO Label(label, ord) VALUES ('Drafts', 20);
INSERT INTO Label(label, ord) VALUES ('Trash', 100);
