Hi!

You're looking at the performance regression test framework for
membase. Currently it is only testing the core functionality of
memcached and ep-engine, but it should be extended to spin up a
complete membase cluster and run a load towards the cluster.

To run the test, simply execute:
mvn clean install && ./run.sh

Cheers,

Trond Norbye
Melhus, Norway
