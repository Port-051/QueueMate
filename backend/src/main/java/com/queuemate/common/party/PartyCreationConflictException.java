package com.queuemate.common.party;

import com.queuemate.common.error.ConflictException;

/**
 * 같은 proposal로 party를 만들려는 트랜잭션이 둘 이상 붙었을 때 진 쪽이 받는다.
 * 이긴 쪽만 확정으로 남고 진 쪽은 통째로 롤백되어야 한다.
 */
public class PartyCreationConflictException extends ConflictException {

    public PartyCreationConflictException(String message) {
        super("PARTY_ALREADY_CREATED", message);
    }
}
