package study.inno.ThreadPool;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

public class SearchOfMening {
    private final static Collection<Character> wordTerminators = new HashSet<>();
    private LinkedList<String> searchResult = new LinkedList<>();
    private final static int linesToSave = 500;
    private int foundSentences;
    private Collection<String> searchWords = new HashSet<>();
    private final static Collection<Character> sentenceTerminators = new HashSet<>();

    static {
        sentenceTerminators.add('!');
        sentenceTerminators.add('?');
        sentenceTerminators.add('.');
        sentenceTerminators.add('…');

        wordTerminators.addAll(sentenceTerminators);
        wordTerminators.add(',');
    }

    private SearchSaveProc saveProc;
    private int searchMethod = 1;

    public int getSearchMethod() {
        return searchMethod;
    }

    public void setSearchMethod(int searchMethod) {
        switch (searchMethod) {
            case 0:
            case 2:
                this.searchMethod = searchMethod;
                break;
            case 1:
            default:
                this.searchMethod = 1;
                break;
        }
    }

    public void getOccurencies(String[] sourceFiles, String[] searchWords, String resultFileName) throws Exception {
        assignSearchWords(searchWords);

        Files.deleteIfExists(Paths.get(resultFileName));
        foundSentences = 0;

        long beginTime = System.currentTimeMillis();
        (saveProc = new SearchSaveProc(resultFileName)).start();
        new TreadPool().add(newTasks(sourceFiles)).start().join();
        saveTail();

        System.out.println("Found sentences: " + foundSentences + ".\r\nTime spent: " + (System.currentTimeMillis() - beginTime) + " msec.");

        clear();
    }

    private Runnable[] newTasks(String[] sourceFiles) {
        return Arrays.stream(sourceFiles).map(this::newTask).toArray(Runnable[]::new);
    }

    private Runnable newTask(String fileUrl) {
        switch (searchMethod) {
            case 0:
                return new SearchMeaningTaskScanBySentences(fileUrl);
            case 2:
                return new SearchMeaningTaskScanByWords(fileUrl);
            default:
                return new SearchMeaningTaskStreams(fileUrl);
        }
    }

    private void assignSearchWords(String[] wordsForSearch) {
        this.searchWords.clear();

        //для ускорения поиска слов
        for (String word : wordsForSearch) {
            if (word != null && word.length() > 0) {
                word = word.trim().toLowerCase();

                if (word.length() > 0) {
                    this.searchWords.add(word);
                }
            }
        }

        System.out.println("Unique words to search in files: " + this.searchWords.size());
    }

    private void saveTail() throws InterruptedException {
        saveProc.setSearchComplete(true);
        synchronized (searchResult) {
            searchResult.notifyAll();
        }
        saveProc.join();
    }

    public void clear() {
        this.searchWords.clear();
        saveProc = null;
    }

    private class SearchSaveProc extends Thread {
        private boolean searchComplete = false;
        private String resultFileName;

        public SearchSaveProc(String resultFileName) {
            this.resultFileName = resultFileName;
        }

        public void setSearchComplete(boolean complete) {
            this.searchComplete = complete;
        }

