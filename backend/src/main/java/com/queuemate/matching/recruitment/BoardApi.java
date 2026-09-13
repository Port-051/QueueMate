package com.queuemate.matching.recruitment;

import com.queuemate.common.api.MatchConditionRequest;
import com.queuemate.common.domain.PlayAmount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class BoardApi {
    private BoardApi() {}
    public record Write(@NotNull @Pattern(regexp="REALTIME|RESERVATION") String type,
                        @NotNull @Valid MatchConditionRequest condition, @NotNull @Valid BoardPreferences preferences,
                        @NotNull @Size(max=120) String description, boolean autoMatch,
                        OffsetDateTime availableFrom, OffsetDateTime availableTo, PlayAmount playAmount, Long version) {}
    public record Search(@NotNull @Pattern(regexp="REALTIME|RESERVATION") String type,
                         @NotNull @Valid MatchConditionRequest condition, @NotNull @Valid BoardPreferences preferences,
                         OffsetDateTime availableFrom, OffsetDateTime availableTo, PlayAmount playAmount,
                         @Pattern(regexp="RECOMMENDED|RECENT") String sort,
                         @Min(0) @Max(1000) int page, @Min(5) @Max(10) Integer pageSize) {
        public Search { if (pageSize == null) pageSize = 10; }
    }
    public record Person(UUID id, UUID userId, String nickname, MatchConditionRequest condition, BoardPreferences preferences) {}
    public record Timing(OffsetDateTime confirmAt, OffsetDateTime hideAt, OffsetDateTime suggestAt, OffsetDateTime nextBumpAt) {}
    public record Row(UUID id, UUID userId, String nickname, String type, MatchConditionRequest condition,
                      BoardPreferences preferences, String description, boolean autoMatch,
                      OffsetDateTime availableFrom, OffsetDateTime availableTo, PlayAmount playAmount,
                      String status, OffsetDateTime createdAt, OffsetDateTime confirmedAt, OffsetDateTime bumpedAt,
                      UUID parentId, UUID requestedParentId, UUID proposalId, long version, int targetSize,
                      List<Person> members, List<Person> applicants, long impressions, boolean alertEnabled, Timing timing) {}
    public record Page(List<Row> items, int total, int page, boolean hasMore, OffsetDateTime asOf) {}
    public record Action(@NotBlank String action, @NotNull Long version) {}
    public record Join(@NotNull UUID sourceId) {}
    public record Respond(@NotNull UUID applicantId, boolean accept) {}
    public record Impressions(@NotNull @Size(max=10) List<@NotNull UUID> ids) {}
    public record Suggestion(String field, String label, MatchConditionRequest condition, BoardPreferences preferences,
                             int candidateCount, List<Row> candidates) {}
    public record Suggestions(int currentCount, List<Suggestion> suggestions, OffsetDateTime asOf) {}
}
