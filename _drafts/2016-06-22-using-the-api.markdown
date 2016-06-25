---
layout: post
title:  "Using the Jabit API"
date:   2016-06-22 00:02:00 +0200
categories: setup
---

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
