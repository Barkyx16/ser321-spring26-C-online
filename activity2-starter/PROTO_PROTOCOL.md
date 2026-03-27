# Activity 2: Auction Game Protocol Specification (vs Bots)

This document specifies the **exact** protocol for the Auction Game where players compete against bot opponents. All server implementations must follow this protocol to ensure compatibility.

## Overview

The protocol uses Protocol Buffers (protobuf) with delimited messages:
- Client sends `Request` messages using `request.writeDelimitedTo(out)`
- Server sends `Response` messages using `response.writeDelimitedTo(out)`
- Read messages using `Request.parseDelimitedFrom(in)` and `Response.parseDelimitedFrom(in)`

## Game Mechanics

- Each player starts with **150 gold**
- Player competes against **3 bot opponents** (each also starts with 150 gold)
- 5 items randomly selected from a pool of ~50 fantasy items
- Each item has a **reserve price** (50% of min_value) — bids must meet or exceed this
- Players can bid **-1 to skip** an item (treated as bid of 0, bots still compete)
- **Ties broken alphabetically** by name (lowest name wins)
- If **no bid meets the reserve price**, the item goes unsold
- If a client **disconnects before GAME_OVER**, their score is NOT added to the leaderboard

## Protocol Flow

```
1. Connection → Server sends WELCOME
2. Client → REGISTER → Server → WELCOME (with gold amount)
3. Client → JOIN → Server → GAME_JOINED (with first item + player gold)
4. Loop for items 1-4:
   Client → BID → Server → BID_RESULT (with next item + updated player gold)
5. Last item (item 5):
   Client → BID
   Server → BID_RESULT (no next_item field)
   Server → GAME_OVER (with final scores) [sent automatically, no client request]
6. From menu: Client → LEADERBOARD → Server → LEADERBOARD_RESPONSE
7. Client → QUIT → Server → FAREWELL
```

**Key Protocol Feature**: Server sends `player_status` with current gold in `GAME_JOINED` and `BID_RESULT` responses. Client displays this as "Enter your bid (0-X, or -1 to skip):" where X is the gold amount from `player_status`.

---

## Protocol Endpoints

Each endpoint shows the Request/Response pair together.

---

### 1. Initial Connection

**Description**: Server sends initial welcome when client connects.

**Client Action**: Connect to server (no request message)

**Server Response**:
```protobuf
Response {
  type: WELCOME
  ok: true
  message: "Welcome to the Auction Game! Please set your name."
}
```

---

### 2. Register Name (REGISTER)

**Description**: Player sets their name. Name must be unique among **active connections**.

**Thread Safety Requirement**: Server must track active player names in a thread-safe collection (e.g., `Collections.synchronizedSet(new HashSet<>())`) and **remove the name when client disconnects** (in finally block).

**Client Request**:
```protobuf
Request {
  type: REGISTER
  name: "Warrior"          // Required: player's chosen name
}
```

**Success Response**:
```protobuf
Response {
  type: WELCOME
  ok: true
  message: "Welcome, Warrior! You have 150 gold. Type 'join' to start playing against bot opponents!"
}
```

**Error Responses**:
```protobuf
// Empty name
Response {
  type: ERROR
  ok: false
  message: "Name cannot be empty"
}

// Name taken
Response {
  type: ERROR
  ok: false
  message: "Name already taken. Please choose another."
}
```

---

### 3. Join Game (JOIN)

**Description**: Start a new game against 3 bot opponents. Game starts immediately with first item. Response includes `player_status` with current gold. Each item includes a `reserve_price`.

**Client Request**:
```protobuf
Request {
  type: JOIN
}
```

**Success Response**:
```protobuf
Response {
  type: GAME_JOINED
  ok: true
  message: "Game started! You're playing against Alaric, Brynn, and Cedric. Current item:"
  player_status: PlayerStatus {
    gold_remaining: 150     // Player's current gold (starting amount)
  }
  next_item: AuctionItem {  // First auction item (randomly selected)
    id: 1
    name: "Sword of Flames"
    category: "weapon"
    min_value: 30
    max_value: 50
    reserve_price: 15       // 50% of min_value
  }
}
```

**Error Responses**:
```protobuf
// Not registered yet
Response {
  type: ERROR
  ok: false
  message: "Please set your name first"
}

// Already in game (player calls JOIN twice)
Response {
  type: ERROR
  ok: false
  message: "You are already in a game"
}
```

