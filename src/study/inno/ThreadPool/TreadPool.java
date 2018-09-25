package study.inno.ThreadPool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class TreadPool {
    private List<Slave> slaves = new ArrayList<>();
    private LinkedList<Runnable> tasks = new LinkedList<>();
    private boolean inJob = false;
    private boolean stop = false;
    private boolean joining = false;
    private int maxThreads;

    public TreadPool() {
        maxThreads = Runtime.getRuntime().availableProcessors();
    }

    private class Slave extends Thread {
        private Runnable task;

        @Override
        public void run() {
            while (!interrupted() && !stop) {
                task = pollTask();

                if (task == null) {
                    break;
                } else {
                    try {
                        task.run();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            onSlaveStop(this);
        }
    }

    public int getMaxThreads() {
        return maxThreads;
    }

    public void setMaxThreads(int maxThreads) throws Exception {
        if (!inJob) {
            this.maxThreads = maxThreads;
        } else {
            throw new Exception("Never, never change a lover in the middle of the night...");
        }
    }

    //в Java не сильно пока разумею
    //в С++ я бы делал suspend/resume
    //здесь сейчас просто удаляю потоки,
    //потом буду создавать новые.
    private void onSlaveStop(Slave slave) {
        synchronized (slaves) {
            int qty = 0;
            for (int iSlave = 0; iSlave < slaves.size(); iSlave++) {
                if (slaves.get(iSlave) == slave) {
                    slaves.set(iSlave, null);
                }

                if (slaves.get(iSlave) != null) {
                    ++qty;
                }
            }

            if (qty == 0) {
                inJob = false;
                stop = false;
                joining = false;
                slaves.clear();
            }
        }
    }

    private synchronized Runnable pollTask() {
        if (inJob && !stop) {
            return tasks.poll();
        } else {
            return null;
        }
    }

    public TreadPool start() throws Exception {
        if (inJob) {
            throw new Exception("in process");
        } else if (joining) {
            throw new Exception("joining in process");
        } else if (stop) {
            throw new Exception("stopping processes");
        } else synchronized (slaves) {
            int needThreads = Math.min(maxThreads, tasks.size());

            if (needThreads < 1) {
                return this;
            }

            for (int iSlave = 0; iSlave < needThreads; iSlave++) {
                slaves.add(new Slave());
            }

            inJob = true;

            for (int iSlave = 0; iSlave < needThreads; iSlave++) {
                slaves.get(iSlave).start();
            }
        }

        return this;
    }

    public TreadPool join() throws InterruptedException {
        if (inJob) {
            joining = true;

            while (inJob) {
                Thread.currentThread().sleep(1);
            }
        }
        return this;
    }

    public void stop() {
        stop = true;
    }

    public TreadPool add(Runnable task) {
        synchronized (tasks) {
            tasks.add(task);
        }
        return this;
    }

    public TreadPool add(Object[] tasks) throws Exception {
        synchronized (this.tasks) {
            for (Object task : tasks) {
                if (task instanceof Runnable) {
                    this.tasks.add((Runnable) task);
                } else {
                    throw new Exception("Task must implement interface Runnable");
                }
            }
        }
        return this;
    }

    public TreadPool add(Runnable[] tasks) {
        synchronized (this.tasks) {
            this.tasks.addAll(Arrays.asList(tasks));
        }
        return this;
    }

    public TreadPool clear() {
        synchronized (tasks) {
            tasks.clear();
        }
        return this;
    }

    public int remainingTasks() {
        synchronized (tasks) {
            return tasks.size();
        }
    }
}
