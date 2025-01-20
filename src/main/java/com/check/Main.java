package com.check;

import com.lowagie.text.pdf.*;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class Main extends TelegramLongPollingBot {

    private Map<Long, UserData> userDataMap = new HashMap<>();

    @Override
    public void onUpdateReceived(Update update) {
        long chatId = update.getMessage() != null ? update.getMessage().getChatId() : update.getCallbackQuery().getMessage().getChatId();

        // Обработка нажатий на кнопки
        if (update.hasCallbackQuery()) {
            CallbackQuery callbackQuery = update.getCallbackQuery();
            String callbackData = callbackQuery.getData();

            if (callbackData.equals("phone") || callbackData.equals("account")) {
                sendMessage(chatId, "Введите данные в нужном формате:\n" + getFormatExample(callbackData));
                userDataMap.get(chatId).setOption(callbackData);
            } else if (callbackData.equals("back")) {
                userDataMap.get(chatId).setOption(null);
                sendMessageWithButtons(chatId);
            }
        }

        // Обработка текстовых сообщений
        else if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText();

            if (!userDataMap.containsKey(chatId)) {
                userDataMap.put(chatId, new UserData());
                sendMessageWithButtons(chatId);
            }

            UserData userData = userDataMap.get(chatId);

            if (userData != null) {
                if (userData.getOption() != null) {
                    String[] lines = text.split("\n");

                    if (userData.getOption().equals("phone")) {
                        // Проверка на количество строк для телефона
                        if (lines.length == 10) {
                            parseUserData(userData, lines);
                            generateAndSendPdf(chatId, userData);  // Генерация PDF для телефона
                        } else {
                            sendMessage(chatId, "Некорректный ввод для телефона. Пожалуйста, введите 10 данных.");
                        }
                    } else if (userData.getOption().equals("account")) {
                        // Проверка на количество строк для счёта
                        if (lines.length == 9) {
                            parseUserData(userData, lines);
                            generateAndSendPdf(chatId, userData);  // Генерация PDF для номера счёта
                        } else {
                            sendMessage(chatId, "Некорректный ввод для номера счёта. Пожалуйста, введите 9 данных.");
                        }
                    }
                }
            }
        }
    }

    private void sendMessageWithButtons(long chatId) {
        try {
            String messageText = "*Выберите способ отрисовки:*";
            InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();

            InlineKeyboardButton button1 = new InlineKeyboardButton();
            button1.setText("1️⃣ - Телефон получателя");
            button1.setCallbackData("phone");

            InlineKeyboardButton button2 = new InlineKeyboardButton();
            button2.setText("2️⃣ - Номер счёта получателя");
            button2.setCallbackData("account");

            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(button1);
            row.add(button2);

            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            keyboard.add(row);

            keyboardMarkup.setKeyboard(keyboard);

            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText(messageText);
            message.setParseMode("Markdown");
            message.setReplyMarkup(keyboardMarkup);

            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
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

    private void generateAndSendPdf(long chatId, UserData userData) {
        try {
            if (userData == null || userData.getOption() == null) {
                sendMessage(chatId, "Ошибка: Не выбрана опция или отсутствуют данные.");
                return;
            }

            ByteArrayOutputStream pdfStream = createPdfFromTemplate(userData);
            ByteArrayInputStream inputPdfStream = new ByteArrayInputStream(pdfStream.toByteArray());
            ByteArrayOutputStream compressedPdfStream = PdfCompressor.compressPdf(inputPdfStream);

            String uniqueFileName = generateUniqueFileName(userData.getDocument());
            SendDocument sendDocument = new SendDocument();
            sendDocument.setChatId(chatId);

            InputFile inputFile = new InputFile();
            inputFile.setMedia(new ByteArrayInputStream(compressedPdfStream.toByteArray()), uniqueFileName);
            sendDocument.setDocument(inputFile);

            execute(sendDocument);
        } catch (Exception e) {
            e.printStackTrace();
            sendMessage(chatId, "Произошла ошибка при генерации PDF. Пожалуйста, повторите попытку.");
        }
    }

    private String getFormatExample(String option) {
        if ("phone".equals(option)) {
            return "*Пример ввода [Телефон]:*\n" +
                    "Дата и время: `16 января 2025 20:07:36 (МСК)`\n" +
                    "ФИО получателя: `Иван Иванович П.`\n" +
                    "Телефон получателя: `+7(912) 345-67-89`\n" +
                    "Номер счёта получателя: `**** 1234`\n" +
                    "ФИО отправителя: `Петр Петрович К.`\n" +
                    "Номер счёта отправителя: `**** 5678`\n" +
                    "Сумма перевода: `10,00 ₽`\n" +
                    "Комиссия: `0,00 ₽`\n" +
                    "Номер документа: `1000000000934111068`\n" +
                    "Код авторизации: `362135`";
        } else if ("account".equals(option)) {
            return "*Пример ввода [Номер счёта]:*\n" +
                    "Дата и время: `16 января 2025 20:07:36 (МСК)`\n" +
                    "ФИО получателя: `Иван Иванович П.`\n" +
                    "Номер счёта получателя: `**** 3083`\n" +
                    "ФИО отправителя: `Петр Петрович К.`\n" +
                    "Номер счёта отправителя: `**** 5678`\n" +
                    "Сумма перевода: `10,00 ₽`\n" +
                    "Комиссия: `0,00 ₽`\n" +
                    "Номер документа: `1000000000934111068`\n" +
                    "Код авторизации: `362135`";
        }
        return "";
    }



    private void parseUserData(UserData userData, String[] lines) {
        userData.setDate(lines[0].trim());
        userData.setRecipientName(lines[1].trim());

        if ("phone".equals(userData.getOption())) {
            userData.setRecipientPhone(lines[2].trim());
            userData.setRecipientAccount(lines[3].trim());
        } else if ("account".equals(userData.getOption())) {
            userData.setRecipientAccount(lines[2].trim());
        }

        userData.setSenderName(lines[3].trim());
        userData.setSenderAccount(lines[4].trim());
        userData.setTransferAmount(lines[5].trim());
        userData.setCommission(lines[6].trim());
        userData.setDocument(lines[7].trim());

        if (lines.length > 8) {
            userData.setAuth(lines[8].trim());
        }
    }


    public ByteArrayOutputStream createPdfFromTemplate(UserData userData) throws Exception {
        if (userData == null || userData.getOption() == null) {
            throw new IllegalArgumentException("Ошибка: Не выбрана опция.");
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // Путь к PDF-шаблону
        String templatePath = userData.getOption().equals("account") ?
                "C:\\Users\\kitaezov\\Desktop\\untitled\\src\\main\\java\\com\\check\\card.pdf" : // Путь для "Номер счёта"
                "C:\\Users\\kitaezov\\Desktop\\untitled\\src\\main\\java\\com\\check\\number.pdf"; // Путь для "Телефон получателя"

        // Загружаем шаблон PDF
        FileInputStream template = new FileInputStream(templatePath);
        PdfReader reader = new PdfReader(template);
        PdfStamper stamper = new PdfStamper(reader, outputStream);

        // Получаем содержимое страницы для рисования
        PdfContentByte content = stamper.getOverContent(1); // Для первой страницы

        // Устанавливаем шрифт Arial
        BaseFont baseFont = BaseFont.createFont("C:/Windows/Fonts/arial.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        content.setFontAndSize(baseFont, 12);

        // Записываем текст в нужные места
        content.beginText();

        if ("phone".equals(userData.getOption())) {


            // Для ФИО получателя
            content.setTextMatrix(100 - 81.1f, 525f);
            content.showText(userData.getRecipientName());

            // Для Телефона получателя
            content.setTextMatrix(100 - 81.1f, 491f);
            content.showText(userData.getRecipientPhone());

            // Для Номера счёта получателя
            content.setTextMatrix(100 - 81.1f, 457f);
            content.showText(userData.getRecipientAccount());

            // Для ФИО отправителя
            content.setTextMatrix(100 - 81.1f, 398.5f);
            content.showText(userData.getSenderName());

            // Для Счёта отправителя
            content.setTextMatrix(100 - 81.1f, 364.5f);
            content.showText(userData.getSenderAccount());

            // Для Суммы перевода
            content.setTextMatrix(100 - 81.1f, 330.5f);
            content.showText(userData.getTransferAmount());

            // Для Комиссии
            content.setTextMatrix(100 - 81.1f, 296.5f);
            content.showText(userData.getCommission());

            // Для номера документа
            content.setTextMatrix(100 - 81.1f, 239f);
            content.showText(userData.getDocument());

            content.setColorFill(new Color(112, 112, 112));  // Устанавливаем цвет для текста даты

            content.setTextMatrix(100 - 81.1f + 47.5f, 616f); // жопалывлаыофвлаываыва
            content.showText(userData.getDate());

            // Для Кода авторизации, только если выбран "Телефон"
            if (userData.getAuth() != null && !userData.getAuth().isEmpty()) {
                content.setColorFill(Color.BLACK); // Устанавливаем черный цвет текста
                content.setTextMatrix(100 - 81.1f, 204.5f);
                content.showText(userData.getAuth());
            }
        }

        if ("account".equals(userData.getOption())) {
            // Для Даты
            content.setColorFill(new Color(112, 112, 112)); // Цвет для даты
            content.setTextMatrix(100 - 81.1f + 47.5f, 616f);
            content.showText(userData.getDate());

            content.setColorFill(Color.BLACK); // Цвет текста

            // Для Номера карты получателя
            content.setTextMatrix(100 - 81.1f, 491f);
            content.showText(userData.getRecipientAccount());

            // Для ФИО получателя
            content.setTextMatrix(100 - 81.1f, 525f);
            content.showText(userData.getRecipientName());

            // Для Счёта отправителя
            content.setTextMatrix(100 - 81.1f, 398.5f);
            content.showText(userData.getSenderAccount());

            // Для ФИО отправителя
            content.setTextMatrix(100 - 81.1f, 433.5f);
            content.showText(userData.getSenderName());

            // Для Суммы перевода
            content.setTextMatrix(100 - 81.1f, 364.5f);
            content.showText(userData.getTransferAmount());

            // Для Комиссии
            content.setTextMatrix(100 - 81.1f, 330.5f);
            content.showText(userData.getCommission());

            // Для номера документа
            content.setTextMatrix(100 - 81.1f, 273f);
            content.showText(userData.getDocument());

            if (userData.getAuth() != null && !userData.getAuth().isEmpty()) {
                content.setColorFill(Color.BLACK); // Устанавливаем черный цвет текста
                content.setTextMatrix(100 - 81.1f, 239f);
                content.showText(userData.getAuth());
            }

        }



        content.setColorFill(Color.BLACK);

        content.endText();

        // Сохраняем документ
        stamper.close();
        reader.close();

        return outputStream;
    }


    private void addPdfMetadata(PdfStamper stamper) throws Exception {
        PdfDictionary info = stamper.getWriter().getInfo();
        info.put(PdfName.CREATOR, new PdfString(getLibraryVersion()));
        info.put(PdfName.PRODUCER, new PdfString(getLibraryVersions()));
    }

    private String getLibraryVersion() {
        return "JasperReports Library version 6.18.1-9d75d1969e774d4f179fb3be8401e98a0e6d1611";
    }

    private String getLibraryVersions() {
        return " iText 2.1.7 by 1T3XT";
    }

    private String generateUniqueFileName(String documentNumber) {
        return "0007_" + documentNumber + ".pdf";
    }


    @Override
    public String getBotUsername() {
        return "@apkojdkoadjawjdapwoidbot"; // Замените на имя вашего бота
    }

    @Override
    public String getBotToken() {
        return "7931742211:AAHRbYo_WRFXPEq2F6YtmIArcma6kcVQclY"; // Замените на токен вашего бота
    }

    public static void main(String[] args) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new Main());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}

class UserData {
    private String recipientName;
    private String recipientPhone;
    private String recipientAccount;
    private String senderName;
    private String senderAccount;
    private String transferAmount;
    private String commission;
    private String document;
    private String auth;
    private String date;
    private String option; // Add this field to store the selected option

    // Getter and setter for the option field
    public String getOption() {
        return option;
    }

    public void setOption(String option) {
        this.option = option;
    }

    // Existing getters and setters for other fields
    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    public String getRecipientPhone() {
        return recipientPhone;
    }

    public void setRecipientPhone(String recipientPhone) {
        this.recipientPhone = recipientPhone;
    }

    public String getRecipientAccount() {
        return recipientAccount;
    }

    public void setRecipientAccount(String recipientAccount) {
        this.recipientAccount = recipientAccount;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getSenderAccount() {
        return senderAccount;
    }

    public void setSenderAccount(String senderAccount) {
        this.senderAccount = senderAccount;
    }

    public String getTransferAmount() {
        return transferAmount;
    }

    public void setTransferAmount(String transferAmount) {
        this.transferAmount = transferAmount;
    }

    public String getCommission() {
        return commission;
    }

    public void setCommission(String commission) {
        this.commission = commission;
    }

    public String getDocument() {
        return document;
    }

    public void setDocument(String document) {
        this.document = document;
    }

    public String getAuth() {
        return auth;
    }

    public void setAuth(String auth) {
        this.auth = auth;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }
}

