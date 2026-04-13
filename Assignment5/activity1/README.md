Assignment 5 – Distributed Consensus System
Architecture

This system has one leader and multiple workers connected with sockets. The leader waits until at least three workers join before starting rounds. When a task is entered, the leader sends it to all workers at the same time and collects their replies using threads.

Each worker receives the task, the user does the berechnung manually, sends the answer back, and then waits for the final decision from the leader.

Protocol

Messages are simple text lines separated with |.

Main messages used:

HELLO|WorkerName
TASK|round|expression
RESULT|round|WorkerName|value
DECISION|round|status|value|agreeCount|totalWorkers|summary

Each round works like this:

leader sends task
workers type answers
leader collects replies
leader counts votes
leader announces result

Testing

Run inside the activity1 folder:

gradle build
gradle runLeader --args="9000"

Start workers in other terminals:

gradle runWorker --args="Worker1 localhost 9000"
gradle runWorker --args="Worker2 localhost 9000"
gradle runWorker --args="Worker3 localhost 9000"

Tested normal agreement, wrong answers, ties, missing replies, and disconnects.

One issue I ran into was the leader waiting too long when a worker didn’t answer. I fixed this by adding a timeout so the round still finishes.

Consensus Algorithm

The leader counts how many workers return each result.

If one value reaches at least 50 percent agreement, that becomes the final answer.

If no value reaches that level, the system prints “No consensus reached”.

If the top results are tied, the round also ends with no consensus.

Workers that do not respond in time are skipped for that round.

Failure Handling

If a worker does not reply, the leader continues with the remaining responses.

If a worker disconnects, it is removed from future rounds.

The system keeps running as long as enough workers stay connected.

Edge Cases and Limitations

Workers can enter incorrect values and the system will still accept them.

The leader is chosen at startup and does not change.

Workers joining late start participating in the next round.