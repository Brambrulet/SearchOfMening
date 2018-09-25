package study.inno.ThreadPool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    private static SearchOfMening searchOfMening = new SearchOfMening();
    private static String searchPath = "C:\\Users\\Сказка\\IdeaProjects\\Day4ClassWork\\DeliriumTest\\";
    private static long bytesTotal;
    private static int filesQty;

    public static void main(String[] args) throws Exception {
        for (int searchMethod = 0; searchMethod < 3; ++searchMethod) {
            searchOfMening.setSearchMethod(searchMethod);
            searchOfMening.getOccurencies(getFiles(searchPath), getSearchWords(), "SearchResult.txt");
//        searchOfMening.getOccurencies(new String[]{"file:" + searchPath + "Delirium0.txt"}, getWords(), "SearchResult.txt");
        }
    }

    static String[] getSearchWords() {
        return new String[]{"мама", "мыла", "раму",
                "сгущалась", "тьма", "над", "пунктом", "населённым",
                "в", "густом", "саду", "коррупция", "цвела",
                "я", "ждал", "как", "полагается", "влюблённым",
                "а", "ты", "как", "полагается", "не", "шла",

                "я", "жаждал", "твоего", "коснуться", "тела",
                "любовный", "жар", "сжигал", "меня", "до", "тла",
                "а", "ты", "прийти", "ко", "мне", "не", "захотела",
                "а", "ты", "смотрите", "выше", "всё", "не", "шла",

                "полночный", "сад", "был", "залит", "лунным", "светом",
                "его", "собою", "лунный", "свет", "залил",
                "хм", "сказать", "такое", "нужно", "быть", "поэтом",
                "такое", "сочинить", "способен", "лишь", "поэт",

                "Поэт", "он", "честным", "должен", "быть", "и", "точным",
                "иначе", "не", "поэт", "он", "а", "дерьмо",
                "короче", "я", "стоял", "в", "саду", "полночном",
                "а", "ты", "как", "чмо", "последнее", "не", "шло"};
    }


    /**
     * На выходе список файлов из директории
     * без прохода по дереву каталогов.
     * <p>
     * Так же процедура выдаёт количество и объём
     * выбранных файлов.
     *
     * @param searchPath
     * @return
     * @throws IOException
     */
    static String[] getFiles(String searchPath) throws IOException {
        bytesTotal = 0;
        filesQty = 0;

        String[] urls = Files.walk(Paths.get(searchPath), 1).
                filter(path -> {
                    ++filesQty;
                    try {
                        if (Files.isRegularFile(path)) {
                            bytesTotal += Files.size(path);
                            return true;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return false;
                })
                .map(path -> "file:" + path).toArray(String[]::new);

        String filesSize;
        if (bytesTotal < 1024) {
            filesSize = bytesTotal + " B";
        } else {
            int k = (63 - Long.numberOfLeadingZeros(bytesTotal)) / 10;
            filesSize = String.format("%.1f %sB", (double) bytesTotal / (1L << (k * 10)), " KMGTPE".charAt(k));
        }

        System.out.println("Total files:" + urls.length + ", total size: " + filesSize);

        return urls;
    }
}
