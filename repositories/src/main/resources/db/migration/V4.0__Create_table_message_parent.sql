ALTER TABLE Message ADD COLUMN conversation UUID NOT NULL DEFAULT RANDOM_UUID();

CREATE TABLE Message_Parent (
    parent       BINARY(64) NOT NULL,
    child        BINARY(64) NOT NULL,
    pos          INT NOT NULL,
    conversation UUID,

    PRIMARY KEY (parent, child),
    FOREIGN KEY (child) REFERENCES Message (iv)
);
