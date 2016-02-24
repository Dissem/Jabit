Jabit
=====
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/ch.dissem.jabit/jabit-core/badge.svg)](https://maven-badges.herokuapp.com/maven-central/ch.dissem.jabit/jabit-core)
[![Javadoc](https://javadoc-emblem.rhcloud.com/doc/ch.dissem.jabit/jabit-core/badge.svg)](http://www.javadoc.io/doc/ch.dissem.jabit/jabit-core)
[![GitHub license](https://img.shields.io/github/license/Dissem/Jabit.svg)](https://raw.githubusercontent.com/Dissem/Jabit/master/LICENSE)
[![Visit our IRC channel](https://img.shields.io/badge/irc-%23jabit-blue.svg)](https://kiwiirc.com/client/irc.freenode.net/#jabit)

A Java implementation for the Bitmessage protocol. To build, use command `gradle build` or `./gradlew build`.

There are still some features missing, most notably acknowledgements. The API however should be stable, and it should work well enough for most use cases.

#### Master
[![Build Status](https://travis-ci.org/Dissem/Jabit.svg?branch=master)](https://travis-ci.org/Dissem/Jabit) [![codecov.io](https://codecov.io/github/Dissem/Jabit/coverage.svg?branch=master)](https://codecov.io/github/Dissem/Jabit?branch=master)

#### Develop
[![Build Status](https://travis-ci.org/Dissem/Jabit.svg?branch=develop)](https://travis-ci.org/Dissem/Jabit?branch=develop) [![codecov.io](https://codecov.io/github/Dissem/Jabit/coverage.svg?branch=develop)](https://codecov.io/github/Dissem/Jabit?branch=develop)

Security
--------

There are most probably some security issues, me programming this thing all by myself. Jabit doesn't do anything against timing attacks yet, for example. Please feel free to use the library, report bugs and maybe even help out. I hope the code is easy to understand and work with.

Project Status
--------------

Basically, everything needed for a working Bitmessage client is there:
* Creating new identities (private addresses)
* Adding contracts and subscriptions
* Receiving broadcasts
* Receiving messages
* Sending messages and broadcasts
* Managing outgoing and incoming connections
* Initialise and manage a registry of Bitmessage network nodes
* An easy to use API
* A command line demo application built using the API

Setup
-----

Add Jabit as Gradle dependency:
```Gradle
compile 'ch.dissem.jabit:jabit-core:1.0.0'
```
Unless you want to implement your own, also add the following:
```Gradle
compile 'ch.dissem.jabit:jabit-networking:1.0.0'
compile 'ch.dissem.jabit:jabit-repositories:1.0.0'
compile 'ch.dissem.jabit:jabit-cryptography-bouncy:1.0.0'
```
And if you want to import from or export to the Wallet Import Format (used by PyBitmessage) you might also want to add:
```Gradle
compile 'ch.dissem.jabit:jabit-wif:1.0.0'
```

For Android clients use `jabit-cryptography-spongy` instead of `jabit-cryptography-bouncy`.

Usage
-----

First, you'll need to create a `BitmessageContext`:
```Java
JdbcConfig jdbcConfig = new JdbcConfig();
BitmessageContext ctx = new BitmessageContext.Builder()
        .addressRepo(new JdbcAddressRepository(jdbcConfig))
        .inventory(new JdbcInventory(jdbcConfig))
        .messageRepo(new JdbcMessageRepository(jdbcConfig))
        .nodeRegistry(new MemoryNodeRegistry())
        .networkHandler(new NetworkNode())
        .cryptography(new BouncyCryptography())
        .build();
```
This creates a simple context using a H2 database that will be created in the user's home directory. Next you'll need to
start the context and decide what happens if a message arrives:
```Java
ctx.startup(new BitmessageContext.Listener() {
    @Override
    public void receive(Plaintext plaintext) {
        // TODO: Notify the user
    }
});
```
Then you might want to create an identity
```Java
BitmessageAddress identity = ctx.createIdentity(false, Pubkey.Feature.DOES_ACK);
```
or add some contacts
```Java
BitmessageAddress contact = new BitmessageAddress("BM-2cTarrmjMdRicKZ4qQ8A13JhoR3Uq6Zh5j");
address.setAlias("Chris");
ctx.addContact(contact);
```
to which you can send some messages
```Java
ctx.send(identity, contact, "Test", "Hello Chris, this is a message.");
```
