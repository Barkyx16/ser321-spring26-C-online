Assignment 6 – Distributed Systems gRPC Node

This project is a small gRPC-based distributed system node that provides three working services: a temperature converter, a library tracker, and a study task tracker. The goal of the project was to practice building services from proto definitions, connecting a client to a node, handling user input through the terminal, and storing data so it remains available after restarting the server.

The converter service allows temperature values to be converted between Celsius and Fahrenheit. The library service allows books to be added and listed, and the saved books stay available even after the server is restarted. 

How to run the program

Start the server first:

gradle runNode

Then open a second terminal window and start the client:

gradle runClient

The server runs on port 8000 by default.

How to use the program

When the client starts, a menu appears showing all available services. Enter the number of the option you want to use and follow the prompts shown in the terminal.

Options include:

1 – Convert Celsius to Fahrenheit  
2 – Convert Fahrenheit to Celsius  
3 – Add a book  
4 – List saved books  
5 – Add a study task  
6 – List saved study tasks  
0 – Exit the program  

The program checks for empty input and continues running without crashing if something invalid is entered.

Screencast demo

This short recording shows the server running, the client interacting with each service, and confirmation that saved data remains available after restarting the server:

https://youtube.com/shorts/W5k6eYIExqA
