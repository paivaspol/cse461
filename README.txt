Name: Lee Lee, Vaspol

Persistent Connection in RPCService:
	fields:
	- a List that stores TCPMessageHandler
	- a List that stores the state(Enum) for each TCPMessageHandler
	- an Enum that contains three different states: FRESH, COMPLETED, PERSISTENT
	- HashMap of HashMap that map from service name -> method name -> rpccallable method

	Initially, all connections being listened are being added to the list with FRESH state.
	If client meant to create a persistent connection, we will update the state to PERSISTENT.
	That way, we will not close it immediately when one request response is completed.
	We will close it only when the next time we loop through the list again to read from
	each socket and timeout occurs, that means the connection is inactive and we can just
	remove it and it's state from the list.

	Our implementation for this is single-threaded.



Persistent Connection in RPCCall: