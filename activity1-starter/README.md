# Assignment 4 – Activity 1: Task Management System

Video Link and Demo: https://youtu.be/FycdsJARbrM?si=uBZEOk_8wu1LI7YA

## Overview

For this activity, I updated the original task management system so it no longer uses JSON and instead communicates using Protocol Buffers. I also changed the server so multiple clients can connect at the same time instead of only one client at once.

The program allows users to:

- add tasks with categories
- list all tasks, pending tasks, or finished tasks
- mark tasks as finished
- connect multiple clients that all see the same shared task list

## How to compile

Run:

./gradlew build

## How to run the threaded server

Run:

./gradlew runThreadedServer

## How to run clients

Open another terminal window (or multiple) and run:

./gradlew runClient

Each client connects to the same server and shares the same tasks.

## What I implemented

Here’s what I changed from the starter version:

- finished the task.proto file
- replaced the JSON request/response system with Protocol Buffers
- created PerformerProto.java
- created ClientProto.java
- created ThreadedServer.java
- added a Gradle task called runThreadedServer
- made TaskList.java thread-safe so multiple clients don’t overwrite each other’s data
- added better socket closing and error handling
- tested the server with multiple clients connected at the same time

## Design decisions

I used one thread per client connection so each user can interact with the server independently. Since all clients share the same task list, I synchronized access to the task list to prevent conflicts when multiple users modify it at once.

## Known issues

No problems came up during testing. The system worked as expected with multiple clients connected at the same time.
