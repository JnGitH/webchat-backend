package com.ywzlp.webchat.msg.config;

import java.util.List;

import org.apache.shiro.authc.IncorrectCredentialsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptorAdapter;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.support.NativeMessageHeaderAccessor;
import org.springframework.util.CollectionUtils;

import com.ywzlp.webchat.msg.dto.MessageType;
import com.ywzlp.webchat.msg.dto.WebChatMessage;
import com.ywzlp.webchat.msg.entity.UserTokenEntity;
import com.ywzlp.webchat.msg.repository.UserTokenRepository;
import com.ywzlp.webchat.msg.util.SpringUtil;

public class WebSocketInterceptor extends ChannelInterceptorAdapter {
	
	@Autowired
	private UserTokenRepository userTokenRepository;
	
	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		StompCommand command = accessor.getCommand();
		String token = this.getToken(message);
		switch (command) {
		case CONNECT:
			UserTokenEntity userToken = userTokenRepository.findByAccessToken(token);
			if (userToken == null) {
				throw new IncorrectCredentialsException();
			}
			String username = userToken.getUsername();
			UserTokenEntity oldToken = userTokenRepository.findByUsernameAndAccessTokenNot(username, token);
			if (oldToken != null) {
				this.pushOut(username);
				userTokenRepository.deleteByAccessToken(oldToken.getAccessToken());
			}
			break;
		case ABORT:
			userTokenRepository.deleteByAccessToken(token);
			break;
		case DISCONNECT:
			userTokenRepository.deleteByAccessToken(token);
			break;
		default:
			break;
		}
		return message;
	}
	
	private String getToken(Message<?> message) {
		NativeMessageHeaderAccessor header = MessageHeaderAccessor.getAccessor(message, NativeMessageHeaderAccessor.class);
		List<String> token = header.getNativeHeader("token");
		if (CollectionUtils.isEmpty(token)) {
			throw new IncorrectCredentialsException();
		}
		return token.get(0);
	}
	
	/**
	 * 通知用户被挤下线
	 */
	public void pushOut(String username) {
		//发送下线通知
		WebChatMessage msg = new WebChatMessage();
		msg.setMessageType(MessageType.PUSH_OUT);
		SimpMessagingTemplate template = SpringUtil.getBean(SimpMessagingTemplate.class);
		template.convertAndSend("/message/" + username, msg);
	}

}
