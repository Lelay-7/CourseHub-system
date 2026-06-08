package com.bugaoshan.controller;

import com.bugaoshan.common.Result;
import com.bugaoshan.common.SecurityUtils;
import com.bugaoshan.service.ContactService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contact")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @GetMapping("/list")
    public Result<?> list(@RequestParam(required = false) String keyword,
                          @RequestParam(required = false, defaultValue = "teacher") String type) {
        return contactService.getContactList(type, keyword);
    }

    @GetMapping("/search")
    public Result<?> search(@RequestParam(required = false) String keyword,
                            @RequestParam(required = false, defaultValue = "teacher") String type) {
        return contactService.searchContact(type, keyword);
    }

    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable Long id) {
        return contactService.getContactDetail(id);
    }
}
