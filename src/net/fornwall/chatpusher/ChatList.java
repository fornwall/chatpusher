package net.fornwall.chatpusher;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.jdo.annotations.IdGeneratorStrategy;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;

import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;

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
}
