Jabit
=====
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/ch.dissem.jabit/jabit-core/badge.svg)](https://maven-badges.herokuapp.com/maven-central/ch.dissem.jabit/jabit-core)
[![Javadoc](https://javadoc-emblem.rhcloud.com/doc/ch.dissem.jabit/jabit-core/badge.svg)](http://www.javadoc.io/doc/ch.dissem.jabit/jabit-core)
[![Apache 2](https://img.shields.io/badge/license-Apache_2.0-blue.svg)](https://raw.githubusercontent.com/Dissem/Jabit/master/LICENSE)
[![Visit our IRC channel](https://img.shields.io/badge/irc-%23jabit-blue.svg)](https://kiwiirc.com/client/irc.freenode.net/#jabit)

A Java implementation for the Bitmessage protocol. To build, use command `./gradlew build`.

Please note that it still has its limitations, but the API should now be stable. Jabit uses Semantic Versioning, meaning as long as the major version doesn't change, nothing should break if you update.

Be aware though that this doesn't necessarily applies for SNAPSHOT builds and the development branch, notably when it comes to database updates. In other words, they may break your installation!_ 

#### Master
[![Build Status](https://travis-ci.org/Dissem/Jabit.svg?branch=master)](https://travis-ci.org/Dissem/Jabit) 
[![Code Quality](https://img.shields.io/codacy/e9938d2adbb74a0db553115bef692ff3/master.svg)](https://www.codacy.com/app/chrigu-meyer/Jabit/dashboard?bid=3144281)

#### Develop
[![Build Status](https://travis-ci.org/Dissem/Jabit.svg?branch=develop)](https://travis-ci.org/Dissem/Jabit?branch=develop) 
[![Code Quality](https://img.shields.io/codacy/e9938d2adbb74a0db553115bef692ff3/develop.svg)](https://www.codacy.com/app/chrigu-meyer/Jabit/dashboard?bid=3144279)

Upgrading
---------

Please be aware that Version 2.0.0 has some breaking changes, most notably in the repository implementations -- please take special care when upgrading them. If you don't implement your own repositories, you should be able to quickly find and fix any compilation errors caused by the few other breaking changes. 

There is also a new network handler which comes highly recommended. If you're having any network problems, please make sure you use `NioNetworkHandler` instead of the now deprecated `DefaultNetworkHandler`.

Security
--------

There are most probably some security issues, me programming this thing all by myself. Jabit doesn't do anything against timing attacks yet, for example. Please feel free to use the library, report bugs and maybe even help out. I hope the code is easy to understand and work with.

Project Status
--------------

Basically, everything needed for a working Bitmessage client is there:
* Creating new identities (private addresses)
* Adding contacts and subscriptions
* Receiving broadcasts
* Receiving messages
* Sending messages and broadcasts
* Managing outgoing and incoming connections
* Initialise and manage a registry of Bitmessage network nodes
* An easy to use API
* A command line demo application built using the API

Setup
-----

It is recommended to define the version like this:
```Gradle
ext.jabitVersion = '2.0.0'
```
Add Jabit as Gradle dependency:
```Gradle
compile "ch.dissem.jabit:jabit-core:$jabitVersion"
```
Unless you want to implement your own, also add the following:
```Gradle
compile "ch.dissem.jabit:jabit-networking:$jabitVersion"
compile "ch.dissem.jabit:jabit-repositories:$jabitVersion"
compile "ch.dissem.jabit:jabit-cryptography-bouncy:$jabitVersion"
```
And if you want to import from or export to the Wallet Import Format (used by PyBitmessage) you might also want to add:
```Gradle
compile "ch.dissem.jabit:jabit-wif:$jabitVersion"
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
        .powRepo(new JdbcProofOfWorkRepository(jdbcConfig))
        .nodeRegistry(new JdbcNodeRegistry(jdbcConfig))
        .networkHandler(new NioNetworkHandler())
        .cryptography(new BouncyCryptography())
        .listener(System.out::println)
        .build();
```
This creates a simple context using a H2 database that will be created in the user's home directory. In the listener you decide what happens when a message arrives. If you can't use lambdas, you may instead write
```Java
        .listener(new BitmessageContext.Listener() {
            @Override
            public void receive(Plaintext plaintext) {
                // TODO: Notify the user
            }
        })
```

Next you'll need to start the context:
```Java
ctx.startup()
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

### Housekeeping

As Bitmessage stores all currently valid messages, we'll need to delete expired objects from time to time:
```Java
ctx.cleanup();
```
If the client runs all the time, it might be a good idea to do this daily or at least weekly. Otherwise, you might just want to clean up on shutdown.

Also, if some messages weren't acknowledged when it expired, they can be resent:
```Java
ctx.resendUnacknowledgedMessages();
```
This could be triggered periodically, or manually by the user. Please be aware that _if_ there is a message to resend, proof of work needs to be calculated, so to not annoy your users you might not want to trigger it on shutdown. As the client might have been offline for some time, it might as well be wise to wait until it caught up downloading new messages before resending those messages, after all they might be acknowledged by now.

There probably won't happen extremely bad things if you don't - at least not more than otherwise - but you can properly shutdown the network connection by calling
```Java
ctx.shutdown();
```
