# MQJavaPerfHarness

Uses MQ Java APIs (not JMS) to send and receive messages to applications via MQ. Connects to a queue manager
using client bindings or local bindings (standard IPC or trusted), with the `-jt` parameter controlling the
connection type as one of `mqc` (the default), `mqb`, or `mqbf`. 

![Overview](ph-2.png)

Key paraneters for connections:

- `-jb` Queue Manager Name (used for all types)
- `-jp` MQ Listener Port (client only)
- `-jc` Queue Manager Channel name (client only)
- `-jh` Endpoint Hostname (client only)

## Commands

Several different commands exist for this implementation, and can be used for different use cases.

### Sender

Sends messages at a fixed rate on a queue. Useful for load testing but does not check response times.

### Receiver

Receives messages from a queue. Useful as a load testing "sink" but does not check response times.

### Requestor

Multi-threaded with each thread repeatedly sending a message and waiting for a response. Useful for
checking request-reply scenarios but will not maintain a fixed rate of sending if the service is 
responding slowly because a request is only sent once a response is received.

### RequestorAsync

Similar to Requestor but uses separate threads for receiving messages and therefore able to send
at a fixed rate (and handles timeouts asynchronously also). Useful for simulating a fixed stream
of messages coming in from large numbers of independent clients, where the messages will keep 
arriving even if the service is running slowly.

See the comments in [RequestorAsync.java](/MQJavaPerfHarness/src/com/ibm/uk/hursley/perfharness/mqjava/RequestorAsync.java) 
for details on how the command works.

Example command as follows:
```
java JMSPerfHarness -tc mqjava.RequestorAsync -nt 10 -ss 1 -sc AsyncResponseTimeStats -wi 5 -rl 60 \
  -mf somefile -jb ACEv12_QM -iq ACE.INPUT.QUEUE -oq ACE.REPLY.QUEUE  -jt mqb -rt 100 -tx true
```
where the parameters are as follows
- `-nt 10` Number of threads to run
- `-ss 1` Statistics pritning interval (seconds)
- `-sc AsyncResponseTimeStats` Stats collector class name
- `-wi 5` Wait between starting threads (ms)
- `-rl 60` Run time (seconds)
- `-mf somefile` Message file name
- `-jb ACEv12_QM` Queue Manager Name 
- `-iq ACE.INPUT.QUEUE` Queue to which messages are sent
- `-oq ACE.REPLY.QUEUE` Queue from which messages are received
- `-jt mqb` MQ Connection type
- `-rt 100` Rate at which each thread should send messages (messages per second)
- `-tx true` Transactional operation


### Responder

Multi-threaded responder that receives messages on a queue and replies to them. Useful as a 
partner to Requestor and RequestorAsync for channel testing in MQ, or for client applications that
need a dummy responder. The command exits if any MQGET times out, so a large `-to` value may
be helpful for some situations.

Example command as follows:
```
java JMSPerfHarness -tc mqjava.Responder -nt 20 -ss 1 -sc BasicStats -wi 10 -to 3000 -rl 3600 \
   -sh false -ws 1 -jb ACEv12_QM -iq ACE.INPUT.QUEUE -oq ACE.REPLY.QUEUE -jt mqb -co true
```

where the parameters are as follows
- `-nt 20` Number of threads to run
- `-ss 1` Statistics pritning interval (seconds)
- `-sc BasicStats` Stats collector class name
- `-wi 10` Wait between starting threads (ms)
- `-to 3000` MQGET timeout (seconds)
- `-rl 3600` Run time (seconds)
- `-sh false` Listenr for SIGINT as a termination signal
- `-jb ACEv12_QM` Queue Manager Name 
- `-iq ACE.INPUT.QUEUE` Queue from which messages are received
- `-oq ACE.REPLY.QUEUE` Queue to which messages are sent
- `-jt mqb` MQ Connection type
- `-co true` Honor messageID-to-correlID pattern
