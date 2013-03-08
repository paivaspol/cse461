Name: Lee Lee, Vaspol

Persistent Connection in RPCService:
	fields:
	- a List that stores TCPMessageHandler
	- a List that keeps track of the total time waited on a connection so that 
		we can remove socket that eventually hit the rpc persistence timeout
	- a List that stores the state(Enum) for each TCPMessageHandler
	- an Enum that contains three different states: FRESH, COMPLETED, PERSISTENT
	- HashMap of HashMap that map from service name -> method name -> rpccallable method

	Initially, all connections being listened are being added to the list with FRESH state.
	If client meant to create a persistent connection, we will update the state to PERSISTENT.
	That way, we will not close it immediately when one request response is completed.
	We will close it only when it hits the rpc persistence timeout, and hence remove all its details
	from the lists. We did reset the timer when the socket is active again before the timeout.

	Our implementation for this is single-threaded.



Persistent Connection in RPCCall:
We have a HashMap that contains all the socket to a server. The key is in
the format of "ip,port". An entry will be removed, if a SocketTimeOutException occurs.
We also have a cleaning up timer that will trigger after 5 minutes, if there is no usage on that connection. However, if there is a usage on that connection, the timer will be reset and restart the timer again.