---
layout: page
title:  "Dependencies"
permalink: "/dependencies"
order: 10
categories: dependencies
---

`jabit-core` contains the Bitmessage context and all entities that will be used by the other modules.
Whatever you do, you'll need this.

`jabit-networking` manages connections to the Bitmessage network. This is probably the most complicated
part of the Jabit project. TODO

`jabit-repositories` is where the entities from core are stored. The default implementation uses JDBC
to access a H2 database. It should mostly be easy to use other SQL databases (I'll happily accept pull
requests) but if you want to use some NoSQL database or a very restricted one (as with SQLite) you might
want to create your own implementation.

`jabit-cryptography-bouncy` impmlements everything related to encryption, cryptographic hashes and
secure random numbers. As the name suggests, it uses the [Bouncycastle](https://www.bouncycastle.org/)
library.

`jabit-cryptography-spongy` is basically a copy of the spongy one, but using
[Spongycastle](https://rtyley.github.io/spongycastle/) instead.




TODO

Add Jabit as Gradle dependency:

{% highlight groovy %}
compile 'ch.dissem.jabit:jabit-core:1.0.0'
{% endhighlight %}

Unless you want to implement your own, also add the following:

{% highlight groovy %}
compile 'ch.dissem.jabit:jabit-networking:1.0.0'
compile 'ch.dissem.jabit:jabit-repositories:1.0.0'
compile 'ch.dissem.jabit:jabit-cryptography-bouncy:1.0.0'
{% endhighlight %}

And if you want to import from or export to the Wallet Import Format (used by PyBitmessage) you might also want to add:

{% highlight groovy %}
compile 'ch.dissem.jabit:jabit-wif:1.0.0'
{% endhighlight %}

For Android clients use `jabit-cryptography-spongy` instead of `jabit-cryptography-bouncy`.
