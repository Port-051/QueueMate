package com.queuemate.chat.dto;

import com.queuemate.chat.domain.Type;

public record Input(Type type, String cid, String nick, String text) {
}
