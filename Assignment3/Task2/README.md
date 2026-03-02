# Assignment 3 Task 2: Hangman Game Protocol

**Author:** Alex Rafalski
**Date:** 3/2/26

---

## How to Run

The project uses Gradle. You can run it either with `gradle` or `./gradlew`.

### Server

Default:
gradle Server

Specify port:
gradle Server -Pport=8888

### Client

Default (clean console output):
gradle Client --console=plain -q

Specify host/port:
gradle Client -Phost=localhost -Pport=8888

---

## Video Demonstration

**Link:** https://youtu.be/4cptMHu4qac

The video shows:

* Starting the server and client
* Playing a full round of Hangman
* Guessing letters and words
* Using hint
* Giving up
* Viewing leaderboard
* Handling invalid inputs

---

## Implemented Features Checklist

### Core Features (Required)

* [x] Set Player Name
* [x] Start New Game
* [x] Guess Letter
* [x] Game State
* [x] Win/Lose Detection
* [x] Graceful Quit

### Medium Features (Enhanced Gameplay)

* [x] Hint feature
* [x] Word Guessing
* [x] Guessed Letters Command
* [x] Give Up

### Advanced Features (Competition)

* [x] Scoring System
* [x] Leaderboard

---

## Protocol Specification

### Overview

The game uses a simple request-response protocol over a TCP socket.
The client sends JSON messages describing the action the player wants to perform, and the server responds with JSON containing the result and updated game state.

Every message includes a "type" field that tells the server what operation to perform.
The server always replies with:

* ok = true for success
* ok = false for errors

The server is responsible for all game logic (word selection, scoring, validation).
The client only displays results and collects user input.

---

### 1. Set Player Name

Request
{ "type": "name", "name": "Alex" }

Success Response
{ "type": "name", "ok": true, "message": "Welcome, Alex!" }

Error Response
{ "ok": false, "message": "Name cannot be empty" }

---

### 2. Start New Game

Request
{ "type": "start" }

Success Response (example)
{
"ok": true,
"type": "start",
"message": "New game started!",
"display": "_ _ _ _ _ _",
"stage": "STAGE0",
"misses": 0,
"maxMisses": 6,
"points": 0,
"inGame": true,
"gameOver": false
}

The server also prints the secret word in the server console for debugging.

Possible Errors
{ "ok": false, "message": "Set your name first." }

---

### Guess Letter or Word

Request
{ "type": "guess", "guess": "e" }
or
{ "type": "guess", "guess": "planet" }

Behavior

* Correct letter: +5 points per occurrence
* Wrong letter: −1 point
* Wrong word guess: +2 misses, −2 points
* Correct word: game ends (win)

---

### Game State

{ "type": "state" }
Returns the current board, stage, misses, and points.

### Guessed Letters

{ "type": "guessed" }
Returns a list of letters already used.

### Hint

{ "type": "hint" }
Reveals one hidden letter and deducts 8 points.

### Give Up

{ "type": "giveup" }
Ends the game and shows the solution. The match is not recorded in the leaderboard.

### Leaderboard

{ "type": "leaderboard" }
Returns a sorted list of player scores.

### Quit

{ "type": "quit" }
Closes the connection gracefully.

---

## Error Handling Strategy

Server-side validation

* Checks required fields exist in every request
* Rejects empty names
* Rejects duplicate guessed letters
* Prevents guessing when no game is active

Missing fields → returns ok:false with a message
Invalid input → returns descriptive error message
Invalid game state → informs client instead of crashing

---

## Robustness

Server robustness
The server never trusts the client. Every request is validated before processing, so invalid input does not crash the program.

Client robustness
The client handles null responses safely, prints readable error messages, and keeps running even if invalid input is entered.

If the server disconnects, the client reports a communication error instead of freezing.

---

## Assumptions

1. Only one player is connected at a time
2. Words contain only letters
3. Miss limit is always 6
4. Leaderboard is stored in memory (not permanent)

---

## Known Issues

1. Leaderboard resets when server restarts
2. ASCII art stage formatting may vary slightly depending on console size

