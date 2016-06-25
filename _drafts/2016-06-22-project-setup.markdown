---
layout: post
title:  "Setting up Jabit for Your Project"
date:   2016-06-22 00:01:00 +0200
categories: setup
---

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