**Client Display**: After receiving GAME_JOINED, client shows item details and prompts:
```
Enter your bid (0-150, or -1 to skip):
```

---

### 4. Place Bid (BID)

**Description**: Place a bid on current item. Server processes with bot bids, determines winner, returns result. Response includes updated `player_status` with current gold and next item (if not last).

**Bid Rules**:
- **bid_amount = -1**: Skip this item (treated as bid of 0 for win evaluation, bots still compete)
- **bid_amount > 0**: Must be >= reserve_price, otherwise ERROR
- **bid_amount > gold**: ERROR (insufficient gold)
- **Ties**: Broken alphabetically by name (lowest name wins)
- **No bids meet reserve**: Item goes unsold (winner_name = "(unsold)")

**Client Request**:
```protobuf
Request {
  type: BID
  item_id: 1             // Required: ID of current item
  bid_amount: 40         // Required: gold to bid (the server needs to check if the value is acceptable)
}
```

**Success Response** (more items remain):
```protobuf
Response {
  type: BID_RESULT
  ok: true
  message: "Auction complete!"
  result: AuctionResult {
    item: AuctionItem { id: 1, name: "Sword of Flames", ..., reserve_price: 15 }
    actual_value: 42              // Item's actual value (revealed)
    winner_name: "Warrior"
    winning_bid: 40
    all_bids: [                   // All bids (player + 3 bots)
      PlayerBid { player_name: "Warrior", bid_amount: 40 },
      PlayerBid { player_name: "Alaric", bid_amount: 35 },
      PlayerBid { player_name: "Brynn", bid_amount: 32 },
      PlayerBid { player_name: "Cedric", bid_amount: 28 }
    ]
  }
  player_status: PlayerStatus {
    gold_remaining: 110           // Player's gold AFTER this auction
  }
  next_item: AuctionItem {        // Next item to bid on
    id: 2
    name: "Magic Shield"
    category: "armor"
    min_value: 25
    max_value: 45
    reserve_price: 12
  }
}
```

**Success Response** (last item - no next_item, followed by GAME_OVER):
```protobuf
Response {
  type: BID_RESULT
  ok: true
  message: "Auction complete! Calculating final scores..."
  result: AuctionResult { /* same as above */ }
  player_status: PlayerStatus { gold_remaining: 110 }
  // NO next_item field - indicates last item
}

// Server immediately sends GAME_OVER (see endpoint #5) without another request
```

**Error Responses** - Player can retry after error:
```protobuf
// Wrong item ID
Response {
  type: ERROR
  ok: false
  message: "Invalid item ID. Current item is #2"
}

// Insufficient gold
Response {
  type: ERROR
  ok: false
  message: "Insufficient gold. You have 30 gold."
}

// Bid below reserve price (bid > 0 but < reserve)
Response {
  type: ERROR
  ok: false
  message: "Bid must meet reserve price of 15 gold."
}

// Negative bid (other than -1)
Response {
  type: ERROR
  ok: false
  message: "Bid cannot be negative (use -1 to skip)"
}

// Not in game
Response {
  type: ERROR
  ok: false
  message: "You must join a game first"
}
```

**IMPORTANT - Invalid Bid Retry Flow**:
When server sends ERROR response, the current item **remains the same**. Client can submit another BID request with corrected values.

---

### 5. Game Over (GAME_OVER)

**Description**: Sent automatically after last auction. Shows final scores for all 4 participants (player + 3 bots), game winner, and player's leaderboard position. Ties broken alphabetically by name.

**Client Request**: None (server sends automatically after last BID)

**Server Response**:
```protobuf
Response {
  type: GAME_OVER
  ok: true
  message: "Game over! Final results:"
  game_result: GameResult {
    player_scores: [
      PlayerStatus {
        player_name: "Warrior"
        gold_remaining: 30
        items_value: 95
        total_score: 125          // gold + items_value
        items_won: ["Sword of Flames", "Magic Shield", "Fire Scroll"]
      },
      PlayerStatus {
        player_name: "Alaric"
        gold_remaining: 40
        items_value: 70
        total_score: 110
        items_won: ["Healing Potion", "Ice Dagger"]
      },
      PlayerStatus {
        player_name: "Brynn"
        gold_remaining: 55
        items_value: 50
        total_score: 105
        items_won: ["Lightning Staff"]
      },
      PlayerStatus {
        player_name: "Cedric"
        gold_remaining: 60
        items_value: 40
        total_score: 100
        items_won: []
      }
    ]
    winner_name: "Warrior"          // Game winner (highest score, ties alphabetical)
    leaderboard_position: 3         // Player's global leaderboard rank
  }
}
```

