package com.check;

import com.lowagie.text.pdf.*;
import java.io.*;
import java.util.Calendar;
import java.util.HashMap;

public class PdfCompressor {

    /**
     * Сжимает PDF до максимально возможного уровня
     *
     * @param inputPdfStream Исходный PDF в виде потока
     * @return Сжатый PDF в виде потока
     * @throws Exception Если что-то пошло не так
     */
    public static ByteArrayOutputStream compressPdf(ByteArrayInputStream inputPdfStream) throws Exception {
        PdfReader reader = new PdfReader(inputPdfStream);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // Используем PdfStamper для изменения и сжатия PDF
        PdfStamper stamper = new PdfStamper(reader, outputStream);

        // Удаляем метаданные
        PdfDictionary catalog = reader.getCatalog();
        if (catalog != null) {
            catalog.remove(PdfName.METADATA);
        }

        // Удаляем данные из словаря Info
        HashMap<String, String> info = reader.getInfo();
        if (info != null) {
            info.clear();
        }

        // Устанавливаем одинаковую дату для CreationDate и ModDate
        Calendar calendar = Calendar.getInstance();
        PdfDate currentDate = new PdfDate(calendar);
        reader.getInfo().put("CreationDate", currentDate.toString());
        reader.getInfo().put("ModDate", currentDate.toString());

        // Удаление неиспользуемых объектов
        reader.removeUnusedObjects();

        // Включаем полное сжатие
        stamper.setFullCompression();

        // Устанавливаем сжатие для шрифтов
        stamper.getWriter().setCompressionLevel(9);

        // Сжимаем шрифты и запрещаем редактирование формы
        stamper.setFormFlattening(true);

        // Закрываем stamper и reader
        stamper.close();
        reader.close();

        return outputStream;
    }
}
