package com.bugaoshan.controller;
import com.bugaoshan.dto.ChatDTO.*;
import com.bugaoshan.common.*;
import com.bugaoshan.service.ChatConversationService;
import com.bugaoshan.service.ChatMessageService;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/chat")
public class ChatController {
    private final ChatConversationService conversations;
    private final ChatMessageService messages;
    public ChatController(ChatConversationService conversations,ChatMessageService messages) { this.conversations=conversations; this.messages=messages; }
    private Actor actor() { return new Actor(SecurityUtils.getCurrentUserId(),SecurityUtils.getCurrentCredentialVersion()); }
    @PostMapping("/conversations/private") public Result<ConversationView> open(@RequestBody CreatePrivateRequest request) {
        if(request==null || request.targetUserId()==null) throw new ChatException(400,"请选择好友");
        return Result.ok(conversations.openPrivate(actor(),request.targetUserId()));
    }
    @GetMapping("/conversations") public Result<ConversationPage> list(@RequestParam(defaultValue="30") int limit,@RequestParam(required=false) String cursor) { return Result.ok(conversations.list(actor(),limit,cursor)); }
    @GetMapping("/conversations/{id}") public Result<ConversationView> detail(@PathVariable String id) { return Result.ok(conversations.detail(actor(),ChatConversationService.parseSequence(id,false))); }
    @PutMapping("/conversations/{id}/read") public Result<ReadView> read(@PathVariable String id,@RequestBody ReadRequest request) { return Result.ok(conversations.markRead(actor(),ChatConversationService.parseSequence(id,false),ChatConversationService.parseSequence(request==null?null:request.lastReadSequence(),true))); }
    @GetMapping("/unread") public Result<UnreadView> unread() { return Result.ok(conversations.unread(actor())); }
    @PostMapping("/conversations/{id}/messages") public Result<MessageView> send(@PathVariable String id,@RequestBody SendTextRequest request) { return Result.ok(messages.send(actor(),ChatConversationService.parseSequence(id,false),request)); }
    @GetMapping("/conversations/{id}/messages") public Result<MessagePage> messages(@PathVariable String id,
        @RequestParam(name="before_sequence",required=false) String before,@RequestParam(name="after_sequence",required=false) String after,@RequestParam(defaultValue="30") int limit) {
        return Result.ok(messages.messages(actor(),ChatConversationService.parseSequence(id,false),before==null?null:ChatConversationService.parseSequence(before,false),after==null?null:ChatConversationService.parseSequence(after,true),limit));
    }
}
