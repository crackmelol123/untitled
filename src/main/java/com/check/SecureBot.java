package com.check;

import com.lowagie.text.pdf.*;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.security.SecureRandom;
import java.util.*;

public class SecureBot extends TelegramLongPollingBot {

    private static final String BOT_USERNAME = "@apkojdkoadjawjdapwoidbot"; // Имя бота
    private static final String BOT_TOKEN = "7931742211:AAHRbYo_WRFXPEq2F6YtmIArcma6kcVQclY"; // Токен бота
    private static final int OTP_VALIDITY_MINUTES = 10000; // Время жизни пароля в минутах

    private final Map<Long, Boolean> authorizedUsers = new HashMap<>();
    private final Map<String, OTP> otpStorage = new HashMap<>();

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            long chatId = update.getMessage().getChatId();
            String text = update.getMessage().getText();

            if (text.equals("/start")) {
                handleStartCommand(chatId);
            } else if (text.startsWith("/otp")) {
                handleOtpCommand(chatId);
            } else if (otpStorage.containsKey(text)) {
                handleOtpValidation(chatId, text);
            } else if (authorizedUsers.getOrDefault(chatId, false)) {
                handleAuthorizedUser(chatId, text);
            } else {
                sendMessage(chatId, "❌ Доступ запрещён. Введите одноразовый пароль.");
            }
        }
    }

    private void handleStartCommand(long chatId) {
        sendMessage(chatId, "👋 Добро пожаловать! Чтобы получить доступ, введите одноразовый пароль (OTP).");
    }

    private void handleOtpCommand(long chatId) {
        String otp = generateOtp();
        otpStorage.put(otp, new OTP(chatId, System.currentTimeMillis()));

        sendMessage(chatId, "🔑 Ваш одноразовый пароль: `" + otp + "`\n" +
                "⏳ Действителен в течение " + OTP_VALIDITY_MINUTES + " минут.");
    }

    private void handleOtpValidation(long chatId, String otp) {
        OTP storedOtp = otpStorage.get(otp);

        if (storedOtp != null && isOtpValid(storedOtp)) {
            authorizedUsers.put(chatId, true);
            otpStorage.remove(otp);

            sendMessage(chatId, "✅ Доступ предоставлен! Вы можете пользоваться ботом.");
        } else {
            sendMessage(chatId, "❌ Неверный или истёкший пароль. Запросите новый.");
        }
    }

    private void handleAuthorizedUser(long chatId, String text) {
        sendMessage(chatId, "🎉 Вы авторизованы! Ваше сообщение: " + text);
        // Здесь можно добавить логику для обработки данных
    }

    private boolean isOtpValid(OTP otp) {
        long currentTime = System.currentTimeMillis();
        return currentTime - otp.getTimestamp() <= OTP_VALIDITY_MINUTES * 60 * 1000;
    }

    private String generateOtp() {
        SecureRandom random = new SecureRandom();
        StringBuilder otp = new StringBuilder();

        for (int i = 0; i < 6; i++) {
            otp.append(random.nextInt(10)); // Генерируем цифру от 0 до 9
        }
        return otp.toString();
    }

    private void sendMessage(long chatId, String text) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText(text);
            message.setParseMode("Markdown");
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getBotUsername() {
        return BOT_USERNAME;
    }

    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }

    public static void main(String[] args) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new SecureBot());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private static class OTP {
        private final long chatId;
        private final long timestamp;

        public OTP(long chatId, long timestamp) {
            this.chatId = chatId;
            this.timestamp = timestamp;
        }

        public long getChatId() {
            return chatId;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
