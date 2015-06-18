Jabit [![Build Status](https://travis-ci.org/Dissem/Jabit.svg?branch=master)](https://travis-ci.org/Dissem/Jabit)
=====

A Java implementation for the Bitmessage protocol. To build, use command `gradle build`. Note that for some tests to run, a standard Bitmessage client needs to run on the same system, using port 8444 (the default port).

Please note that development is still heavily in progress, and I will break the database a lot until it's ready for prime time.

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
