package net.fornwall.chatpusher;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.jdo.annotations.IdGeneratorStrategy;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;

import com.google.appengine.api.channel.ChannelMessage;
import com.google.appengine.api.channel.ChannelService;
import com.google.appengine.api.channel.ChannelServiceFactory;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.xmpp.JID;
import com.google.appengine.api.xmpp.MessageBuilder;
import com.google.appengine.api.xmpp.XMPPService;
import com.google.appengine.api.xmpp.XMPPServiceFactory;

@PersistenceCapable public class ChatList {

	@PrimaryKey @Persistent(valueStrategy = IdGeneratorStrategy.IDENTITY) private Key id;
	@Persistent private String secretToken;
	@Persistent private String ownerEmail;
	@Persistent private Set<String> members;

	public static Key keyForName(String name) {
		return KeyFactory.createKey("ChatList", name);
	}

	public void setSecretToken(String secretToken) {
		this.secretToken = secretToken;
	}

	public String getSecretToken() {
		return secretToken;
	}

	public static ChatList createNewUnlessAlreadyTaken(String name, String ownerEmail) {
		ChatList existing = Database.objectById(ChatList.class, keyForName(name));
		if (existing != null) {
			return null;
		} else {
			ChatList newList = new ChatList();
			newList.id = keyForName(name);
			newList.secretToken = UUID.randomUUID().toString();
			newList.ownerEmail = ownerEmail;
			Set<String> members = new HashSet<String>();
			members.add(ownerEmail);
			newList.setMembers(members);
			Database.save(newList);
			return newList;
		}
	}

	public String getOwnerEmail() {
		return ownerEmail;
	}

	public String getName() {
		return id.getName();
	}

	void setMembers(Set<String> members) {
		this.members = members;
	}

	Set<String> getMembers() {
		return members;
	}

	public void sendToMembers(String messageText, JID from) {
		String senderEmail = emailAddressFromJID(from);
		JID listJID = new JID(XMPPReceiverServlet.listAddressFromName(getName()));

		XMPPService service = XMPPServiceFactory.getXMPPService();
		ChannelService channelService = ChannelServiceFactory.getChannelService();

		ArrayList<JID> recipientJIDS = new ArrayList<JID>(getMembers().size());
		for (String member : getMembers()) {
			if (!member.equals(senderEmail))
				recipientJIDS.add(new JID(member));
		}

		MessageBuilder builder = new MessageBuilder();

		if (recipientJIDS.isEmpty()) {
			service.sendMessage(builder.withBody("You are the only member of this list.").withRecipientJids(from)
					.withFromJid(listJID).build());
		} else {
			String textToSend;
			if (messageText.contains("\n")) {
				textToSend = messageText + "\n\n" + senderEmail;
			} else {
				textToSend = messageText + " - " + senderEmail;
			}
			service.sendMessage(builder.withBody(textToSend)
					.withRecipientJids(recipientJIDS.toArray(new JID[recipientJIDS.size()])).withFromJid(from).build());

			for (String member : getMembers()) {
				String channelClientId = member + "_secret_stuff";
				channelService.sendMessage(new ChannelMessage(channelClientId, textToSend));
			}
		}

	}

	private static String emailAddressFromJID(JID jid) {
		String id = jid.getId();
		int slashIndex = id.indexOf('/');
		if (slashIndex == -1) {
			return id;
		} else {
			return id.substring(0, slashIndex);
		}
	}
}
