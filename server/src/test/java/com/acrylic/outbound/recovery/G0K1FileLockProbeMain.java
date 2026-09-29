package com.acrylic.outbound.recovery;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * G0-K1-3 子 JVM 探针（真实独立进程；JDK-only，无第三方依赖 ⇒ 可用仅含测试类目录的 classpath 启动）。
 *
 * <p>用法：{@code <mode> <lockPath> <statusPath> [holdMillis] [extraPath]}
 *
 * <p>输出：所有可观察事实逐行**追加**到 {@code statusPath}（父进程轮询该文件，不用管道）；
 * stdout/stderr 由父进程重定向到文件。模式：
 * <ul>
 *   <li>{@code hold-exclusive} / {@code hold-shared}：持有锁 holdMillis 后显式 release 并退出；</li>
 *   <li>{@code hold-exclusive-exit}：持有排他锁，**不显式释放**，直接由 JVM 退出（OS 释放）；</li>
 *   <li>{@code try-exclusive} / {@code try-shared}：非阻塞 tryLock，记录 ACQUIRED/NULL/OVERLAP 与耗时；</li>
 *   <li>{@code halt-while-locked}：持锁后 {@code Runtime.halt(70)}（绕过 shutdown hook）；</li>
 *   <li>{@code delete-while-locked} / {@code rename-while-locked}：持锁时删除/重命名锁文件，记录结果；</li>
 *   <li>{@code close-then-relock}：同一 JVM 内 close channel 前后 {@code isValid()} 与再次加锁结果；</li>
 *   <li>{@code delete-file}：直接删除锁文件（用于“外部删除”场景）。</li>
 * </ul>
 */
