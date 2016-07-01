---
layout: page
title:  "API"
permalink: "/api"
order: 200
categories: api
---
> See [Jabit's JavaDoc](http://www.javadoc.io/doc/ch.dissem.jabit/jabit-core/1.0.1) for a detailed documentation of all classes and methods. Feel free to either open an issue or create a pull request if you find something to be unclear or incorrect.
{: .info}

# Context Setup
The `BitmessageContext` provides all methods a usual Bitmessage client needs. Note that all its methods should be thread safe, but you'll need to check your `BitmessageContext.Listener` for thread safety. The listener may even be called simultaneously from different threads.

To keep the library small, flexible and independent from (but compatible to) heavy-duty frameworks such as Spring, the `BitmessageContext` needs to be initialized with all its dependencies.

# Creating Identities

{% highlight java %}
BitmessageAddress identity = ctx.createIdentity(false, Pubkey.Feature.DOES_ACK);
{% endhighlight %}

If _shorter_ is enabled, Jabit searches a private key that yields a slightly shorter address. This is not optimized and may take a long time to complete.

# Importing Identities
Identities can be imported from PyBitmessages keys.dat by using the WifImporter from the library `jabit-wif`. The constructor needs a `BitmessageContext` and either a file, a string representation of a keys.dat or an input stream. The importer will throw an exception if the data can't be parsed.

Once constructed you can choose to list the contained identities, import all, a subset or a specific identity into the given `BitmessageContext`.

# Exporting Identities
Exports work very similar to imports. A `WifExporter` also needs a `BitmessageContext` to construct. You then can add either all or specific identities to the importer. Id you provide specific identities, make sure they have a private key. They will not be reloaded.

If you're done adding identities, call one of the `write` methods to save the exported identities, or `toString()` for the string representation.

# Threads
> TODO
