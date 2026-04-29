Assignment 6 – Distributed Systems gRPC Node

This project is a small gRPC-based distributed system node that provides three working services: a temperature converter, a library tracker, and a study task tracker. The goal of the project was to practice building services from proto definitions, connecting a client to a node, handling user input through the terminal, and storing data so it remains available after restarting the server.

The converter service allows temperature values to be converted between Celsius and Fahrenheit. The library service allows books to be added and listed, and the saved books stay available even after the server is restarted. The studybuddy service works the same way but stores study tasks instead of books.

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

Requirements completed in this project

This project includes the converter service from the provided proto file and supports converting values both directions between Celsius and Fahrenheit. The library service from the proto file was implemented and stores data in a JSON file so books remain saved after restarting the server. A custom studybuddy service was added that allows study tasks to be saved and listed, and those tasks also remain available after restart.

The client displays a menu so it is easy to choose which service to use and guides the user step by step through each request. The server handles invalid or empty input safely. Unit tests were added to verify correct responses from the services and to confirm that stored data remains available after restarting the server. The node was also deployed online so other users could connect and test the services remotely.

Screencast demo

This short recording shows the server running, the client interacting with each service, and confirmation that saved data remains available after restarting the server:

https://youtube.com/shorts/W5k6eYIExqA