        @Override
        public void run() {
            String sentence;
            byte[] endOfLine = new byte[]{'\r', '\n'};

            try (BufferedOutputStream toFile = new BufferedOutputStream(new FileOutputStream(resultFileName))) {
                while (!interrupted()) {
                    synchronized (searchResult) {
                        if (searchComplete || searchResult.size() >= linesToSave) {
                            while ((sentence = searchResult.poll()) != null) {
                                toFile.write(sentence.getBytes());
                                toFile.write(endOfLine);
                            }
                        }

                        if (searchComplete) {
                            break;
                        } else {
                            searchResult.wait();
                        }
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private class BaseSearchMeaningTask {
        protected String fileName;
        protected Collection<String> sentenceWords = new HashSet<>();

        public BaseSearchMeaningTask(String fileName) {
            this.fileName = fileName;
        }


        protected void checkSentence(Object sentence) {
            if (searchIntersection(searchWords, sentenceWords)) synchronized (searchResult) {
                ++foundSentences;
//                        System.out.println(foundSentences + " " + sentence);

                searchResult.add(sentence instanceof String ? (String) sentence : sentence.toString());
                searchResult.notifyAll();
            }
            sentenceWords.clear();
        }

        protected boolean searchIntersection(Collection<String> first, Collection<String> second) {
            try {
                //Ищем меньшее количество слов в большем - так быстрее
                if (second.size() < first.size()) {
                    Collection<String> tmp = first;
                    first = second;
                    second = tmp;
                }

                for (String word : first) {
                    if (second.contains(word)) {
                        return true;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }
    }

    private class SearchMeaningTaskScanBySentences extends BaseSearchMeaningTask implements Runnable {

        public SearchMeaningTaskScanBySentences(String fileName) {
            super(fileName);
        }

        /**
         * <p>
         * Загрузка из потока по предложениям, разбивка предложения на слова, поиск совпадений.
         * <p>
         */
        @Override
        public void run() {
            try (Scanner scanner = new Scanner(
                    new BufferedReader(
                            new InputStreamReader(
                                    new URL(fileName).openStream())));) {
                scanner.useDelimiter(Pattern.compile("(?<=[.!?…])[\\s$]+?"));
                String sentence;

                while (scanner.hasNext()) {
                    sentence = scanner.next().trim();

                    for (String word : sentence.split("[\\s,.?!…]+")) {
                        sentenceWords.add(word.toLowerCase());
                    }

                    checkSentence(sentence);
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private class SearchMeaningTaskScanByWords extends BaseSearchMeaningTask implements Runnable {

        public SearchMeaningTaskScanByWords(String fileName) {
            super(fileName);
        }

        /**
         * Поиск предложений с заданными словами.
         * Чтение из потока по словам, затем проверка,
         * а предложение формируется в StringBuilder'е.
         */
        @Override
        public void run() {
            try (Scanner scanner = new Scanner(
                    new BufferedReader(
                            new InputStreamReader(
                                    new URL(fileName).openStream())));) {
                scanner.useDelimiter(Pattern.compile("[\\s$]+"));
                String word;
                StringBuilder sentence = new StringBuilder();
                boolean endOfSentence = false;
                int length;

                while (scanner.hasNext()) {
                    word = scanner.next().trim();

                    if (word.length() > 0) {
                        if (sentence.length() > 0) {
                            sentence.append(' ');
                        }

                        sentence.append(word);
                        length = word.length();

                        endOfSentence = sentenceTerminators.contains(word.charAt(word.length() - 1));
                        while (length > 0 && wordTerminators.contains(word.charAt(length - 1))) {
                            --length;
                        }

                        word = (word.length() > length ?
                                word.substring(0, length) :
                                word).toLowerCase();
                        if (word.length() > 0) {
                            sentenceWords.add(word);
                        }

                        if (endOfSentence) {
                            checkSentence(sentence);
                            sentence.setLength(0);
                        }
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class SearchMeaningTaskStreams extends BaseSearchMeaningTask implements Runnable {

        public SearchMeaningTaskStreams(String fileName) {
            super(fileName);
        }

        @Override
        public void run() {
            try (Scanner scanner = new Scanner(
                    new BufferedReader(
                            new InputStreamReader(
                                    new URL(fileName).openStream())));) {

                while (scanner.hasNextLine()) {
                    for (String sentence : scanner.nextLine().split("(?<=[.?!…])\\s+")) {
                        Arrays.stream(sentence.split("[,.?!…\\s]+")).
                                map(str -> str.trim().toLowerCase()).
                                filter(str -> str != null && !str.equals("")).
                                forEach(sentenceWords::add);

                        checkSentence(sentence);
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

