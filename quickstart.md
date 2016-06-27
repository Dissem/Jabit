---
layout: page
title:  "Quick Start"
permalink: "/quickstart"
order: 1
categories: quick start
---

### Project Setup

> As Jabit uses Gradle, it is also used for this documentation. If you're used to Maven
> it should be simple enough to deduce what you'll need to put into your `pom.xml`.
{: .info}

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


### Usage

First, you'll need to create a `BitmessageContext`:

{% highlight java %}
JdbcConfig jdbcConfig = new JdbcConfig();
BitmessageContext ctx = new BitmessageContext.Builder()
        .addressRepo(new JdbcAddressRepository(jdbcConfig))
        .inventory(new JdbcInventory(jdbcConfig))
        .messageRepo(new JdbcMessageRepository(jdbcConfig))
        .nodeRegistry(new MemoryNodeRegistry())
        .networkHandler(new NetworkNode())
        .cryptography(new BouncyCryptography())
        .build();
{% endhighlight %}

This creates a simple context using a H2 database that will be created in the user's home directory. Next you'll need to
start the context and decide what happens if a message arrives:

{% highlight java %}
ctx.startup(new BitmessageContext.Listener() {
    @Override
    public void receive(Plaintext plaintext) {
        // TODO: Notify the user
    }
});
{% endhighlight %}

Then you might want to create an identity

{% highlight java %}
BitmessageAddress identity = ctx.createIdentity(false, Pubkey.Feature.DOES_ACK);
{% endhighlight %}

or add some contacts

{% highlight java %}
BitmessageAddress contact = new BitmessageAddress("BM-2cTarrmjMdRicKZ4qQ8A13JhoR3Uq6Zh5j");
address.setAlias("Chris");
ctx.addContact(contact);
{% endhighlight %}

to which you can send some messages

{% highlight java %}
ctx.send(identity, contact, "Test", "Hello Chris, this is a message.");
{% endhighlight %}

