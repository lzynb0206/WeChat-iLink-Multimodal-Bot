package com.example.demo.agent.state;

import com.example.demo.agent.AgentTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentStateMachineTests {
    private final AgentStateMachine stateMachine = new AgentStateMachine();

    @Test
    void unlocksDependentTaskAndCompletesRun() {
        AgentRun run = stateMachine.createRun(AgentTestFixtures.validPlan());

        assertEquals(2, stateMachine.readyTasks(run).size());
        assertThrows(IllegalStateException.class,
                () -> stateMachine.startTask(run, "final_report"));

        complete(run, "inspect", "项目结构结果");
        assertEquals(1, stateMachine.readyTasks(run).size());
        complete(run, "security", "安全检查结果");
        assertEquals("final_report", stateMachine.readyTasks(run).getFirst().id());
        complete(run, "final_report", "最终交付报告");

        AgentRunSnapshot snapshot = stateMachine.snapshot(run);
        assertEquals(AgentRunStatus.SUCCEEDED, snapshot.status());
        assertTrue(stateMachine.isFinished(run));
        assertTrue(snapshot.tasks().stream()
                .allMatch(task -> task.status() == AgentTaskStatus.SUCCEEDED));
    }

    @Test
    void retriesTaskThenFailsAndSkipsDependents() {
        AgentRun run = stateMachine.createRun(AgentTestFixtures.validPlan());

        stateMachine.startTask(run, "inspect");
        stateMachine.failTask(run, "inspect", "第一次网络失败");
        AgentTaskState retryState = states(run).get("inspect");
        assertEquals(AgentTaskStatus.READY, retryState.status());
        assertEquals(1, retryState.attempts());

        stateMachine.startTask(run, "inspect");
        stateMachine.failTask(run, "inspect", "第二次仍然失败");
        assertEquals(AgentTaskStatus.FAILED, states(run).get("inspect").status());
        assertEquals(AgentTaskStatus.SKIPPED, states(run).get("final_report").status());
        assertFalse(stateMachine.isFinished(run));

        complete(run, "security", "安全检查仍然可以独立完成");
        assertEquals(AgentRunStatus.FAILED, stateMachine.snapshot(run).status());
        assertTrue(stateMachine.isFinished(run));
    }

    private void complete(AgentRun run, String taskId, String result) {
        stateMachine.startTask(run, taskId);
        stateMachine.succeedTask(run, taskId, result);
    }

    private Map<String, AgentTaskState> states(AgentRun run) {
        return stateMachine.snapshot(run).tasks().stream()
                .collect(Collectors.toMap(AgentTaskState::taskId, Function.identity()));
    }
}
