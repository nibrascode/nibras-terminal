package com.termux.terminal;

/** Real pseudo-terminal for the remote web terminal (uses the terminal library's native helpers). */
public final class NibrasPty {
    private NibrasPty() {}

    /** Starts cmd on a new PTY. pid[0] receives the process id; returns the PTY master fd. */
    public static int start(String cmd, String cwd, String[] args, String[] env, int[] pid, int rows, int cols) {
        return JNI.createSubprocess(cmd, cwd, args, env, pid, rows, cols);
    }

    public static void resize(int fd, int rows, int cols) { JNI.setPtyWindowSize(fd, rows, cols); }

    public static int waitFor(int pid) { return JNI.waitFor(pid); }

    public static void close(int fd) { JNI.close(fd); }
}
