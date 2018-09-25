package study.inno.ThreadPool;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

public class SearchOfMening {
    private final static int threadsQty = 4;
    private SearchSaveProc saveProc;
    private LinkedList<String> searchResult = new LinkedList<>();
    private final static int linesToSave = 500;
    private int foundSentences;
    private long totalSentencesLength;
    private Collection<String> searchWords = new HashSet<>();

    public void getOccurencies(String[] sourceFiles, String[] searchWords, String resultFileName) throws Exception {
        assignSearchWords(searchWords);

        Files.deleteIfExists(Paths.get(resultFileName));
        foundSentences = 0;
        totalSentencesLength = 0;

        long beginTime = System.currentTimeMillis();
        (saveProc = new SearchSaveProc(resultFileName)).start();
        new TreadPool(threadsQty).add(Arrays.stream(sourceFiles).
                map(path -> new SearchMeaningTask(path)).toArray()).
                start().
                join();

        saveTail();

        System.out.println("Found sentences: " + foundSentences + ".\r\nTime spent: " + (System.currentTimeMillis() - beginTime) + " msec.");
        System.out.println("Total sentences length: " + totalSentencesLength);
        clear();
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

    private class SearchMeaningTask implements Runnable {
        private String fileName;
        private Collection<String> sentenceWords = new HashSet<>();
        private boolean taskInJon = false;

        public SearchMeaningTask(String fileName) {
            this.fileName = fileName;
        }

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
                    synchronized (searchResult) {
                        totalSentencesLength += sentence.length();
                    }

                    sentenceWords.clear();
                    for (String word : sentence.split("[\\s,.?!…]+")) {
                        sentenceWords.add(word.toLowerCase());
                    }

                    if (searchIntersection(searchWords, sentenceWords)) synchronized (searchResult) {
                        ++foundSentences;
                        searchResult.add(sentence);
                        searchResult.notifyAll();
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private boolean searchIntersection(Collection<String> first, Collection<String> second) {
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
                //от чего-то выскакивает
                //java.util.ConcurrentModificationException
                e.printStackTrace();
            }
            return false;
        }
    }
}