---

### 6. View Leaderboard (LEADERBOARD)

**Description**: Query top 10 scores from global leaderboard. Can be called anytime.

**Client Request**:
```protobuf
Request {
  type: LEADERBOARD
}
```

**Server Response**:
```protobuf
Response {
  type: LEADERBOARD_RESPONSE
  ok: true
  message: "Top 10 Scores:"
  leaderboard: Leaderboard {
    entries: [
      LeaderboardEntry {
        rank: 1
        player_name: "Warrior"
        score: 250
        timestamp: "2026-02-15 10:30:00"
      },
      // ... up to 10 entries ...
    ]
  }
}
```

---

### 7. Quit (QUIT)

**Description**: Disconnect from server gracefully.

**Client Request**:
```protobuf
Request {
  type: QUIT
}
```

**Server Response**:
```protobuf
Response {
  type: FAREWELL
  ok: true
  message: "Thanks for playing! Final score: 225. Goodbye!"
}
```

---

**Usage Notes**:
- In `GAME_JOINED` and `BID_RESULT` responses: Only `gold_remaining` is set in `player_status` (other fields unused)
- In `GAME_OVER` response: All fields are populated with final stats in `player_scores`
- Leaderboard persists to `scores.txt` across server restarts

---

## Example Game Flow

```
1. Player connects
   Server → WELCOME: "Welcome to the Auction Game! Please set your name."

2. Player → REGISTER: "Warrior"
   Server → WELCOME: "Welcome, Warrior! You have 150 gold. Type 'join' to start..."

3. Player → JOIN
   Server creates: Warrior vs Alaric vs Brynn vs Cedric (3 bots)
   Server randomly selects 5 items from pool
   Server → GAME_JOINED:
     - First item: Sword of Flames (30-50, reserve: 15)
     - player_status: {gold_remaining: 150}
   Client shows: "Enter your bid (0-150, or -1 to skip):"

4. Player → BID: item_id=1, amount=40
   Server generates: Alaric=35, Brynn=32, Cedric=28
   Warrior wins! (spent 40 gold)
   Server → BID_RESULT:
     - Winner: Warrior (40)
     - Bids: Warrior=40, Alaric=35, Brynn=32, Cedric=28
     - Actual value: 42
     - player_status: {gold_remaining: 110}
     - Next item: Magic Shield (25-45, reserve: 12)
   Client shows: "Enter your bid (0-110, or -1 to skip):"

5. Player → BID: item_id=2, amount=-1  (skip)
   Player bid treated as 0. Bots still compete.
   Alaric wins with bid of 30.
   Server → BID_RESULT: ...

6. ... continues for all 5 items ...

7. After last item (item 5):
   Server → BID_RESULT: (result for item 5, no next item)
   Server → GAME_OVER:
     - Warrior: gold=70, items_value=95, total=165
     - Alaric: gold=40, items_value=70, total=110
     - Brynn: gold=55, items_value=50, total=105
     - Cedric: gold=60, items_value=40, total=100
     - Winner: Warrior
     - Leaderboard position: #3

8. Player → LEADERBOARD
   Server → LEADERBOARD_RESPONSE: Top 10 scores

9. Player → QUIT
   Server → FAREWELL: "Thanks for playing! Final score: 165. Goodbye!"
```


## Protocol Compliance

Your server is compliant if:
1. Accepts all request types (REGISTER, JOIN, BID, LEADERBOARD, QUIT)
2. Sends responses in exact formats with correct types
3. Error messages match standard messages
4. Reserve price enforced correctly (50% of min_value)
5. Skip bid (-1) treated as bid of 0
6. Ties broken alphabetically by name
7. 4 participants in GAME_OVER (player + 3 bots)
8. Leaderboard persists to `scores.txt` across server restarts
9. Bot opponents bid correctly using provided BotOpponent class
10. Provided client can play successfully
