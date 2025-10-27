package dev.rubasace.linkedin.games.ldrbot.session;

import dev.rubasace.linkedin.games.ldrbot.group.GroupNotFoundException;
import dev.rubasace.linkedin.games.ldrbot.group.TelegramGroup;
import dev.rubasace.linkedin.games.ldrbot.group.TelegramGroupService;
import dev.rubasace.linkedin.games.ldrbot.user.TelegramUser;
import dev.rubasace.linkedin.games.ldrbot.user.TelegramUserService;
import dev.rubasace.linkedin.games.ldrbot.user.UsernameNotFoundException;
import dev.rubasace.linkedin.games.ldrbot.util.LinkedinTimeUtils;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Transactional(readOnly = true)
@Service
public class GameSessionService {

    private final GameSessionRepository gameSessionRepository;
    private final TelegramUserService telegramUserService;
    private final TelegramGroupService telegramGroupService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final EntityManager entityManager;

    public GameSessionService(final GameSessionRepository gameSessionRepository, final TelegramUserService telegramUserService, final TelegramGroupService telegramGroupService, final ApplicationEventPublisher applicationEventPublisher, final EntityManager entityManager) {
        this.gameSessionRepository = gameSessionRepository;
        this.telegramUserService = telegramUserService;
        this.telegramGroupService = telegramGroupService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.entityManager = entityManager;
    }

    @Transactional
    public Optional<GameSession> recordGameSession(final Long userId, final Long chatId, final String userName, final GameDuration gameDuration) throws AlreadyRegisteredSession, GroupNotFoundException {
        TelegramGroup telegramGroup = telegramGroupService.findGroupOrThrow(chatId);
        TelegramUser telegramUser = telegramUserService.findOrCreate(userId, userName);
        return recordGameSession(telegramUser, telegramGroup, gameDuration);
    }

    /**
     * Convenience method used by admins to override a user time in case the screenshots aren't properly read by the bot
     */
    @Transactional
    public Optional<GameSession> manuallRrecordGameSession(final Long chatId, final String userName, final GameDuration gameDuration) throws AlreadyRegisteredSession, GroupNotFoundException, UsernameNotFoundException {
        TelegramGroup telegramGroup = telegramGroupService.findGroupOrThrow(chatId);
        TelegramUser telegramUser = telegramUserService.findByUserName(userName).orElseThrow(() -> new UsernameNotFoundException(chatId, userName));
        deleteTodaySession(telegramUser.getId(), chatId, gameDuration.type());
        entityManager.flush();
        return recordGameSession(telegramUser, telegramGroup, gameDuration);
    }

    private Optional<GameSession> recordGameSession(final TelegramUser telegramUser, final TelegramGroup telegramGroup, final GameDuration gameDuration) throws AlreadyRegisteredSession {
        if (!telegramGroup.getTrackedGames().contains(gameDuration.type())) {
            return Optional.empty();
        }
        LocalDate gameDay = LinkedinTimeUtils.todayGameDay();
        if (gameSessionRepository.existsByUserIdAndGroupChatIdAndGameAndGameDay(telegramUser.getId(), telegramGroup.getChatId(), gameDuration.type(), gameDay)) {
            throw new AlreadyRegisteredSession(telegramUser.getUserName(), gameDuration.type(), telegramGroup.getChatId());
        }
        GameSession gameSession = new GameSession();
        gameSession.setGame(gameDuration.type());
        gameSession.setUser(telegramUser);
        gameSession.setGroup(telegramGroup);
        gameSession.setGameDay(gameDay);
        gameSession.setDuration(gameDuration.duration());
        GameSession savedSession = gameSessionRepository.saveAndFlush(gameSession);
        applicationEventPublisher.publishEvent(
                new GameSessionRegistrationEvent(this, telegramUser.getId(), telegramUser.getUserName(), gameSession.getGame(), gameSession.getDuration(), gameDay,
                                                 telegramGroup.getChatId()));
        return Optional.of(savedSession);
    }

    @Transactional
    public void deleteTodaySession(final Long userId, final Long chatId, final GameType game) {
        gameSessionRepository.deleteByUserIdAndGroupChatIdAndGameAndGameDay(userId, chatId, game, LinkedinTimeUtils.todayGameDay());
        telegramUserService.find(userId).ifPresent(
                user -> applicationEventPublisher.publishEvent(new GameSessionDeletionEvent(this, user.getId(), user.getUserName(), game, chatId)));
    }

    public Stream<GameSession> getDaySessions(final Long userId, final Long chatId, final LocalDate gameDay) {
        return gameSessionRepository.getByUserIdAndGroupChatIdAndGameDay(userId, chatId, gameDay);
    }

    public Stream<GameSession> getDaySessions(final Set<Long> userIds, final Long chatId, final LocalDate gameDay) {
        return gameSessionRepository.getByUserIdInAndGroupChatIdAndGameDay(userIds, chatId, gameDay);
    }

    @Transactional
    public void deleteDaySessions(final Long userId, final Long chatId, final LocalDate gameDay) {
        gameSessionRepository.deleteByUserIdAndGroupChatIdAndGameDay(userId, chatId, gameDay);
        telegramUserService.find(userId).ifPresent(
                user -> applicationEventPublisher.publishEvent(new GameSessionDeletionEvent(this, user.getId(), user.getUserName(), chatId)));
    }


}
