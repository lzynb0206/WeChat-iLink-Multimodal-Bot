package com.example.demo.agent.state;

import com.example.demo.agent.model.AgentPlan;
import com.example.demo.agent.model.AgentTaskDefinition;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class AgentStateMachine {
    private static final Set<AgentTaskStatus> TERMINAL_STATUSES = Set.of(
            AgentTaskStatus.SUCCEEDED,
            AgentTaskStatus.FAILED,
            AgentTaskStatus.SKIPPED);

    public AgentRun createRun(AgentPlan plan) {
        if (plan == null || plan.tasks().isEmpty()) {
            throw new IllegalArgumentException("无法为无任务计划创建Agent运行实例");
        }
        AgentRun run = new AgentRun(plan);
        synchronized (run) {
            refreshReadyTasks(run);
            updateRunStatus(run);
        }
        return run;
    }

    public List<AgentTaskDefinition> readyTasks(AgentRun run) {
        synchronized (run) {
            return run.plan().tasks().stream()
                    .filter(task -> state(run, task.id()).status() == AgentTaskStatus.READY)
                    .toList();
        }
    }

    public void startTask(AgentRun run, String taskId) {
        synchronized (run) {
            AgentTaskState current = requireStatus(run, taskId, AgentTaskStatus.READY);
            run.taskStates().put(taskId, new AgentTaskState(
                    taskId,
                    AgentTaskStatus.RUNNING,
                    current.attempts() + 1,
                    null,
                    null,
                    Instant.now(),
                    null));
            run.touch();
            updateRunStatus(run);
        }
    }

    public void succeedTask(AgentRun run, String taskId, String result) {
        if (!StringUtils.hasText(result)) {
            throw new IllegalArgumentException("成功任务必须保存非空执行结果");
        }
        synchronized (run) {
            AgentTaskState current = requireStatus(run, taskId, AgentTaskStatus.RUNNING);
            run.taskStates().put(taskId, new AgentTaskState(
                    taskId,
                    AgentTaskStatus.SUCCEEDED,
                    current.attempts(),
                    result.trim(),
                    null,
                    current.startedAt(),
                    Instant.now()));
            refreshReadyTasks(run);
            run.touch();
            updateRunStatus(run);
        }
    }

    public void failTask(AgentRun run, String taskId, String error) {
        synchronized (run) {
            AgentTaskState current = requireStatus(run, taskId, AgentTaskStatus.RUNNING);
            AgentTaskDefinition definition = definition(run, taskId);
            boolean canRetry = current.attempts() < definition.maxAttempts();
            run.taskStates().put(taskId, new AgentTaskState(
                    taskId,
                    canRetry ? AgentTaskStatus.READY : AgentTaskStatus.FAILED,
                    current.attempts(),
                    null,
                    StringUtils.hasText(error) ? error.trim() : "任务执行失败",
                    current.startedAt(),
                    canRetry ? null : Instant.now()));
            if (!canRetry) {
                skipBlockedTasks(run);
            }
            run.touch();
            updateRunStatus(run);
        }
    }

    public void skipTask(AgentRun run, String taskId, String reason) {
        synchronized (run) {
            AgentTaskState current = state(run, taskId);
            if (current.status() != AgentTaskStatus.PENDING
                    && current.status() != AgentTaskStatus.READY) {
                throw new IllegalStateException(
                        "只有PENDING或READY任务可以跳过：" + taskId);
            }
            run.taskStates().put(taskId, new AgentTaskState(
                    taskId,
                    AgentTaskStatus.SKIPPED,
                    current.attempts(),
                    null,
                    StringUtils.hasText(reason) ? reason.trim() : "任务被跳过",
                    current.startedAt(),
                    Instant.now()));
            skipBlockedTasks(run);
            run.touch();
            updateRunStatus(run);
        }
    }

    public AgentRunSnapshot snapshot(AgentRun run) {
        synchronized (run) {
            return new AgentRunSnapshot(
                    run.id(),
                    run.plan().goal(),
                    run.status(),
                    run.createdAt(),
                    run.updatedAt(),
                    new ArrayList<>(run.taskStates().values()));
        }
    }

    public boolean isFinished(AgentRun run) {
        synchronized (run) {
            return run.status() == AgentRunStatus.SUCCEEDED
                    || run.status() == AgentRunStatus.FAILED;
        }
    }

    private void refreshReadyTasks(AgentRun run) {
        for (AgentTaskDefinition task : run.plan().tasks()) {
            AgentTaskState current = state(run, task.id());
            if (current.status() != AgentTaskStatus.PENDING) {
                continue;
            }
            boolean allDependenciesSucceeded = task.dependsOn().stream()
                    .allMatch(dependency -> state(run, dependency).status()
                            == AgentTaskStatus.SUCCEEDED);
            if (allDependenciesSucceeded) {
                run.taskStates().put(task.id(), new AgentTaskState(
                        task.id(), AgentTaskStatus.READY, current.attempts(),
                        null, null, null, null));
            }
        }
    }

    private void skipBlockedTasks(AgentRun run) {
        boolean changed;
        do {
            changed = false;
            for (AgentTaskDefinition task : run.plan().tasks()) {
                AgentTaskState current = state(run, task.id());
                if (current.status() != AgentTaskStatus.PENDING
                        && current.status() != AgentTaskStatus.READY) {
                    continue;
                }
                boolean blocked = task.dependsOn().stream()
                        .map(dependency -> state(run, dependency).status())
                        .anyMatch(status -> status == AgentTaskStatus.FAILED
                                || status == AgentTaskStatus.SKIPPED);
                if (blocked) {
                    run.taskStates().put(task.id(), new AgentTaskState(
                            task.id(), AgentTaskStatus.SKIPPED, current.attempts(),
                            null, "前置任务失败或被跳过", current.startedAt(), Instant.now()));
                    changed = true;
                }
            }
        } while (changed);
    }

    private void updateRunStatus(AgentRun run) {
        List<AgentTaskState> states = new ArrayList<>(run.taskStates().values());
        boolean allSucceeded = states.stream()
                .allMatch(task -> task.status() == AgentTaskStatus.SUCCEEDED);
        if (allSucceeded) {
            run.status(AgentRunStatus.SUCCEEDED);
            return;
        }
        boolean allTerminal = states.stream()
                .allMatch(task -> TERMINAL_STATUSES.contains(task.status()));
        if (allTerminal) {
            run.status(AgentRunStatus.FAILED);
            return;
        }
        boolean hasStarted = states.stream().anyMatch(task -> task.attempts() > 0);
        run.status(hasStarted ? AgentRunStatus.RUNNING : AgentRunStatus.PLANNED);
    }

    private AgentTaskState requireStatus(
            AgentRun run,
            String taskId,
            AgentTaskStatus required) {
        AgentTaskState state = state(run, taskId);
        if (state.status() != required) {
            throw new IllegalStateException(
                    "任务%s当前状态为%s，要求状态为%s"
                            .formatted(taskId, state.status(), required));
        }
        return state;
    }

    private AgentTaskState state(AgentRun run, String taskId) {
        AgentTaskState state = run.taskStates().get(taskId);
        if (state == null) {
            throw new IllegalArgumentException("不存在的Agent任务：" + taskId);
        }
        return state;
    }

    private AgentTaskDefinition definition(AgentRun run, String taskId) {
        return run.plan().tasks().stream()
                .filter(task -> task.id().equals(taskId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不存在的Agent任务：" + taskId));
    }
}
