net.fornwall.chatpusher - an App Engine project to broadcast messages to a list of email addresses over XMPP.

Chat lists have addresses of the form ${NAME}@chatpusher.appspotchat.com. To claim a list and start using it, add
the desired list to as an XMPP buddy and issue the command /claim.

Other commands are:
	/token: Write out the current secret token (used with broadcasting over HTTP, see below).
	/newtoken: Generate a new token and replace the current one (used with broadcasting over HTTP, see below).
	/add <newmember>: Add a new member to the chat list. An invitation will be sent, but messages will not be
					  received by the new member until he adds the chat list as a buddy. If the specified email
					  is already a member of the chat list an invitation will be sent again.
	/remove <oldmember>: Remove a member from the chat list.
	/info: Get some information on the chat list.

To broadcast a message to a chat list issue a GET or POST to the URL https://chatpusher.appspot.com/broadcast with the following parameters:
	list: The name of the list to broadcast to.
	secret_token: The secret token (see the /token and /newtoken commands above).
	payload: The message text to broadcast.

Examples with curl:
	curl -d list=mylist -d secret_token=68de5ebf-f5d1-49df-9fef-f8d1a089838f -d payload=HelloWorld http://chatpusher.appspot.com/broadcast
	./my_script_generating_output | curl -d list=test -d secret_token=68de5ebf-f5d1-49df-9fef-f8d1a089838f --data-urlencode payload@- http://chatpusher.appspot.com/broadcast