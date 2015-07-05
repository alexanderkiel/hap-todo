__This software is ALPHA.__

# HAP ToDo

[![Build Status](https://travis-ci.org/alexanderkiel/hap-todo.svg?branch=master)](https://travis-ci.org/alexanderkiel/hap-todo)

Example ToDo Service demonstrating the [Hypermedia Application Protocol][2].

## Hosted HAP ToDo

A hosted version of HAP ToDo is available [here][3]. It's open to everyone and
has only a volatile in-memory database. So don't expect it to hold your example
data. The best way to try out HAP ToDo is by using the [HAP Browser][4] and
entering `hap-todo.alexanderkiel.net` in the address bar.

## Usage with Leiningen
         
To start the service with leiningen, run the following command

    lein with-profile production trampoline run

## Usage on Heroku Compatible PaaS

This application uses the following environment vars:

* `PORT` - the port to listen on

If you have [foreman][1] installed you can create an `.env` file listing the
environment vars specified above and just type `foreman start`.

## Docker Image

There is a docker image of HAP ToDo available on [Docker Hub][5].

## Development

You can start a REPL with `lein repl`. After it is up, go into the user
namespace which is located in under `dev`. There is a comment block
"Init Development". Just invoke the function:

```clojure
(startup)
```
    
You should be able to see the ToDo service running at port 8080. If you like to 
specify a different port, you can do it in the `.lein-env` file like so

```clojure
{:port "5004"}
```

## License

Copyright © 2015 Alexander Kiel

Distributed under the Eclipse Public License, the same as Clojure.

[1]: <https://github.com/ddollar/foreman>
[2]: <https://github.com/alexanderkiel/hap-spec>
[3]: <http://hap-todo.alexanderkiel.net>
[4]: <http://hap-browser.alexanderkiel.net>
[5]: <https://registry.hub.docker.com/u/akiel/hap-todo/>
