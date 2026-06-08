package com.bugaoshan.service;

import com.bugaoshan.common.Result;

public interface ContactService {
    Result getContactList(String type, String keyword);
    Result searchContact(String type, String keyword);
    Result getContactDetail(Long userId);
}
