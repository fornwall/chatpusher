package net.fornwall.chatpusher;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.xmpp.JID;
import com.google.appengine.api.xmpp.MessageBuilder;

/**
 * Receives GET and POST requests to https://chatpusher.appspot.com/broadcast with three parameters:
 * 
 * <dl>
 * <dt>list</dt>
 * <dd>list to post the message to.</dd>
 * <dt>secretToken</dt>
 * <dd>The secret token of the list, obtained by the list manager with the /token or /newtoken command.</dd>
 * <dt>message</dt>
 * <dd>The message to broadcast to the list.</dd>
 */
public class BroadcastServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	@Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
			IOException {
		doPost(req, resp);
	}

	@Override protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
			IOException {
		String listName = req.getParameter("name");
		String secretToken = req.getParameter("secret_token");
		String message = req.getParameter("payload");
		if (listName == null) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "The parameter 'name' is required!");
			return;
		} else if (secretToken == null) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "The parameter 'secret_token' is required!");
			return;
		} else if (message == null) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "The parameter 'payload' is required!");
			return;
		}

		ChatList list = Database.objectById(ChatList.class, ChatList.keyForName(listName));
		if (list == null) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "No list with the name '" + listName + "'.");
			return;
		} else if (!secretToken.equals(list.getSecretToken())) {
			resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Incorrect secret key.");
			return;
		}

		JID listJID = new JID(XMPPReceiverServlet.listAddressFromName(list.getName()));
		JID[] recipientJIDs = new JID[list.getMembers().size()];
		int i = 0;
		for (String member : list.getMembers())
			recipientJIDs[i++] = new JID(member);
		MessageBuilder builder = new MessageBuilder();

		XMPPReceiverServlet.service.sendMessage(builder.withBody(message).withRecipientJids(recipientJIDs)
				.withFromJid(listJID).build());
		resp.setContentType("text/plain");
		PrintWriter writer = resp.getWriter();
		writer.println("Ok");
		writer.close();
	}

}
