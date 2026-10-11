package com.bugaoshan.chat;
import com.bugaoshan.entity.ChatMessage;
import com.bugaoshan.dto.ChatDTO.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZoneOffset;
public final class ChatMessageViews {
    private static final ObjectMapper JSON=new ObjectMapper();
    private ChatMessageViews() {}
    public static MessageView view(ChatMessage row,int maxCodePoints) {
        if(row==null) return null;
        String text="暂不支持此消息类型";
        if("TEXT".equals(row.getMessageType())) try { text=JSON.readTree(row.getContent()).path("text").asText(); } catch(Exception e) { text="消息暂不可用"; }
        if(text.codePointCount(0,text.length())>maxCodePoints) text=text.substring(0,text.offsetByCodePoints(0,maxCodePoints));
        return new MessageView(""+row.getMessageId(),""+row.getConversationId(),""+row.getSequence(),""+row.getSenderId(),row.getClientMessageId(),row.getMessageType(),new TextContent(text),row.getCreatedAt().toInstant(ZoneOffset.UTC));
    }
}
