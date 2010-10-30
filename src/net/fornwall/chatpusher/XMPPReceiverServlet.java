package net.fornwall.chatpusher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;
import java.util.logging.Logger;

import javax.mail.internet.InternetAddress;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.xmpp.JID;
import com.google.appengine.api.xmpp.Message;
import com.google.appengine.api.xmpp.MessageBuilder;
import com.google.appengine.api.xmpp.XMPPService;
import com.google.appengine.api.xmpp.XMPPServiceFactory;

/**
 * Servlet which receives xmpp messages to chatpusher@appspot.com or *@chatpusher.appspotchat.com.
 */
@SuppressWarnings("serial") public class XMPPReceiverServlet extends HttpServlet {

	private static final Logger logger = Logger.getLogger(XMPPReceiverServlet.class.getName());
	public static final XMPPService service = XMPPServiceFactory.getXMPPService();
	private static final String JID_SUFFIX = "@chatpusher.appspotchat.com";
	private static final int JID_SUFFIX_LENGTH = JID_SUFFIX.length();

	private static String emailAddressFromJID(JID jid) {
		String id = jid.getId();
		int slashIndex = id.indexOf('/');
		if (slashIndex == -1) {
			return id;
		} else {
			return id.substring(0, slashIndex);
		}
	}

	public static String listAddressFromName(String name) {
		return name + "@chatpusher.appspotchat.com";
	}

	public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
		Message message = service.parseMessage(request);
		MessageBuilder builder = new MessageBuilder();

		if (message.getRecipientJids().length != 1)
			return;

		JID recipientJID = message.getRecipientJids()[0];
		String recipientId = emailAddressFromJID(recipientJID);
		if (!recipientId.endsWith(JID_SUFFIX)) {
			logger.warning("JID: " + recipientId + " does not end with " + JID_SUFFIX);
			return;
		}

		String listName = recipientId.substring(0, recipientId.length() - JID_SUFFIX_LENGTH);
		String messageText = message.getBody().trim();
		String senderEmail = emailAddressFromJID(message.getFromJid());

		ChatList chatList = Database.objectById(ChatList.class, ChatList.keyForName(listName));
		if (chatList == null) {
			if (messageText.equals("/claim")) {
				chatList = ChatList.createNewUnlessAlreadyTaken(listName, senderEmail);
				service.sendMessage(builder
						.withBody(
								"You have claimed the list " + listAddressFromName(listName) + ". Secret token: "
										+ chatList.getSecretToken()).withRecipientJids(message.getFromJid())
						.withFromJid(recipientJID).build());
				return;
			} else {
				service.sendMessage(builder
						.withBody("This list is not claimed. Issue the /claim command if you want to claim it.")
						.withRecipientJids(message.getFromJid()).withFromJid(recipientJID).build());
				return;
			}
		}

		if (!chatList.getMembers().contains(senderEmail)) {
			service.sendMessage(builder
					.withBody(
							"You are not a member of this list. Contact the owner " + chatList.getOwnerEmail()
									+ " about it if you want.").withRecipientJids(message.getFromJid())
					.withFromJid(recipientJID).build());
			return;
		}

		if (messageText.equals("/info")) {
			service.sendMessage(builder
					.withBody(
							"Chat list : " + recipientId + "\nOwner: " + chatList.getOwnerEmail() + "\nMembers: "
									+ chatList.getMembers()).withRecipientJids(message.getFromJid())
					.withFromJid(recipientJID).build());
			return;
		}

		if (chatList.getOwnerEmail().equals(senderEmail)) {
			if (messageText.equals("/token")) {
				service.sendMessage(builder.withBody("Secret token : " + chatList.getSecretToken())
						.withRecipientJids(message.getFromJid()).withFromJid(recipientJID).build());
				return;
			} else if (messageText.equals("/newtoken")) {
				chatList.setSecretToken(UUID.randomUUID().toString());
				Database.save(chatList);
				service.sendMessage(builder.withBody("Secret token : " + chatList.getSecretToken())
						.withRecipientJids(message.getFromJid()).withFromJid(recipientJID).build());
				return;
			} else if (messageText.equals("/list")) {
				service.sendMessage(builder.withBody("Members : " + chatList.getMembers())
						.withRecipientJids(message.getFromJid()).withFromJid(recipientJID).build());
				return;
			} else if (messageText.startsWith("/add ")) {
				String[] parts = messageText.split(" ");
				if (parts.length >= 2) {
					String email = parts[1];
					if (!isValidEmailAddress(email)) {
						service.sendMessage(builder.withBody("Invalid email.").withRecipientJids(message.getFromJid())
								.withFromJid(recipientJID).build());
						return;
					} else if (chatList.getMembers().contains(email)) {
						service.sendInvitation(new JID(email), recipientJID);
						service.sendMessage(builder.withBody(email + " is already a member. Invitation re-sent.")
								.withRecipientJids(message.getFromJid()).withFromJid(recipientJID).build());
						return;
					} else {
						chatList.getMembers().add(email);
						Database.save(chatList);
						service.sendInvitation(new JID(email), recipientJID);
						service.sendMessage(builder.withBody(email + " added.").withRecipientJids(message.getFromJid())
								.withFromJid(recipientJID).build());
						return;
					}
				}
			} else if (messageText.startsWith("/remove ")) {
				String[] parts = messageText.split(" ");
				if (parts.length >= 2) {
					String email = parts[1];
					if (!isValidEmailAddress(email)) {
						service.sendMessage(builder.withBody("Invalid email.").withRecipientJids(message.getFromJid())
								.withFromJid(recipientJID).build());
						return;
					} else if (!chatList.getMembers().contains(email)) {
						service.sendMessage(builder.withBody(email + " is not a member of the list.")
								.withRecipientJids(message.getFromJid()).withFromJid(recipientJID).build());
						return;
					} else {
						chatList.getMembers().remove(email);
						Database.save(chatList);
						service.sendMessage(builder.withBody(email + " removed.")
								.withRecipientJids(message.getFromJid()).withFromJid(recipientJID).build());
						return;
					}
				}
			}
		}

		ArrayList<JID> recipientJIDS = new ArrayList<JID>(chatList.getMembers().size());
		for (String member : chatList.getMembers()) {
			if (!member.equals(senderEmail))
				recipientJIDS.add(new JID(member));
		}
		if (recipientJIDS.isEmpty()) {
			service.sendMessage(builder.withBody("You are the only member of this list.")
					.withRecipientJids(message.getFromJid()).withFromJid(recipientJID).build());
		} else {
			String textToSend;
			if (messageText.contains("\n")) {
				textToSend = messageText + "\n\n" + senderEmail;
			} else {
				textToSend = messageText + " - " + senderEmail;
			}
			service.sendMessage(builder.withBody(textToSend)
					.withRecipientJids(recipientJIDS.toArray(new JID[recipientJIDS.size()])).withFromJid(recipientJID)
					.build());
		}
	}

	public static boolean isValidEmailAddress(String aEmailAddress) {
		if (aEmailAddress == null)
			return false;
		boolean result = true;
		try {
			new InternetAddress(aEmailAddress);
			if (!hasNameAndDomain(aEmailAddress))
				result = false;
		} catch (Exception ex) {
			result = false;
		}
		return result;
	}

	private static boolean hasNameAndDomain(String aEmailAddress) {
		String[] tokens = aEmailAddress.split("@");
		return tokens.length == 2 && !tokens[0].trim().isEmpty() && !tokens[0].trim().isEmpty();
	}

}
