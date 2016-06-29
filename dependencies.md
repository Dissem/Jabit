---
layout: page
title:  "Dependencies"
permalink: "/dependencies"
order: 100
categories: dependencies
---

`jabit-core` contains the Bitmessage context and all entities that will be used by the other modules.
Whatever you do, you'll always need this.

`jabit-networking` manages connections to the Bitmessage network. This is probably the most complicated
part of the Jabit project. TODO

`jabit-repositories` is where the entities from core are stored. The default implementation uses JDBC
to access a H2 database. It should mostly be easy to use other SQL databases (I'll happily accept pull
requests) but if you want to use some NoSQL database or a very restricted one (as with SQLite) you might
want to create your own implementation.

`jabit-cryptography-bouncy` impmlements everything related to encryption, cryptographic hashes and
secure random numbers. As the name suggests, it uses the [Bouncycastle][bouncycastle] library.

`jabit-cryptography-spongy` is basically a copy of the spongy one, but using [Spongycastle][spongycastle]
instead. Go there and look at the first section "why?" for the messed up reason this module exists.

`jabit-wif` lets you import from and export to the Wallet Import Format, which is used by PyBitmessage to store its identities.

`jabit-extensions` you won't usually need, it contains a few helper classes for if you want to build protocol extensions.

[bouncycastle]: https://www.bouncycastle.org/
[spongycastle]: https://rtyley.github.io/spongycastle/