public final class G0K1FileLockProbeMain {

    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("usage: <mode> <lockPath> <statusPath> [holdMillis] [extraPath]");
            System.exit(2);
        }
        String mode = args[0];
        Path lockPath = Path.of(args[1]);
        Path statusPath = Path.of(args[2]);
        long holdMillis = args.length > 3 && !args[3].isBlank() ? Long.parseLong(args[3]) : 0L;
        Path extra = args.length > 4 && !args[4].isBlank() ? Path.of(args[4]) : null;
        try {
            status(statusPath, "START mode=" + mode + " pid=" + ProcessHandle.current().pid()
                + " jvm=" + System.getProperty("java.version") + " os=" + System.getProperty("os.name")
                + " at=" + Instant.now());
            switch (mode) {
                case "hold-exclusive" -> hold(lockPath, statusPath, holdMillis, false, true);
                case "hold-shared" -> hold(lockPath, statusPath, holdMillis, true, true);
                case "hold-exclusive-exit" -> hold(lockPath, statusPath, holdMillis, false, false);
                case "try-exclusive" -> tryLock(lockPath, statusPath, false);
                case "try-shared" -> tryLock(lockPath, statusPath, true);
                case "halt-while-locked" -> haltWhileLocked(lockPath, statusPath);
                case "delete-while-locked" -> deleteWhileLocked(lockPath, statusPath);
                case "rename-while-locked" -> renameWhileLocked(lockPath, statusPath, extra);
                case "close-then-relock" -> closeThenRelock(lockPath, statusPath);
                case "delete-file" -> {
                    boolean deleted = Files.deleteIfExists(lockPath);
                    status(statusPath, "DELETE-FILE deleted=" + deleted + " existsAfter=" + Files.exists(lockPath));
                }
                default -> {
                    status(statusPath, "ERROR unknown mode=" + mode);
                    System.exit(2);
                }
            }
            status(statusPath, "END at=" + Instant.now());
            System.exit(0);
        } catch (Throwable failure) {
            statusSafe(statusPath, "THROWN " + failure.getClass().getName() + ": " + failure.getMessage());
            failure.printStackTrace();
            System.exit(3);
        }
    }

    /** catch 块内使用：写入状态失败也不能再抛（否则丢失真实错误原因）。 */
    private static void statusSafe(Path statusPath, String line) {
        try {
            status(statusPath, line);
        } catch (Exception ignored) {
            System.err.println(line);
        }
    }

    private static void hold(Path lockPath, Path statusPath, long holdMillis, boolean shared, boolean release)
            throws Exception {
        try (FileChannel channel = open(lockPath)) {
            FileLock lock = channel.lock(0L, Long.MAX_VALUE, shared);
            status(statusPath, "LOCKED shared=" + lock.isShared() + " valid=" + lock.isValid()
                + " channelPid=" + ProcessHandle.current().pid());
            sleep(holdMillis);
            if (release) {
                lock.release();
                status(statusPath, "RELEASED valid=" + lock.isValid());
            } else {
                status(statusPath, "EXITING-WITHOUT-RELEASE valid=" + lock.isValid());
            }
        }
    }

    private static void tryLock(Path lockPath, Path statusPath, boolean shared) throws Exception {
        try (FileChannel channel = open(lockPath)) {
            long started = System.nanoTime();
            try {
                FileLock lock = channel.tryLock(0L, Long.MAX_VALUE, shared);
                long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
                if (lock == null) {
                    status(statusPath, "TRYLOCK-NULL shared=" + shared + " elapsedMs=" + elapsedMs);
                } else {
                    status(statusPath, "TRYLOCK-ACQUIRED shared=" + lock.isShared() + " elapsedMs=" + elapsedMs);
                    lock.release();
                }
            } catch (OverlappingFileLockException overlap) {
                status(statusPath, "TRYLOCK-OVERLAP " + overlap.getClass().getName());
            }
        }
    }

    private static void haltWhileLocked(Path lockPath, Path statusPath) throws Exception {
        FileChannel channel = open(lockPath);
        FileLock lock = channel.lock(0L, Long.MAX_VALUE, false);
        status(statusPath, "LOCKED shared=" + lock.isShared() + " valid=" + lock.isValid() + " halting=70");
        Runtime.getRuntime().halt(70);   // 绕过 shutdown hook，不释放、不关闭
    }

    private static void deleteWhileLocked(Path lockPath, Path statusPath) throws Exception {
        try (FileChannel channel = open(lockPath)) {
            FileLock lock = channel.lock(0L, Long.MAX_VALUE, false);
            status(statusPath, "LOCKED valid=" + lock.isValid());
            try {
                boolean deleted = Files.deleteIfExists(lockPath);
                status(statusPath, "DELETE-WHILE-LOCKED-OK deleted=" + deleted + " existsAfter="
                    + Files.exists(lockPath) + " lockStillValid=" + lock.isValid());
            } catch (Throwable failure) {
                status(statusPath, "DELETE-WHILE-LOCKED-THREW " + failure.getClass().getName() + ": "
                    + failure.getMessage());
            }
        }
    }

    private static void renameWhileLocked(Path lockPath, Path statusPath, Path target) throws Exception {
        try (FileChannel channel = open(lockPath)) {
            FileLock lock = channel.lock(0L, Long.MAX_VALUE, false);
            status(statusPath, "LOCKED valid=" + lock.isValid());
            try {
                Files.move(lockPath, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                status(statusPath, "RENAME-WHILE-LOCKED-OK targetExists=" + Files.exists(target)
                    + " sourceExists=" + Files.exists(lockPath) + " lockStillValid=" + lock.isValid());
            } catch (Throwable failure) {
                status(statusPath, "RENAME-WHILE-LOCKED-THREW " + failure.getClass().getName() + ": "
                    + failure.getMessage());
            }
        }
    }

    private static void closeThenRelock(Path lockPath, Path statusPath) throws Exception {
        FileChannel first = open(lockPath);
        FileLock lock = first.lock(0L, Long.MAX_VALUE, false);
        status(statusPath, "VALID-BEFORE-CLOSE=" + lock.isValid());
        first.close();
        status(statusPath, "VALID-AFTER-CLOSE=" + lock.isValid());
        try (FileChannel second = open(lockPath)) {
            FileLock again = second.tryLock(0L, Long.MAX_VALUE, false);
            status(statusPath, "RELOCK=" + (again == null ? "NULL" : "ACQUIRED"));
            if (again != null) again.release();
        }
    }

    private static FileChannel open(Path path) throws Exception {
        return FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
    }

    private static void sleep(long millis) throws InterruptedException {
        if (millis > 0) Thread.sleep(millis);
    }

    /** 逐行追加并 flush，父进程轮询该文件即可观察到中间状态。 */
    private static void status(Path statusPath, String line) throws Exception {
        Files.writeString(statusPath, line + System.lineSeparator(),
            StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
    }
}
