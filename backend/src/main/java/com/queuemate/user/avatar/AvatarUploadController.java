package com.queuemate.user.avatar;

import com.queuemate.common.security.CurrentUser;
import com.queuemate.user.api.UserDtos.UserProfileResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users/me/avatar")
public class AvatarUploadController {

    private final AvatarService avatarService;

    public AvatarUploadController(AvatarService avatarService) {
        this.avatarService = avatarService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserProfileResponse upload(CurrentUser currentUser,
                                      @RequestParam("file") MultipartFile file) {
        return UserProfileResponse.from(avatarService.upload(currentUser.userId(), file));
    }
}
