package net.fornwall.chatpusher;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Message.RecipientType;
import javax.mail.Transport;
import javax.mail.internet.ContentType;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.xmpp.JID;
import com.google.appengine.api.xmpp.MessageBuilder;

/**
 * Receives mail to ${LISTNAME}+${TOKEN}@chatpusher.appspotmail.com and sends messages to the chat list.
 */
public class MailReceiverServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	private final Properties EMPTY_PROPERTIES = new Properties();
	private static final Logger logger = Logger.getLogger(MailReceiverServlet.class.getName());

	@Override protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
			IOException {
		try {
			Session session = Session.getDefaultInstance(EMPTY_PROPERTIES);
			MimeMessage receivedMail = new MimeMessage(session, req.getInputStream());
			String messageText = getText(receivedMail);
			if (messageText == null) {
				return;
			}
			for (Address recipient : receivedMail.getRecipients(RecipientType.TO)) {
				String recipientString = ((InternetAddress) recipient).getAddress();
				if (recipientString.endsWith("@chatpusher.appspotmail.com")) {
					String nameAndToken = recipientString.substring(0, recipientString.indexOf('@'));
					int plusIndex = nameAndToken.indexOf('+');
					if (plusIndex == -1 || plusIndex == nameAndToken.length() - 1) {
						continue;
					} else {
						String name = nameAndToken.substring(0, plusIndex);
						String token = nameAndToken.substring(plusIndex + 1);
						ChatList list = Database.objectById(ChatList.class, ChatList.keyForName(name));
						if (list == null) {
							logger.warning("No list for recipient: " + recipientString);
						} else if (!list.getSecretToken().equals(token)) {
							logger.warning("Incorrect token '" + token + "' for recipient: " + recipientString);
						} else {
							String textToSend = receivedMail.getSubject().trim() + "\n\n" + messageText.trim() + "\n\n "
									+ receivedMail.getFrom()[0].toString().trim();
							JID listJID = new JID(XMPPReceiverServlet.listAddressFromName(list.getName()));
							JID[] recipientJIDs = new JID[list.getMembers().size()];
							int i = 0;
							for (String member : list.getMembers())
								recipientJIDs[i++] = new JID(member);
							MessageBuilder builder = new MessageBuilder();
							XMPPReceiverServlet.service.sendMessage(builder.withBody(textToSend)
									.withRecipientJids(recipientJIDs).withFromJid(listJID).build());
						}
					}
				} else {
					logger.warning("Ignoring recipient: " + recipientString);
				}
			}
		} catch (Exception e) {
			logger.log(Level.SEVERE, "MailReceiverServlet#doPost", e);
		}
	}

	public static void send(String from, String fromName, String to, String subject, String message, Session session)
			throws UnsupportedEncodingException, MessagingException {
		Message msg = new MimeMessage(session);
		msg.setFrom(new InternetAddress(from, fromName, "utf-8"));
		msg.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
		msg.setSubject(subject);
		msg.setText(message);
		Transport.send(msg);
	}

	public static String getText(MimeMessage message) throws Exception {
		ContentType contentType = new ContentType(message.getContentType());
		Object content = message.getContent();
		if ("text".equals(contentType.getPrimaryType())) {
			if (content instanceof String) {
				return (String) content;
			} else {
				logger.warning("Content type is " + contentType + " but content is non-string: " + content);
				return null;
			}
		} else if ("multipart".equals(contentType.getPrimaryType())) {
			MimeMultipart multiPart = (MimeMultipart) content;
			for (int i = 0; i < multiPart.getCount(); i++) {
				BodyPart part = multiPart.getBodyPart(i);
				if (part.getContentType().startsWith("text/") && part.getContent() instanceof String)
					return (String) part.getContent();
			}
			return null;
		} else {
			return null;
		}
	}

}
