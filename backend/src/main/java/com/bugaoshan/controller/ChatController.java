package com.bugaoshan.controller;
import com.bugaoshan.dto.ChatDTO.*;
import com.bugaoshan.common.*;
import com.bugaoshan.service.ChatConversationService;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/chat")
public class ChatController {
    private final ChatConversationService conversations;
    public ChatController(ChatConversationService conversations) { this.conversations=conversations; }
    private Actor actor() { return new Actor(SecurityUtils.getCurrentUserId(),SecurityUtils.getCurrentCredentialVersion()); }
    @PostMapping("/conversations/private") public Result<ConversationView> open(@RequestBody CreatePrivateRequest request) {
        if(request==null || request.targetUserId()==null) throw new ChatException(400,"请选择好友");
        return Result.ok(conversations.openPrivate(actor(),request.targetUserId()));
    }
    @GetMapping("/conversations") public Result<ConversationPage> list(@RequestParam(defaultValue="30") int limit,@RequestParam(required=false) String cursor) { return Result.ok(conversations.list(actor(),limit,cursor)); }
    @GetMapping("/conversations/{id}") public Result<ConversationView> detail(@PathVariable String id) { return Result.ok(conversations.detail(actor(),ChatConversationService.parseSequence(id,false))); }
    @PutMapping("/conversations/{id}/read") public Result<ReadView> read(@PathVariable String id,@RequestBody ReadRequest request) { return Result.ok(conversations.markRead(actor(),ChatConversationService.parseSequence(id,false),ChatConversationService.parseSequence(request==null?null:request.lastReadSequence(),true))); }
    @GetMapping("/unread") public Result<UnreadView> unread() { return Result.ok(conversations.unread(actor())); }
}
