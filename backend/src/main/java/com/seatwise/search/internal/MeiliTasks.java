package com.seatwise.search.internal;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.model.Task;
import com.meilisearch.sdk.model.TaskInfo;
import com.meilisearch.sdk.model.TaskStatus;
import java.time.Duration;

/** Meilisearch applies writes asynchronously; these wait for one task to finish. */
final class MeiliTasks {

    private static final Duration WAIT = Duration.ofSeconds(30);
    private static final long POLL_MILLIS = 25;

    private MeiliTasks() {}

    /** Waits until the task succeeded, failed or was cancelled, and returns it. */
    static Task awaitFinished(Client client, TaskInfo info) {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (true) {
            Task task = client.getTask(info.getTaskUid());
            TaskStatus status = task.getStatus();
            if (status == TaskStatus.SUCCEEDED || status == TaskStatus.FAILED || status == TaskStatus.CANCELED) {
                return task;
            }
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException(
                        "Meilisearch task " + info.getTaskUid() + " still " + status + " after " + WAIT);
            }
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for Meilisearch", e);
            }
        }
    }

    /** Waits for the task; anything but success is an error. */
    static void awaitSuccess(Client client, TaskInfo info) {
        Task task = awaitFinished(client, info);
        if (task.getStatus() != TaskStatus.SUCCEEDED) {
            throw new IllegalStateException("Meilisearch task " + info.getTaskUid() + " (" + info.getType()
                    + ") did not succeed: " + errorCode(task));
        }
    }

    static String errorCode(Task task) {
        return task.getError() == null ? String.valueOf(task.getStatus()) : task.getError().getCode();
    }
}
