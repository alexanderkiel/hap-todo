# HAP ToDo Walkthrough

Every API conforming to the Hypermedia Application Protocol has a service 
document. The service document is the entry point in an API and the only thing
which has to be shared with clients. All other resources of an API are 
discoverable through links.

The service document of HAP Todo looks like this:

```json
{"~:data": 
 {"~:name": "HAP ToDo",
  "~:version": "0.1-SNAPSHOT"},
 "~:links":
 {"~:self": {"~:href": "~r/"},
  "~:todo/items": {"~:href": "~r/items"}},
 "~:forms":
 {"~:todo/create-item":
  {"~:href": "~r/items",
   "~:label": "Create Item",
   "~:params":
   {"~:label":
    {"~:type": "~SStr",
     "~:label": "The label of the ToDo item (what should be done)."}}}}}
```

The above JSON document is encoded using [JSON-Verbose Transit][1]. Transit 
supports also a more compact JSON and a binary MsgPack encoding. This document
will always use the JSON-Verbose encoding because it's the most human readable
of all three formats.

Transit supports a rich set of data types which are encoded in JSON strings by
tag prefixes. The first tag in the service document is `:` the colon tag. It's
used in `"~:data"` making it a keyword instead of a string. Keywords are most
often used as map keys. The tilde declares a string to start with a tag. This 
document uses a more concise representation of keywords in text were `"~:data"`
becomes just `:data`.

Every HAP representation is a map. The first HAP key `:data` holds a map of 
application-specific data. In the above case the service document conveys the
name and version of the ToDo service.

The second HAP key `:links` holds a map of link relations to link maps.
Every HAP representation has a link with link relation `:self`. You can read 
more about standard link relations at [IANA][2]. Link maps have at least a 
`:href` key. In the above service document the `:href` points to `/` a relative
URI with has to be resolved to an absolute URI using the 
[effective request URI][3]. URI's are encoded using the `r` tag.

The second link points to the list of ToDo items. It's link relation 
`:todo/items` contains a namespace. Keywords can be prefixed with a namespace 
were the namespace and the name are separated by a slash. You can read more
about keywords in the [HAP Spec][4]. Client can follow the URI encoded in the
`:href` to receive the list of all ToDo items. The client should not remember 
the URI `/items` because the server is always free to change it every time.
The client should remember or have hard-coded the link relation `:todo/items`
instead.

Alongside links there can be also forms in a HAP representation. Think of forms
like the same thing in HTML. Forms convey information about how to create a new
resource. The information should be sufficient for a developer to write a 
client, so that no additional documentation is needed. Forms make HAP API's 
self-documented. You can read more about forms in the [HAP Spec][5]. Forms are 
also keyed by relations.

The form in the above service document describes how to create a new ToDo item.
It's keyd under the `:todo/create-item` relation. The `:params` key in the form 
map holds a map which describes each parameter of the form. The item creation 
form has one parameter, the label of the ToDo item. Params itself are maps again 
providing information about the data type and other human related things. 

The data type encoded in the value of the `:type` key describes how to encode 
the param value while posting it to the server. Although the server is free to 
accept param values as strings and convert them accordingly, in HAP values 
should be encoded by there data type over the wire as often as possible.

In the item creation form, the data type of the label is a string encoded as
`"~SStr"`. The string `"~SStr"` has the Transit tag `S` which is defined by the
[Transit Schema][6] lib. It marks a string to convey a [Prismatic Schema][7]
value. Schemas are a way to express the shape of a value which can be also
non-scalar. In the simple case of ToDo item labels, it's just a string but more
complex schemas are possible.

If you access the ToDo API using the [HAP Browser][8], it can render a human
editable form.

TODO: CONTINUE

[1]: <https://github.com/cognitect/transit-format>
[2]: <http://www.iana.org/assignments/link-relations/link-relations.xhtml>
[3]: <https://tools.ietf.org/html/rfc7230#section-5.5>
[4]: <https://github.com/alexanderkiel/hap-spec#keywords>
[5]: <https://github.com/alexanderkiel/hap-spec#forms>
[6]: <https://github.com/alexanderkiel/transit-schema>
[7]: <https://github.com/Prismatic/schema>
[8]: <http://hap-browser.alexanderkiel.net>
