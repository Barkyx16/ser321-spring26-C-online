Activity 2: Auction Game Server (Protocol Buffers + Threading)
Overview

In this activity, I updated the auction server so it can handle multiple players connecting at the same time instead of only working with one client. I also switched the communication format from JSON to Protocol Buffers and followed the structure shown in PROTO_PROTOCOL.md.

Each player connects to the server, chooses a name, and plays an auction game against three bots. Every connection runs its own game, so different players can play at the same time without affecting each other.

At the start of the game, the player gets 150 gold and bids on five items. After the last item, the server calculates the final score and adds it to the leaderboard.

How to build the project

Run this in the project folder:

./gradlew build -x test

The tests are skipped because they expect the server to already be running.

How to run the server

To start the server normally:

./gradlew runServer

To run the server in grading mode:

./gradlew runServerGrading

Grading mode uses fixed item values so results stay the same each time.

How to run the client

Open another terminal and run:

./gradlew runClient

You can open multiple clients at once to test several players connecting at the same time.

Requests the server supports

The server handles these commands:

REGISTER – sets the player name
JOIN – starts a new game
BID – places a bid on the current item
LEADERBOARD – shows the top scores
QUIT – disconnects from the server

After the last item is finished, the server automatically sends the final results.

Threading

The server uses a fixed thread pool so more than one player can connect at once. Each connection runs separately, which lets different players play their own games without waiting on each other.

Player names are stored in a synchronized set so two people can’t register the same name at the same time.

Game flow

Each player plays against three bots with random names. The server loads five items and sends them one at a time during the game.

For each round:

the player enters a bid
the bots generate their bids
the highest bid wins
ties are decided alphabetically
bids must meet the reserve price or the item stays unsold

If the player wins the item, the cost is removed from their gold and the item is added to their inventory.

After the last round, the server adds together the remaining gold and the value of the items the player collected to get the final score.

Leaderboard

Scores are saved in scores.txt, so they stay there even after restarting the server.

The leaderboard can be viewed anytime by sending a LEADERBOARD request from the client.

Design choices

I used a thread pool so the server could handle multiple connections without creating too many threads. Each player has their own game state, which keeps everything separate while the server is running.

Player names are removed from the active list when someone disconnects so the name can be used again later.

Known limitations

Each player runs their own game against bots instead of playing in the same shared auction with other players. This matches how the assignment is set up.
